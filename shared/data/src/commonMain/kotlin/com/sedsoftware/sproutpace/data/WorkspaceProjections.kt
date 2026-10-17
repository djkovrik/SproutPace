package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.BatchIdentity
import com.sedsoftware.sproutpace.component.ZoneOccupancy
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.BatchSuggestion
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import kotlinx.datetime.LocalDate

internal fun GrowBatch.identity() = BatchIdentity(
    batchId = id.value,
    trayName = tray.nameSnapshot,
    sowDate = plannedSowDate,
    zoneName = (placement as? Placement.Assigned)?.zoneNameSnapshot,
    state = state,
    closedAt = outcome?.occurredAt,
    actualHarvestMassGrams = (outcome as? com.sedsoftware.sproutpace.domain.model.BatchOutcome.Harvested)?.actualMass?.value,
)

internal fun GrowZone.occupancy(batches: List<GrowBatch>) = ZoneOccupancy(
    zoneId = id.value,
    zoneName = name,
    type = type,
    occupied = batches.count { it.state.occupiesPlace && (it.placement as? Placement.Assigned)?.zoneId == id },
    capacity = capacity,
)

internal fun Workspace.suggestion(
    id: String?, today: LocalDate, timeZone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault(),
): BatchSuggestion? = id?.let { value -> planningForecast(today, timeZone).firstOrNull { it.suggestion.id == value }?.suggestion }

/** A saved fixed harvest target fulfills a plan slot, even when its sow date was adjusted. */
internal fun Workspace.coversSuggestion(suggestion: BatchSuggestion): Boolean = batches.any { batch ->
    batch.cultureIdSnapshot == suggestion.cultureId && batch.actions.any { action ->
        action.stage == GrowStage.READY_FOR_HARVEST && action.userFixed &&
            (action.due as? DueMoment.CivilTime)?.localDateTime?.date == suggestion.targetHarvestDate
    }
}

internal fun BatchSuggestion.sowDate(timeZoneId: String): LocalDate {
    val culture = SproutPaceCatalog.cultures.first { it.id == cultureId }
    return BatchPlanningEngine.sowDateForHarvest(
        culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, targetHarvestDate, timeZoneId,
    )
}

