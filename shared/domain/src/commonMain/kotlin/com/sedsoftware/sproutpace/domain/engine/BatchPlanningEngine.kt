package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

/** Pure creation/correction rules for AC-030 and AC-032. */
object BatchPlanningEngine {
    /** Reverse the same maximum-duration schedule used for a newly planned batch. */
    fun sowDateForHarvest(
        stages: List<RecipeStage>,
        harvestDate: LocalDate,
        timeZoneId: String,
    ): LocalDate {
        val sowIndex = stages.indexOfFirst { it.stage == GrowStage.SOWN }
        val harvestIndex = stages.indexOfFirst { it.stage == GrowStage.READY_FOR_HARVEST }
        require(sowIndex >= 0 && harvestIndex > sowIndex)
        val zone = TimeZone.of(timeZoneId)
        val harvestAt = LocalDateTime(harvestDate, plannedActionTime).toInstant(zone)
        return (harvestAt - stages.subList(sowIndex, harvestIndex).totalMaximumDuration()).toLocalDateTime(zone).date
    }

    fun plannedActions(
        batchId: BatchId,
        stages: List<RecipeStage>,
        plannedSowDate: LocalDate,
        fixedStageDates: Map<GrowStage, LocalDate>,
        timeZoneId: String,
    ): List<ScheduledAction> {
        val sowIndex = stages.indexOfFirst { it.stage == GrowStage.SOWN }
        require(sowIndex >= 0) { "A planned batch requires a sown stage" }
        val zone = TimeZone.of(timeZoneId)
        val sowAt = LocalDateTime(plannedSowDate, plannedActionTime).toInstant(zone)
        return stages.mapIndexed { index, stage ->
            val projected = if (index < sowIndex) {
                sowAt - stages.subList(index, sowIndex).totalMaximumDuration()
            } else {
                sowAt + stages.subList(sowIndex, index).totalMaximumDuration()
            }
            val fixedDate = fixedStageDates[stage.stage]
            val due = if (fixedDate == null) {
                projected.toLocalDateTime(zone)
            } else {
                LocalDateTime(fixedDate, plannedActionTime)
            }
            ScheduledAction(
                id = ScheduledActionId("planned:${batchId.value}:${stage.stage.name.lowercase()}"),
                batchId = batchId,
                stage = stage.stage,
                due = DueMoment.CivilTime(due, timeZoneId),
                userFixed = fixedDate != null,
            )
        }
    }

    fun applyFixedDates(
        actions: List<ScheduledAction>,
        fixedStageDates: Map<GrowStage, LocalDate>,
        timeZoneId: String,
    ): List<ScheduledAction> = actions.map { action ->
        if (action.status != ScheduledActionStatus.PENDING) return@map action
        val stage = action.stage ?: return@map action
        val fixedDate = fixedStageDates[stage]
        when {
            fixedDate != null -> action.copy(
                due = DueMoment.CivilTime(LocalDateTime(fixedDate, plannedActionTime), timeZoneId),
                userFixed = true,
            )
            action.userFixed -> action.copy(userFixed = false)
            else -> action
        }
    }

    fun correctActualEventDates(
        batch: GrowBatch,
        correctedDates: Map<BatchState, Instant>,
    ): GrowBatch {
        if (correctedDates.isEmpty()) return batch
        val missing = correctedDates.keys - batch.events.mapTo(mutableSetOf()) { it.to }
        require(missing.isEmpty()) { "Cannot correct an actual date for a stage without an event: $missing" }
        val events = batch.events.map { event ->
            correctedDates[event.to]?.let { corrected ->
                if (corrected == event.occurredAt) event else event.copy(
                    occurredAt = corrected,
                    priorOccurredAt = event.priorOccurredAt + event.occurredAt,
                )
            } ?: event
        }
        require(events.zipWithNext().all { (first, second) -> first.occurredAt <= second.occurredAt }) {
            "Corrected actual dates must preserve event order"
        }
        return batch.copy(events = events)
    }

    private fun List<RecipeStage>.totalMaximumDuration(): Duration =
        fold(Duration.ZERO) { total, stage -> total + stage.duration.maximum }

    private val plannedActionTime = LocalTime(hour = 9, minute = 0)
}
