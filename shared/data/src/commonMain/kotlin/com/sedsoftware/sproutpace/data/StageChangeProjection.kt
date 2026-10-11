package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.StageChangeProjection
import com.sedsoftware.sproutpace.component.StageDateChange
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.BatchChange
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.StageEventId

internal fun stageTransition(batch: GrowBatch, command: ScreenCommand, placement: Placement): BatchChange {
    val event = StageEventId(command.required("eventId"))
    val occurredAt = command.instant("occurredAt")
    return when (command.operation) {
        "confirm-sowing" -> BatchLifecycleEngine.confirmSowing(batch, event, occurredAt, placement)
        "confirm-stage" -> BatchLifecycleEngine.confirmStage(batch, event, occurredAt, command.enum("nextState"), placement)
        "skip-stage" -> BatchLifecycleEngine.skipStage(batch, event, occurredAt, command.enum("skippedState"),
            command.required("reason"), command.enum("resultingState"))
        else -> error("Unsupported stage preview")
    }
}

internal fun BatchChange.preview(original: GrowBatch, command: ScreenCommand) = StageChangeProjection(
    command,
    batch.actions.filter { it.status == ScheduledActionStatus.PENDING }.map { action ->
        val previous = original.actions.firstOrNull { it.id == action.id }
        val conflict = conflicts.firstOrNull { it.actionId == action.id }
        StageDateChange(action.id.value, TextKey(when (action.stage) {
            GrowStage.SOAKING -> "stage_soaking"
            GrowStage.SOWN -> "stage_sown"
            GrowStage.BLACKOUT_OR_WEIGHT -> "stage_blackout"
            GrowStage.LIGHT_GROWTH -> "stage_light_growth"
            GrowStage.READY_FOR_HARVEST -> "stage_harvest"
            null -> "today_next_action"
        }), previous?.let(SchedulingEngine::resolvedInstant), SchedulingEngine.resolvedInstant(action), action.userFixed,
            conflict?.let { SchedulingEngine.resolvedInstant(action.copy(due = it.proposedDue)) })
    },
)
