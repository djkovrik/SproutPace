package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.ForecastCapacityEngine
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.ForecastRequest
import com.sedsoftware.sproutpace.domain.engine.ForecastReservation
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.StageEventId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class ForecastCapacityTest {
    private val light = GrowZone(GrowZoneId("light"), "Light", GrowZoneType.GROW_LIGHT, 1)
    private val second = light.copy(id = GrowZoneId("second"))
    private val dark = light.copy(id = GrowZoneId("dark"), type = GrowZoneType.DARK)
    private fun date(day: Int) = LocalDate(2026, 10, day)
    private fun leg(zone: GrowZone, start: Int, end: Int? = null) =
        ForecastReservation(zone.id, date(start), end?.let(::date))

    @Test fun soakingRetainsPlannedIntentWithoutOccupyingAndSowingUsesExplicitPlacement() {
        val planned = DomainFixtures.batch(placement = Placement.Assigned(dark.id, dark.name))
        val soaking = BatchLifecycleEngine.startSoaking(planned, StageEventId("soak"), DomainFixtures.instant).batch
        assertEquals(planned.placement, soaking.placement)
        assertFalse(soaking.state.occupiesPlace)
        val destination = Placement.Assigned(light.id, light.name)
        val sown = BatchLifecycleEngine.confirmSowing(soaking, StageEventId("sow"), DomainFixtures.instant, destination).batch
        assertEquals(destination, sown.placement)
    }

    @Test fun overlappingRequestsCompeteButSeparatedForecastsReuseCapacity() {
        val results = ForecastCapacityEngine.allocate(listOf(light), emptyList(), listOf(
            ForecastRequest("first", listOf(listOf(leg(light, 1, 10)))),
            ForecastRequest("overlap", listOf(listOf(leg(light, 5, 15)))),
            ForecastRequest("same-day", listOf(listOf(leg(light, 10, 20)))),
            ForecastRequest("later", listOf(listOf(leg(light, 11, 20)))),
        ))
        assertEquals(CapacityDecision.Feasible, results[0].decision)
        assertEquals(date(5), assertIs<CapacityDecision.NeedsResolution>(results[1].decision).conflict.date)
        assertIs<CapacityDecision.NeedsResolution>(results[2].decision)
        assertEquals(CapacityDecision.Feasible, results[3].decision)
    }

    @Test fun searchesOtherZonesAndChecksFutureStartsInsideInterval() {
        val results = ForecastCapacityEngine.allocate(listOf(light, second), listOf(leg(light, 7)), listOf(
            ForecastRequest("proposal", listOf(listOf(leg(light, 1, 10)), listOf(leg(second, 1, 10)))),
        ))
        assertEquals(CapacityDecision.Feasible, results.single().decision)
        assertEquals(second.id, results.single().route.single().zoneId)
    }

    @Test fun darkAndLightLegsMustBothFitAndCommittedConflictsKeepTheirDemand() {
        val existing = listOf(leg(light, 1, 4))
        val split = listOf(leg(dark, 1, 4), leg(light, 5, 10))
        val result = ForecastCapacityEngine.allocate(listOf(light, dark), existing, listOf(
            ForecastRequest("first", listOf(listOf(leg(light, 1, 10)), split)),
            ForecastRequest("second", listOf(split), committed = true),
            ForecastRequest("third", listOf(listOf(leg(light, 6, 8)))),
        ))
        assertEquals(split, result.first().route)
        assertEquals(CapacityDecision.Feasible, result.first().decision)
        assertEquals(dark.id, assertIs<CapacityDecision.NeedsResolution>(result[1].decision).conflict.zoneId)
        assertIs<CapacityDecision.NeedsResolution>(result[2].decision)
    }
}
