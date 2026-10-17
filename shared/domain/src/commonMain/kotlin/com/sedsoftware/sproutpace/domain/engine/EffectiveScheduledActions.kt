package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.ScheduledActionKind
import com.sedsoftware.sproutpace.domain.model.StageEventKind

/** Derived only: undo removes actual history without overwriting explicit task decisions. */
fun GrowBatch.effectiveActions(): List<ScheduledAction> = actions.map { action ->
    action.copy(status = effectiveStatus(action))
}

private fun GrowBatch.effectiveStatus(action: ScheduledAction): ScheduledActionStatus {
    val stage = action.stage
    if (action.status != ScheduledActionStatus.PENDING || (stage == null && action.kind != ScheduledActionKind.SOAK_TARGET)) {
        return if (action.status == ScheduledActionStatus.PENDING && state.isTerminal)
            ScheduledActionStatus.CANCELLED else action.status
    }
    if (stage == GrowStage.READY_FOR_HARVEST) {
        return when (state) {
            BatchState.HARVESTED -> ScheduledActionStatus.COMPLETED
            BatchState.FAILED, BatchState.ABANDONED -> ScheduledActionStatus.CANCELLED
            else -> action.status
        }
    }
    val target = if (action.kind == ScheduledActionKind.SOAK_TARGET ||
        (stage == GrowStage.SOAKING && action.due is DueMoment.ElapsedTarget)) {
        BatchState.SOWN
    } else BatchState.valueOf(requireNotNull(stage).name)
    val skipped = events.any { event ->
        event.kind == StageEventKind.SKIPPED && event.from.ordinal < target.ordinal && event.to.ordinal > target.ordinal
    }
    val reached = events.any { it.to.ordinal >= target.ordinal && !it.to.isTerminal } ||
        (!state.isTerminal && state.ordinal >= target.ordinal)
    return when {
        skipped -> ScheduledActionStatus.SKIPPED
        reached -> ScheduledActionStatus.COMPLETED
        state.isTerminal -> ScheduledActionStatus.CANCELLED
        else -> action.status
    }
}
