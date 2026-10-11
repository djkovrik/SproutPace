package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchSuggestion
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import kotlinx.datetime.LocalDate

/** Fixed targets identify accepted slots without changing the persisted batch schema. */
data class ContinuousQueueSlot(val culture: CultureId, val target: LocalDate, val closedOn: LocalDate?)

object ContinuousQueueEngine {
    fun next(plan: HarvestPlan, slots: List<ContinuousQueueSlot>): BatchSuggestion? {
        var anchor = plan.anchorDate
        var rotation = 0
        val remaining = slots.toMutableList()
        while (true) {
            val culture = plan.cultureRotation[rotation % plan.cultureRotation.size]
            val candidate = PlannerEngine.nextContinuousSuggestion(
                plan.copy(cultureRotation = listOf(culture)), anchor,
            ) { _, _ -> CapacityDecision.Feasible } ?: return null
            // Later fixed targets also recognize slots accepted by the former calendar-based planner.
            val matching = remaining.filter { it.culture == culture && it.target >= candidate.targetHarvestDate }
                .minByOrNull { it.target } ?: return candidate
            val sameSlot = remaining.filter { it.culture == culture && it.target == matching.target }
            if (sameSlot.any { it.closedOn == null }) return null
            anchor = maxOf(matching.target, sameSlot.maxOf { requireNotNull(it.closedOn) })
            remaining.removeAll(sameSlot.toSet())
            rotation++
        }
    }
}
