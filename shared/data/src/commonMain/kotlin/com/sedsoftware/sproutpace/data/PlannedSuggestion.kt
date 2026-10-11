package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.component.ZoneFutureConflict
import com.sedsoftware.sproutpace.domain.engine.ContinuousQueueEngine
import com.sedsoftware.sproutpace.domain.engine.ContinuousQueueSlot
import com.sedsoftware.sproutpace.domain.engine.ForecastAllocation
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.engine.ForecastCapacityEngine
import com.sedsoftware.sproutpace.domain.engine.ForecastRequest
import com.sedsoftware.sproutpace.domain.engine.ForecastReservation
import com.sedsoftware.sproutpace.domain.engine.PlannerEngine
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchSuggestion
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.SuggestionStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

internal data class PlannedSuggestion(val suggestion: BatchSuggestion, val route: List<ForecastReservation>)

/** Shared, side-effect-free Plan/Today/editor forecast. Real batches take priority over proposals. */
internal data class WorkspaceForecast(val suggestions: List<PlannedSuggestion>, val allocations: List<ForecastAllocation>) {
    fun zoneConflict(zone: GrowZone): ZoneFutureConflict? = allocations.mapNotNull { allocation ->
        val conflict = (allocation.decision as? CapacityDecision.NeedsResolution)?.conflict
        val date = conflict?.date
        if (conflict?.zoneId != zone.id || date == null) null else {
            ZoneFutureConflict(date, allocation.projectedOccupied ?: zone.capacity + 1, zone.capacity)
        }
    }.minByOrNull { it.date }
}

internal fun Workspace.planningForecast(today: LocalDate, timeZone: TimeZone): List<PlannedSuggestion> =
    workspaceForecast(today, timeZone).suggestions

internal fun Workspace.workspaceForecast(today: LocalDate, timeZone: TimeZone): WorkspaceForecast {
    val candidates = plans.flatMap { plan ->
        if (plan.mode == HarvestPlanMode.CONTINUOUS_QUEUE) {
            listOfNotNull(ContinuousQueueEngine.next(plan, batches.mapNotNull { batch ->
                val culture = batch.cultureIdSnapshot ?: return@mapNotNull null
                val target = batch.actions.firstOrNull { it.stage == GrowStage.READY_FOR_HARVEST && it.userFixed }
                    ?.let { SchedulingEngine.resolvedInstant(it).toLocalDateTime(timeZone).date } ?: return@mapNotNull null
                ContinuousQueueSlot(culture, target, batch.outcome?.occurredAt?.toLocalDateTime(timeZone)?.date)
            }))
        } else {
            PlannerEngine.suggestions(plan, FORECAST_COUNT) { _, _ -> CapacityDecision.Feasible }
        }
    }.filterNot(this::coversSuggestion).sortedWith(compareBy({ it.sowDate(timeZone.id) }, { it.planId.value }, { it.id }))
    val existing = batches.filter { it.state.occupiesPlace }.mapNotNull { batch ->
        (batch.placement as? Placement.Assigned)?.let { ForecastReservation(it.zoneId, today) }
    }
    val committed = batches.filterNot { it.state.isTerminal }.sortedBy { it.id.value }.mapNotNull { batch ->
        batch.forecastRequest(zones, today, timeZone)
    }
    val requests = candidates.map { suggestion ->
        val stages = SproutPaceCatalog.cultures.first { it.id == suggestion.cultureId }.stages
            .map { RecipeStage(it.stage, it.duration, it.instructionKey) }
        val dates = forecastDates(stages, suggestion.sowDate(timeZone.id), suggestion.targetHarvestDate, timeZone)
        ForecastRequest(suggestion.id, forecastRoutes(zones, dates))
    }
    val allocations = ForecastCapacityEngine.allocate(zones, existing, committed + requests)
    val byId = allocations.associateBy { it.id }
    val suggestions = candidates.map { suggestion ->
        val result = byId.getValue(suggestion.id)
        val status = when (val decision = result.decision) {
            CapacityDecision.Feasible -> SuggestionStatus.Feasible
            is CapacityDecision.NeedsResolution -> SuggestionStatus.NeedsResolution(decision.conflict)
        }
        PlannedSuggestion(suggestion.copy(status = status), result.route)
    }
    return WorkspaceForecast(suggestions, allocations)
}

private data class ForecastDates(val sow: LocalDate, val light: LocalDate, val harvest: LocalDate)

private fun GrowBatch.forecastRequest(zones: List<GrowZone>, today: LocalDate, timeZone: TimeZone): ForecastRequest? {
    val selected = zones.firstOrNull { it.id == (placement as? Placement.Assigned)?.zoneId }
    if (state.occupiesPlace && selected?.isLightCapable != false) return null
    val sow = plannedSowDate ?: today
    val scheduled = actions.associate { it.stage to SchedulingEngine.resolvedInstant(it).toLocalDateTime(timeZone).date }
    val fallback = forecastDates(recipe.stages, sow, scheduled[GrowStage.READY_FOR_HARVEST], timeZone)
    val light = scheduled[GrowStage.LIGHT_GROWTH] ?: fallback.light
    val dates = ForecastDates(maxOf(sow, today), maxOf(light, today), maxOf(fallback.harvest, light, today))
    val routes = if (state.occupiesPlace) {
        // Current dark placement remains occupied until a real move; future light demand is still visible.
        zones.filter { it.isLightCapable }.sortedBy { it.id.value }.map {
            listOf(ForecastReservation(it.id, dates.light, dates.harvest))
        }
    } else forecastRoutes(zones, dates, selected)
    return ForecastRequest("batch:${id.value}", routes, committed = true)
}

private fun forecastDates(
    stages: List<RecipeStage>, sow: LocalDate, harvest: LocalDate?, timeZone: TimeZone,
): ForecastDates {
    val actions = BatchPlanningEngine.plannedActions(BatchId("forecast"), stages, sow, emptyMap(), timeZone.id)
    fun date(stage: GrowStage) = actions.firstOrNull { it.stage == stage }
        ?.let { SchedulingEngine.resolvedInstant(it).toLocalDateTime(timeZone).date }
    val end = maxOf(harvest ?: date(GrowStage.READY_FOR_HARVEST) ?: sow, sow)
    return ForecastDates(sow, (date(GrowStage.LIGHT_GROWTH) ?: sow).coerceIn(sow, end), end)
}

private fun forecastRoutes(zones: List<GrowZone>, dates: ForecastDates, selected: GrowZone? = null): List<List<ForecastReservation>> {
    val lights = zones.filter { it.isLightCapable }.sortedBy { it.id.value }
    val direct = lights.filter { selected == null || selected.id == it.id }.map {
        listOf(ForecastReservation(it.id, dates.sow, dates.harvest))
    }
    if (dates.light <= dates.sow) return direct
    val darks = zones.filter { !it.isLightCapable && (selected == null || selected.id == it.id) }.sortedBy { it.id.value }
    val split = darks.flatMap { dark -> lights.map { light ->
        listOf(ForecastReservation(dark.id, dates.sow, dates.light - DatePeriod(days = 1)),
            ForecastReservation(light.id, dates.light, dates.harvest))
    } }
    return direct + split
}

private const val FORECAST_COUNT = 3
