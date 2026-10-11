package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real SQLite -> production manager -> retained component -> UI adapter regressions. */
class ProductionUxRegressionTest {
    @Test
    fun todayStageTaskRequiresLifecycleConfirmationAndRebuildsWorkExactlyOnce() = runTest {
        fixture { database, manager ->
            database.saveZone(GrowZone(GrowZoneId("zone"), "Grow shelf", GrowZoneType.GROW_LIGHT, 8))
            val original = batch("lifecycle", BatchState.SOAKING)
            val action = ScheduledAction(ScheduledActionId("sow"), original.id, GrowStage.SOWN,
                DueMoment.ElapsedTarget(NOW))
            val soak = ScheduledAction(ScheduledActionId("soak"), original.id, GrowStage.SOAKING,
                DueMoment.ElapsedTarget(NOW + kotlin.time.Duration.parse("1h")))
            val light = ScheduledAction(ScheduledActionId("light"), original.id, GrowStage.LIGHT_GROWTH,
                DueMoment.ElapsedTarget(Instant.parse("2026-10-11T10:00:00Z")))
            val seeded = original.copy(actions = listOf(action, soak, light))
            database.saveBatch(seeded)
            val reminderSettings = MultiplatformProductSettings(MapSettings()).apply { setSoakRemindersEnabled(true) }
            val reminderSource = SnapshotReminderPlanSource({ database.snapshot() }, reminderSettings,
                { NOW.toEpochMilliseconds() }, { kotlinx.datetime.TimeZone.UTC })
            assertEquals(1, reminderSource.currentPlan().soakReminders.size)
            withComponent(manager, ScreenId.TODAY) { today ->
                val item = today.model.value.items.single { it.task?.actionId == "sow" }
                assertEquals(listOf(ScreenItemCommand("open-batch", original.id.value)), item.commands)
                today.onAction(ScreenAction.Execute(ScreenCommand("complete-action", "sow").newInvocation()))
                assertEquals(seeded, database.snapshot().batches.single())
                assertTrue(manager.execute(ScreenId.TODAY, ScreenCommand("complete-action", "sow")).isFailure)
            }
            withComponent(manager, ScreenId.BATCH_DETAIL, original.id.value) { detail ->
                mapOf("occurredAt" to NOW.toString(), "zoneId" to "zone").forEach { (key, value) ->
                    detail.onAction(ScreenAction.EditField(ScreenFieldId(key), value))
                }
                detail.onAction(ScreenAction.Submit(ScreenActionId("confirm-sowing")))
                val preview = assertNotNull(detail.model.value.stagePreview)
                assertEquals(BatchState.SOAKING, database.snapshot().batches.single().state)
                detail.onAction(ScreenAction.ConfirmStagePreview)
                val changed = database.snapshot().batches.single()
                assertEquals(BatchState.SOWN, changed.state)
                assertEquals(Placement.Assigned(GrowZoneId("zone"), "Grow shelf"), changed.placement)
                assertEquals(1, changed.events.size)
                val rebuiltLight = changed.actions.single { it.id == light.id }
                assertEquals(ScheduledActionStatus.PENDING, rebuiltLight.status)
                assertEquals(DueMoment.ElapsedTarget(Instant.parse("2026-10-12T10:00:00Z")), rebuiltLight.due)
                detail.onAction(ScreenAction.Execute(preview.command))
                assertEquals(changed, database.snapshot().batches.single())
                assertTrue(reminderSource.currentPlan().soakReminders.isEmpty())
            }
        }
    }

    @Test
    fun stageSkipRequiresReasonAndRecordsAnEventInsteadOfStatusOnly() = runTest {
        fixture { database, manager ->
            database.saveZone(GrowZone(GrowZoneId("zone"), "Grow shelf", GrowZoneType.GROW_LIGHT, 8))
            val original = batch("skip", BatchState.SOWN)
            val action = ScheduledAction(ScheduledActionId("cover"), original.id, GrowStage.BLACKOUT_OR_WEIGHT,
                DueMoment.ElapsedTarget(NOW))
            database.saveBatch(original.copy(actions = listOf(action)))
            assertTrue(manager.execute(ScreenId.TODAY, ScreenCommand("skip-action", "cover")).isFailure)
            val command = ScreenCommand("skip-stage", original.id.value, mapOf(
                "eventId" to "skip-cover", "occurredAt" to NOW.toString(),
                "skippedState" to "blackout_or_weight", "resultingState" to "light_growth", "reason" to "",
            ))
            assertTrue(manager.execute(ScreenId.BATCH_DETAIL, command).isFailure)
            assertEquals(original.copy(actions = listOf(action)), database.snapshot().batches.single())
            val confirmed = command.copy(arguments = command.arguments + ("reason" to "Already uncovered"))
                .newInvocation()
            withComponent(manager, ScreenId.BATCH_DETAIL, original.id.value) { detail ->
                detail.onAction(ScreenAction.Execute(confirmed))
                val changed = database.snapshot().batches.single()
                assertEquals(BatchState.LIGHT_GROWTH, changed.state)
                assertEquals(StageEventKind.SKIPPED, changed.events.single().kind)
                assertEquals("Already uncovered", changed.events.single().reason)
                detail.onAction(ScreenAction.Execute(confirmed))
                assertEquals(changed, database.snapshot().batches.single())
            }
        }
    }

    @Test
    fun todayHarvestTaskClosesOnlyThroughOutcomeAndReleasesPlacement() = runTest {
        fixture { database, manager ->
            val original = batch("harvest", BatchState.READY_FOR_HARVEST)
            val action = ScheduledAction(ScheduledActionId("harvest-action"), original.id, GrowStage.READY_FOR_HARVEST,
                DueMoment.ElapsedTarget(NOW))
            database.saveBatch(original.copy(actions = listOf(action)))
            withComponent(manager, ScreenId.TODAY) { today ->
                assertEquals(setOf(ScreenItemCommand("close-batch", original.id.value),
                    ScreenItemCommand("open-batch", original.id.value)),
                    today.model.value.items.single { it.task != null }.commands.toSet())
                today.onAction(ScreenAction.Execute(ScreenCommand("complete-action", action.id.value)))
                assertEquals(BatchState.READY_FOR_HARVEST, database.snapshot().batches.single().state)
                assertTrue(manager.execute(ScreenId.TODAY, ScreenCommand("complete-action", action.id.value)).isFailure)
            }
            withComponent(manager, ScreenId.CLOSE_BATCH, original.id.value) { close ->
                close.onAction(ScreenAction.Execute(ScreenCommand("save-outcome", original.id.value,
                    mapOf("occurredAt" to NOW.toString(), "outcome" to "harvested")).newInvocation()))
                val changed = database.snapshot().batches.single()
                assertEquals(BatchState.HARVESTED, changed.state)
                assertEquals(Placement.None, changed.placement)
                assertTrue(changed.actions.none { it.status == ScheduledActionStatus.PENDING })
            }
            assertTrue(manager.watch(ScreenId.TODAY).first().items.none { it.task != null })
        }
    }

    @Test
    fun settingsPreferenceCommandsRetainWatchedPermissionAndEffectiveCrashContext() = runTest {
        val settings = MultiplatformProductSettings(MapSettings())
        settings.setNotificationsEnabled(true)
        settings.setCrashReportingEnabled(true)
        fixture(settings) { _, manager ->
            val crash = MutableStateFlow<com.sedsoftware.sproutpace.platform.CrashReportingState>(
                com.sedsoftware.sproutpace.platform.CrashReportingState.Disabled(true,
                    com.sedsoftware.sproutpace.platform.DisableReason.NON_RELEASE_ENVIRONMENT))
            val wrapped = ReminderAwareScreenDataManager(CrashAwareScreenDataManager(manager, crash), settings,
                com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService(
                    com.sedsoftware.sproutpace.platform.PermissionState.Denied(false)), {})
            val initial = wrapped.watch(ScreenId.SETTINGS, null).first()
            listOf(
                ScreenCommand("set-theme", arguments = mapOf("theme" to "light")).newInvocation(),
                ScreenCommand("set-crash-reporting", arguments = mapOf("enabled" to "false")).newInvocation(),
            ).forEach { command ->
                val result = wrapped.execute(ScreenId.SETTINGS, command).getOrThrow()
                val watched = wrapped.watch(ScreenId.SETTINGS, null).first()
                assertEquals(initial.permissionState, result.permissionState)
                assertEquals(PermissionState.PERMANENTLY_DENIED, result.permissionState)
                listOf("notifications", "crash-reporting").forEach { id ->
                    assertEquals(watched.items.single { it.id == id }, result.items.single { it.id == id })
                }
                assertEquals("notifications_permission_denied",
                    result.items.single { it.id == "notifications" }.supportingKey?.value)
                assertEquals("crash_reporting_build_disabled",
                    result.items.single { it.id == "crash-reporting" }.stateKey?.value)
                assertEquals(watched.facts, result.facts)
            }
            assertEquals(com.sedsoftware.sproutpace.data.ThemePreference.LIGHT, settings.theme.value)
            assertEquals(false, settings.crashReportingEnabled.value)
        }
    }

    @Test
    fun reminderEnvironmentBridgePersistsCivilTimeChangesBeforeReconciliation() = runTest {
        fixture { database, manager ->
            val original = batch("timezone", BatchState.SOAKING)
            val elapsed = ScheduledAction(ScheduledActionId("elapsed"), original.id, GrowStage.SOAKING,
                DueMoment.ElapsedTarget(NOW + kotlin.time.Duration.parse("2h")))
            val civil = ScheduledAction(ScheduledActionId("civil"), original.id, GrowStage.SOWN,
                DueMoment.CivilTime(kotlinx.datetime.LocalDateTime.parse("2026-10-11T09:00:00"), "UTC"))
            database.saveBatch(original.copy(actions = listOf(elapsed, civil)))
            var zone = kotlinx.datetime.TimeZone.of("Europe/Berlin")
            val source = SnapshotReminderPlanSource({ database.snapshot() },
                MultiplatformProductSettings(MapSettings()), { NOW.toEpochMilliseconds() }, { zone }, true)
            val driver = com.sedsoftware.sproutpace.platform.FakeReminderDriver()
            val coordinator = com.sedsoftware.sproutpace.platform.ReminderLifecycleCoordinator(
                com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService(
                    com.sedsoftware.sproutpace.platform.PermissionState.Granted), source,
                com.sedsoftware.sproutpace.platform.ReminderReconciler(driver),
                com.sedsoftware.sproutpace.platform.EpochMillisClock { NOW.toEpochMilliseconds() })
            suspend fun reconcile(reason: com.sedsoftware.sproutpace.platform.ReconciliationReason) =
                reconcileReminderEnvironment(reason, manager, coordinator, zone)
            reconcile(com.sedsoftware.sproutpace.platform.ReconciliationReason.TIME_ZONE_CHANGED)
            zone = kotlinx.datetime.TimeZone.of("Europe/Moscow")
            reconcile(com.sedsoftware.sproutpace.platform.ReconciliationReason.TIME_ZONE_CHANGED)
            val actions = database.snapshot().batches.single().actions.associateBy { it.id }
            assertEquals(elapsed.due, actions.getValue(elapsed.id).due)
            assertEquals("Europe/Moscow", (actions.getValue(civil.id).due as DueMoment.CivilTime).timeZoneId)
            reconcile(com.sedsoftware.sproutpace.platform.ReconciliationReason.DEVICE_RESTART)
            reconcile(com.sedsoftware.sproutpace.platform.ReconciliationReason.APP_REPLACED)
            assertEquals(2, driver.registeredIds().size)
        }
    }

    @Test
    fun ux01_productionStatesPartitionPlannedActiveAndHistory() = runTest {
        fixture { database, manager ->
            val states = listOf(
                BatchState.PLANNED, BatchState.SOAKING, BatchState.LIGHT_GROWTH,
                BatchState.HARVESTED, BatchState.FAILED, BatchState.ABANDONED,
            )
            states.forEach { database.saveBatch(batch(it.name.lowercase(), it)) }
            withComponent(manager, ScreenId.TRAYS) { component ->
                val expected = mapOf(
                    "planned" to setOf("planned"),
                    "active" to setOf("soaking", "light_growth"),
                    "history" to setOf("harvested", "failed", "abandoned"),
                )
                expected.forEach { (category, ids) ->
                    component.onAction(ScreenAction.ChooseOption(ScreenFieldId("category"), category))
                    val ui = component.model.value.toUiState()
                    assertEquals(ids, ui.items.map { it.id }.toSet(), category)
                    ui.items.forEach { item ->
                        assertEquals(item.id, assertNotNull(item.batch).batchId)
                        assertEquals("Tray ${item.id}", item.batch?.trayName)
                    }
                }
            }
        }
    }

    @Test
    fun ux02_todayKeepsEachActionOnceAndDoesNotTreatFutureDatesAsToday() = runTest {
        fixture { database, manager ->
            val dates = listOf("2026-10-09", "2026-10-10", "2026-10-11", "2026-10-17")
            dates.forEachIndexed { index, date ->
                val value = batch("batch-$index", BatchState.SOAKING)
                database.saveBatch(value.copy(actions = listOf(ScheduledAction(
                    ScheduledActionId("action-$index"), value.id, GrowStage.SOWN,
                    DueMoment.ElapsedTarget(Instant.parse("${date}T12:00:00Z")),
                ))))
            }
            withComponent(manager, ScreenId.TODAY) { component ->
                val ui = component.model.value.toUiState()
                val subjects = ui.items.mapNotNull { it.task?.actionId }
                assertEquals(subjects.distinct(), subjects, "Next action must not duplicate a task")
                val tasks = ui.items.map { assertNotNull(it.task, "Missing task projection for ${it.id}") }
                assertEquals(setOf("action-0", "action-1", "action-2", "action-3"), tasks.map { it.actionId }.toSet())
                assertEquals(
                    mapOf("action-0" to TaskDayGroup.OVERDUE, "action-1" to TaskDayGroup.TODAY,
                        "action-2" to TaskDayGroup.UPCOMING, "action-3" to TaskDayGroup.UPCOMING),
                    tasks.associate { it.actionId to it.group },
                )
                assertEquals(4, ui.items.map { assertNotNull(it.batch).batchId }.distinct().size)
            }
        }
    }

    @Test
    fun ux04_zoneReportsActualTwoOfEightPlaces() = runTest {
        fixture { database, manager ->
            database.saveZone(GrowZone(GrowZoneId("zone"), "Grow shelf", GrowZoneType.GROW_LIGHT, 8))
            listOf("first", "second").forEach { database.saveBatch(batch(it, BatchState.LIGHT_GROWTH)) }
            database.saveBatch(batch("planned", BatchState.PLANNED))
            withComponent(manager, ScreenId.ZONES) { component ->
                val zone = assertNotNull(component.model.value.toUiState().items.single().occupancy)
                assertEquals(2, zone.occupied)
                assertEquals(8, zone.capacity)
                assertEquals(6, zone.available)
                assertEquals(0, zone.overflow)
            }
        }
    }

    @Test
    fun ux05_planProjectsFiveDayCadenceAndMultipleCultures() = runTest {
        fixture { database, manager ->
            database.saveZone(GrowZone(GrowZoneId("zone"), "Grow shelf", GrowZoneType.GROW_LIGHT, 8))
            database.savePlan(HarvestPlan(
                HarvestPlanId("rotation"), LocalDate.parse("2026-10-20"), HarvestCadenceDays(5),
                listOf(CultureId("radish"), CultureId("pea")), HarvestPlanMode.CADENCE,
            ))
            withComponent(manager, ScreenId.PLAN) { component ->
                val items = component.model.value.toUiState().items.filter { it.actionId == "confirm-suggestion" }
                assertTrue(items.size >= 3)
                val entries = items.map { assertNotNull(it.planEntry, "Missing plan projection for ${it.id}") }
                assertEquals(listOf("radish", "pea", "radish"), entries.take(3).map { it.cultureId })
                assertEquals(listOf("2026-10-20", "2026-10-25", "2026-10-30"),
                    entries.take(3).map { it.harvestDate.toString() })
                entries.forEach { entry ->
                    assertNotNull(entry.sowDate)
                    assertEquals("rotation", entry.planId)
                    assertEquals(PlanEntryStatus.SUGGESTED, entry.status)
                    assertTrue(entry.sowDate!! < entry.harvestDate!!)
                }
            }
        }
    }

    private suspend fun fixture(
        settings: com.sedsoftware.sproutpace.data.ProductSettings = MultiplatformProductSettings(MapSettings()),
        block: suspend (LocalDatabase, ProductionScreenDataManager) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SproutPaceDatabase.Schema.create(driver)
        val factory = object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }
        val database = LocalDatabase(factory, Dispatchers.Unconfined)
        try {
            block(database, ProductionScreenDataManager(
                database, settings,
                now = { NOW }, today = { LocalDate.parse("2026-10-10") },
            ))
        } finally {
            database.close()
        }
    }

    private suspend fun withComponent(
        manager: ProductionScreenDataManager,
        screen: ScreenId,
        stableId: String? = null,
        block: suspend (ScreenComponent) -> Unit,
    ) {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        try {
            val component = ScreenComponentFactory(ScreenComponentDependencies(
                DefaultStoreFactory(), manager, Dispatchers.Unconfined,
            )).create(screen, DefaultComponentContext(lifecycle), stableId) {}
            assertEquals(LoadState.CONTENT, component.model.value.loadState)
            block(component)
        } finally {
            lifecycle.destroy()
        }
    }

    private fun batch(id: String, state: BatchState): GrowBatch {
        val culture = SproutPaceCatalog.cultures.first { it.id.value == "radish" }
        return GrowBatch(
            id = BatchId(id),
            recipe = RecipeSnapshot(null, culture.id, null,
                culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
            tray = TraySnapshot("Tray $id", TrayShape.Rectangle(10.0, 20.0)),
            substrateSnapshot = "Coco", recommendedSeedMass = PositiveGrams(10.0),
            actualSeedMass = PositiveGrams(10.0), state = state,
            placement = if (state.occupiesPlace) Placement.Assigned(GrowZoneId("zone"), "Grow shelf") else Placement.None,
            events = emptyList(), actions = emptyList(), plannedSowDate = LocalDate.parse("2026-10-08"),
            outcome = when (state) {
                BatchState.HARVESTED -> BatchOutcome.Harvested(NOW)
                BatchState.FAILED -> BatchOutcome.Failed(NOW)
                BatchState.ABANDONED -> BatchOutcome.Abandoned(NOW)
                else -> null
            },
        )
    }

    private companion object {
        val NOW = Instant.parse("2026-10-10T10:00:00Z")
    }
}
