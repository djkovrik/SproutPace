package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.data.InMemoryPendingPhotoImportStore
import com.sedsoftware.sproutpace.data.InMemoryPrivateFileStore
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.platform.FakeCrashReportingDriver
import com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService
import com.sedsoftware.sproutpace.platform.FakeReminderDriver
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlanHorizonProductionRegressionTest {
    @Test fun cadenceRestartAfterLongPausePreservesAcceptedBatchAndAdvancesSuggestions() = restart("cadence")

    @Test fun continuousRestartRotatesAfterClosedBatchAndAcceptsShiftedSuggestion() = restart("continuous_queue")

    @Test fun retainedPlanAdvancesOnForegroundTickAndTimeZoneChangeWithoutReopening() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.save(ScreenId.PLAN, null, "save-plan", mapOf("id" to "retained",
                    "anchorDate" to "2026-10-01", "cadenceDays" to "1", "cultureIds" to "pea", "mode" to "cadence"))
            }
            var now = Instant.parse("2026-11-19T12:30:00Z")
            var zone: TimeZone = TimeZone.of("America/Los_Angeles")
            val ticks = MutableSharedFlow<Unit>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val driver = JdbcSqliteDriver("jdbc:sqlite:${directory.resolve("workspace.db").toAbsolutePath()}")
            val graph = ProductionAppGraph(object : SqlDriverFactory {
                override fun create(): SqlDriver = driver
                override fun close() = Unit
            }, preferences, InMemoryPrivateFileStore(), InMemoryPendingPhotoImportStore(),
                PublicFileDocuments(directory.resolve("documents")), FakeReminderDriver(),
                FakeNotificationPermissionService(com.sedsoftware.sproutpace.platform.PermissionState.Granted),
                FakeCrashReportingDriver(), dispatcher, dispatcher, false, DefaultStoreFactory(), DefaultStoreFactory(),
                nowEpochMillis = { now.toEpochMilliseconds() }, timeZone = { zone }, screenTimeTicks = ticks)
            try {
                graph.onStart()
                graph.onResume()
                runCurrent()
                graph.root.open(ScreenId.PLAN, "retained")
                runCurrent()
                val retained = graph.root.stack.value.active.instance
                val initial = retained.model.value.items.filter { it.actionId == "confirm-suggestion" }
                now = Instant.parse("2027-02-01T12:30:00Z")
                ticks.emit(Unit)
                runCurrent()
                val advanced = retained.model.value.items.filter { it.actionId == "confirm-suggestion" }
                assertEquals(3, advanced.size)
                assertTrue(advanced.none { row -> initial.any { it.id == row.id } })
                advanced.forEach { assertFuture(it, now.toLocalDateTime(zone).date, zone) }
                zone = TimeZone.of("Pacific/Kiritimati")
                graph.onReminderEnvironmentChanged(ReconciliationReason.TIME_ZONE_CHANGED)
                runCurrent()
                val moved = retained.model.value.items.filter { it.actionId == "confirm-suggestion" }
                assertEquals(3, moved.size)
                assertTrue(moved.map { it.id } != advanced.map { it.id })
                moved.forEach { assertFuture(it, now.toLocalDateTime(zone).date, zone) }
            } finally { graph.close() }
            runCurrent()
        }
    }

    private fun restart(mode: String) = runTest {
        withPublicWorkspace { directory, preferences ->
            var now = Instant.parse("2026-10-10T10:00:00Z")
            val before = PublicProductionFixture(directory, preferences, now = { now }).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.PLAN, null, "save-plan", mapOf("id" to "horizon",
                    "anchorDate" to "2026-10-30", "cadenceDays" to "5", "cultureIds" to "radish,pea", "mode" to mode))
                fixture.accept(fixture.proposals().first(), "first-accepted")
                if (mode == "continuous_queue") {
                    fixture.save(ScreenId.CLOSE_BATCH, "first-accepted", "save-outcome", mapOf(
                        "outcome" to "failed", "occurredAt" to "2026-11-06T10:00:00Z"))
                }
                fixture.open(ScreenId.PLAN, "horizon")
                fixture.submit("pause-plan")
                assertTrue(fixture.proposals().isEmpty())
                fixture.database.snapshot().batches
            }
            now = Instant.parse("2027-02-01T12:30:00Z")
            val zone = TimeZone.of("Pacific/Kiritimati")
            val after = PublicProductionFixture(directory, preferences, now = { now }, timeZone = { zone }).use { fixture ->
                fixture.open(ScreenId.PLAN, "horizon")
                assertTrue(fixture.proposals().isEmpty())
                fixture.submit("resume-plan")
                val proposals = fixture.proposals()
                assertEquals(if (mode == "cadence") 3 else 1, proposals.size)
                proposals.forEach { assertFuture(it, now.toLocalDateTime(zone).date, zone) }
                assertEquals(before, fixture.database.snapshot().batches)
                assertEquals(LocalDate(2026, 10, 30), fixture.database.snapshot().plans.single().anchorDate)
                if (mode == "continuous_queue") assertEquals("pea", proposals.single().planEntry?.cultureId)
                fixture.accept(proposals.first(), "shifted-accepted")
                fixture.open(ScreenId.PLAN, "horizon")
                assertTrue(fixture.proposals().none { it.id == proposals.first().id })
                if (mode == "continuous_queue") assertTrue(fixture.proposals().isEmpty())
                fixture.database.snapshot().batches.also { assertEquals(before.size + 1, it.size) }
            }
            PublicProductionFixture(directory, preferences, now = { now }, timeZone = { zone }).use { fixture ->
                assertEquals(after, fixture.database.snapshot().batches)
                fixture.open(ScreenId.PLAN, "horizon")
                if (mode == "continuous_queue") assertTrue(fixture.proposals().isEmpty())
                else fixture.proposals().forEach { assertFuture(it, now.toLocalDateTime(zone).date, zone) }
            }
        }
    }

    private fun PublicProductionFixture.proposals() = model().items.filter { it.actionId == "confirm-suggestion" }

    private fun PublicProductionFixture.accept(suggestion: ScreenItem, id: String) {
        submit("confirm-suggestion", suggestion.id)
        assertEquals(ScreenId.BATCH_EDITOR, model().screen)
        edit(mapOf("id" to id, "trayId" to "tray", "substrate" to "Coir"))
        submit("save-batch")
        assertEquals(null, model().errorKey)
    }

    private fun assertFuture(row: ScreenItem, today: LocalDate, zone: TimeZone) {
        val entry = requireNotNull(row.planEntry)
        val stages = SproutPaceCatalog.cultures.first { it.id.value == entry.cultureId }.stages
            .map { RecipeStage(it.stage, it.duration, it.instructionKey) }
        val actions = BatchPlanningEngine.plannedActions(BatchId("check"), stages,
            requireNotNull(entry.sowDate), emptyMap(), zone.id)
        assertTrue(actions.all { (it.due as DueMoment.CivilTime).localDateTime.date >= today })
    }
}
