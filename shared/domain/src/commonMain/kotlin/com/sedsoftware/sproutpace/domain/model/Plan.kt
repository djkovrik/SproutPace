package com.sedsoftware.sproutpace.domain.model

import kotlinx.datetime.LocalDate

enum class HarvestPlanMode { CADENCE, CONTINUOUS_QUEUE }

data class HarvestCadenceDays(val value: Int) { init { require(value > 0) } }

data class HarvestPlan(
    val id: HarvestPlanId,
    val anchorDate: LocalDate,
    val cadence: HarvestCadenceDays,
    val cultureRotation: List<CultureId>,
    val mode: HarvestPlanMode,
    val paused: Boolean = false,
) {
    init { require(cultureRotation.isNotEmpty()) }
}

data class BatchSuggestion(
    val id: String,
    val planId: HarvestPlanId,
    val cultureId: CultureId,
    val targetHarvestDate: LocalDate,
    val status: SuggestionStatus,
)

sealed interface SuggestionStatus {
    data object Feasible : SuggestionStatus
    data class NeedsResolution(val conflict: CapacityConflict) : SuggestionStatus
}

enum class CapacityResolution { CHOOSE_ANOTHER_DATE, CHOOSE_ANOTHER_ZONE, CHANGE_CAPACITY, LEAVE_UNASSIGNED }

data class CapacityConflict(
    val zoneId: GrowZoneId?,
    val date: LocalDate?,
    val requiredPlaces: Int,
    val availablePlaces: Int,
    val resolutions: Set<CapacityResolution>,
) {
    init {
        require(requiredPlaces > availablePlaces)
        require(availablePlaces >= 0)
        require(resolutions.isNotEmpty())
    }
}

sealed interface CapacityDecision {
    data object Feasible : CapacityDecision
    data class NeedsResolution(val conflict: CapacityConflict) : CapacityDecision
}
