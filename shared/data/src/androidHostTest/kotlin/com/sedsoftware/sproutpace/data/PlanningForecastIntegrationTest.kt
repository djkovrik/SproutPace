package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.PlanEntryStatus
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestCadenceDays
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanId
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.SuggestionStatus
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanningForecastIntegrationTest {
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var manager: ProductionScreenDataManager
    private var today = LocalDate(2026, 10, 1)
    private val zone = GrowZone(GrowZoneId("a"), "First shelf", GrowZoneType.GROW_LIGHT, 1)
    private val timeZone = TimeZone.UTC

    @BeforeTest fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            now = { Instant.parse("2026-10-01T12:00:00Z") }, today = { today }, timeZone = { timeZone })
    }

    @AfterTest fun tearDown() { database.close(); factory.close() }

    @Test fun todayOrdersSimultaneousStagesInLifecycleOrderInsteadOfActionIdOrder() = runTest {
        val planned = batch(zone)
        val sow = planned.actions.single { it.stage == GrowStage.SOWN }
        val cover = planned.actions.single { it.stage == GrowStage.BLACKOUT_OR_WEIGHT }
        assertEquals(sow.due, cover.due)
        database.saveBatch(planned)
        val rows = manager.watch(ScreenId.TODAY).first().items
        assertEquals(sow.id.value, rows.first().task?.actionId)
        assertTrue(rows.indexOfFirst { it.task?.actionId == sow.id.value } <
            rows.indexOfFirst { it.task?.actionId == cover.id.value })
    }

    @Test fun continuousCandidateSurvivesRolloverAndAcceptedBatchSuppressesUntilTerminalOutcome() = runTest {
        database.saveZone(zone)
        val continuous = plan("continuous").copy(anchorDate = today, cadence = HarvestCadenceDays(5),
            cultureRotation = listOf(CultureId("radish"), CultureId("pea")), mode = HarvestPlanMode.CONTINUOUS_QUEUE)
        database.savePlan(continuous)
        val first = manager.watch(ScreenId.PLAN).first().items.single()
        assertEquals(LocalDate(2026, 10, 6), first.planEntry?.harvestDate)
        today = LocalDate(2026, 10, 2)
        assertEquals(first, manager.watch(ScreenId.PLAN).first().items.single())
        assertEquals(first, manager.watch(ScreenId.TODAY).first().items.single())
        val accepted = batch(zone).copy(actions = BatchPlanningEngine.plannedActions(BatchId("committed"),
            batch(zone).recipe.stages, today, mapOf(GrowStage.READY_FOR_HARVEST to LocalDate(2026, 10, 6)), timeZone.id))
        database.saveBatch(accepted)
        today = LocalDate(2026, 10, 3)
        assertTrue(manager.watch(ScreenId.PLAN).first().items.none { it.actionId == "confirm-suggestion" })
        assertTrue(manager.watch(ScreenId.TODAY).first().items.none { it.actionId == "confirm-suggestion" })
        assertTrue(manager.watch(ScreenId.BATCH_EDITOR, first.id).first().fields.isEmpty())
        database.saveBatch(accepted.copy(state = BatchState.FAILED,
            outcome = BatchOutcome.Failed(Instant.parse("2026-10-03T12:00:00Z"))))
        val next = manager.watch(ScreenId.PLAN).first().items.single()
        assertEquals("pea", next.planEntry?.cultureId)
        assertEquals(LocalDate(2026, 10, 11), next.planEntry?.harvestDate)
        today = LocalDate(2026, 10, 4)
        assertEquals(next, manager.watch(ScreenId.PLAN).first().items.single())
        assertEquals(1, database.snapshot().batches.size)
    }

    @Test fun legacyCalendarTargetRemainsCoveredAfterRolloverAndHarvestAdvancesFromActualDate() = runTest {
        database.saveZone(zone)
        database.savePlan(plan("continuous").copy(anchorDate = today, cadence = HarvestCadenceDays(5),
            mode = HarvestPlanMode.CONTINUOUS_QUEUE))
        val accepted = batch(zone).copy(actions = batch(zone).actions.map {
            if (it.stage == GrowStage.READY_FOR_HARVEST) it.copy(userFixed = true) else it
        })
        database.saveBatch(accepted)
        today = LocalDate(2026, 10, 28)
        assertTrue(manager.watch(ScreenId.PLAN).first().items.none { it.actionId == "confirm-suggestion" })
        database.saveBatch(accepted.copy(state = BatchState.HARVESTED,
            outcome = BatchOutcome.Harvested(Instant.parse("2026-10-28T12:00:00Z"))))
        assertEquals(LocalDate(2026, 11, 2), manager.watch(ScreenId.PLAN).first().items.single().planEntry?.harvestDate)
    }

    @Test fun zonesAndSavedPlanRowsUseSameIntervalsAndConflictDates() = runTest {
        database.saveZone(zone)
        val first = batch(zone)
        database.saveBatch(first)
        fun second(sow: LocalDate) = first.copy(id = BatchId("second"), plannedSowDate = sow,
            actions = BatchPlanningEngine.plannedActions(BatchId("second"), first.recipe.stages, sow, emptyMap(), timeZone.id))
        database.saveBatch(second(LocalDate(2026, 10, 26)))
        assertEquals(null, manager.watch(ScreenId.ZONES).first().items.single().occupancy?.futureConflict)
        assertTrue(manager.watch(ScreenId.PLAN).first().items.all { it.planEntry?.status == PlanEntryStatus.CONFIRMED })
        database.saveBatch(second(LocalDate(2026, 10, 12)))
        val zoneRow = manager.watch(ScreenId.ZONES).first().items.single().occupancy
        val conflict = manager.watch(ScreenId.PLAN).first().items.single { it.id == "second" }.planEntry
        assertEquals(0, zoneRow?.occupied)
        assertEquals(LocalDate(2026, 10, 12), zoneRow?.futureConflict?.date)
        assertEquals(PlanEntryStatus.NEEDS_RESOLUTION, conflict?.status)
        assertEquals(zoneRow?.futureConflict, conflict?.zone?.futureConflict)
    }

    @Test fun zonesIncludeProposalDemandAndDarkToLightCommittedDemand() = runTest {
        database.saveZone(zone)
        database.savePlan(plan("plan"))
        assertEquals(LocalDate(2026, 10, 12), manager.watch(ScreenId.ZONES).first().items.single().occupancy?.futureConflict?.date)
        database.savePlan(plan("plan").copy(paused = true))
        assertEquals(null, manager.watch(ScreenId.ZONES).first().items.single().occupancy?.futureConflict)
        val dark = zone.copy(id = GrowZoneId("dark"), type = GrowZoneType.DARK)
        database.saveZone(dark)
        database.saveZone(zone.copy(capacity = 0))
        database.saveBatch(batch(dark))
        val light = manager.watch(ScreenId.ZONES).first().items.single { it.id == zone.id.value }.occupancy
        assertEquals(LocalDate(2026, 10, 14), light?.futureConflict?.date)
        assertEquals(0, light?.occupied)
        assertEquals(PlanEntryStatus.NEEDS_RESOLUTION, manager.watch(ScreenId.PLAN).first().items.single().planEntry?.status)
    }

    @Test fun overlappingPlansShareCapacityAndEditorStatusWithoutWriting() = runTest {
        database.saveZone(zone)
        database.savePlan(plan("first"))
        database.savePlan(plan("second"))
        val before = database.snapshot()
        val rows = manager.watch(ScreenId.PLAN).first().items.filter { it.planEntry != null }
        assertEquals(1, rows.count { it.planEntry?.status == PlanEntryStatus.SUGGESTED })
        assertEquals(5, rows.count { it.planEntry?.status == PlanEntryStatus.NEEDS_RESOLUTION })
        rows.forEach { row ->
            val draft = manager.watch(ScreenId.BATCH_EDITOR, row.id).first()
            assertEquals(row.planEntry, draft.items.single { it.id == row.id }.planEntry)
        }
        assertEquals(before, database.snapshot())
        assertEquals(rows, manager.watch(ScreenId.PLAN).first().items)
    }

    @Test fun savedPlannedZoneBlocksOverlapButDoesNotCountAsCurrentOccupancy() = runTest {
        database.saveZone(zone)
        database.saveTrayPreset(TraySizePreset(TraySizePresetId("tray"), "Tray", TrayShape.Rectangle(20.0, 10.0)))
        manager.execute(ScreenId.BATCH_EDITOR, ScreenCommand("save-batch", "saved", mapOf(
            "cultureId" to "radish", "trayId" to "tray", "zoneId" to zone.id.value,
            "substrate" to "Coir", "actualSeedMassGrams" to "10", "plannedSowDate" to "2026-10-10",
            "fixedHarvestDate" to "2026-10-24", "timeZoneId" to "UTC",
        ))).getOrThrow()
        database.savePlan(plan("plan"))
        val saved = database.snapshot().batches.single()
        assertEquals(Placement.Assigned(zone.id, zone.name), saved.placement)
        assertEquals(0, zone.occupancy(listOf(saved)).occupied)
        assertTrue(manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" }
            .all { it.planEntry?.status == PlanEntryStatus.NEEDS_RESOLUTION })
        database.saveZone(zone.copy(id = GrowZoneId("b"), name = "Free shelf"))
        val first = manager.watch(ScreenId.PLAN).first().items.first { it.actionId == "confirm-suggestion" }
        assertEquals(PlanEntryStatus.SUGGESTED, first.planEntry?.status)
        assertEquals("b", first.planEntry?.zone?.zoneId)
        assertEquals("b", manager.watch(ScreenId.BATCH_EDITOR, first.id).first().fields.single { it.id.value == "zoneId" }.value)
    }

    @Test fun continuousTodayPlanAndEditorUseOneIdenticalForecastAndPauseReleasesDemand() = runTest {
        database.saveZone(zone)
        database.savePlan(plan("continuous").copy(mode = HarvestPlanMode.CONTINUOUS_QUEUE))
        val planRow = manager.watch(ScreenId.PLAN).first().items.single()
        val todayRow = manager.watch(ScreenId.TODAY).first().items.single()
        assertEquals(planRow, todayRow)
        assertEquals(planRow.planEntry, manager.watch(ScreenId.BATCH_EDITOR, planRow.id).first()
            .items.single { it.id == planRow.id }.planEntry)
        database.savePlan(plan("continuous").copy(mode = HarvestPlanMode.CONTINUOUS_QUEUE, paused = true))
        assertTrue(manager.watch(ScreenId.TODAY).first().items.isEmpty())
        assertTrue(manager.watch(ScreenId.PLAN).first().items.isEmpty())
    }

    @Test fun darkIntentReservesBothPhasesAndSoakingDoesNotOccupyNow() {
        val dark = zone.copy(id = GrowZoneId("dark"), type = GrowZoneType.DARK)
        val batch = batch(dark).copy(state = BatchState.SOAKING)
        val workspace = Workspace(listOf(zone, dark), emptyList(), emptyList(), listOf(batch), listOf(plan("plan")), emptyList())
        assertEquals(0, dark.occupancy(workspace.batches).occupied)
        assertTrue(workspace.planningForecast(today, timeZone).all { it.suggestion.status is SuggestionStatus.NeedsResolution })
        val free = workspace.copy(zones = workspace.zones + zone.copy(id = GrowZoneId("b")))
        assertIs<SuggestionStatus.Feasible>(free.planningForecast(today, timeZone).first().suggestion.status)
    }

    @Test fun actualOccupancyIsNotReleasedByEstimatedHarvestAndSeparatedProposalsFit() {
        val empty = Workspace(listOf(zone), emptyList(), emptyList(), emptyList(),
            listOf(plan("plan").copy(cadence = HarvestCadenceDays(20))), emptyList())
        assertTrue(empty.planningForecast(today, timeZone).all { it.suggestion.status is SuggestionStatus.Feasible })
        val active = empty.copy(batches = listOf(batch(zone).copy(state = BatchState.LIGHT_GROWTH)))
        assertTrue(active.planningForecast(today, timeZone).all { it.suggestion.status is SuggestionStatus.NeedsResolution })
    }

    private fun plan(id: String) = HarvestPlan(HarvestPlanId(id), LocalDate(2026, 10, 25), HarvestCadenceDays(1),
        listOf(CultureId("radish")), HarvestPlanMode.CADENCE)

    private fun batch(placement: GrowZone): GrowBatch {
        val culture = SproutPaceCatalog.cultures.first { it.id.value == "radish" }
        val stages = culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }
        val id = BatchId("committed")
        val sow = LocalDate(2026, 10, 10)
        return GrowBatch(id, RecipeSnapshot(null, culture.id, null, stages, culture.seedDensity),
            TraySnapshot("Tray", TrayShape.Rectangle(20.0, 10.0)), "Coir", PositiveGrams(10.0), PositiveGrams(10.0),
            BatchState.PLANNED, Placement.Assigned(placement.id, placement.name), emptyList(),
            BatchPlanningEngine.plannedActions(id, stages, sow, emptyMap(), timeZone.id), plannedSowDate = sow)
    }
}
