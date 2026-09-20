package com.sedsoftware.sproutpace.platform

import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ProductPreferences
import com.sedsoftware.sproutpace.data.ProductSettings
import com.sedsoftware.sproutpace.data.SummaryLocalTime
import com.sedsoftware.sproutpace.data.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlatformServicesTest {
    @Test
    fun ac074_photoPickerContractExposesOpaquePendingTokenWithoutPath() {
        val photo = ImportedFile(
            pendingToken = "picker-token.jpg",
            displayName = "seedlings.jpg",
            mediaType = "image/jpeg",
            sizeBytes = 42,
        )
        val backup = ImportedFile(
            appOwnedPath = "/private/imports/backup.zip",
            displayName = "backup.zip",
            mediaType = "application/zip",
            sizeBytes = 84,
        )

        assertEquals(null, photo.appOwnedPath)
        assertEquals("picker-token.jpg", photo.pendingToken)
        assertEquals(null, backup.pendingToken)
    }

    @Test
    fun permissionCheckRequestAndResumeAreSeparateTransitions() = runTest {
        val service = FakeNotificationPermissionService(
            initial = PermissionState.Denied(canRequestAgain = true),
            requestResult = PermissionState.Granted,
        )

        assertIs<PermissionState.Denied>(service.check())
        assertEquals(0, service.requestCount)
        assertEquals(PermissionState.Granted, service.request())
        service.setSystemState(PermissionState.Denied(canRequestAgain = false))
        assertIs<PermissionState.Denied>(service.onHostResume())
        assertEquals(2, service.checkCount)
        assertEquals(1, service.requestCount)
    }

    @Test
    fun dailySummaryRequiresWorkAndPastSoakIsNotRegistered() = runTest {
        val daily = reminder("daily", ReminderKind.DAILY_SUMMARY, 2_000)
        val futureSoak = reminder("soak-future", ReminderKind.SOAK, 3_000)
        val pastSoak = reminder("soak-past", ReminderKind.SOAK, 900)
        val driver = FakeReminderDriver()

        val result = ReminderReconciler(driver).reconcile(
            plan = ReminderPlan(0, daily, listOf(futureSoak, pastSoak)),
            nowEpochMillis = 1_000,
            permission = PermissionState.Granted,
            reason = ReconciliationReason.PRODUCT_DATA_CHANGED,
        )

        assertEquals(setOf(futureSoak.id), result.scheduled)
        assertEquals(listOf(futureSoak.id), driver.scheduledCalls.map { it.id })
    }

    @Test
    fun restoreReconciliationCancelsObsoleteIdsWithoutDuplicates() = runTest {
        val obsolete = reminder("obsolete", ReminderKind.SOAK, 4_000)
        val daily = reminder("daily", ReminderKind.DAILY_SUMMARY, 5_000)
        val driver = FakeReminderDriver(listOf(obsolete, daily))
        val reconciler = ReminderReconciler(driver)
        val plan = ReminderPlan(2, daily, emptyList())

        val first = reconciler.reconcile(plan, 1_000, PermissionState.Granted, ReconciliationReason.RESTORE_COMPLETED)
        val second = reconciler.reconcile(plan, 1_000, PermissionState.Granted, ReconciliationReason.PROCESS_RESTART)

        assertEquals(setOf(obsolete.id), first.cancelled)
        assertEquals(setOf(daily.id), first.scheduled)
        assertTrue(second.cancelled.isEmpty())
        assertEquals(2, driver.scheduledCalls.count { it.id == daily.id })
        assertEquals(1, driver.registeredIds().size)
    }

    @Test
    fun deniedPermissionCancelsPhysicalRegistrationsButNotLogicalPlan() = runTest {
        val daily = reminder("daily", ReminderKind.DAILY_SUMMARY, 5_000)
        val driver = FakeReminderDriver(listOf(daily))

        val result = ReminderReconciler(driver).reconcile(
            ReminderPlan(1, daily, emptyList()),
            1_000,
            PermissionState.Denied(canRequestAgain = false),
            ReconciliationReason.PERMISSION_RESTORED,
        )

        assertEquals(setOf(daily.id), result.cancelled)
        assertTrue(result.scheduled.isEmpty())
    }

    @Test
    fun unknownPermissionDuringColdStartPreservesExistingRegistrationsUntilOsCheckCompletes() = runTest {
        val daily = reminder("daily", ReminderKind.DAILY_SUMMARY, 5_000)
        val driver = FakeReminderDriver(listOf(daily))

        val result = ReminderReconciler(driver).reconcile(
            ReminderPlan(1, daily, emptyList()),
            1_000,
            PermissionState.Unknown,
            ReconciliationReason.APP_START,
        )

        assertTrue(result.cancelled.isEmpty())
        assertTrue(result.scheduled.isEmpty())
        assertEquals(setOf(daily.id), driver.registeredIds())
    }

    @Test
    fun restoreCompletionUsesCurrentPlanAndFutureOnlyReconciliation() = runTest {
        val daily = reminder("daily", ReminderKind.DAILY_SUMMARY, 5_000)
        val driver = FakeReminderDriver()
        val coordinator = ReminderLifecycleCoordinator(
            permissionService = FakeNotificationPermissionService(PermissionState.Granted),
            planSource = ReminderPlanSource { ReminderPlan(1, daily, emptyList()) },
            reconciler = ReminderReconciler(driver),
            clock = EpochMillisClock { 1_000 },
        )

        val result = coordinator.onRestoreCompleted()

        assertEquals(ReconciliationReason.RESTORE_COMPLETED, result.reason)
        assertEquals(setOf(daily.id), result.scheduled)
    }

    @Test
    fun physicalIdsAreStableAndDistinct() {
        val first = PhysicalReminderId("soak:batch-42")
        val same = PhysicalReminderId("soak:batch-42")
        val other = PhysicalReminderId("daily:summary")

        assertEquals(first.requestCode(), same.requestCode())
        assertNotEquals(first.requestCode(), other.requestCode())
    }

    @Test
    fun crashReportingUsesPersistedReleasePreferenceAndDisablesTestHosts() {
        val settings = FakeProductSettings(crashEnabled = true)
        val releaseDriver = FakeCrashReportingDriver()
        val release = CrashReportingCoordinator(settings, releaseDriver, RuntimeEnvironment.RELEASE)

        assertIs<CrashReportingState.Active>(release.applyStoredPreference())
        assertIs<CrashReportingState.Disabled>(release.updatePreference(false))
        assertEquals(listOf(true, false), releaseDriver.collectionChanges)
        assertEquals(false, settings.crashReportingEnabled.value)

        val testDriver = FakeCrashReportingDriver()
        val test = CrashReportingCoordinator(settings, testDriver, RuntimeEnvironment.TEST)
        assertIs<CrashReportingState.Disabled>(test.updatePreference(true))
        assertEquals(listOf(false), testDriver.collectionChanges)
    }

    @Test
    fun unavailableCrashDriverNeverReceivesPrivateContext() {
        val settings = FakeProductSettings(crashEnabled = true)
        val driver = FakeCrashReportingDriver(isConfigured = false)
        val coordinator = CrashReportingCoordinator(settings, driver, RuntimeEnvironment.RELEASE)

        assertIs<CrashReportingState.Unavailable>(coordinator.applyStoredPreference())
        assertTrue(coordinator.recordReviewedTechnicalFailure(IllegalStateException()).isSuccess)
        assertTrue(driver.recordedFailureTypes.isEmpty())
    }

    @Test
    fun completeErasureClearsProductFilesAndEveryKnownPhysicalId() = runTest {
        val events = mutableListOf<String>()
        val driver = object : ReminderDriver {
            override suspend fun registeredIds() = emptySet<PhysicalReminderId>()
            override suspend fun schedule(reminder: PhysicalReminder) = Result.success(Unit)
            override suspend fun cancel(id: PhysicalReminderId) = runCatching { events += "cancel:${id.value}" }
        }
        val files = object : AppOwnedFileStore {
            override suspend fun delete(path: String) = Result.success(Unit)
            override suspend fun deleteAll() = runCatching { events += "files" }
        }
        val data = object : ProductDataEraser {
            override suspend fun eraseProductData() = runCatching { events += "data" }
        }

        val result = CompleteErasureCoordinator(data, files, driver).erase(
            setOf(PhysicalReminderId("soak:b"), PhysicalReminderId("daily:a")),
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("data", "files", "cancel:daily:a", "cancel:soak:b"), events)
    }

    private fun reminder(id: String, kind: ReminderKind, at: Long) = PhysicalReminder(
        id = PhysicalReminderId(id),
        kind = kind,
        fireAtEpochMillis = at,
        titleKey = if (kind == ReminderKind.SOAK) NativeTextKey.SOAK_COMPLETE_TITLE else NativeTextKey.DAILY_SUMMARY_TITLE,
        bodyKey = if (kind == ReminderKind.SOAK) NativeTextKey.SOAK_COMPLETE_BODY else NativeTextKey.DAILY_SUMMARY_BODY,
    )
}

private class FakeProductSettings(crashEnabled: Boolean) : ProductSettings {
    private val mutableTheme = MutableStateFlow(ThemePreference.SYSTEM)
    private val mutableCrash = MutableStateFlow(crashEnabled)
    private val mutableNotifications = MutableStateFlow(true)
    private val mutablePrivacy = MutableStateFlow<AdvertisingPrivacyState?>(null)

    override val theme: StateFlow<ThemePreference> = mutableTheme
    override val crashReportingEnabled: StateFlow<Boolean> = mutableCrash
    override val notificationsEnabled: StateFlow<Boolean> = mutableNotifications
    private val mutableSummaryTime = MutableStateFlow(SummaryLocalTime.DEFAULT)
    override val summaryTime: StateFlow<SummaryLocalTime> = mutableSummaryTime
    private val mutableSoakReminders = MutableStateFlow(true)
    override val soakRemindersEnabled: StateFlow<Boolean> = mutableSoakReminders
    override val privacyState: StateFlow<AdvertisingPrivacyState?> = mutablePrivacy

    override fun setTheme(value: ThemePreference) { mutableTheme.value = value }
    override fun setCrashReportingEnabled(value: Boolean) { mutableCrash.value = value }
    override fun setNotificationsEnabled(value: Boolean) { mutableNotifications.value = value }
    override fun setSummaryTime(value: SummaryLocalTime) { mutableSummaryTime.value = value }
    override fun setSoakRemindersEnabled(value: Boolean) { mutableSoakReminders.value = value }
    override fun setPrivacyState(value: AdvertisingPrivacyState?) { mutablePrivacy.value = value }
    override fun snapshot() = ProductPreferences(
        theme.value,
        crashReportingEnabled.value,
        notificationsEnabled.value,
        privacyState.value,
        summaryTime.value,
        soakRemindersEnabled.value,
    )
    override fun replace(value: ProductPreferences) {
        setTheme(value.theme)
        setCrashReportingEnabled(value.crashReportingEnabled)
        setNotificationsEnabled(value.notificationsEnabled)
        setPrivacyState(value.privacyState)
        setSummaryTime(value.summaryTime)
        setSoakRemindersEnabled(value.soakRemindersEnabled)
    }
    override fun clearProductState() {
        replace(ProductPreferences())
    }
}
