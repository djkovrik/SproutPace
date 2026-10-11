package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.TrayShape
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogV2Test {
    @Test fun referenceTrayMassesReproducePublishedStartingRates() {
        val grams = mapOf("radish" to 42.0, "broccoli" to 28.0, "arugula" to 10.0,
            "mustard" to 10.0, "pea" to 150.0, "sunflower" to 48.0, "basil" to 28.0, "garden_cress" to 10.0)
        SproutPaceCatalog.cultures.forEach { culture ->
            val supplier = culture.id.value in setOf("mustard", "garden_cress")
            val tray = if (supplier) TrayShape.Rectangle(25.0, 50.0) else TrayShape.Rectangle(25.4, 50.8)
            assertEquals(grams.getValue(culture.id.value),
                MeasurementEngine.recommendedSeedMass(tray, culture.seedDensity).value, 1e-9)
            assertEquals(2, culture.seedDensity.version)
            assertEquals(culture.sources.single(), culture.seedDensity.source)
        }
    }

    @Test fun harvestWindowAndPlannerDoNotCountCoveredTimeTwice() {
        val windows = mapOf("radish" to 8..12, "broccoli" to 8..12, "arugula" to 6..8,
            "mustard" to 8..12, "pea" to 8..10, "sunflower" to 9..10, "basil" to 12..16, "garden_cress" to 8..12)
        val sow = LocalDate(2026, 10, 1)
        SproutPaceCatalog.cultures.forEach { culture ->
            val window = windows.getValue(culture.id.value)
            val afterSow = culture.stages.filter { it.stage != GrowStage.SOAKING }
            assertEquals(window.first.toLong(), afterSow.sumOf { it.duration.minimum.inWholeDays })
            assertEquals(window.last.toLong(), afterSow.sumOf { it.duration.maximum.inWholeDays })
            val stages = culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }
            val actions = BatchPlanningEngine.plannedActions(BatchId(culture.id.value), stages, sow, emptyMap(), "UTC")
            val harvest = (actions.single { it.stage == GrowStage.READY_FOR_HARVEST }.due as DueMoment.CivilTime).localDateTime.date
            assertEquals(LocalDate(2026, 10, 1 + window.last), harvest)
            assertEquals(sow, BatchPlanningEngine.sowDateForHarvest(stages, harvest, "UTC"))
            assertTrue(stages.all { it.guidanceKey!!.value.contains("_v2_") })
        }
    }

    @Test fun presoakIsBeforeSowingAndOnlyConfiguredForLargeSeeds() {
        val soaking = SproutPaceCatalog.cultures.filter { c -> c.stages.any { it.stage == GrowStage.SOAKING } }
        assertEquals(setOf("pea", "sunflower"), soaking.map { it.id.value }.toSet())
        soaking.forEach { culture ->
            val expected = if (culture.id.value == "pea") 6L else 12L
            val stage = culture.stages.first()
            assertEquals(expected, stage.duration.minimum.inWholeHours)
            assertEquals(expected, stage.duration.maximum.inWholeHours)
        }
    }
}
