package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
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
}
