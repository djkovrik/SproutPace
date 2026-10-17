package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleEngine
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Uses the real editor command preparation and SQL close/reopen, rather than hand-authored anchors. */
class EditorSchedulePersistenceTest {
    private lateinit var directory: File
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var manager: ProductionScreenDataManager
    private val culture = SproutPaceCatalog.cultures.first { it.id.value == "radish" }
    private val tray = TraySizePreset(TraySizePresetId("tray"), "Tray", TrayShape.Rectangle(20.0, 10.0))
    private val zone = GrowZone(GrowZoneId("shelf"), "Shelf", GrowZoneType.GROW_LIGHT, 1)

    @BeforeTest fun setUp() {
        directory = Files.createTempDirectory("sproutpace-editor-schedule-").toFile()
        open(initialize = true)
    }

    @AfterTest fun tearDown() {
        database.close()
        factory.close()
        directory.deleteRecursively()
    }

    @Test fun plannedMetadataAndNoOpEditorSavesKeepFutureScheduleAndCompletedHistoryAfterReopen() = runTest {
        database.saveTrayPreset(tray)
        val initial = batch("planned", LocalDate(2026, 11, 17), LocalDate(2026, 12, 1)).let { batch ->
            batch.copy(actions = batch.actions.mapIndexed { index, action ->
                when {
                    index == 0 -> action.copy(status = ScheduledActionStatus.COMPLETED)
                    action.stage == GrowStage.LIGHT_GROWTH -> action.copy(
                        due = DueMoment.CivilTime(LocalDateTime(2026, 11, 30, 17, 30), "UTC"))
                    else -> action
                }
            })
        }
        database.saveBatch(initial)
        val draft = manager.watch(ScreenId.BATCH_EDITOR, initial.id.value).first()
        assertTrue(draft.fields.filter { it.id.value in setOf("anchorState", "anchorAt") }.all { it.value.isEmpty() })
        assertTrue(draft.actions.any { it.id.value == "correct-batch" })
        assertTrue(draft.actions.none { it.id.value == "save-batch" })
        executeEditor(initial.id.value, mapOf("actualSeedMassGrams" to "15"))
        reopen()
        val edited = database.snapshot().batches.single()
        assertEquals(PositiveGrams(15.0), edited.actualSeedMass)
        assertEquals(initial.plannedSowDate, edited.plannedSowDate)
        assertEquals(initial.actions, edited.actions)
        assertEquals(initial.events, edited.events)
        executeEditor(initial.id.value)
        reopen()
        assertEquals(edited, database.snapshot().batches.single())
    }

    @Test fun editorActualDateCorrectionUsesCorrectedEventAndPreservesFixedDatesAndCompletedActions() = runTest {
        database.saveTrayPreset(tray)
        val sowing = Instant.parse("2026-10-11T09:00:00Z")
        val initial = BatchLifecycleEngine.confirmSowing(
            batch("active", LocalDate(2026, 10, 10), LocalDate(2026, 10, 31)),
            StageEventId("sowing"), sowing, Placement.Unassigned(null),
        ).batch.let { batch ->
            batch.copy(actions = batch.actions.map { action ->
                if (action.stage == GrowStage.SOWN) action.copy(status = ScheduledActionStatus.COMPLETED) else action
            })
        }
        database.saveBatch(initial)
        val correctedAt = Instant.parse("2026-10-13T09:00:00Z")
        executeEditor(initial.id.value, mapOf("actualSownAt" to correctedAt.toString()))
        reopen()
        val corrected = database.snapshot().batches.single()
        assertEquals(correctedAt, corrected.events.single().occurredAt)
        assertEquals(listOf(sowing), corrected.events.single().priorOccurredAt)
        val expected = ActionScheduleEngine.rebuild(initial, ActionScheduleCorrection(BatchState.SOWN, correctedAt))
        assertEquals(expected.actions, corrected.actions)
        assertTrue(expected.conflicts.isNotEmpty())
        assertEquals(expected.conflicts,
            ActionScheduleEngine.rebuild(corrected, ActionScheduleCorrection(BatchState.SOWN, correctedAt)).conflicts)
        listOf(ScreenId.BATCH_EDITOR, ScreenId.BATCH_DETAIL).forEach { screen ->
            assertTrue(TextKey("schedule_fixed_date_conflict") in
                manager.watch(screen, initial.id.value).first().conflictResolutionKeys)
        }
        assertEquals(initial.actions.single { it.userFixed }, corrected.actions.single { it.userFixed })
        assertEquals(initial.actions.single { it.status == ScheduledActionStatus.COMPLETED },
            corrected.actions.single { it.status == ScheduledActionStatus.COMPLETED })
        val oldLight = initial.actions.single { it.stage == GrowStage.LIGHT_GROWTH }.due as DueMoment.CivilTime
        val newLight = corrected.actions.single { it.stage == GrowStage.LIGHT_GROWTH }.due as DueMoment.CivilTime
        assertEquals(oldLight.localDateTime.date.toEpochDays() + 2, newLight.localDateTime.date.toEpochDays())
        executeEditor(initial.id.value)
        reopen()
        assertEquals(corrected, database.snapshot().batches.single())
    }

    @Test fun cadenceKeepsThreeUnfulfilledStableSlotsAfterSixAcceptsPauseAndRestartWithoutAutomaticWrites() = runTest {
        database.saveTrayPreset(tray)
        database.saveZone(zone)
        database.savePlan(HarvestPlan(HarvestPlanId("cadence"), LocalDate(2026, 11, 17), HarvestCadenceDays(2),
            listOf(CultureId("radish"), CultureId("pea")), HarvestPlanMode.CADENCE))
        repeat(6) { index ->
            val plan = manager.watch(ScreenId.PLAN).first()
            val proposals = plan.items.filter { it.actionId == "confirm-suggestion" }
            assertEquals(3, proposals.size)
            val selected = proposals.single { it.id == "cadence:$index" }
            assertEquals(if (index % 2 == 0) "radish" else "pea", selected.planEntry?.cultureId)
            val before = database.snapshot()
            manager.execute(ScreenId.PLAN, ScreenCommand("confirm-suggestion", selected.id)).getOrThrow()
            val editor = manager.watch(ScreenId.BATCH_EDITOR, selected.id).first()
            assertTrue(editor.actions.any { it.id.value == "save-batch" })
            assertTrue(editor.actions.none { it.id.value == "correct-batch" })
            assertEquals(before, database.snapshot())
            val save = editor.prepared(ScreenId.BATCH_EDITOR, "save-batch", mapOf(
                "id" to "confirmed-$index", "trayId" to tray.id.value, "substrate" to "Coir",
            ))
            manager.execute(ScreenId.BATCH_EDITOR, save).getOrThrow()
            val saved = database.snapshot().batches
            assertEquals(index + 1, saved.size)
            assertEquals(selected.planEntry?.harvestDate,
                (saved.last().actions.single { it.stage == GrowStage.READY_FOR_HARVEST }.due as DueMoment.CivilTime)
                    .localDateTime.date)
            reopen()
            val next = manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" }
            assertEquals((index + 1..index + 3).map { "cadence:$it" }.toSet(), next.map { it.id }.toSet())
            assertTrue(next.any { it.planEntry?.status == PlanEntryStatus.NEEDS_RESOLUTION })
            manager.execute(ScreenId.PLAN, ScreenCommand("pause-plan", "cadence")).getOrThrow()
            assertTrue(manager.watch(ScreenId.PLAN).first().items.none { it.actionId == "confirm-suggestion" })
            assertEquals(saved, database.snapshot().batches)
            reopen()
            manager.execute(ScreenId.PLAN, ScreenCommand("resume-plan", "cadence")).getOrThrow()
            assertEquals(next, manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" })
            assertEquals(saved, database.snapshot().batches)
        }
        val expected = manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" }
            .associate { it.id to (it.planEntry?.cultureId to it.planEntry?.harvestDate) }
        manager.execute(ScreenId.CLOSE_BATCH, ScreenCommand("close-failed", "confirmed-0",
            mapOf("occurredAt" to "2026-10-17T10:00:00Z"))).getOrThrow()
        reopen()
        assertEquals(expected, manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" }
            .associate { it.id to (it.planEntry?.cultureId to it.planEntry?.harvestDate) })
        assertEquals(6, database.snapshot().batches.size)
    }

    private suspend fun executeEditor(id: String, overrides: Map<String, String> = emptyMap()) {
        val editor = manager.watch(ScreenId.BATCH_EDITOR, id).first()
        manager.execute(ScreenId.BATCH_EDITOR, editor.prepared(ScreenId.BATCH_EDITOR, "correct-batch", overrides)).getOrThrow()
    }

    private fun open(initialize: Boolean) {
        factory = TestSqlDriverFactory(url = "jdbc:sqlite:${File(directory, "workspace.db").absolutePath}",
            initializeSchema = initialize)
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            now = { Instant.parse("2026-10-17T09:00:00Z") }, today = { LocalDate(2026, 10, 17) },
            timeZone = { TimeZone.UTC })
    }

    private fun reopen() {
        database.close()
        factory.close()
        open(initialize = false)
    }

    private fun batch(id: String, sow: LocalDate, harvest: LocalDate): GrowBatch {
        val stages = culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }
        val batchId = BatchId(id)
        return GrowBatch(batchId, RecipeSnapshot(null, culture.id, null, stages, culture.seedDensity),
            TraySnapshot(tray.name, tray.shape), "Coir", PositiveGrams(10.0), PositiveGrams(10.0),
            BatchState.PLANNED, Placement.None, emptyList(), BatchPlanningEngine.plannedActions(
                batchId, stages, sow, mapOf(GrowStage.READY_FOR_HARVEST to harvest), "UTC"),
            plannedSowDate = sow, cultureIdSnapshot = culture.id)
    }

    private fun ScreenSnapshot.prepared(screen: ScreenId, action: String, overrides: Map<String, String>): ScreenCommand =
        assertIs<CommandPreparation.Ready>(ScreenContractRegistry.prepareCommand(screen, ScreenActionId(action),
            selectedItemId, fields.associate { it.id to (overrides[it.id.value] ?: it.value) })).command
}
