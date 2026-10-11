package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SuggestionEditorIntegrationTest {
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var manager: ProductionScreenDataManager
    private lateinit var files: InMemoryPrivateFileStore

    @BeforeTest fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        files = InMemoryPrivateFileStore()
        manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            workspaceRepository = LocalWorkspaceRepository(database, files, Dispatchers.Unconfined),
            now = { Instant.parse("2026-10-10T12:00:00Z") }, today = { LocalDate(2026, 10, 10) },
            timeZone = { TimeZone.of("Europe/Berlin") })
    }

    @AfterTest fun tearDown() { database.close(); files.close(); factory.close() }

    @Test fun acceptedTargetDisappearsRejectsStaleRouteAndReturnsOnlyAfterBatchDeletion() = runTest {
        seed()
        val source = manager.watch(ScreenId.BATCH_EDITOR, "first:0").first()
        val adjusted = source.copy(fields = source.fields.map {
            if (it.id.value == "plannedSowDate") it.copy(value = "2026-10-12") else it
        })
        val save = prepare(adjusted, "first:0")
        manager.execute(ScreenId.BATCH_EDITOR, save).getOrThrow()
        assertEquals(1, database.snapshot().batches.size)
        assertEquals(LocalDate(2026, 10, 12), database.snapshot().batches.single().plannedSowDate)
        assertTrue(manager.watch(ScreenId.PLAN).first().items.none { it.id == "first:0" })
        val stale = manager.watch(ScreenId.BATCH_EDITOR, "first:0").first()
        assertTrue(stale.fields.isEmpty() && stale.actions.isEmpty())
        assertEquals("suggestion-unavailable", stale.facts.single().id)
        assertEquals(1, database.snapshot().batches.size)

        manager.execute(ScreenId.CLOSE_BATCH, ScreenCommand("close-abandoned", save.subjectId,
            mapOf("occurredAt" to "2026-10-10T12:00:00Z"))).getOrThrow()
        assertTrue(manager.watch(ScreenId.PLAN).first().items.none { it.id == "first:0" })
        manager.execute(ScreenId.BATCH_DETAIL, ScreenCommand("delete-batch", save.subjectId)).getOrThrow()
        assertTrue(database.snapshot().batches.isEmpty())
        assertTrue(manager.watch(ScreenId.PLAN).first().items.any { it.id == "first:0" })
    }

    @Test fun acceptedContinuousSuggestionDisappearsFromTodayAndCannotReopenAsANewDraft() = runTest {
        seed()
        manager.execute(ScreenId.PLAN, ScreenCommand("save-plan", "continuous", mapOf(
            "anchorDate" to "2026-10-29", "cadenceDays" to "5", "cultureIds" to "radish,pea,broccoli",
            "mode" to "continuous_queue",
        ))).getOrThrow()
        val suggestion = manager.watch(ScreenId.TODAY).first().items.single { it.actionId == "confirm-suggestion" }
        val draft = manager.watch(ScreenId.BATCH_EDITOR, suggestion.id).first()
        manager.execute(ScreenId.BATCH_EDITOR, prepare(draft, suggestion.id)).getOrThrow()
        assertEquals(1, database.snapshot().batches.size)
        assertTrue(manager.watch(ScreenId.TODAY).first().items.none { it.id == suggestion.id })
        val stale = manager.watch(ScreenId.BATCH_EDITOR, suggestion.id).first()
        assertTrue(stale.actions.isEmpty())
        assertEquals("suggestion-unavailable", stale.facts.single().id)
    }

    @Test fun twoPlanRotationsOpenWithoutWritingAndSaveExactlyTheirDraftIdentityCultureAndDates() = runTest {
        seed()
        val suggestions = manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" }
        assertEquals(6, suggestions.size)
        assertEquals(listOf("radish", "pea", "broccoli"), suggestions.filter { it.id.startsWith("first:") }.sortedBy { it.id }.map { it.planEntry?.cultureId })
        assertEquals(listOf("broccoli", "radish", "pea"), suggestions.filter { it.id.startsWith("second:") }.sortedBy { it.id }.map { it.planEntry?.cultureId })
        suggestions.forEachIndexed { index, suggestion ->
            val before = database.snapshot().batches.size
            manager.watch(ScreenId.BATCH_EDITOR, suggestion.id).first()
            assertEquals(before, database.snapshot().batches.size, "Opening and abandoning a draft must not persist a batch")
            val editor = manager.watch(ScreenId.BATCH_EDITOR, suggestion.id).first()
            assertEquals(suggestion.planEntry?.cultureId, editor.value("cultureId"))
            assertEquals(suggestion.planEntry?.sowDate.toString(), editor.value("plannedSowDate"))
            assertEquals(suggestion.planEntry?.harvestDate.toString(), editor.value("fixedHarvestDate"))
            assertTrue(editor.value("recipeId").isBlank(), "An unrelated personal recipe must not override the suggestion")
            val command = prepare(editor, suggestion.id)
            assertEquals(editor.value("id"), command.subjectId)
            assertFalse(command.subjectId == suggestion.id || command.subjectId == editor.value("cultureId"))
            manager.execute(ScreenId.BATCH_EDITOR, command).getOrThrow()
            val saved = database.snapshot().batches.single { it.id.value == command.subjectId }
            assertEquals(index + 1, database.snapshot().batches.size)
            assertEquals(suggestion.planEntry?.cultureId, saved.cultureIdSnapshot?.value)
            assertEquals(suggestion.planEntry?.sowDate, saved.plannedSowDate)
            val harvest = saved.actions.single { it.stage == GrowStage.READY_FOR_HARVEST }
            assertTrue(harvest.userFixed)
            assertEquals(suggestion.planEntry?.harvestDate, (harvest.due as DueMoment.CivilTime).localDateTime.date)
            manager.execute(ScreenId.BATCH_EDITOR, command).getOrThrow()
            assertEquals(index + 1, database.snapshot().batches.size, "Retrying Save must not duplicate a batch")
        }
        val options = manager.watch(ScreenId.OBSERVATION_EDITOR).first().fields.single { it.id.value == "batchId" }.options
        assertEquals(6, options.map { Triple(it.labelKey, it.batchIdentity?.trayName, it.batchIdentity?.sowDate) }.distinct().size)
    }

    @Test fun pausedPlanProducesNoRecommendationAndOtherPlanKeepsItsIdentity() = runTest {
        seed()
        manager.execute(ScreenId.PLAN, ScreenCommand("pause-plan", "first")).getOrThrow()
        val suggestions = manager.watch(ScreenId.PLAN).first().items.filter { it.actionId == "confirm-suggestion" }
        assertEquals(3, suggestions.size)
        assertTrue(suggestions.all { it.id.startsWith("second:") })
        assertTrue(database.snapshot().batches.isEmpty())
        val stale = manager.watch(ScreenId.BATCH_EDITOR, "first:0").first()
        assertTrue(stale.actions.isEmpty())
        assertEquals(null, stale.primaryActionKey)
        assertEquals("suggestion-unavailable", stale.facts.single().id)
    }

    @Test fun lateTransitionPreviewDoesNotWriteAndKeepsFixedHarvestOnConfirmAndUndo() = runTest {
        seed()
        val editor = manager.watch(ScreenId.BATCH_EDITOR, "first:0").first()
        val save = prepare(editor, "first:0")
        manager.execute(ScreenId.BATCH_EDITOR, save).getOrThrow()
        manager.execute(ScreenId.BATCH_DETAIL, ScreenCommand("confirm-sowing", save.subjectId, mapOf(
            "eventId" to "sown", "occurredAt" to "2026-10-30T09:00:00Z", "unassigned" to "true",
        ))).getOrThrow()
        val before = database.snapshot().batches.single()
        val command = ScreenCommand("confirm-stage", save.subjectId, mapOf(
            "eventId" to "blackout", "occurredAt" to "2026-10-31T09:00:00Z", "nextState" to "blackout_or_weight",
        ))
        val preview = manager.previewStage(command).getOrThrow()
        assertEquals(before, database.snapshot().batches.single())
        val fixed = preview.dates.single { it.stageKey.value == "stage_harvest" }
        assertTrue(fixed.fixed)
        assertTrue(fixed.proposed != null && fixed.proposed != fixed.resulting)
        manager.execute(ScreenId.BATCH_DETAIL, preview.command).getOrThrow()
        val after = database.snapshot().batches.single()
        val harvest = after.actions.single { it.stage == GrowStage.READY_FOR_HARVEST }
        assertEquals(fixed.resulting, com.sedsoftware.sproutpace.domain.engine.SchedulingEngine.resolvedInstant(harvest))
        assertEquals(before.events.size + 1, after.events.size)
        manager.execute(ScreenId.BATCH_DETAIL, preview.command).getOrThrow()
        assertEquals(after, database.snapshot().batches.single(), "Repeated callback must not add a transition")
        val sowId = after.actions.single { it.stage == GrowStage.SOWN }.id.value
        val coverId = after.actions.single { it.stage == GrowStage.BLACKOUT_OR_WEIGHT }.id.value
        val todayTasks = manager.watch(ScreenId.TODAY).first().items.mapNotNull { it.task?.actionId }
        assertFalse(sowId in todayTasks)
        assertFalse(coverId in todayTasks)
        val reached = manager.watch(ScreenId.BATCH_DETAIL, save.subjectId).first().items
            .filter { it.task?.actionId in setOf(sowId, coverId) }
        assertEquals(2, reached.size)
        assertTrue(reached.all { it.commands.isEmpty() })
        assertTrue(reached.all { it.task?.pending == false && it.task?.overdue == false },
            "Reached tasks must not be described as expected or overdue")
        manager.execute(ScreenId.BATCH_DETAIL, ScreenCommand("undo-stage", save.subjectId)).getOrThrow()
        val undone = database.snapshot().batches.single()
        assertEquals(before.state, undone.state)
        assertEquals(before.events, undone.events)
        assertTrue(manager.watch(ScreenId.TODAY).first().items.any { it.task?.actionId == coverId })
        assertTrue(manager.watch(ScreenId.TODAY).first().items.single { it.task?.actionId == coverId }.task!!.pending)
        assertFalse(manager.watch(ScreenId.TODAY).first().items.any { it.task?.actionId == sowId })
        assertEquals(harvest.due, undone.actions.single { it.stage == GrowStage.READY_FOR_HARVEST }.due)
    }

    private suspend fun seed() {
        val recipeFields = buildMap {
            putAll(mapOf("name" to "My basil", "cultureId" to "basil", "substrate" to "Hemp mat"))
            com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog.cultures.first { it.id.value == "basil" }
                .stages.forEachIndexed { index, stage ->
                    val prefix = "stage${index + 1}"
                    put(prefix, stage.stage.name.lowercase())
                    put("${prefix}MinimumHours", stage.duration.minimum.inWholeHours.toString())
                    put("${prefix}MaximumHours", stage.duration.maximum.inWholeHours.toString())
                    put("${prefix}Guidance", "My saved instructions")
                }
        }
        manager.execute(ScreenId.RECIPE, ScreenCommand("save-recipe", "unrelated", recipeFields)).getOrThrow()
        manager.execute(ScreenId.TRAY_SIZES, ScreenCommand("save-tray-size", "tray", mapOf(
            "name" to "My tray", "shape" to "rectangle", "widthCm" to "25", "lengthCm" to "35",
        ))).getOrThrow()
        listOf("first" to "radish,pea,broccoli", "second" to "broccoli,radish,pea").forEach { (id, rotation) ->
            manager.execute(ScreenId.PLAN, ScreenCommand("save-plan", id, mapOf(
                "anchorDate" to "2026-10-29", "cadenceDays" to "5", "cultureIds" to rotation, "mode" to "cadence",
            ))).getOrThrow()
        }
    }

    private fun ScreenSnapshot.value(id: String) = fields.firstOrNull { it.id.value == id }?.value.orEmpty()

    private fun prepare(editor: ScreenSnapshot, route: String): ScreenCommand {
        val draft = editor.fields.associate { it.id to it.value }.toMutableMap()
        draft[ScreenFieldId("substrate")] = "Coconut coir"
        return assertIs<CommandPreparation.Ready>(ScreenContractRegistry.prepareCommand(
            ScreenId.BATCH_EDITOR, ScreenActionId("save-batch"), route, draft,
        )).command
    }
}
