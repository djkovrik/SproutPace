package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.contract.PlanManager
import com.sedsoftware.sproutpace.domain.contract.SetupManager
import com.sedsoftware.sproutpace.domain.contract.domainResult
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.CapacityEngine
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import com.sedsoftware.sproutpace.domain.engine.Occupancy
import com.sedsoftware.sproutpace.domain.engine.SnapshotEngine
import com.sedsoftware.sproutpace.domain.model.BackupManifest
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CapacityResolution
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestCadenceDays
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanId
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DependencyReadinessTest {
    @Test
    fun ac011_setupManagerExposesRestorableZonesAndFlatResultBoundaries() = runTest {
        val zone = GrowZone(GrowZoneId("zone-persisted"), "Window shelf", GrowZoneType.NATURAL_LIGHT, 2)
        val failure = IllegalStateException("database unavailable")
        val manager = object : SetupManager {
            override fun watchZones(): Flow<List<GrowZone>> = flowOf(listOf(zone))
            override suspend fun saveZone(zone: GrowZone): Result<GrowZone> = domainResult { throw failure }
            override suspend fun saveTrayPreset(preset: TraySizePreset): Result<TraySizePreset> = Result.success(preset)
        }

        assertEquals(listOf(zone), manager.watchZones().first())
        assertSame(failure, manager.saveZone(zone).exceptionOrNull())
    }

    @Test
    fun ac014_metricTrayGeometryIsStableForPersistence() {
        val rectangle = TrayShape.Rectangle(widthCentimetres = 10.0, lengthCentimetres = 20.0)
        val circle = TrayShape.Circle(diameterCentimetres = 10.0)

        assertEquals(200.0, MeasurementEngine.trayAreaSquareCentimetres(rectangle))
        assertEquals(PI * 25.0, MeasurementEngine.trayAreaSquareCentimetres(circle), absoluteTolerance = 1e-9)
        assertFailsWith<IllegalArgumentException> { TrayShape.Rectangle(Double.NaN, 20.0) }
    }

    @Test
    fun ac020_builtInCatalogHasVersionedLanguageNeutralIdentity() {
        val expectedIds = listOf(
            "radish",
            "broccoli",
            "arugula",
            "mustard",
            "pea",
            "sunflower",
            "basil",
            "garden_cress",
        )

        assertTrue(SproutPaceCatalog.VERSION > 0)
        assertEquals(expectedIds, SproutPaceCatalog.cultures.map { it.id.value })
        assertEquals(expectedIds.toSet(), SproutPaceCatalog.cultureIds.mapTo(linkedSetOf()) { it.value })
        SproutPaceCatalog.cultures.forEach { culture ->
            assertEquals("culture_${culture.id.value}_name", culture.nameKey.value)
            assertEquals(SproutPaceCatalog.VERSION, culture.seedDensity.version)
        }
    }

    @Test
    fun ac021_personalRecipePreservesStableIdentityAndAuthoredFields() {
        val recipe = UserGrowRecipe(
            id = RecipeId("recipe-personal"),
            name = "  My radish mix  ",
            cultureId = CultureId("radish"),
            stages = DomainFixtures.stages,
            density = DomainFixtures.density,
            substrate = "  Coco and hemp  ",
            supplier = "Local supplier",
            seedLot = "LOT-42",
        )

        assertEquals("recipe-personal", recipe.id.value)
        assertEquals("  My radish mix  ", recipe.name)
        assertEquals("  Coco and hemp  ", recipe.substrate)
        assertEquals("LOT-42", recipe.seedLot)
        assertFailsWith<IllegalArgumentException> { recipe.copy(stages = emptyList()) }
    }

    @Test
    fun ac030_plannedBatchOwnsImmutableRecipeAndTraySnapshotIdentity() {
        val batch = DomainFixtures.batch().copy(id = BatchId("batch-planned"))

        assertEquals("batch-planned", batch.id.value)
        assertEquals(RecipeId("recipe-1"), batch.recipe.sourceRecipeId)
        assertEquals("User recipe", batch.recipe.nameSnapshot)
        assertEquals("Tray", batch.tray.nameSnapshot)
        assertEquals(TrayShape.Rectangle(10.0, 20.0), batch.tray.shape)
    }

    @Test
    fun ac031_snapshotRestoresBatchDetailAndLinkedObservationIdentity() {
        val batch = DomainFixtures.batch().copy(id = BatchId("batch-restored"))
        val observation = Observation(
            id = ObservationId("observation-restored"),
            batchId = batch.id,
            occurredAt = DomainFixtures.instant,
            text = "  Roots visible  ",
        )
        val snapshot = ProductSnapshot(
            manifest = BackupManifest(
                schemaVersion = 1,
                archiveVersion = 1,
                recordCounts = mapOf(
                    "zones" to 0,
                    "trayPresets" to 0,
                    "recipes" to 0,
                    "batches" to 1,
                    "plans" to 0,
                    "observations" to 1,
                ),
                photoIntegrity = emptyMap(),
            ),
            zones = emptyList(),
            trayPresets = emptyList(),
            recipes = emptyList(),
            batches = listOf(batch),
            plans = emptyList(),
            observations = listOf(observation),
        )

        val restored = assertIs<RestoreValidation.Valid>(SnapshotEngine.validate(snapshot)).snapshot
        assertEquals(batch.id, restored.batches.single().id)
        assertEquals(batch.recipe, restored.batches.single().recipe)
        assertEquals(batch.tray, restored.batches.single().tray)
        assertEquals(observation.id, restored.observations.single().id)
        assertEquals(batch.id, restored.observations.single().batchId)
        assertEquals("  Roots visible  ", restored.observations.single().text)
    }

    @Test
    fun ac051_planManagerRestoresStablePlanIdentity() = runTest {
        val plan = HarvestPlan(
            id = HarvestPlanId("plan-weekly"),
            anchorDate = LocalDate(2026, 9, 20),
            cadence = HarvestCadenceDays(7),
            cultureRotation = listOf(CultureId("radish"), CultureId("pea")),
            mode = HarvestPlanMode.CADENCE,
        )
        val manager = object : PlanManager {
            override fun watchPlans(): Flow<List<HarvestPlan>> = flowOf(listOf(plan))
            override suspend fun savePlan(plan: HarvestPlan): Result<HarvestPlan> = Result.success(plan)
        }

        assertEquals(plan, manager.watchPlans().first().single())
        assertEquals(LocalDate(2026, 9, 20), manager.watchPlans().first().single().anchorDate)
        assertEquals(plan.id, manager.savePlan(plan).getOrThrow().id)
    }

    @Test
    fun ac055_capacityConflictPreservesDateCountsAndResolutionSemantics() {
        val zone = GrowZone(GrowZoneId("zone-full"), "Grow shelf", GrowZoneType.GROW_LIGHT, capacity = 1)
        val date = LocalDate(2026, 10, 1)
        val decision = CapacityEngine.validate(
            zone = zone,
            occupancies = listOf(Occupancy(zone.id, BatchId("batch-existing"))),
            additionalPlaces = 2,
            date = date,
        )

        val conflict = assertIs<CapacityDecision.NeedsResolution>(decision).conflict
        assertEquals(zone.id, conflict.zoneId)
        assertEquals(date, conflict.date)
        assertEquals(2, conflict.requiredPlaces)
        assertEquals(0, conflict.availablePlaces)
        assertEquals(
            setOf(
                CapacityResolution.CHOOSE_ANOTHER_DATE,
                CapacityResolution.CHOOSE_ANOTHER_ZONE,
                CapacityResolution.CHANGE_CAPACITY,
            ),
            conflict.resolutions,
        )
    }

    @Test
    fun ac080_everyTerminalOutcomeReleasesCapacityAndCancelsPendingWork() {
        val zone = GrowZone(GrowZoneId("zone-terminal"), "Light shelf", GrowZoneType.GROW_LIGHT, 1)
        val assigned = Placement.Assigned(zone.id, zone.name)
        val outcomes = listOf(
            BatchOutcome.Harvested(DomainFixtures.instant) to BatchState.HARVESTED,
            BatchOutcome.Failed(DomainFixtures.instant, reason = "Mold") to BatchState.FAILED,
            BatchOutcome.Abandoned(DomainFixtures.instant, reason = "Travel") to BatchState.ABANDONED,
        )

        outcomes.forEachIndexed { index, (outcome, expectedState) ->
            val action = ScheduledAction(
                id = ScheduledActionId("pending-$index"),
                batchId = BatchId("batch-1"),
                stage = GrowStage.READY_FOR_HARVEST,
                due = DueMoment.ElapsedTarget(DomainFixtures.instant),
            )
            val change = BatchLifecycleEngine.close(
                DomainFixtures.batch(BatchState.READY_FOR_HARVEST, assigned, actions = listOf(action)),
                outcome,
            )

            assertEquals(expectedState, change.batch.state)
            assertEquals(Placement.None, change.batch.placement)
            assertEquals(ScheduledActionStatus.CANCELLED, change.batch.actions.single().status)
            assertEquals(setOf(action.id), change.cancelledActionIds)
        }
    }
}
