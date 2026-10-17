package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.TaskDayGroup
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.InMemoryPendingPhotoImportStore
import com.sedsoftware.sproutpace.data.InMemoryPrivateFileStore
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.platform.FakeCrashReportingDriver
import com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService
import com.sedsoftware.sproutpace.platform.FakeReminderDriver
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

/** Actual clock progression reaches retained public models without a database/settings mutation. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeRefreshProductionTest {
    @Test fun retainedTodayMovesAcrossMidnightAndDueTimeWhileDirtyEditorAndStorageStayIntact() = runTest {
        Fixture(testScheduler).use { fixture ->
            fixture.seed()
            val today = fixture.component(ScreenId.TODAY)
            val editor = fixture.component(ScreenId.BATCH_EDITOR)
            fixture.timeUpdates.onResume()
            runCurrent()
            val idBefore = editor.field("id")
            val storedBefore = fixture.database.snapshot()
            val settingsBefore = fixture.settings.snapshot()
            editor.onAction(ScreenAction.EditField(ScreenFieldId("supplier"), "  Unfinished note\nKeep it  "))
            assertTrue(editor.model.value.hasUnsavedChanges)
            assertEquals(TaskDayGroup.UPCOMING, today.dayGroup())

            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(TaskDayGroup.TODAY, today.dayGroup())
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(TaskDayGroup.OVERDUE, today.dayGroup())
            assertEquals("  Unfinished note\nKeep it  ", editor.field("supplier"))
            assertEquals(idBefore, editor.field("id"))
            assertTrue(editor.model.value.hasUnsavedChanges)
            assertEquals(storedBefore, fixture.database.snapshot())
            assertEquals(settingsBefore, fixture.settings.snapshot())
        }
    }

    @Test fun foregroundResumeImmediatelyRefreshesRetainedModelAndBackgroundDoesNotKeepTicking() = runTest {
        Fixture(testScheduler).use { fixture ->
            fixture.seed()
            val today = fixture.component(ScreenId.TODAY)
            fixture.timeUpdates.onResume()
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(TaskDayGroup.TODAY, today.dayGroup())
            fixture.timeUpdates.onPause()
            runCurrent()
            val pausedRevision = fixture.timeUpdates.revisions.value
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(pausedRevision, fixture.timeUpdates.revisions.value)
            assertEquals(TaskDayGroup.TODAY, today.dayGroup())
            fixture.timeUpdates.onResume()
            runCurrent()
            assertEquals(TaskDayGroup.OVERDUE, today.dayGroup())
            assertEquals(1, fixture.database.snapshot().batches.size)
        }
    }

    @Test fun repeatedResumeUsesOneInjectedTickerAndDisposalCancelsItsSubscription() = runTest {
        val ticks = MutableSharedFlow<Unit>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val updates = ScreenTimeUpdates(scope, ticks)
        try {
            updates.onResume()
            runCurrent()
            updates.onResume()
            runCurrent()
            assertEquals(1, ticks.subscriptionCount.value)
            val beforeTick = updates.revisions.value
            ticks.emit(Unit)
            runCurrent()
            assertEquals(beforeTick + 1, updates.revisions.value)
            updates.onPause()
            runCurrent()
            assertEquals(0, ticks.subscriptionCount.value)
            updates.onResume()
            runCurrent()
            assertEquals(1, ticks.subscriptionCount.value)
        } finally {
            scope.cancel()
        }
        runCurrent()
        assertEquals(0, ticks.subscriptionCount.value)
    }

    @Test fun foregroundEnvironmentEntryPersistsCivilZoneBeforeRegistrationAndPreservesElapsedTarget() = runTest {
        withPublicWorkspace { directory, prefs ->
            lateinit var before: GrowBatch
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.PLAN, null, "save-plan", mapOf(
                    "id" to "environment-plan", "anchorDate" to "2026-11-01", "cadenceDays" to "7",
                    "cultureIds" to "pea", "mode" to "cadence"))
                assertEquals(1, fixture.database.snapshot().plans.size)
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                fixture.submit("start-soaking")
                fixture.open(ScreenId.NOTIFICATIONS)
                fixture.edit(mapOf("dailySummaryEnabled" to "false", "soakRemindersEnabled" to "true"))
                before = fixture.database.snapshot().batches.single()
            }
            val clock = Instant.parse("2026-10-10T10:00:00Z")
            var zone: TimeZone = TimeZone.UTC
            val driver = JdbcSqliteDriver("jdbc:sqlite:${directory.resolve("workspace.db").toAbsolutePath()}")
            val physical = FakeReminderDriver()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val graph = ProductionAppGraph(object : SqlDriverFactory {
                override fun create(): SqlDriver = driver
                override fun close() = Unit
            }, prefs, InMemoryPrivateFileStore(), InMemoryPendingPhotoImportStore(),
                PublicFileDocuments(directory.resolve("documents")), physical,
                FakeNotificationPermissionService(com.sedsoftware.sproutpace.platform.PermissionState.Granted),
                FakeCrashReportingDriver(), dispatcher, dispatcher, false, DefaultStoreFactory(), DefaultStoreFactory(),
                nowEpochMillis = { clock.toEpochMilliseconds() }, timeZone = { zone }, screenTimeTicks = emptyFlow())
            val civil = before.actions.first { it.stage == GrowStage.SOWN }
            val elapsed = before.actions.single { it.kind == ScheduledActionKind.SOAK_TARGET }
            try {
                graph.onStart()
                graph.onResume()
                runCurrent()
                val registered = physical.registeredIds()
                assertTrue(registered.isNotEmpty())
                graph.onPause()
                zone = TimeZone.of("Europe/Moscow")
                graph.onReminderEnvironmentChanged(ReconciliationReason.TIME_ZONE_CHANGED)
                graph.onResume()
                runCurrent()
                val model = graph.root.stack.value.active.instance.model.value
                assertEquals(ScreenId.TODAY, model.screen)
                assertEquals(SchedulingEngine.resolvedInstant(civil) - 3.hours,
                    model.items.single { it.task?.actionId == civil.id.value }.task?.dueAt)
                assertEquals(SchedulingEngine.resolvedInstant(elapsed),
                    model.items.single { it.task?.actionId == elapsed.id.value }.task?.dueAt)
                assertEquals(registered, physical.registeredIds())
                assertEquals(SchedulingEngine.resolvedInstant(elapsed).toEpochMilliseconds(),
                    physical.scheduledCalls.last().fireAtEpochMillis)
            } finally {
                graph.close()
            }
            runCurrent()
            PublicProductionFixture(directory, prefs, timeZone = { zone }).use { reopened ->
                val after = reopened.database.snapshot().batches.single()
                val updated = after.actions.single { it.id == civil.id }.due as DueMoment.CivilTime
                assertEquals("Europe/Moscow", updated.timeZoneId)
                assertEquals((civil.due as DueMoment.CivilTime).localDateTime, updated.localDateTime)
                assertEquals(elapsed, after.actions.single { it.id == elapsed.id })
                assertEquals(before.events, after.events)
            }
        }
    }

    private fun ScreenComponent.field(name: String) = model.value.fields.single { it.id.value == name }.value
    private fun ScreenComponent.dayGroup() = model.value.items.single { it.task != null }.task?.group

    private class Fixture(private val scheduler: TestCoroutineScheduler) : AutoCloseable {
        private val dispatcher = StandardTestDispatcher(scheduler)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val timeUpdates = ScreenTimeUpdates(scope, minuteScreenTimeTicks())
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { SproutPaceDatabase.Schema.create(it) }
        val database = LocalDatabase(object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }, dispatcher)
        val settings = MultiplatformProductSettings(MapSettings())
        private fun now() = Instant.fromEpochMilliseconds(BASE_EPOCH_MILLIS + scheduler.currentTime)
        private val manager = ProductionScreenDataManager(database, settings,
            now = ::now, today = { now().toLocalDateTime(TimeZone.UTC).date }, timeZone = { TimeZone.UTC },
            timeUpdates = timeUpdates.revisions)
        private val lifecycles = mutableListOf<LifecycleRegistry>()

        fun component(screen: ScreenId): ScreenComponent {
            val lifecycle = LifecycleRegistry().apply { resume() }
            lifecycles += lifecycle
            return ScreenComponentFactory(ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher))
                .create(screen, DefaultComponentContext(lifecycle), output = {})
        }

        suspend fun seed() {
            val zone = GrowZone(GrowZoneId("clock-zone"), "Shelf", GrowZoneType.GROW_LIGHT, 2)
            val tray = TraySizePreset(TraySizePresetId("clock-tray"), "Tray", TrayShape.Rectangle(20.0, 10.0))
            database.saveZone(zone)
            database.saveTrayPreset(tray)
            val culture = SproutPaceCatalog.cultures.first { it.id.value == "radish" }
            val batchId = BatchId("clock-batch")
            database.saveBatch(GrowBatch(batchId, RecipeSnapshot(null, culture.id, null,
                culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
                TraySnapshot(tray.name, tray.shape), "Coir", PositiveGrams(10.0), PositiveGrams(10.0),
                BatchState.PLANNED, Placement.None, emptyList(), listOf(ScheduledAction(
                    ScheduledActionId("clock-action"), batchId, null,
                    DueMoment.ElapsedTarget(Instant.fromEpochMilliseconds(BASE_EPOCH_MILLIS + 60_000)))),
                plannedSowDate = LocalDate(2026, 10, 17)))
        }

        override fun close() {
            timeUpdates.onPause()
            lifecycles.forEach { it.destroy() }
            scope.cancel()
            database.close()
        }
    }

    private companion object {
        val BASE_EPOCH_MILLIS = Instant.parse("2026-10-17T23:59:30Z").toEpochMilliseconds()
    }
}
