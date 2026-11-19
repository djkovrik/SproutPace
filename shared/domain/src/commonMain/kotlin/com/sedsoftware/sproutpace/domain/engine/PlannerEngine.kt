package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchSuggestion
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.SuggestionStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object PlannerEngine {
    fun suggestions(
        plan: HarvestPlan,
        count: Int,
        capacityAt: (LocalDate, CultureId) -> CapacityDecision,
    ): List<BatchSuggestion> = suggestions(plan, count, emptyMap(), capacityAt)

    /** Keep absolute cadence slots and rotation when advancing the unconfirmed horizon. */
    fun suggestions(
        plan: HarvestPlan,
        count: Int,
        earliestHarvestDates: Map<CultureId, LocalDate>,
        capacityAt: (LocalDate, CultureId) -> CapacityDecision,
    ): List<BatchSuggestion> {
        require(count >= 0)
        if (plan.paused) return emptyList()
        val earliest = plan.cultureRotation.minOf { earliestHarvestDates[it] ?: plan.anchorDate }
        val firstIndex = ((earliest.toEpochDays() - plan.anchorDate.toEpochDays()) / plan.cadence.value)
            .coerceAtLeast(0).toInt()
        return generateSequence(firstIndex) { it + 1 }.map { index ->
            val culture = plan.cultureRotation[index % plan.cultureRotation.size]
            val date = when (plan.mode) {
                HarvestPlanMode.CADENCE -> plan.anchorDate + DatePeriod(days = plan.cadence.value * index)
                HarvestPlanMode.CONTINUOUS_QUEUE -> plan.anchorDate + DatePeriod(days = plan.cadence.value * index)
            }
            val status = when (val decision = capacityAt(date, culture)) {
                CapacityDecision.Feasible -> SuggestionStatus.Feasible
                is CapacityDecision.NeedsResolution -> SuggestionStatus.NeedsResolution(decision.conflict)
            }
            BatchSuggestion("${plan.id.value}:$index", plan.id, culture, date, status)
        }.filter { it.targetHarvestDate >= (earliestHarvestDates[it.cultureId] ?: plan.anchorDate) }
            .take(count).toList()
    }

    fun nextContinuousSuggestion(
        plan: HarvestPlan,
        actualHarvestDate: LocalDate,
        capacityAt: (LocalDate, CultureId) -> CapacityDecision,
    ): BatchSuggestion? {
        if (plan.paused || plan.mode != HarvestPlanMode.CONTINUOUS_QUEUE) return null
        val laterDate = maxOf(plan.anchorDate, actualHarvestDate)
        val targetDate = laterDate + DatePeriod(days = plan.cadence.value)
        val culture = plan.cultureRotation.first()
        val status = when (val decision = capacityAt(targetDate, culture)) {
            CapacityDecision.Feasible -> SuggestionStatus.Feasible
            is CapacityDecision.NeedsResolution -> SuggestionStatus.NeedsResolution(decision.conflict)
        }
        return BatchSuggestion(
            id = "${plan.id.value}:continuous:$targetDate",
            planId = plan.id,
            cultureId = culture,
            targetHarvestDate = targetDate,
            status = status,
        )
    }
}
