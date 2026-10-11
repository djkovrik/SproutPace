package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.CapacityConflict
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CapacityResolution
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import kotlinx.datetime.LocalDate

/** Forecast only: dates include the harvest day; no actual tray is released by this calculation. */
data class ForecastReservation(val zoneId: GrowZoneId, val from: LocalDate, val through: LocalDate? = null) {
    init { require(through == null || through >= from) }
    fun contains(date: LocalDate): Boolean = date >= from && (through == null || date <= through)
}

data class ForecastRequest(
    val id: String,
    val routes: List<List<ForecastReservation>>,
    val committed: Boolean = false,
)
data class ForecastAllocation(
    val id: String,
    val decision: CapacityDecision,
    val route: List<ForecastReservation>,
    val projectedOccupied: Int? = null,
)

/** Callers supply stable priority order; only feasible hypothetical routes reserve capacity. */
object ForecastCapacityEngine {
    fun allocate(
        zones: List<GrowZone>,
        existing: List<ForecastReservation>,
        requests: List<ForecastRequest>,
    ): List<ForecastAllocation> {
        val reserved = existing.toMutableList()
        return requests.map { request ->
            val choices = request.routes.map { route -> route to validate(zones, reserved, route) }
            val selected = choices.firstOrNull { it.second == CapacityDecision.Feasible } ?: choices.firstOrNull()
            val decision = selected?.second ?: missingCapacity()
            val route = selected?.first.orEmpty()
            val conflict = (decision as? CapacityDecision.NeedsResolution)?.conflict
            val projected = conflict?.date?.let { date ->
                reserved.count { it.zoneId == conflict.zoneId && it.contains(date) } + 1
            }
            if (decision == CapacityDecision.Feasible || request.committed) reserved += route
            ForecastAllocation(request.id, decision, route, projected)
        }
    }

    private fun validate(
        zones: List<GrowZone>,
        reserved: List<ForecastReservation>,
        route: List<ForecastReservation>,
    ): CapacityDecision {
        if (route.isEmpty()) return missingCapacity()
        for (leg in route) {
            val decision = validateLeg(zones, reserved, leg)
            if (decision != CapacityDecision.Feasible) return decision
        }
        return CapacityDecision.Feasible
    }

    private fun validateLeg(
        zones: List<GrowZone>, reserved: List<ForecastReservation>, leg: ForecastReservation,
    ): CapacityDecision {
        val zone = zones.firstOrNull { it.id == leg.zoneId } ?: return missingCapacity(leg.from)
        val dates = (listOf(leg.from) + reserved.filter { leg.contains(it.from) }.map { it.from }).distinct().sorted()
        for (date in dates) {
            val occupied = reserved.count { it.zoneId == zone.id && it.contains(date) }
            val available = (zone.capacity - occupied).coerceAtLeast(0)
            if (available == 0) return CapacityDecision.NeedsResolution(
                CapacityConflict(zone.id, date, 1, available, resolutions),
            )
        }
        return CapacityDecision.Feasible
    }

    private fun missingCapacity(date: LocalDate? = null) = CapacityDecision.NeedsResolution(
        CapacityConflict(null, date, 1, 0, resolutions),
    )

    private val resolutions = setOf(
        CapacityResolution.CHOOSE_ANOTHER_DATE, CapacityResolution.CHOOSE_ANOTHER_ZONE, CapacityResolution.CHANGE_CAPACITY,
    )
}
