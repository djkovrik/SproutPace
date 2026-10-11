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
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderProductionIntegrationTest {
    @Test
    fun failedSchedulingKeepsSavedPreferencesAndDoesNotHideTodayTasks() = runTest {
        val settings = FakeSettings(ProductPreferences(notificationsEnabled = true, soakRemindersEnabled = true))
        val source = ScreenSnapshot(items = listOf(com.sedsoftware.sproutpace.component.ScreenItem(
            "due-work", titleKey = com.sedsoftware.sproutpace.component.TextKey("today_next_action"),
        )))
        val scheduling = MutableStateFlow(ReminderSchedulingState.FAILED)
        val manager = ReminderAwareScreenDataManager(FixedManager(source), settings,
            FakeNotificationPermissionService(PermissionState.Granted), {}, scheduling)
        assertEquals("notifications_schedule_failed",
            manager.watch(ScreenId.NOTIFICATIONS).first().facts.single().valueKey?.value)
        assertEquals(source.copy(permissionState = com.sedsoftware.sproutpace.component.PermissionState.GRANTED),
            manager.watch(ScreenId.TODAY).first())
        assertTrue(settings.notificationsEnabled.value)
        assertTrue(settings.soakRemindersEnabled.value)
        scheduling.value = ReminderSchedulingState.IDLE
        assertTrue(manager.watch(ScreenId.NOTIFICATIONS).first().facts.isEmpty())
    }

    @Test
    fun previewPassesThroughProductionWrappersWithoutReminderReconciliation() = runTest {
        val command = ScreenCommand("confirm-stage", "batch", mapOf("nextState" to "light_growth"))
        val expected = com.sedsoftware.sproutpace.component.StageChangeProjection(command, emptyList())
        var mutations = 0
        val delegate = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
            override suspend fun previewStage(command: ScreenCommand) = Result.success(expected)
            override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
                mutations += 1
                return Result.success(ScreenSnapshot())
            }
        }
        val reasons = mutableListOf<ReconciliationReason>()
        val wrapped = CrashAwareScreenDataManager(ReminderAwareScreenDataManager(delegate,
            FakeSettings(ProductPreferences()), FakeNotificationPermissionService(PermissionState.Granted), reasons::add),
            MutableStateFlow(com.sedsoftware.sproutpace.platform.CrashReportingState.Active(true)))
        assertEquals(expected, wrapped.previewStage(command).getOrThrow())
        assertEquals(0, mutations)
        assertTrue(reasons.isEmpty())
    }

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
    fun reachedSowingDoesNotRemainInSummaryOrScheduleCompletedSoakReminder() = runTest {
        val now = Instant.parse("2026-09-20T10:00:00Z").toEpochMilliseconds()
        val initial = snapshot(dueAt = now - 1, soakAt = now + 3_600_000)
        val sown = initial.batches.single().copy(state = BatchState.SOWN,
            placement = Placement.Unassigned("Waiting for a shelf"))
        val source = SnapshotReminderPlanSource({ initial.copy(batches = listOf(sown)) },
            FakeSettings(ProductPreferences()), { now }, { TimeZone.UTC })
        val plan = source.currentPlan()
        assertEquals(0, plan.currentOrOverdueWorkCount)
        assertNull(plan.dailySummary)
        assertTrue(plan.soakReminders.isEmpty())
    }

    @Test
    fun ac065_futureWorkPlansNextWindowButCannotDeliverEarly() = runTest {
        val now = Instant.parse("2026-09-20T10:00:00Z").toEpochMilliseconds()
        val source = SnapshotReminderPlanSource(
            snapshot = { snapshot(dueAt = now + 1_000, soakAt = now + 2_000) },
            settings = FakeSettings(ProductPreferences()),
            nowEpochMillis = { now },
            timeZone = { TimeZone.UTC },
        )

        val reminder = kotlin.test.assertNotNull(source.currentPlan().dailySummary)
        assertEquals(0, source.currentPlan().currentOrOverdueWorkCount)
        assertEquals(2, source.currentPlan().summaryWindowWorkCount)
        assertFalse(source.shouldDeliver(reminder.id, reminder.fireAtEpochMillis))
    }

    @Test
    fun androidDeliveryRearmsNextCivilDayAndSuppressesCompletedWorkWithoutForeground() = runTest {
        var now = Instant.parse("2026-10-10T08:00:00Z").toEpochMilliseconds()
        var current = snapshot(now - 1, now - 1)
        val settings = FakeSettings(ProductPreferences(notificationsEnabled = true))
        val source = SnapshotReminderPlanSource({ current }, settings, { now }, { TimeZone.UTC }, true)
        val driver = com.sedsoftware.sproutpace.platform.FakeReminderDriver()
        val coordinator = com.sedsoftware.sproutpace.platform.ReminderLifecycleCoordinator(
            FakeNotificationPermissionService(PermissionState.Granted), source,
            com.sedsoftware.sproutpace.platform.ReminderReconciler(driver),
            com.sedsoftware.sproutpace.platform.EpochMillisClock { now })
        coordinator.reconcile(ReconciliationReason.APP_START)
        val first = kotlin.test.assertNotNull(source.currentPlan().dailySummary)
        now = first.fireAtEpochMillis
        assertTrue(coordinator.onDelivery { source.shouldDeliver(first.id, first.fireAtEpochMillis) })
        assertEquals(Instant.parse("2026-10-11T09:00:00Z").toEpochMilliseconds(),
            driver.scheduledCalls.last { it.kind == com.sedsoftware.sproutpace.platform.ReminderKind.DAILY_SUMMARY }.fireAtEpochMillis)
        current = current.copy(batches = emptyList())
        val next = kotlin.test.assertNotNull(source.currentPlan().dailySummary)
        now = next.fireAtEpochMillis
        assertFalse(coordinator.onDelivery { source.shouldDeliver(next.id, next.fireAtEpochMillis) })
        assertEquals(Instant.parse("2026-10-12T09:00:00Z").toEpochMilliseconds(),
            driver.scheduledCalls.last { it.kind == com.sedsoftware.sproutpace.platform.ReminderKind.DAILY_SUMMARY }.fireAtEpochMillis)
    }

    @Test
    fun dailyWindowFollowsDstWhileSoakTargetRemainsElapsedAndObsoleteTargetsAreSuppressed() = runTest {
        var now = Instant.parse("2026-03-28T08:00:00Z").toEpochMilliseconds()
        val target = now + 3_600_000
        var current = snapshot(now - 1, target)
        var zone = TimeZone.of("Europe/Berlin")
        val source = SnapshotReminderPlanSource({ current }, FakeSettings(ProductPreferences(
            notificationsEnabled = true, soakRemindersEnabled = true)), { now }, { zone }, true)
        assertEquals(Instant.parse("2026-03-29T07:00:00Z").toEpochMilliseconds(),
            kotlin.test.assertNotNull(source.currentPlan().dailySummary).fireAtEpochMillis)
        val soak = source.currentPlan().soakReminders.single()
        zone = TimeZone.of("Europe/Moscow")
        assertEquals(target, source.currentPlan().soakReminders.single().fireAtEpochMillis)
        now = target
        assertTrue(source.shouldDeliver(soak.id, target))
        assertFalse(source.shouldDeliver(soak.id, target - 1))
        current = current.copy(batches = listOf(current.batches.single().copy(
            state = BatchState.SOWN, placement = Placement.Unassigned("Waiting for a shelf"))))
        assertFalse(source.shouldDeliver(soak.id, target))
    }

    @Test
    fun iosHorizonSchedulesTwoSuccessiveUsefulDaysAndCancelsWhenWorkIsCompleted() = runTest {
        val now = Instant.parse("2026-10-10T08:00:00Z").toEpochMilliseconds()
        val due = Instant.parse("2026-10-11T08:00:00Z").toEpochMilliseconds()
        var current = snapshot(due, due)
        val source = SnapshotReminderPlanSource({ current }, FakeSettings(ProductPreferences(
            notificationsEnabled = true, soakRemindersEnabled = false)), { now }, { TimeZone.UTC },
            summaryHorizonDays = 30)
        val plan = source.currentPlan()
        assertEquals(0, plan.currentOrOverdueWorkCount)
        assertNull(plan.dailySummary)
        assertEquals(29, plan.dailySummaries.size)
        assertEquals(listOf("2026-10-11T09:00", "2026-10-12T09:00"),
            plan.dailySummaries.take(2).map { it.civilDateTime.toString() })
        assertEquals(plan.dailySummaries.size, plan.dailySummaries.map { it.id }.distinct().size)
        val driver = com.sedsoftware.sproutpace.platform.FakeReminderDriver()
        val reconciler = com.sedsoftware.sproutpace.platform.ReminderReconciler(driver)
        reconciler.reconcile(plan, now, PermissionState.Granted, ReconciliationReason.APP_START)
        assertEquals(29, driver.registeredIds().size)
        current = current.copy(batches = emptyList())
        val noWork = source.currentPlan()
        assertTrue(noWork.dailySummaries.isEmpty())
        reconciler.reconcile(noWork, now, PermissionState.Granted, ReconciliationReason.PRODUCT_DATA_CHANGED)
        assertTrue(driver.registeredIds().isEmpty())
        assertEquals(29, driver.cancelledCalls.size)
    }

    @Test
    fun ac064_deniedPermissionKeepsTodayTasksAndAppearsOnSettingsAndFirstRun() = runTest {
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

        val expected = today.copy(permissionState =
            com.sedsoftware.sproutpace.component.PermissionState.PERMANENTLY_DENIED)
        assertEquals(expected, manager.watch(ScreenId.TODAY).first())
        assertEquals(expected, manager.watch(ScreenId.FIRST_RUN).first())
        assertEquals(expected, manager.watch(ScreenId.SETTINGS).first())
        val notificationModel = manager.watch(ScreenId.NOTIFICATIONS).first()
        assertEquals(com.sedsoftware.sproutpace.component.PermissionState.PERMANENTLY_DENIED, notificationModel.permissionState)
        assertEquals(listOf("daily-summary", "summary-time", "soak-reminder"), notificationModel.items.map { it.id })
    }

    @Test
    fun disabledRemindersDoNotPresentPermissionAsRequired() = runTest {
        val source = ScreenSnapshot()
        val manager = ReminderAwareScreenDataManager(FixedManager(source),
            FakeSettings(ProductPreferences(notificationsEnabled = false, soakRemindersEnabled = false)),
            FakeNotificationPermissionService(PermissionState.Denied(canRequestAgain = true)), {})
        listOf(ScreenId.TODAY, ScreenId.FIRST_RUN, ScreenId.SETTINGS).forEach { screen ->
            assertEquals(source, manager.watch(screen).first())
        }
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
