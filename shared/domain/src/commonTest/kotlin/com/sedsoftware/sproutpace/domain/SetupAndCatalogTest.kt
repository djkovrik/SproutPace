package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.CapacityEngine
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import com.sedsoftware.sproutpace.domain.engine.Occupancy
import com.sedsoftware.sproutpace.domain.model.*
import kotlin.math.PI
import kotlin.test.*

class SetupAndCatalogTest {
    @Test fun ac010_zoneAcceptsValidCapacityAndRejectsNegativeCapacity() {
        val zone = GrowZone(GrowZoneId("zone-1"), "Window shelf", GrowZoneType.NATURAL_LIGHT, 2)

        assertEquals(2, zone.capacity)
        assertTrue(zone.isLightCapable)
        assertFailsWith<IllegalArgumentException> {
            GrowZone(GrowZoneId("zone-invalid"), "Window shelf", GrowZoneType.NATURAL_LIGHT, -1)
        }
    }

    @Test fun ac012_zoneUpdateExposesCapacityConflictAndRealityResolution() {
        val zone = GrowZone(GrowZoneId("light"), "Shelf", GrowZoneType.GROW_LIGHT, 1)
        val decision = CapacityEngine.validate(
            zone,
            listOf(Occupancy(zone.id, BatchId("existing"))),
            recordingReality = true,
        )
        val conflict = assertIs<CapacityDecision.NeedsResolution>(decision).conflict
        assertEquals(0, conflict.availablePlaces)
        assertTrue(CapacityResolution.LEAVE_UNASSIGNED in conflict.resolutions)
    }

    @Test fun ac014_metricTrayShapesHaveExactAreaRules() {
        assertEquals(200.0, MeasurementEngine.trayAreaSquareCentimetres(TrayShape.Rectangle(10.0, 20.0)))
        assertEquals(PI * 100.0 / 4.0, MeasurementEngine.trayAreaSquareCentimetres(TrayShape.Circle(10.0)), 1e-9)
        assertFailsWith<IllegalArgumentException> { TrayShape.Rectangle(0.0, 20.0) }
        assertFailsWith<IllegalArgumentException> { TrayShape.Circle(-1.0) }
    }

    @Test fun ac020_catalogIsVersionedOfflineAndHasEightStableCultures() {
        assertTrue(SproutPaceCatalog.VERSION > 0)
        assertEquals(8, SproutPaceCatalog.cultures.size)
        assertEquals(
            setOf("radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress"),
            SproutPaceCatalog.cultureIds.mapTo(mutableSetOf()) { it.value },
        )
    }

    @Test fun ac021_personalRecipeRequiresValidAuthoredConfiguration() {
        val recipe = UserGrowRecipe(
            RecipeId("personal-1"), "My recipe", CultureId("radish"), DomainFixtures.stages,
            DomainFixtures.density, "Coco", germinationPercentage = Percentage(92),
        )
        assertEquals("My recipe", recipe.name)
        assertFailsWith<IllegalArgumentException> {
            recipe.copy(id = RecipeId("personal-2"), stages = emptyList())
        }
    }

    @Test fun ac026_seedRecommendationUsesMetricAreaAndRemainsSeparateFromActualMass() {
        val shape = TrayShape.Rectangle(10.0, 20.0)
        val recommended = MeasurementEngine.recommendedSeedMass(shape, DomainFixtures.density)
        val batch = DomainFixtures.batch().copy(recommendedSeedMass = recommended, actualSeedMass = PositiveGrams(5.5))
        assertEquals(4.0, batch.recommendedSeedMass.value)
        assertEquals(5.5, batch.actualSeedMass.value)
    }

    @Test fun ac027_safetyGuidanceAndSourcesAreBundledAsStableKeysAndMetadata() {
        SproutPaceCatalog.cultures.forEach { culture ->
            assertTrue(culture.safetyNoteKeys.isNotEmpty())
            assertTrue(culture.sources.all { it.url.startsWith("https://") })
            assertTrue(culture.safetyNoteKeys.any { it.value == "safety_images_not_diagnostic" })
        }
    }

    @Test fun qg011_catalogIdentityMetadataAndLocalizationKeysAreComplete() {
        val entries = SproutPaceCatalog.cultures
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.nameKey.value == "culture_${it.id.value}_name" })
        assertTrue(entries.all { it.scientificNameKey.value == "culture_${it.id.value}_scientific_name" })
        assertTrue(entries.all { it.stages.isNotEmpty() && it.seedDensity.version == SproutPaceCatalog.VERSION })
        val cress = entries.single { it.id == CultureId("garden_cress") }
        assertEquals("culture_garden_cress_scientific_name", cress.scientificNameKey.value)
        assertTrue(entries.none { it.id.value == "watercress" })
    }
}
