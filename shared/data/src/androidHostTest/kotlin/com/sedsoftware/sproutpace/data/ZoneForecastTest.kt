package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZoneForecastTest {
    private val today = LocalDate.parse("2026-10-10")
    private val zone = GrowZone(GrowZoneId("shelf"), "Shelf", GrowZoneType.NATURAL_LIGHT, 2)

    @Test fun plannedStartsAccumulateWithoutChangingActualOccupancy() {
        val batches = listOf(batch("active", BatchState.SOWN, "2026-10-09"),
            batch("first", BatchState.PLANNED, "2026-10-11"), batch("second", BatchState.PLANNED, "2026-10-12"))
        val forecast = forecast(batches)
        assertEquals(LocalDate.parse("2026-10-12"), forecast?.date)
        assertEquals(3, forecast?.projectedOccupied)
        assertEquals(1, zone.occupancy(batches).occupied)
        assertNull(forecast(batches.dropLast(1)))
    }

    @Test fun unassignedAndClosedBatchesDoNotInventFutureDemand() {
        val active = batch("active", BatchState.SOWN, "2026-10-09")
        val unassigned = batch("unassigned", BatchState.PLANNED, "2026-10-11").copy(placement = Placement.None)
        assertNull(forecast(listOf(active, unassigned)))
        assertNull(forecast(listOf(active.copy(state = BatchState.ABANDONED,
            outcome = BatchOutcome.Abandoned(kotlinx.datetime.Instant.parse("2026-10-10T10:00:00Z"))),
            batch("first", BatchState.PLANNED, "2026-10-11"),
            batch("second", BatchState.PLANNED, "2026-10-12"))))
    }

    private fun forecast(batches: List<GrowBatch>) = Workspace(listOf(zone), emptyList(), emptyList(), batches,
        emptyList(), emptyList()).workspaceForecast(today, kotlinx.datetime.TimeZone.UTC).zoneConflict(zone)

    private fun batch(id: String, state: BatchState, date: String): GrowBatch {
        val culture = SproutPaceCatalog.cultures.first()
        return GrowBatch(
            id = BatchId(id), recipe = RecipeSnapshot(null, culture.id, null,
                culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
            tray = TraySnapshot("Tray", TrayShape.Rectangle(20.0, 10.0)), substrateSnapshot = "Coir",
            recommendedSeedMass = PositiveGrams(10.0), actualSeedMass = PositiveGrams(10.0),
            state = state, placement = Placement.Assigned(zone.id, zone.name), events = emptyList(), actions = emptyList(),
            plannedSowDate = LocalDate.parse(date),
        )
    }
}
