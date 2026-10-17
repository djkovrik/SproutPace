@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sedsoftware.sproutpace.compose

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.NSUserDefaultsSettings
import com.sedsoftware.sproutpace.data.ArchiveDocumentPort
import com.sedsoftware.sproutpace.data.IosPrivateFileStore
import com.sedsoftware.sproutpace.data.IosPendingPhotoImportStore
import com.sedsoftware.sproutpace.data.IosSqlDriverFactory
import com.sedsoftware.sproutpace.platform.NativeTextKey
import com.sedsoftware.sproutpace.platform.NotificationPermissionService
import com.sedsoftware.sproutpace.platform.PermissionState
import com.sedsoftware.sproutpace.platform.PhysicalReminder
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import com.sedsoftware.sproutpace.platform.ReminderDriver
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.platform.remainingReminderCapacity
import com.sedsoftware.sproutpace.platform.IosCrashReportingBridge
import com.sedsoftware.sproutpace.platform.IosCrashReportingDriver
import com.sedsoftware.sproutpace.root.AppRootComponent
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.number
import platform.Foundation.NSBundle
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSData
import platform.Foundation.NSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.time.Clock

/** Typed iOS application entry point. Swift owns and retains one instance. */
class IosProductionAppGraph(
    isDebug: Boolean,
    crashReportingBridge: IosCrashReportingBridge,
    archiveImports: IosArchiveImportCoordinator = IosArchiveImportCoordinator(),
) : AutoCloseable {
    private val notificationPermissionService = IosNotificationPermissionService()
    private val graph = ProductionAppGraph(
        driverFactory = IosSqlDriverFactory(),
        settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
        flushPreferences = { check(NSUserDefaults.standardUserDefaults.synchronize()) { "preferences_flush_failed" } },
        privateFileStore = IosPrivateFileStore(),
        pendingPhotoImportStore = IosPendingPhotoImportStore(),
        documentPort = IosArchiveDocumentPort(imports = archiveImports),
        reminderDriver = IosReminderDriver(),
        summaryHorizonDays = IOS_SUMMARY_HORIZON_DAYS,
        notificationPermissionService = notificationPermissionService,
        crashReportingDriver = IosCrashReportingDriver(crashReportingBridge),
        ioDispatcher = Dispatchers.Default,
        mainContext = Dispatchers.Main,
        isDebug = isDebug,
        releaseStoreFactory = DefaultStoreFactory(),
        debugStoreFactory = DefaultStoreFactory(),
    )

    val root: AppRootComponent get() = graph.root

    fun onStart() = graph.onStart()
    fun onResume() {
        onTimeZoneChanged()
        graph.onResume()
    }
    fun onTimeZoneChanged() = graph.onReminderEnvironmentChanged(ReconciliationReason.TIME_ZONE_CHANGED)
    fun onPause() = graph.onPause()
    fun onStop() = graph.onStop()
    fun requestNotificationPermission() = graph.requestNotificationPermission()
    override fun close() = graph.close()
}

internal class IosNotificationPermissionService(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : NotificationPermissionService {
    private val mutableState = MutableStateFlow<PermissionState>(PermissionState.Unknown)
    override val state = mutableState.asStateFlow()

    override suspend fun check(): PermissionState = suspendCoroutine { continuation ->
        mutableState.value = PermissionState.Checking
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val resolved = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized,
                UNAuthorizationStatusProvisional,
                UNAuthorizationStatusEphemeral -> PermissionState.Granted
                UNAuthorizationStatusDenied -> PermissionState.Denied(canRequestAgain = false)
                UNAuthorizationStatusNotDetermined -> PermissionState.Unknown
                else -> PermissionState.Unavailable
            }
            mutableState.value = resolved
            continuation.resume(resolved)
        }
    }

    override suspend fun request(): PermissionState = suspendCoroutine { continuation ->
        mutableState.value = PermissionState.Requesting
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { _, _ ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                val resolved = when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized,
                    UNAuthorizationStatusProvisional,
                    UNAuthorizationStatusEphemeral -> PermissionState.Granted
                    UNAuthorizationStatusDenied -> PermissionState.Denied(canRequestAgain = false)
                    UNAuthorizationStatusNotDetermined -> PermissionState.Unknown
                    else -> PermissionState.Unavailable
                }
                mutableState.value = resolved
                continuation.resume(resolved)
            }
        }
    }
}

/** Shared by the Swift picker worker and reset. Main-thread epoch reads never wait for bulk file I/O. */
class IosArchiveImportCoordinator {
    private val generationLock = NSLock()
    private val ioLock = NSLock()
    private var generation = 0L

    fun currentGeneration(): Long {
        generationLock.lock()
        return try { generation } finally { generationLock.unlock() }
    }

    /** Worker-only admission. Every successful admission must be paired with [endCopy]. */
    fun beginCopy(expectedGeneration: Long): Boolean {
        ioLock.lock()
        if (currentGeneration() == expectedGeneration) return true
        ioLock.unlock()
        return false
    }

    fun endCopy() = ioLock.unlock()

    internal fun invalidateAndClear(clear: () -> Unit) {
        generationLock.lock()
        try { generation += 1 } finally { generationLock.unlock() }
        ioLock.lock()
        try { clear() } finally { ioLock.unlock() }
    }
}

internal class IosArchiveDocumentPort(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val imports: IosArchiveImportCoordinator = IosArchiveImportCoordinator(),
) : ArchiveDocumentPort {
    override suspend fun read(documentId: String): ByteArray? = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val bytes = documentId.withSecurityScopedUrl { url ->
            NSData.dataWithContentsOfURL(url)?.let { data -> data.bytes?.readBytes(data.length.toInt()) }
        }
        currentCoroutineContext().ensureActive()
        bytes
    }

    override suspend fun write(documentId: String, bytes: ByteArray) = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        documentId.withSecurityScopedUrl { url ->
            bytes.usePinned { pinned ->
                val data = NSData.create(
                    bytes = if (bytes.isEmpty()) null else pinned.addressOf(0),
                    length = bytes.size.toULong(),
                )
                check(data.writeToURL(url, true)) { "The document provider rejected the archive write" }
            }
        }
        currentCoroutineContext().ensureActive()
    }

    override suspend fun clearImportedArchives() = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        imports.invalidateAndClear { clearOwnedImports() }
    }

    private fun clearOwnedImports() {
        val manager = NSFileManager.defaultManager
        val support = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
        val appRoot = support.URLByAppendingPathComponent("SproutPace", true)!!.URLByResolvingSymlinksInPath!!
        require(appRoot.URLByDeletingLastPathComponent?.path == support.URLByResolvingSymlinksInPath?.path) {
            "Archive application directory must be app-owned"
        }
        val imports = appRoot.URLByAppendingPathComponent("Imports", true)!!.URLByResolvingSymlinksInPath!!
        require(imports.URLByDeletingLastPathComponent?.path == appRoot.path) {
            "Archive import directory must be app-owned"
        }
        val path = requireNotNull(imports.path)
        if (manager.fileExistsAtPath(path)) check(manager.removeItemAtPath(path, null)) {
            "Unable to clear imported archives"
        }
    }

    private inline fun <T> String.withSecurityScopedUrl(block: (NSURL) -> T): T {
        val url = requireNotNull(NSURL.URLWithString(this)) { "Invalid iOS document URL" }
        require(url.isFileURL()) { "iOS archive documents must use file URLs" }
        val accessed = url.startAccessingSecurityScopedResource()
        return try {
            block(url)
        } finally {
            if (accessed) url.stopAccessingSecurityScopedResource()
        }
    }
}

internal class IosReminderDriver(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : ReminderDriver {
    override suspend fun pendingCapacity(): Int = suspendCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val otherRequests = requests.orEmpty().filterIsInstance<UNNotificationRequest>()
                .count { !it.identifier.startsWith(APP_REMINDER_PREFIX) }
            continuation.resume(remainingReminderCapacity(IOS_PENDING_REQUEST_BUDGET, otherRequests))
        }
    }

    override suspend fun registeredIds(): Set<PhysicalReminderId> = suspendCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.orEmpty().filterIsInstance<UNNotificationRequest>()
                .map { it.identifier }
                .filter { it.startsWith(APP_REMINDER_PREFIX) }
                .mapTo(linkedSetOf(), ::PhysicalReminderId)
            continuation.resume(ids)
        }
    }

    override suspend fun schedule(reminder: PhysicalReminder): Result<Unit> = suspendCoroutine { continuation ->
        val content = UNMutableNotificationContent().also { notification ->
            notification.setTitle(reminder.titleKey.localized())
            notification.setBody(reminder.bodyKey.localized())
        }
        val nowSeconds = Clock.System.now().toEpochMilliseconds() / MILLIS_PER_SECOND
        val seconds = max(1.0, reminder.fireAtEpochMillis / MILLIS_PER_SECOND - nowSeconds)
        val trigger = reminder.civilDateTime?.let { civil ->
            val components = NSDateComponents().apply {
                calendar = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)
                year = civil.year.toLong()
                month = civil.month.number.toLong()
                day = civil.day.toLong()
                hour = civil.hour.toLong()
                minute = civil.minute.toLong()
            }
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        } ?: UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(reminder.id.value, content, trigger)
        center.addNotificationRequest(request) { error -> continuation.resume(error.asResult()) }
    }

    override suspend fun cancel(id: PhysicalReminderId): Result<Unit> = runCatching {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id.value))
    }

    private fun NSError?.asResult(): Result<Unit> =
        if (this == null) Result.success(Unit) else Result.failure(IllegalStateException(localizedDescription))

    private fun NativeTextKey.localized(): String = NSBundle.mainBundle.localizedStringForKey(
        key = stableKey,
        value = stableKey,
        table = null,
    )

    private companion object {
        const val APP_REMINDER_PREFIX = "sproutpace:"
        const val MILLIS_PER_SECOND = 1_000.0
    }
}

private const val IOS_SUMMARY_HORIZON_DAYS = 30
private const val IOS_PENDING_REQUEST_BUDGET = 64
