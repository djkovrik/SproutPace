package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TaskDayGroup
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductionPreviewProjectionTest {
    @Test
    fun conflictedSuggestionRetainsItsDatedCapacityContextInTheEditor() {
        val scenario = ProductionScreenFixtures.Scenario.NEEDS_RESOLUTION
        val plan = typedPreviewState(UiScreen.PLAN, scenario = scenario)
        val editor = typedPreviewState(UiScreen.BATCH_EDITOR, scenario = scenario)
        val selected = assertNotNull(editor.items.singleOrNull { it.planEntry != null })
        assertEquals(plan.items.single { it.id == selected.id }.planEntry, selected.planEntry)
        assertEquals(UiVariant.CONFLICT, editor.variant)
        assertTrue(editor.conflictKeys.containsAll(listOf("choose_another_date", "choose_another_zone", "change_capacity")))
        assertEquals(selected.planEntry?.harvestDate.toString(), editor.fields.single { it.id == "fixedHarvestDate" }.value)
    }

    @Test
    fun invalidMassAndRecipeRangePreviewsExposeTheirIntendedEditorErrors() {
        val batch = typedPreviewState(UiScreen.BATCH_EDITOR, UiVariant.VALIDATION_ERROR)
        val batchErrors = batch.fields.filter { it.errorKey != null }
        assertEquals(listOf("actualSeedMassGrams"), batchErrors.map { it.id })
        assertEquals(BatchEditorStep.DETAILS, batchEditorStepForField(batchErrors.single().id))
        val recipe = typedPreviewState(UiScreen.RECIPE, UiVariant.VALIDATION_ERROR,
            scenario = ProductionScreenFixtures.Scenario.PERSONAL_RECIPE)
        val editor = recipeEditorFields(recipe.fields, copying = false)
        assertTrue(editor.any { it.id in setOf("stage1MinimumHours", "stage1MaximumHours") && it.errorKey != null })
    }

    @Test
    fun futureZoneFixtureSeparatesActualFreeSpaceFromCommittedAndProposedDemand() {
        val state = typedPreviewState(UiScreen.ZONES, scenario = ProductionScreenFixtures.Scenario.FUTURE_ZONE_CONFLICT)
        val occupancy = assertNotNull(state.items.single { it.id == "shelf" }.occupancy)
        assertEquals(2, occupancy.occupied)
        assertEquals(3, occupancy.capacity)
        val forecast = assertNotNull(occupancy.futureConflict)
        assertEquals(4, forecast.projectedOccupied)
        // The pending plan also needs this shelf today; planned batches alone conflict tomorrow.
        assertEquals("2026-10-10", forecast.date.toString())
    }

    @Test
    fun disabledNotificationsNeverReviveAnUnconditionalPrimaryPermissionAction() {
        val disabled = typedPreviewState(UiScreen.NOTIFICATIONS, scenario = ProductionScreenFixtures.Scenario.NOTIFICATIONS_DISABLED)
        val draftDisabled = typedPreviewState(UiScreen.NOTIFICATIONS,
            draft = mapOf("dailySummaryEnabled" to "false", "soakRemindersEnabled" to "false"))
        listOf(disabled, draftDisabled).forEach { state ->
            assertTrue(state.actions.none { it.id in setOf("request-notification-permission", "open-system-settings") })
        }
    }

    @Test
    fun repeatAndNoImageFixturesPreserveActualProductionSemantics() {
        val repeat = ProductionScreenFixtures.snapshot(ScreenId.BATCH_DETAIL,
            scenario = ProductionScreenFixtures.Scenario.REPEATED_BATCH)
        assertEquals("repeat-history-0", repeat.selectedItemId)
        val identity = assertNotNull(repeat.items.firstNotNullOfOrNull { it.batch })
        assertEquals(com.sedsoftware.sproutpace.domain.model.BatchState.PLANNED, identity.state)
        assertEquals(null, identity.closedAt)
        val recipe = typedPreviewState(UiScreen.RECIPE, scenario = ProductionScreenFixtures.Scenario.NO_IMAGE_RECIPE)
        assertEquals(null, assertNotNull(recipe.items.first().recipe).cultureId)
        val observation = typedPreviewState(UiScreen.OBSERVATION_EDITOR,
            scenario = ProductionScreenFixtures.Scenario.EDIT_OBSERVATION, errorKeyOverride = "error_operation_failed")
        assertEquals("error_operation_failed", observation.errorKey)
        assertTrue(observation.fields.single { it.id == "text" }.value.isNotBlank())
    }

    @Test
    fun selectedListDetailPreviewsRetainProductionChildItemsAndParentProjections() {
        listOf(Triple(UiScreen.TRAYS, "active-radish", UiScreen.BATCH_DETAIL),
            Triple(UiScreen.CATALOG, "radish", UiScreen.RECIPE)).forEach { (screen, selection, child) ->
            val ui = typedPreviewState(screen, scenario = ProductionScreenFixtures.Scenario.ACTIVE, selectedId = selection)
            assertEquals(selection, ui.selectedItemId)
            assertTrue(ui.items.single { it.id == selection }.selected)
            val detail = assertNotNull(ui.detail)
            assertEquals(child, detail.screen)
            val source = ProductionScreenFixtures.snapshot(child.toScreenId(), selection)
            assertEquals(source.items.map { it.id }, detail.items.map { it.id })
            assertEquals(source.facts.map { it.id }, detail.facts.map { it.id })
        }
        val plan = typedPreviewState(UiScreen.PLAN)
        assertEquals(ProductionScreenFixtures.snapshot(ScreenId.PLAN).plans, plan.plans)
        assertTrue(plan.plans.isNotEmpty())
        assertEquals(ProductionScreenFixtures.snapshot(ScreenId.FIRST_RUN).setupState,
            typedPreviewState(UiScreen.FIRST_RUN).setupState)
    }

    @Test
    fun availablePhotoPassesBytesThroughTheRealRendererAndBackupResultsUseManagerProjection() {
        val detail = ProductionScreenFixtures.snapshot(ScreenId.BATCH_DETAIL, scenario = ProductionScreenFixtures.Scenario.PHOTO)
        assertTrue(detail.items.any { it.photo?.bytes?.isNotEmpty() == true })
        listOf(ProductionScreenFixtures.Scenario.BACKUP_CREATED to "backup_created",
            ProductionScreenFixtures.Scenario.BACKUP_RESTORED to "backup_restored",
            ProductionScreenFixtures.Scenario.CSV_EXPORTED to "backup_csv_exported").forEach { (scenario, key) ->
            val ui = typedPreviewState(UiScreen.BACKUP_RESTORE, scenario = scenario)
            assertEquals(BackupScreenPhase.RESULT, backupScreenPhase(ui))
            assertEquals(key, ui.facts.single { it.id == "backup-result" }.valueKey)
        }
    }

    @Test
    fun allScreenPreviewsUseProductionSnapshotFieldsWithoutFabricatedUiValues() {
        ScreenId.entries.forEach { screen ->
            val source = ProductionScreenFixtures.snapshot(screen)
            val ui = typedPreviewState(UiScreen.valueOf(screen.name))
            source.fields.filterNot { screen == ScreenId.TRAYS && it.id.value == "category" }.forEach { field ->
                assertEquals(field.value, ui.fields.first { it.id == field.id.value }.value, "$screen/${field.id}")
            }
            if (screen == ScreenId.ADVERTISING_PRIVACY) {
                assertEquals("2026-10", ui.facts.single { it.id == "policy-version" }.authoredValue)
                assertEquals("privacy_undecided", ui.facts.single { it.id == "privacy-applicability" }.valueKey)
            } else if (screen == ScreenId.TRAYS) {
                assertEquals(ui.items.size.toString(), ui.facts.single { it.id == "batch-count" }.authoredValue)
                assertEquals(source.facts.filterNot { it.id == "batch-count" }.map { it.authoredValue },
                    ui.facts.filterNot { it.id == "batch-count" }.map { it.authoredValue })
            } else if (screen == ScreenId.BATCH_EDITOR) {
                assertNewBatchPreviewFacts(source, ui)
            } else {
                assertEquals(source.facts.map { it.authoredValue }, ui.facts.map { it.authoredValue }, screen.name)
            }
        }
    }

    @Test
    fun todayPreviewCarriesFourUniqueProductionTasksAcrossThreeDateGroups() {
        val tasks = typedPreviewState(UiScreen.TODAY).items.mapNotNull { it.task }
        assertEquals(4, tasks.size)
        assertEquals(4, tasks.map { it.actionId }.distinct().size)
        assertEquals(setOf(TaskDayGroup.OVERDUE, TaskDayGroup.TODAY, TaskDayGroup.UPCOMING), tasks.map { it.group }.toSet())
    }

    @Test
    fun zoneAndHistoryPreviewsExposeRealOccupancyAndTerminalIdentities() {
        val zone = typedPreviewState(UiScreen.ZONES).items.single { it.id == "shelf" }
        assertEquals(2, assertNotNull(zone.occupancy).occupied)
        assertEquals(8, zone.occupancy?.capacity)
        val history = typedPreviewState(UiScreen.TRAYS, scenario = ProductionScreenFixtures.Scenario.HISTORY)
        assertEquals(6, history.items.size)
        assertEquals("6", history.facts.single { it.id == "batch-count" }.authoredValue)
        assertTrue(history.items.all { assertNotNull(it.batch).state.isTerminal })
        assertTrue(history.items.map { it.batch?.closedAt }.distinct().size > 1)
        val harvested = history.items.filter { it.batch?.state == com.sedsoftware.sproutpace.domain.model.BatchState.HARVESTED }
        assertTrue(harvested.isNotEmpty())
        assertTrue(harvested.all { it.batch?.actualHarvestMassGrams == 184.0 })
    }

    @Test
    fun restorePreviewUsesProductionArchiveFacts() {
        val restore = typedPreviewState(UiScreen.BACKUP_RESTORE, UiVariant.RESTORE_PREVIEW)
        assertEquals(BackupScreenPhase.VALID_PREVIEW, backupScreenPhase(restore))
        assertEquals("2026-10-07T15:45:00Z", restore.facts.single { it.id == "restore-created-at" }.authoredValue)
        assertEquals("10", restore.facts.single { it.id == "restore-record-count-batches" }.authoredValue)
    }
}

/** Retained draft guidance adds a calculated fact while every raw production fact remains exact. */
internal fun assertNewBatchPreviewFacts(source: ScreenSnapshot, state: SproutPaceUiState) {
    assertTrue(source.actions.any { it.id.value == "save-batch" }, "The default preview must use a fresh draft")
    assertEquals("", source.fields.single { it.id.value == "recipeId" }.value,
        "This fresh fixture uses the selected built-in culture's density")
    val selectedTray = source.fields.single { it.id.value == "trayId" }
    val shape = assertNotNull(selectedTray.options.single { it.id == selectedTray.value }.trayShape)
    val selectedCulture = source.fields.single { it.id.value == "cultureId" }.value
    val density = SproutPaceCatalog.cultures.single { it.id.value == selectedCulture }.seedDensity
    val expectedMass = MeasurementEngine.recommendedSeedMass(shape, density).value.toString()
    val recommendation = state.facts.single { it.id == "recommended-seed-mass" }
    assertEquals(expectedMass, recommendation.authoredValue)
    val rawFacts = source.facts.map { UiFact(it.id, it.labelKey.value, it.valueKey?.value, it.authoredValue) }
    assertEquals(rawFacts + UiFact("recommended-seed-mass", "field_recommended_seed_mass", null, expectedMass), state.facts,
        "Only the independently calculated draft recommendation may supplement the unchanged production facts")
}
