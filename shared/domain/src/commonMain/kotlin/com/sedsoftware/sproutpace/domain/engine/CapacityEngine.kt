package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.CapacityConflict
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CapacityResolution
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import kotlinx.datetime.LocalDate

data class Occupancy(val zoneId: GrowZoneId, val batchId: BatchId)

object CapacityEngine {
    fun plannerHasLightCapacity(zones: List<GrowZone>): Boolean = zones.any { it.isLightCapable && it.capacity > 0 }

    fun validate(
        zone: GrowZone,
        occupancies: List<Occupancy>,
        additionalPlaces: Int = 1,
        date: LocalDate? = null,
        recordingReality: Boolean = false,
    ): CapacityDecision {
        require(additionalPlaces > 0)
        val available = (zone.capacity - occupancies.count { it.zoneId == zone.id }).coerceAtLeast(0)
        if (additionalPlaces <= available) return CapacityDecision.Feasible
        val resolutions = buildSet {
            add(CapacityResolution.CHOOSE_ANOTHER_DATE)
            add(CapacityResolution.CHOOSE_ANOTHER_ZONE)
            add(CapacityResolution.CHANGE_CAPACITY)
            if (recordingReality) add(CapacityResolution.LEAVE_UNASSIGNED)
        }
        return CapacityDecision.NeedsResolution(
            CapacityConflict(zone.id, date, additionalPlaces, available, resolutions),
        )
    }
}
