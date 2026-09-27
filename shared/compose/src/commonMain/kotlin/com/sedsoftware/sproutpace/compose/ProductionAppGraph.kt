package com.sedsoftware.sproutpace.compose

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.start
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.russhwolf.settings.Settings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.data.ArchiveDocumentPort
import com.sedsoftware.sproutpace.data.LocalBackupService
import com.sedsoftware.sproutpace.data.LocalDataIntegrationEvent
import com.sedsoftware.sproutpace.data.LocalDataIntegrationOutput
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.LocalWorkspaceRepository
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.PrivateFileStore
import com.sedsoftware.sproutpace.data.PendingPhotoImportStore
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.ProductSettings
import com.sedsoftware.sproutpace.data.ReminderReconciliationPort
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.SummaryLocalTime
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.platform.NativeTextKey
import com.sedsoftware.sproutpace.platform.PhysicalReminder
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import com.sedsoftware.sproutpace.platform.ReminderDriver
import com.sedsoftware.sproutpace.platform.ReminderKind
import com.sedsoftware.sproutpace.platform.NotificationPermissionService
import com.sedsoftware.sproutpace.platform.PermissionState
import com.sedsoftware.sproutpace.platform.ReminderLifecycleCoordinator
import com.sedsoftware.sproutpace.platform.ReminderPlan
import com.sedsoftware.sproutpace.platform.ReminderPlanSource
import com.sedsoftware.sproutpace.platform.ReminderReconciler
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.platform.EpochMillisClock
import com.sedsoftware.sproutpace.platform.CrashReportingCoordinator
import com.sedsoftware.sproutpace.platform.CrashReportingDriver
import com.sedsoftware.sproutpace.platform.CrashReportingState
import com.sedsoftware.sproutpace.platform.DisableReason
import com.sedsoftware.sproutpace.platform.RuntimeEnvironment
import com.sedsoftware.sproutpace.root.AppRootComponent
import com.sedsoftware.sproutpace.root.AppRootDependencies
import com.sedsoftware.sproutpace.root.AppRootFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Application-scoped production graph. Hosts construct and retain exactly one instance. */
class ProductionAppGraph internal constructor(
    driverFactory: SqlDriverFactory,
    settings: Settings,
    private val privateFileStore: PrivateFileStore,
    private val pendingPhotoImportStore: PendingPhotoImportStore,
    documentPort: ArchiveDocumentPort,
    reminderDriver: ReminderDriver,
    private val notificationPermissionService: NotificationPermissionService,
    crashReportingDriver: CrashReportingDriver,
    ioDispatcher: CoroutineDispatcher,
    mainContext: CoroutineContext,
    isDebug: Boolean,
    releaseStoreFactory: StoreFactory,
    debugStoreFactory: StoreFactory,
    lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(),
    nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : AutoCloseable {
    private val lifecycleRegistry = lifecycleRegistry.also { it.create() }
    private val database = LocalDatabase(driverFactory, ioDispatcher)
    val productSettings: ProductSettings = MultiplatformProductSettings(settings)
    private val applicationScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val crashReportingCoordinator = CrashReportingCoordinator(
        productSettings,
        crashReportingDriver,
        if (isDebug) RuntimeEnvironment.DEBUG else RuntimeEnvironment.RELEASE,
    )
    private val mutableCrashReportingState = MutableStateFlow(crashReportingCoordinator.applyStoredPreference())
    private val reminderPlanSource = SnapshotReminderPlanSource(
        snapshot = { database.snapshot() },
        settings = productSettings,
        nowEpochMillis = nowEpochMillis,
        timeZone = timeZone,
    )
    private val reminderCoordinator = ReminderLifecycleCoordinator(
        permissionService = notificationPermissionService,
        planSource = reminderPlanSource,
        reconciler = ReminderReconciler(reminderDriver),
        clock = EpochMillisClock(nowEpochMillis),
    )
    private val reminderPort = SnapshotReminderReconciliationPort(reminderCoordinator)
    private val workspaceRepository = LocalWorkspaceRepository(database, privateFileStore, ioDispatcher)
    private lateinit var rootComponent: AppRootComponent
    private val backupService = LocalBackupService(
        database = database,
        files = privateFileStore,
        settings = productSettings,
        ioDispatcher = ioDispatcher,
        documents = documentPort,
        reminders = reminderPort,
        output = FirstRunNavigationOutput(mainContext) { rootComponent.resetToFirstRun() },
    )
    private val productionDataManager = ProductionScreenDataManager(
        database = database,
        settings = productSettings,
        workspaceRepository = workspaceRepository,
        backupService = backupService,
        pendingPhotos = pendingPhotoImportStore,
    )
    private val screenDataManager = ReminderAwareScreenDataManager(
        delegate = CrashAwareScreenDataManager(productionDataManager, mutableCrashReportingState.asStateFlow()),
        settings = productSettings,
        permissionService = notificationPermissionService,
        reconcile = ::reconcile,
    )
    private var closed = false

    private val productionRoot: AppRootComponent = AppRootFactory(
        productionRootDependencies(
            releaseStoreFactory = releaseStoreFactory,
            debugStoreFactory = debugStoreFactory,
            isDebug = isDebug,
            screenDataManager = screenDataManager,
            mainContext = mainContext,
            initialScreen = if (database.hasMinimumGrowingSetup()) ScreenId.TODAY else ScreenId.FIRST_RUN,
        ),
    ).create(DefaultComponentContext(lifecycleRegistry)).also { rootComponent = it }
    val root: AppRootComponent = ThemedAppRootComponent(productionRoot, productSettings.theme)

    val lifecycleState: Lifecycle.State get() = lifecycleRegistry.state

    init {
        applicationScope.launch { runCatching { pendingPhotoImportStore.clear() } }
        applicationScope.launch {
            productSettings.crashReportingEnabled.collect { preference ->
                mutableCrashReportingState.value = crashReportingCoordinator.applyPreference(preference)
            }
        }
        reconcile(ReconciliationReason.APP_START)
    }

    fun onStart() = lifecycleRegistry.start()
    fun onResume() {
        lifecycleRegistry.resume()
        applicationScope.launch { runCatching { reminderCoordinator.onHostResume() } }
    }
    fun onPause() = lifecycleRegistry.pause()
    fun onStop() = lifecycleRegistry.stop()

    fun requestNotificationPermission() {
        applicationScope.launch {
            runCatching {
                notificationPermissionService.request()
                reminderCoordinator.reconcile(ReconciliationReason.PERMISSION_RESTORED)
            }
        }
    }

    fun reconcile(reason: ReconciliationReason) {
        applicationScope.launch { runCatching { reminderCoordinator.reconcile(reason) } }
    }

    override fun close() {
        if (closed) return
        closed = true
        applicationScope.cancel()
        lifecycleRegistry.destroy()
        pendingPhotoImportStore.close()
        privateFileStore.close()
        database.close()
    }
}

private class ThemedAppRootComponent(
    delegate: AppRootComponent,
    override val themePreference: StateFlow<com.sedsoftware.sproutpace.data.ThemePreference>,
) : AppRootComponent by delegate, ThemePreferenceOwner

internal class CrashAwareScreenDataManager(
    private val delegate: ScreenDataManager,
    private val runtimeState: StateFlow<CrashReportingState>,
) : ScreenDataManager {
    override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> {
        val source = delegate.watch(screen, stableId)
        if (screen != ScreenId.SETTINGS) return source
        return combine(source, runtimeState) { snapshot, state ->
            snapshot.copy(
                items = snapshot.items.map { item ->
                    if (item.id == "crash-reporting") item.copy(stateKey = state.textKey()) else item
                },
                facts = snapshot.facts.filterNot { it.id == "crash-reporting-effective" } +
                    com.sedsoftware.sproutpace.component.ScreenFact(
                        id = "crash-reporting-effective",
                        labelKey = TextKey("settings_crash_reporting_effective"),
                        valueKey = state.textKey(),
                    ),
            )
        }
    }

    override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
        delegate.execute(screen, command)

    private fun CrashReportingState.textKey() = TextKey(when (this) {
        is CrashReportingState.Active -> "crash_reporting_active"
        is CrashReportingState.Unavailable -> "crash_reporting_unavailable"
        is CrashReportingState.Disabled -> when (reason) {
            DisableReason.USER_CHOICE -> "crash_reporting_user_off"
            DisableReason.NON_RELEASE_ENVIRONMENT -> "crash_reporting_build_disabled"
        }
    })
}

internal class SnapshotReminderReconciliationPort(
    private val coordinator: ReminderLifecycleCoordinator?,
    private val legacyDriver: ReminderDriver? = null,
    private val legacyNowEpochMillis: (() -> Long)? = null,
) : ReminderReconciliationPort {
    constructor(coordinator: ReminderLifecycleCoordinator) : this(coordinator, null, null)
    constructor(driver: ReminderDriver, nowEpochMillis: () -> Long) : this(null, driver, nowEpochMillis)

    override suspend fun replaceFutureReminders(snapshot: ProductSnapshot) {
        coordinator?.onRestoreCompleted() ?: run {
            val driver = requireNotNull(legacyDriver)
            val desired = snapshot.futureSoakReminders(requireNotNull(legacyNowEpochMillis).invoke()).associateBy { it.id }
            val existing = driver.registeredIds()
            (existing - desired.keys).sortedBy { it.value }.forEach { driver.cancel(it).getOrThrow() }
            desired.values.sortedBy { it.id.value }.forEach { driver.schedule(it).getOrThrow() }
        }
    }
}

internal class SnapshotReminderPlanSource(
    private val snapshot: suspend () -> ProductSnapshot,
    private val settings: com.sedsoftware.sproutpace.data.ProductSettings,
    private val nowEpochMillis: () -> Long,
    private val timeZone: () -> TimeZone,
) : ReminderPlanSource {
    override suspend fun currentPlan(): ReminderPlan {
        val now = nowEpochMillis()
        val current = snapshot()
        val preferences = settings.snapshot()
        val dueCount = current.batches.asSequence()
            .filterNot { it.state.isTerminal }
            .flatMap { it.actions.asSequence() }
            .count { it.status == ScheduledActionStatus.PENDING && SchedulingEngine.resolvedInstant(it).toEpochMilliseconds() <= now }
        val daily = if (preferences.notificationsEnabled && dueCount > 0) {
            PhysicalReminder(
                id = PhysicalReminderId(DAILY_SUMMARY_ID),
                kind = ReminderKind.DAILY_SUMMARY,
                fireAtEpochMillis = nextSummaryAt(now, preferences.summaryTime, timeZone()),
                titleKey = NativeTextKey.DAILY_SUMMARY_TITLE,
                bodyKey = NativeTextKey.DAILY_SUMMARY_BODY,
            )
        } else null
        val soak = if (preferences.soakRemindersEnabled) current.futureSoakReminders(now) else emptyList()
        return ReminderPlan(dueCount, daily, soak)
    }

    private fun nextSummaryAt(now: Long, localTime: SummaryLocalTime, zone: TimeZone): Long {
        val nowLocal = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(zone)
        var date = nowLocal.date
        var candidate = LocalDateTime(date.year, date.month, date.day, localTime.hour, localTime.minute)
            .toInstant(zone).toEpochMilliseconds()
        if (candidate <= now) {
            date = date.plus(1, DateTimeUnit.DAY)
            candidate = LocalDateTime(date.year, date.month, date.day, localTime.hour, localTime.minute)
                .toInstant(zone).toEpochMilliseconds()
        }
        return candidate
    }

    private companion object {
        const val DAILY_SUMMARY_ID = "sproutpace:daily-summary"
    }
}

internal class FirstRunNavigationOutput(
    private val mainContext: CoroutineContext,
    private val resetToFirstRun: () -> Unit,
) : LocalDataIntegrationOutput {
    override suspend fun emit(event: LocalDataIntegrationEvent) {
        if (event == LocalDataIntegrationEvent.FirstRunNavigationRequired) {
            withContext(mainContext) { resetToFirstRun() }
        }
    }
}

internal class ReminderAwareScreenDataManager(
    private val delegate: ScreenDataManager,
    private val settings: com.sedsoftware.sproutpace.data.ProductSettings,
    private val permissionService: NotificationPermissionService,
    private val reconcile: (ReconciliationReason) -> Unit,
) : ScreenDataManager {
    override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> {
        val source = delegate.watch(screen, stableId)
        if (screen != ScreenId.NOTIFICATIONS) return source
        val preferences = combine(
            settings.notificationsEnabled,
            settings.summaryTime,
            settings.soakRemindersEnabled,
        ) { daily, time, soak -> Triple(daily, time, soak) }
        return combine(source, preferences, permissionService.state) { snapshot, values, permission ->
            snapshot.copy(
                items = listOf(
                    ScreenItem("daily-summary", titleKey = TextKey("notifications_daily_summary"), stateKey = booleanKey(values.first)),
                    ScreenItem("summary-time", titleKey = TextKey("notifications_time"), authoredTitle = values.second.toString()),
                    ScreenItem("soak-reminder", titleKey = TextKey("notifications_soak_reminder"), stateKey = booleanKey(values.third)),
                ),
                permissionState = permission.toComponentPermissionState(),
            )
        }
    }

    override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
        delegate.execute(screen, command).fold(
            onSuccess = { snapshot ->
                if (command.operation in REMINDER_MUTATIONS || command.operation == "update-notifications") {
                    reconcile(ReconciliationReason.PRODUCT_DATA_CHANGED)
                }
                Result.success(snapshot)
            },
            onFailure = { Result.failure(it) },
        )

    private fun booleanKey(value: Boolean) = TextKey(if (value) "state_enabled" else "state_disabled")

    private fun PermissionState.toComponentPermissionState() = when (this) {
        PermissionState.Unknown, PermissionState.Checking, PermissionState.Requesting ->
            com.sedsoftware.sproutpace.component.PermissionState.NOT_REQUESTED
        PermissionState.Granted -> com.sedsoftware.sproutpace.component.PermissionState.GRANTED
        is PermissionState.Denied -> if (canRequestAgain) {
            com.sedsoftware.sproutpace.component.PermissionState.DENIED
        } else {
            com.sedsoftware.sproutpace.component.PermissionState.PERMANENTLY_DENIED
        }
        PermissionState.Unavailable -> com.sedsoftware.sproutpace.component.PermissionState.NOT_REQUIRED
    }

    private companion object {
        val REMINDER_MUTATIONS = setOf(
            "save-batch", "delete-batch", "delete-batch-cascade", "confirm-sowing", "confirm-stage",
            "undo-stage", "skip-stage", "complete-action", "snooze", "skip-action", "schedule-soak-target",
            "time-zone-change", "close-harvest", "close-failed", "close-abandoned", "save-outcome",
        )
    }
}

private fun ProductSnapshot.futureSoakReminders(nowEpochMillis: Long): List<PhysicalReminder> =
    batches.flatMap { batch ->
        batch.actions.mapNotNull { action ->
            val fireAt = SchedulingEngine.resolvedInstant(action).toEpochMilliseconds()
            if (action.status != ScheduledActionStatus.PENDING || action.stage != GrowStage.SOAKING ||
                fireAt <= nowEpochMillis
            ) {
                null
            } else {
                PhysicalReminder(
                    id = PhysicalReminderId("sproutpace:soak:${batch.id.value}:${action.id.value}"),
                    kind = ReminderKind.SOAK,
                    fireAtEpochMillis = fireAt,
                    titleKey = NativeTextKey.SOAK_COMPLETE_TITLE,
                    bodyKey = NativeTextKey.SOAK_COMPLETE_BODY,
                )
            }
        }
    }

/** Pure assembly seam used by contract tests without constructing production storage. */
internal fun productionRootDependencies(
    releaseStoreFactory: StoreFactory,
    debugStoreFactory: StoreFactory,
    isDebug: Boolean,
    screenDataManager: ScreenDataManager,
    mainContext: CoroutineContext,
    initialScreen: ScreenId = ScreenId.TODAY,
): AppRootDependencies = AppRootDependencies(
    releaseStoreFactory = releaseStoreFactory,
    debugStoreFactory = debugStoreFactory,
    isDebug = isDebug,
    screenDataManager = screenDataManager,
    mainContext = mainContext,
    initialScreen = initialScreen,
)
