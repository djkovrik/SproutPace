package com.sedsoftware.sproutpace.platform

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline

sealed interface PlatformFailure {
    data object Cancelled : PlatformFailure
    data object Unavailable : PlatformFailure
    data class Io(val code: String) : PlatformFailure
    data class Rejected(val code: String) : PlatformFailure
}

sealed interface PermissionState {
    data object Unknown : PermissionState
    data object Checking : PermissionState
    data object Requesting : PermissionState
    data object Granted : PermissionState
    data class Denied(val canRequestAgain: Boolean) : PermissionState
    data object Unavailable : PermissionState
}

interface NotificationPermissionService {
    val state: StateFlow<PermissionState>

    /** Reads current OS authorization without presenting system UI. */
    suspend fun check(): PermissionState

    /** Presents the OS authorization request when that transition is available. */
    suspend fun request(): PermissionState

    /** Rechecks after returning from Settings or whenever the host resumes. */
    suspend fun onHostResume(): PermissionState = check()
}

enum class NativeTextKey(val stableKey: String) {
    REMINDER_CHANNEL_NAME("notification_channel_reminders"),
    DAILY_SUMMARY_TITLE("notification_daily_summary_title"),
    DAILY_SUMMARY_BODY("notification_daily_summary_body"),
    SOAK_COMPLETE_TITLE("notification_soak_complete_title"),
    SOAK_COMPLETE_BODY("notification_soak_complete_body"),
}

enum class ReminderKind { DAILY_SUMMARY, SOAK }

@JvmInline
value class PhysicalReminderId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    fun requestCode(): Int {
        var hash = FNV_OFFSET_BASIS
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor (byte.toLong() and BYTE_MASK)) * FNV_PRIME
            hash = hash and UINT_MASK
        }
        return (hash and POSITIVE_INT_MASK).toInt()
    }

    private companion object {
        const val FNV_OFFSET_BASIS = 0x811c9dc5L
        const val FNV_PRIME = 0x01000193L
        const val BYTE_MASK = 0xffL
        const val UINT_MASK = 0xffffffffL
        const val POSITIVE_INT_MASK = 0x7fffffffL
    }
}

data class PhysicalReminder(
    val id: PhysicalReminderId,
    val kind: ReminderKind,
    val fireAtEpochMillis: Long,
    val titleKey: NativeTextKey,
    val bodyKey: NativeTextKey,
    val civilDateTime: kotlinx.datetime.LocalDateTime? = null,
)

data class ReminderPlan(
    val currentOrOverdueWorkCount: Int,
    val dailySummary: PhysicalReminder?,
    val soakReminders: List<PhysicalReminder>,
    val summaryWindowWorkCount: Int = currentOrOverdueWorkCount,
    val dailyEvaluationEnabled: Boolean = false,
    val dailySummaries: List<PhysicalReminder> = emptyList(),
) {
    init {
        require(currentOrOverdueWorkCount >= 0)
        require(summaryWindowWorkCount >= 0)
        require(dailySummary == null || dailySummary.kind == ReminderKind.DAILY_SUMMARY)
        require(soakReminders.all { it.kind == ReminderKind.SOAK })
        require(dailySummaries.all { it.kind == ReminderKind.DAILY_SUMMARY })
    }

    fun futurePhysicalReminders(nowEpochMillis: Long): List<PhysicalReminder> = buildList {
        if (dailyEvaluationEnabled || summaryWindowWorkCount > 0) {
            dailySummary?.takeIf { it.fireAtEpochMillis > nowEpochMillis }?.let(::add)
        }
        addAll(soakReminders.filter { it.fireAtEpochMillis > nowEpochMillis })
        addAll(dailySummaries.filter { it.fireAtEpochMillis > nowEpochMillis })
    }.distinctBy { it.id }
}

enum class ReconciliationReason {
    APP_START,
    PROCESS_RESTART,
    DEVICE_RESTART,
    APP_REPLACED,
    TIME_ZONE_CHANGED,
    PERMISSION_RESTORED,
    RESTORE_COMPLETED,
    PRODUCT_DATA_CHANGED,
    REMINDER_DELIVERED,
}

interface ReminderDriver {
    /** Slots available to this driver after accounting for requests owned by other app features. */
    suspend fun pendingCapacity(): Int? = null
    suspend fun registeredIds(): Set<PhysicalReminderId>
    suspend fun schedule(reminder: PhysicalReminder): Result<Unit>
    suspend fun cancel(id: PhysicalReminderId): Result<Unit>
}

fun remainingReminderCapacity(totalLimit: Int, otherPendingCount: Int): Int {
    require(totalLimit >= 0 && otherPendingCount >= 0)
    return (totalLimit - otherPendingCount).coerceAtLeast(0)
}

data class ReminderReconciliationResult(
    val reason: ReconciliationReason,
    val scheduled: Set<PhysicalReminderId>,
    val cancelled: Set<PhysicalReminderId>,
    val failed: Map<PhysicalReminderId, PlatformFailure>,
)

class ReminderReconciliationException(
    val failures: Map<PhysicalReminderId, PlatformFailure>,
) : Exception("reminder_schedule_failed")

fun ReminderReconciliationResult.requireSuccessful(): ReminderReconciliationResult {
    if (failed.isNotEmpty()) throw ReminderReconciliationException(failed)
    return this
}

class ReminderReconciler(private val driver: ReminderDriver) {
    suspend fun reconcile(
        plan: ReminderPlan,
        nowEpochMillis: Long,
        permission: PermissionState,
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        if (permission == PermissionState.Unknown || permission == PermissionState.Checking ||
            permission == PermissionState.Requesting
        ) {
            return ReminderReconciliationResult(reason, emptySet(), emptySet(), emptyMap())
        }
        val candidates = if (permission == PermissionState.Granted) {
            plan.futurePhysicalReminders(nowEpochMillis).associateBy { it.id }
        } else {
            emptyMap()
        }
        val existing = driver.registeredIds()
        val cancelled = linkedSetOf<PhysicalReminderId>()
        val scheduled = linkedSetOf<PhysicalReminderId>()
        val failures = linkedMapOf<PhysicalReminderId, PlatformFailure>()
        val capacity = driver.pendingCapacity()?.coerceAtLeast(0) ?: candidates.size
        val desired = candidates.values.sortedWith(compareBy({ it.fireAtEpochMillis }, { it.id.value }))
            .take(capacity).associateBy { it.id }
        (candidates.keys - desired.keys).forEach { id ->
            failures[id] = PlatformFailure.Rejected("reminder_capacity_exceeded")
        }

        (existing - desired.keys).sortedBy { it.value }.forEach { id ->
            driver.cancel(id).fold(
                onSuccess = { cancelled += id },
                onFailure = { failures[id] = it.toPlatformFailure("reminder_cancel_failed") },
            )
        }
        desired.values.sortedBy { it.id.value }.forEach { reminder ->
            driver.schedule(reminder).fold(
                onSuccess = { scheduled += reminder.id },
                onFailure = { failures[reminder.id] = it.toPlatformFailure("reminder_schedule_failed") },
            )
        }
        return ReminderReconciliationResult(reason, scheduled, cancelled, failures)
    }
}

fun interface ReminderPlanSource {
    suspend fun currentPlan(): ReminderPlan
}

fun interface EpochMillisClock {
    fun now(): Long
}

class ReminderLifecycleCoordinator(
    private val permissionService: NotificationPermissionService,
    private val planSource: ReminderPlanSource,
    private val reconciler: ReminderReconciler,
    private val clock: EpochMillisClock,
) {
    private val reconciliationMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun reconcile(
        reason: ReconciliationReason,
        beforeReconcile: suspend () -> Unit = {},
    ): ReminderReconciliationResult = reconciliationMutex.withLock {
        beforeReconcile()
        reconcileUnlocked(reason)
    }

    private suspend fun reconcileUnlocked(reason: ReconciliationReason): ReminderReconciliationResult =
        reconciler.reconcile(
            plan = planSource.currentPlan(),
            nowEpochMillis = clock.now(),
            permission = permissionService.check(),
            reason = reason,
        )

    /** Decision and next registration share the same ownership as foreground/restore reconciliation. */
    suspend fun onDelivery(decide: suspend () -> Boolean): Boolean = reconciliationMutex.withLock {
        val shouldNotify = decide()
        reconcileUnlocked(ReconciliationReason.REMINDER_DELIVERED).requireSuccessful()
        shouldNotify
    }

    suspend fun onRestoreCompleted(): ReminderReconciliationResult =
        reconcile(ReconciliationReason.RESTORE_COMPLETED)

    suspend fun onHostResume(): ReminderReconciliationResult = reconciliationMutex.withLock {
        permissionService.onHostResume()
        reconciler.reconcile(
            plan = planSource.currentPlan(),
            nowEpochMillis = clock.now(),
            permission = permissionService.state.value,
            reason = ReconciliationReason.PERMISSION_RESTORED,
        )
    }
}

private fun Throwable.toPlatformFailure(code: String): PlatformFailure {
    if (this is kotlinx.coroutines.CancellationException) throw this
    return PlatformFailure.Io(code)
}

enum class PickedContentKind { PHOTO, BACKUP_ARCHIVE }

data class ImportedFile(
    /** Present only for non-photo imports. Photo staging is addressed exclusively by [pendingToken]. */
    val appOwnedPath: String? = null,
    val pendingToken: String? = null,
    val displayName: String,
    val mediaType: String?,
    val sizeBytes: Long,
)

interface SystemFilePicker {
    suspend fun pickAndImport(kind: PickedContentKind): Result<ImportedFile?>
    suspend fun discardPendingPhoto(token: String): Result<Unit>
}

data class ExternalDocumentHandle(val opaqueLocation: String)

interface DocumentDestinationPicker {
    suspend fun createDocument(suggestedName: String, mediaType: String): Result<ExternalDocumentHandle?>
}

interface AppOwnedFileStore {
    suspend fun delete(path: String): Result<Unit>
    suspend fun deleteAll(): Result<Unit>
}

interface ProductDataEraser {
    suspend fun eraseProductData(): Result<Unit>
}

class CompleteErasureCoordinator(
    private val productDataEraser: ProductDataEraser,
    private val fileStore: AppOwnedFileStore,
    private val reminderDriver: ReminderDriver,
) {
    suspend fun erase(knownReminderIds: Set<PhysicalReminderId>): Result<Unit> = runCatching {
        productDataEraser.eraseProductData().getOrThrow()
        fileStore.deleteAll().getOrThrow()
        knownReminderIds.sortedBy { it.value }.forEach { reminderDriver.cancel(it).getOrThrow() }
    }
}
