package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import com.sedsoftware.sproutpace.domain.model.TrayShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class RecipeGuideIntegrationTest {
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var manager: ProductionScreenDataManager

    @BeforeTest
    fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            now = { Instant.parse("2026-10-10T09:00:00Z") }, today = { LocalDate.parse("2026-10-10") })
    }

    @AfterTest
    fun tearDown() {
        database.close()
        factory.close()
    }

    @Test
    fun builtInGuideHasExactRangesDensityAndUsableProvenanceWithoutEditActions() = runTest {
        val culture = SproutPaceCatalog.cultures.first()
        val snapshot = manager.watch(ScreenId.RECIPE, culture.id.value).first()
        val guide = assertNotNull(snapshot.items.first().recipe)
        assertTrue(guide.readOnly)
        assertEquals(culture.stages.map { it.duration.minimum.inWholeHours }, guide.stages.map { it.minimumHours })
        assertEquals(culture.stages.map { it.duration.maximum.inWholeHours }, guide.stages.map { it.maximumHours })
        assertEquals(culture.seedDensity.gramsPerSquareCentimetre, guide.gramsPerSquareCentimetre)
        assertEquals(culture.sources.map { it.url }, guide.sources.map { it.url })
        assertEquals(culture.seedDensity.version, guide.version)
        assertEquals(culture.sources.minOf { it.reviewedOn }.toString(), guide.reviewedOn)
        assertFalse(snapshot.actions.any { it.id.value in setOf("save-recipe", "update-recipe", "delete-recipe") })
        assertTrue(snapshot.actions.any { it.id.value == "duplicate-recipe" })
    }

    @Test
    fun builtInCopyCreatesAnIndependentPersonalRecipeWithoutMutatingCatalog() = runTest {
        val culture = SproutPaceCatalog.cultures.first()
        manager.execute(ScreenId.RECIPE, ScreenCommand("duplicate-recipe", culture.id.value, mapOf(
            "newId" to "personal-from-guide", "name" to "My growing method", "substrate" to "Coconut coir",
        ))).getOrThrow()
        val copied = database.watchRecipes().first().single()
        assertEquals("My growing method", copied.name)
        assertEquals(culture.seedDensity, copied.density)
        assertEquals(culture.stages.map { it.duration }, copied.stages.map { it.duration })
        assertEquals(culture.stages.map { it.instructionKey }, copied.stages.map { it.guidanceKey })
        database.saveRecipe(copied.copy(name = "Edited personal name", substrate = "Hemp mat"))
        assertEquals(culture, SproutPaceCatalog.cultures.first())
        assertFalse(assertNotNull(manager.watch(ScreenId.RECIPE, copied.id.value).first().items.first().recipe).readOnly)
    }

    @Test
    fun personalDuplicateRetainsAuthoredStageSnapshotWhenSourceChanges() = runTest {
        val culture = SproutPaceCatalog.cultures.first()
        val original = UserGrowRecipe(RecipeId("original"), "Original", culture.id,
            culture.stages.map { RecipeStage(it.stage, it.duration, userGuidance = "My ${it.stage.name} notes") },
            culture.seedDensity, "Coir")
        database.saveRecipe(original)
        manager.execute(ScreenId.RECIPE, ScreenCommand("duplicate-recipe", "original", mapOf("newId" to "copy"))).getOrThrow()
        database.saveRecipe(original.copy(name = "Changed", stages = original.stages.map { it.copy(userGuidance = "Changed") }))
        val duplicate = database.watchRecipes().first().first { it.id.value == "copy" }
        assertEquals(original.stages, duplicate.stages)
        assertEquals(original.density, duplicate.density)
        assertEquals("Original", duplicate.name)
    }

    @Test
    fun copiedGuideCanEditNameWithoutReplacingLocalizedGuidanceWithAuthoredText() = runTest {
        val culture = SproutPaceCatalog.cultures.first()
        manager.execute(ScreenId.RECIPE, ScreenCommand("duplicate-recipe", culture.id.value, mapOf(
            "newId" to "copy-to-edit", "name" to "My guide", "substrate" to "Coir",
        ))).getOrThrow()
        val copied = database.watchRecipes().first().single()
        val arguments = buildMap {
            put("name", "My renamed guide")
            put("cultureId", culture.id.value)
            put("substrate", copied.substrate)
            copied.stages.forEachIndexed { index, stage ->
                put("stage${index + 1}", stage.stage.name.lowercase())
                put("stage${index + 1}MinimumHours", stage.duration.minimum.inWholeHours.toString())
                put("stage${index + 1}MaximumHours", stage.duration.maximum.inWholeHours.toString())
            }
        }
        manager.execute(ScreenId.RECIPE, ScreenCommand("update-recipe", copied.id.value, arguments)).getOrThrow()
        val edited = database.watchRecipes().first().single()
        assertEquals("My renamed guide", edited.name)
        assertEquals(copied.stages, edited.stages)
        assertEquals(copied.density, edited.density)
    }

    @Test fun preparedRecipeEditorRejectsMissingReorderedAndDuplicateStagesWithoutReplacingSavedData() = runTest {
        val culture = SproutPaceCatalog.cultures.first()
        val initial = UserGrowRecipe(RecipeId("personal"), "Personal", culture.id,
            culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity, "Coir")
        database.saveRecipe(initial)
        val invalidConfigurations = listOf(
            listOf(GrowStage.LIGHT_GROWTH, GrowStage.SOWN, GrowStage.READY_FOR_HARVEST),
            listOf(GrowStage.SOWN, GrowStage.READY_FOR_HARVEST),
            listOf(GrowStage.SOWN, GrowStage.LIGHT_GROWTH),
            listOf(GrowStage.SOWN, GrowStage.SOWN, GrowStage.LIGHT_GROWTH, GrowStage.READY_FOR_HARVEST),
        )
        invalidConfigurations.forEach { stages ->
            val editor = manager.watch(ScreenId.RECIPE, initial.id.value).first()
            val result = manager.execute(ScreenId.RECIPE, editor.prepared("update-recipe", stageArguments(stages)))
            val error = assertIs<LocalDataException>(result.exceptionOrNull())
            assertEquals(LocalDataErrorKey.INVALID_RECIPE_STAGES, error.key)
            assertEquals("error_recipe_stage_order", error.errorKey.value)
            assertEquals(listOf(initial), database.snapshot().recipes)
        }
        // Previously saved malformed data still opens; rejecting another save must preserve it.
        val legacy = initial.copy(stages = initial.stages.reversed())
        database.saveRecipe(legacy)
        val legacyEditor = manager.watch(ScreenId.RECIPE, legacy.id.value).first()
        assertTrue(legacyEditor.fields.isNotEmpty())
        val result = manager.execute(ScreenId.RECIPE, legacyEditor.prepared("update-recipe", mapOf("name" to "Changed")))
        assertEquals(LocalDataErrorKey.INVALID_RECIPE_STAGES, assertIs<LocalDataException>(result.exceptionOrNull()).key)
        assertEquals(listOf(legacy), database.snapshot().recipes)
        val duplicate = manager.execute(ScreenId.RECIPE, ScreenCommand("duplicate-recipe", legacy.id.value,
            mapOf("newId" to "invalid-copy")))
        assertEquals(LocalDataErrorKey.INVALID_RECIPE_STAGES, assertIs<LocalDataException>(duplicate.exceptionOrNull()).key)
        val newBatch = manager.execute(ScreenId.BATCH_EDITOR, ScreenCommand("save-batch", "invalid-batch", mapOf(
            "recipeId" to legacy.id.value, "trayId" to "unused", "plannedSowDate" to "2026-10-20",
        )))
        assertEquals(LocalDataErrorKey.INVALID_RECIPE_STAGES, assertIs<LocalDataException>(newBatch.exceptionOrNull()).key)
        assertTrue(database.snapshot().batches.isEmpty())
        assertEquals(listOf(legacy), database.snapshot().recipes)
    }

    @Test fun optionalStagesCanBeOmittedAndRecipeCorrectionsKeepExistingBatchSnapshotAndAuthoredGuidance() = runTest {
        val culture = SproutPaceCatalog.cultures.first()
        val recipe = UserGrowRecipe(RecipeId("personal"), "Personal", culture.id,
            culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity, "Coir")
        database.saveRecipe(recipe)
        database.saveTrayPreset(TraySizePreset(TraySizePresetId("tray"), "Tray", TrayShape.Rectangle(20.0, 10.0)))
        manager.execute(ScreenId.BATCH_EDITOR, ScreenCommand("save-batch", "snapshot", mapOf(
            "recipeId" to recipe.id.value, "trayId" to "tray", "substrate" to "Coir", "plannedSowDate" to "2026-10-20",
        ))).getOrThrow()
        val snapshot = database.snapshot().batches.single()
        val required = listOf(GrowStage.SOWN, GrowStage.LIGHT_GROWTH, GrowStage.READY_FOR_HARVEST)
        val authored = "  My stage notes\nKeep this spacing.  "
        val editor = manager.watch(ScreenId.RECIPE, recipe.id.value).first()
        manager.execute(ScreenId.RECIPE, editor.prepared("update-recipe", stageArguments(required) +
            mapOf("stage1Guidance" to authored))).getOrThrow()
        val updated = database.snapshot().recipes.single()
        assertEquals(required, updated.stages.map { it.stage })
        assertEquals(authored, updated.stages.first().userGuidance)
        assertEquals(authored, manager.watch(ScreenId.RECIPE, recipe.id.value).first().fields
            .single { it.id.value == "stage1Guidance" }.value)
        assertEquals(snapshot, database.snapshot().batches.single())
        assertEquals(required, DomainJsonCodec().decodeRecipe(DomainJsonCodec().encodeRecipe(updated)).stages.map { it.stage })
        assertEquals(snapshot.recipe, DomainJsonCodec().decodeBatch(DomainJsonCodec().encodeBatch(snapshot)).recipe)
    }

    private fun stageArguments(stages: List<GrowStage>): Map<String, String> = buildMap {
        (1..5).forEach { slot ->
            put("stage$slot", stages.getOrNull(slot - 1)?.name?.lowercase() ?: "none")
            put("stage${slot}MinimumHours", "1")
            put("stage${slot}MaximumHours", "2")
            put("stage${slot}Guidance", "My growing guidance")
        }
    }

    private fun ScreenSnapshot.prepared(action: String, overrides: Map<String, String>) =
        assertIs<CommandPreparation.Ready>(ScreenContractRegistry.prepareCommand(ScreenId.RECIPE,
            ScreenActionId(action), selectedItemId,
            fields.associate { it.id to (overrides[it.id.value] ?: it.value) })).command
}
