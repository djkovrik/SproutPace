package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ProductPreferences
import com.sedsoftware.sproutpace.data.ProductSettings
import com.sedsoftware.sproutpace.data.SummaryLocalTime
import com.sedsoftware.sproutpace.data.ThemePreference
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService
import com.sedsoftware.sproutpace.platform.PermissionState
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.platform.ReminderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderProductionIntegrationTest {
    @Test
    fun ac065_planContainsOneUsefulDailySummaryAndHonorsIndependentSoakToggle() = runTest {
        val now = Instant.parse("2026-09-20T10:00:00Z").toEpochMilliseconds()
        val settings = FakeSettings(ProductPreferences(
            notificationsEnabled = true,
            summaryTime = SummaryLocalTime(9, 0),
            soakRemindersEnabled = false,
        ))
        val source = SnapshotReminderPlanSource(
            snapshot = { snapshot(dueAt = now - 1, soakAt = now + 3_600_000) },
            settings = settings,
            nowEpochMillis = { now },
            timeZone = { TimeZone.UTC },
        )

        val plan = source.currentPlan()

        assertEquals(1, plan.currentOrOverdueWorkCount)
        assertEquals("sproutpace:daily-summary", plan.dailySummary?.id?.value)
        assertEquals(Instant.parse("2026-09-21T09:00:00Z").toEpochMilliseconds(), plan.dailySummary?.fireAtEpochMillis)
        assertTrue(plan.soakReminders.isEmpty())
    }

    @Test
    fun ac065_noCurrentOrOverdueWorkProducesNoDailyNotification() = runTest {
        val now = Instant.parse("2026-09-20T10:00:00Z").toEpochMilliseconds()
        val source = SnapshotReminderPlanSource(
            snapshot = { snapshot(dueAt = now + 1_000, soakAt = now + 2_000) },
            settings = FakeSettings(ProductPreferences()),
            nowEpochMillis = { now },
            timeZone = { TimeZone.UTC },
        )

        assertNull(source.currentPlan().dailySummary)
    }

    @Test
    fun ac064_deniedPermissionIsExposedOnlyOnNotificationScreenAndDoesNotAlterToday() = runTest {
        val settings = FakeSettings(ProductPreferences())
        val denied = FakeNotificationPermissionService(PermissionState.Denied(canRequestAgain = false))
        val today = ScreenSnapshot(items = listOf(com.sedsoftware.sproutpace.component.ScreenItem(
            id = "action-1", titleKey = com.sedsoftware.sproutpace.component.TextKey("today_next_action"),
        )))
        val manager = ReminderAwareScreenDataManager(
            delegate = FixedManager(today),
            settings = settings,
            permissionService = denied,
            reconcile = {},
        )

        assertEquals(today, manager.watch(ScreenId.TODAY).first())
        val notificationModel = manager.watch(ScreenId.NOTIFICATIONS).first()
        assertEquals(com.sedsoftware.sproutpace.component.PermissionState.PERMANENTLY_DENIED, notificationModel.permissionState)
        assertEquals(listOf("daily-summary", "summary-time", "soak-reminder"), notificationModel.items.map { it.id })
    }

    @Test
    fun ac067_notificationPreferenceChangeTriggersProductReconciliation() = runTest {
        val settings = FakeSettings(ProductPreferences())
        val reasons = mutableListOf<ReconciliationReason>()
        val delegate = FixedManager(ScreenSnapshot())
        val manager = ReminderAwareScreenDataManager(
            delegate = delegate,
            settings = settings,
            permissionService = FakeNotificationPermissionService(PermissionState.Granted),
            reconcile = reasons::add,
        )

        val result = manager.execute(
            ScreenId.NOTIFICATIONS,
            ScreenCommand("update-notifications", arguments = mapOf(
                "dailySummaryEnabled" to "false",
                "summaryTime" to "06:30",
                "soakRemindersEnabled" to "true",
            )),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            mapOf(
                "dailySummaryEnabled" to "false",
                "summaryTime" to "06:30",
                "soakRemindersEnabled" to "true",
            ),
            delegate.commands.single().arguments,
        )
        assertEquals(listOf(ReconciliationReason.PRODUCT_DATA_CHANGED), reasons)
    }

    private fun snapshot(dueAt: Long, soakAt: Long): ProductSnapshot {
        val culture = SproutPaceCatalog.cultures.first()
        val batchId = BatchId("batch-1")
        val recipe = RecipeSnapshot(
            sourceRecipeId = null,
            builtInCultureId = culture.id,
            nameSnapshot = null,
            stages = culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) },
            density = culture.seedDensity,
        )
        val actions = listOf(
            ScheduledAction(ScheduledActionId("work-1"), batchId, GrowStage.SOWN, DueMoment.ElapsedTarget(Instant.fromEpochMilliseconds(dueAt))),
            ScheduledAction(ScheduledActionId("soak-1"), batchId, GrowStage.SOAKING, DueMoment.ElapsedTarget(Instant.fromEpochMilliseconds(soakAt))),
        )
        val batch = GrowBatch(
            batchId, recipe, TraySnapshot("Tray", TrayShape.Rectangle(10.0, 20.0)), "Substrate",
            PositiveGrams(10.0), PositiveGrams(10.0), BatchState.SOAKING, Placement.None,
            emptyList(), actions,
        )
        return ProductSnapshot(
            BackupManifest(1, 1, emptyMap(), emptyMap()), emptyList(), emptyList(), emptyList(),
            listOf(batch), emptyList(), emptyList(),
        )
    }
}

private class FixedManager(private val snapshot: ScreenSnapshot) : ScreenDataManager {
    val commands = mutableListOf<ScreenCommand>()
    override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> = flowOf(snapshot)
    override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
        commands += command
        return Result.success(snapshot)
    }
}

private class FakeSettings(initial: ProductPreferences) : ProductSettings {
    private val mutableTheme = MutableStateFlow(initial.theme)
    private val mutableCrash = MutableStateFlow(initial.crashReportingEnabled)
    private val mutableDaily = MutableStateFlow(initial.notificationsEnabled)
    private val mutablePrivacy = MutableStateFlow(initial.privacyState)
    private val mutableTime = MutableStateFlow(initial.summaryTime)
    private val mutableSoak = MutableStateFlow(initial.soakRemindersEnabled)
    override val theme: StateFlow<ThemePreference> = mutableTheme
    override val crashReportingEnabled: StateFlow<Boolean> = mutableCrash
    override val notificationsEnabled: StateFlow<Boolean> = mutableDaily
    override val privacyState: StateFlow<AdvertisingPrivacyState?> = mutablePrivacy
    override val summaryTime: StateFlow<SummaryLocalTime> = mutableTime
    override val soakRemindersEnabled: StateFlow<Boolean> = mutableSoak
    override fun setTheme(value: ThemePreference) { mutableTheme.value = value }
    override fun setCrashReportingEnabled(value: Boolean) { mutableCrash.value = value }
    override fun setNotificationsEnabled(value: Boolean) { mutableDaily.value = value }
    override fun setPrivacyState(value: AdvertisingPrivacyState?) { mutablePrivacy.value = value }
    override fun setSummaryTime(value: SummaryLocalTime) { mutableTime.value = value }
    override fun setSoakRemindersEnabled(value: Boolean) { mutableSoak.value = value }
    override fun snapshot() = ProductPreferences(
        theme.value, crashReportingEnabled.value, notificationsEnabled.value, privacyState.value,
        summaryTime.value, soakRemindersEnabled.value,
    )
    override fun replace(value: ProductPreferences) {
        setTheme(value.theme); setCrashReportingEnabled(value.crashReportingEnabled)
        setNotificationsEnabled(value.notificationsEnabled); setPrivacyState(value.privacyState)
        setSummaryTime(value.summaryTime); setSoakRemindersEnabled(value.soakRemindersEnabled)
    }
    override fun clearProductState() = replace(ProductPreferences())
}
