package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

/** Public router -> retained Store -> draft preparation -> production manager -> SQLite restart. */
class EditorCorrectionRegressionTest {
    @Test fun presetResizeKeepsSnapshotUntilExplicitReplacement() = snapshotSurvives("resize")
    @Test fun presetRenameKeepsSnapshotUntilExplicitReplacement() = snapshotSurvives("rename")
    @Test fun presetDeletionWithAnotherChoiceKeepsSnapshotUntilExplicitReplacement() = snapshotSurvives("delete")

    private fun snapshotSurvives(mutation: String) = runTest {
        withPublicWorkspace { directory, preferences ->
            val original = PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                    "name" to "Original", "shape" to "rectangle", "widthCm" to "10", "lengthCm" to "20"))
                val tray = fixture.database.snapshot().trayPresets.single()
                fixture.save(ScreenId.BATCH_EDITOR, null, "save-batch", mapOf(
                    "cultureId" to "radish", "trayId" to tray.id.value, "substrate" to "Coir"))
                val batch = fixture.database.snapshot().batches.single()
                fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                    "name" to "Other", "shape" to "rectangle", "widthCm" to "30", "lengthCm" to "40"))
                when (mutation) {
                    "delete" -> { fixture.open(ScreenId.TRAY_SIZES, tray.id.value); fixture.submit("delete-tray-size") }
                    "rename" -> fixture.save(ScreenId.TRAY_SIZES, tray.id.value, "save-tray-size", mapOf("name" to "Renamed"))
                    else -> fixture.save(ScreenId.TRAY_SIZES, tray.id.value, "save-tray-size",
                        mapOf("widthCm" to "20", "lengthCm" to "30"))
                }
                fixture.save(ScreenId.BATCH_EDITOR, batch.id.value, "correct-batch", mapOf("supplier" to "  Supplier  "))
                val corrected = fixture.database.snapshot().batches.single()
                assertEquals("  Supplier  ", corrected.supplier)
                assertEquals(batch.tray, corrected.tray)
                assertEquals(batch.recommendedSeedMass, corrected.recommendedSeedMass)
                batch
            }
            PublicProductionFixture(directory, preferences).use { fixture ->
                assertEquals(original.tray, fixture.database.snapshot().batches.single().tray)
                fixture.open(ScreenId.BATCH_EDITOR, original.id.value)
                val choice = fixture.model().fields.single { it.id.value == "trayId" }
                assertEquals(original.tray.shape, choice.options.single { it.id == choice.value }.trayShape)
                val other = fixture.database.snapshot().trayPresets.single { it.name == "Other" }
                fixture.dispatch(UiAction.ChooseOption("trayId", other.id.value))
                assertEquals(MeasurementEngine.recommendedSeedMass(other.shape, original.recipe.density).value.toString(),
                    fixture.model().facts.single { it.id == "recommended-seed-mass" }.authoredValue)
                fixture.submit("correct-batch")
                val changed = fixture.database.snapshot().batches.single()
                assertEquals(TraySnapshot(other.name, other.shape), changed.tray)
                assertEquals(MeasurementEngine.recommendedSeedMass(other.shape, original.recipe.density), changed.recommendedSeedMass)
                assertEquals(original.actualSeedMass, changed.actualSeedMass)
            }
            PublicProductionFixture(directory, preferences).use { fixture ->
                assertEquals("Other", fixture.database.snapshot().batches.single().tray.nameSnapshot)
            }
        }
    }

    @Test fun replacementPreviewUsesBatchDensityAfterRecipeChanged() = previewUsesSnapshot(deleteRecipe = false)
    @Test fun replacementPreviewUsesBatchDensityAfterRecipeDeleted() = previewUsesSnapshot(deleteRecipe = true)

    private fun previewUsesSnapshot(deleteRecipe: Boolean) = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.RECIPE, null, "save-recipe", mapOf("name" to "Original recipe", "substrate" to "Coir"))
                val recipe = fixture.database.snapshot().recipes.single()
                fixture.save(ScreenId.BATCH_EDITOR, recipe.id.value, "save-batch", mapOf("trayId" to "tray"))
                val original = fixture.database.snapshot().batches.single { it.id.value != "batch" }
                if (deleteRecipe) {
                    fixture.open(ScreenId.RECIPE, recipe.id.value); fixture.submit("delete-recipe")
                    assertTrue(fixture.database.snapshot().recipes.isEmpty())
                } else {
                    fixture.save(ScreenId.RECIPE, recipe.id.value, "save-recipe", mapOf("cultureId" to "pea"))
                    assertNotEquals(original.recipe.density, fixture.database.snapshot().recipes.single().density)
                }
                fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                    "name" to "Other", "shape" to "rectangle", "widthCm" to "10", "lengthCm" to "20"))
                val other = fixture.database.snapshot().trayPresets.single { it.name == "Other" }
                fixture.open(ScreenId.BATCH_EDITOR, original.id.value)
                assertEquals(1, fixture.model().fields.single { it.id.value == "trayId" }.options.count {
                    it.authoredLabel == original.tray.nameSnapshot && it.trayShape == original.tray.shape
                })
                fixture.dispatch(UiAction.ChooseOption("trayId", other.id.value))
                val expected = MeasurementEngine.recommendedSeedMass(other.shape, original.recipe.density)
                assertEquals(expected.value.toString(), fixture.model().facts.single { it.id == "recommended-seed-mass" }.authoredValue)
                fixture.submit("correct-batch")
                val saved = fixture.database.snapshot().batches.single { it.id == original.id }
                assertEquals(expected, saved.recommendedSeedMass)
                assertEquals(original.recipe, saved.recipe)
                assertEquals(original.actualSeedMass, saved.actualSeedMass)
            }
        }
    }

    @Test fun recipeAndBatchOptionalFieldsClearThroughDraftAndStayEmptyAfterRestart() = runTest {
        withPublicWorkspace { directory, preferences ->
            val ids = PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.RECIPE, null, "save-recipe", metadata + mapOf("name" to "Recipe", "substrate" to "Coir"))
                val recipe = fixture.database.snapshot().recipes.single()
                fixture.save(ScreenId.BATCH_EDITOR, recipe.id.value, "save-batch", mapOf("recipeId" to recipe.id.value,
                    "trayId" to "tray", "substrate" to "Coir"))
                val batch = fixture.database.snapshot().batches.single { it.id.value != "batch" }
                assertEquals(metadata["supplier"], batch.supplier)
                // Clear batch while recipe still has values: reopening must not inherit them again.
                fixture.save(ScreenId.BATCH_EDITOR, batch.id.value, "correct-batch", cleared)
                assertEmptyBatch(fixture.database.snapshot().batches.single { it.id == batch.id })
                fixture.open(ScreenId.BATCH_EDITOR, batch.id.value)
                cleared.keys.forEach { assertEquals("", fixture.field(it)) }
                fixture.save(ScreenId.RECIPE, recipe.id.value, "save-recipe", cleared)
                assertEmptyRecipe(fixture.database.snapshot().recipes.single())
                recipe.id.value to batch.id.value
            }
            PublicProductionFixture(directory, preferences).use { fixture ->
                assertEmptyRecipe(fixture.database.snapshot().recipes.single())
                assertEmptyBatch(fixture.database.snapshot().batches.single { it.id.value == ids.second })
                listOf(ScreenId.RECIPE to ids.first, ScreenId.BATCH_EDITOR to ids.second).forEach { (screen, id) ->
                    fixture.open(screen, id)
                    cleared.keys.forEach { assertEquals("", fixture.field(it)) }
                }
            }
        }
    }

    @Test fun removingOneAndThenLastFixedDateSurvivesReopenAndRestart() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.BATCH_EDITOR, "batch", "correct-batch", mapOf(
                    "fixedSownDate" to "2026-10-11", "fixedHarvestDate" to "2026-10-30"))
                fixture.save(ScreenId.BATCH_EDITOR, "batch", "correct-batch", mapOf("fixedSownDate" to ""))
                assertEquals(listOf(GrowStage.READY_FOR_HARVEST), fixture.database.snapshot().batches.single().actions
                    .filter { it.userFixed }.map { it.stage })
            }
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.open(ScreenId.BATCH_EDITOR, "batch")
                assertEquals("", fixture.field("fixedSownDate"))
                assertEquals("2026-10-30", fixture.field("fixedHarvestDate"))
                fixture.edit(mapOf("fixedHarvestDate" to "")); fixture.submit("correct-batch")
                assertTrue(fixture.database.snapshot().batches.single().actions.none { it.userFixed })
            }
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.open(ScreenId.BATCH_EDITOR, "batch")
                assertEquals("", fixture.field("fixedHarvestDate"))
                assertTrue(fixture.database.snapshot().batches.single().actions.none { it.userFixed })
            }
        }
    }

    @Test fun partialCommandsKeepAbsentMetadataAndFixedDatesAndPreserveAuthoredWhitespace() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.BATCH_EDITOR, "batch", "correct-batch", metadata + mapOf(
                    "fixedSownDate" to "2026-10-11", "fixedHarvestDate" to "2026-10-30"))
                val prepared = assertIs<CommandPreparation.Ready>(ScreenContractRegistry.prepareCommand(
                    ScreenId.BATCH_EDITOR, ScreenActionId("correct-batch"), "batch",
                    mapOf(ScreenFieldId("supplier") to "  new supplier  ", ScreenFieldId("fixedSownDate") to ""))).command
                fixture.manager.execute(ScreenId.BATCH_EDITOR, prepared).getOrThrow()
                val batch = fixture.database.snapshot().batches.single()
                assertEquals("  new supplier  ", batch.supplier)
                assertEquals(metadata["cultivar"], batch.cultivar)
                assertEquals(metadata["seedLot"], batch.seedLot)
                assertEquals(Percentage(85), batch.germinationPercentage)
                assertEquals(listOf(GrowStage.READY_FOR_HARVEST), batch.actions.filter { it.userFixed }.map { it.stage })
            }
        }
    }

    private fun assertEmptyBatch(batch: GrowBatch) {
        assertNull(batch.supplier); assertNull(batch.cultivar); assertNull(batch.seedLot); assertNull(batch.germinationPercentage)
    }
    private fun assertEmptyRecipe(recipe: UserGrowRecipe) {
        assertNull(recipe.supplier); assertNull(recipe.cultivar); assertNull(recipe.seedLot); assertNull(recipe.germinationPercentage)
    }
    private val metadata = mapOf("supplier" to "  supplier  ", "cultivar" to "  variety  ",
        "seedLot" to "  lot  ", "germinationPercentage" to "85")
    private val cleared = metadata.mapValues { "" }
}
