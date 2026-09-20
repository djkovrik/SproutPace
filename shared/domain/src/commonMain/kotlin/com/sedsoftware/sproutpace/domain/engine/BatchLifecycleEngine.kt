package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchChange
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.StageEvent
import com.sedsoftware.sproutpace.domain.model.StageEventId
import com.sedsoftware.sproutpace.domain.model.StageEventKind
import kotlinx.datetime.Instant

object BatchLifecycleEngine {
    fun startSoaking(batch: GrowBatch, eventId: StageEventId, occurredAt: Instant): BatchChange {
        require(batch.state == BatchState.PLANNED)
        require(batch.recipe.stages.any { it.stage == GrowStage.SOAKING })
        return transition(batch, eventId, occurredAt, BatchState.SOAKING, Placement.None)
    }

    fun confirmSowing(
        batch: GrowBatch,
        eventId: StageEventId,
        occurredAt: Instant,
        placement: Placement,
    ): BatchChange {
        require(batch.state == BatchState.PLANNED || batch.state == BatchState.SOAKING)
        require(placement is Placement.Assigned || placement is Placement.Unassigned)
        return transition(batch, eventId, occurredAt, BatchState.SOWN, placement)
    }

    fun confirmStage(
        batch: GrowBatch,
        eventId: StageEventId,
        occurredAt: Instant,
        nextState: BatchState,
        placement: Placement = batch.placement,
    ): BatchChange {
        require(!batch.state.isTerminal && !nextState.isTerminal)
        require(nextState in allowedSuccessors(batch.state, batch.recipe))
        return transition(batch, eventId, occurredAt, nextState, placement)
    }

    fun skipStage(
        batch: GrowBatch,
        eventId: StageEventId,
        occurredAt: Instant,
        skippedState: BatchState,
        reason: String,
        resultingState: BatchState,
    ): BatchChange {
        require(reason.isNotBlank())
        require(skippedState in allowedSuccessors(batch.state, batch.recipe))
        require(resultingState in allowedSuccessors(skippedState, batch.recipe))
        val event = StageEvent(
            eventId, batch.state, resultingState, occurredAt, StageEventKind.SKIPPED, reason,
            batch.placement, batch.placement,
        )
        return reschedule(batch.copy(state = resultingState, events = batch.events + event), occurredAt, resultingState)
    }

    fun undoLastTransition(batch: GrowBatch, rebuiltActions: List<ScheduledAction>): BatchChange {
        require(!batch.state.isTerminal)
        val latest = batch.events.lastOrNull() ?: error("No transition to undo")
        val oldActionIds = batch.actions.mapTo(mutableSetOf()) { it.id }
        val newActionIds = rebuiltActions.mapTo(mutableSetOf()) { it.id }
        return BatchChange(
            batch = batch.copy(
                state = latest.from,
                placement = latest.placementBefore,
                events = batch.events.dropLast(1),
                actions = rebuiltActions.distinctBy { it.id },
            ),
            cancelledActionIds = oldActionIds - newActionIds,
        )
    }

    fun undoLastTransition(batch: GrowBatch): BatchChange {
        require(!batch.state.isTerminal)
        val latest = batch.events.lastOrNull() ?: error("No transition to undo")
        val reverted = batch.copy(
            state = latest.from,
            placement = latest.placementBefore,
            events = batch.events.dropLast(1),
        )
        val anchorAt = reverted.events.lastOrNull()?.occurredAt ?: latest.occurredAt
        val rebuilt = ActionScheduleEngine.rebuild(
            reverted,
            ActionScheduleCorrection(anchorState = reverted.state, anchorAt = anchorAt),
        )
        return BatchChange(
            batch = reverted.copy(actions = rebuilt.actions),
            cancelledActionIds = rebuilt.cancelledActionIds,
            conflicts = rebuilt.conflicts,
        )
    }

    fun applyCorrection(batch: GrowBatch, correction: ActionScheduleCorrection): BatchChange {
        val rebuilt = ActionScheduleEngine.rebuild(batch, correction)
        return BatchChange(
            batch = batch.copy(actions = rebuilt.actions),
            cancelledActionIds = rebuilt.cancelledActionIds,
            conflicts = rebuilt.conflicts,
        )
    }

    fun close(batch: GrowBatch, outcome: BatchOutcome): BatchChange {
        require(!batch.state.isTerminal)
        val terminalState = when (outcome) {
            is BatchOutcome.Harvested -> BatchState.HARVESTED
            is BatchOutcome.Failed -> BatchState.FAILED
            is BatchOutcome.Abandoned -> BatchState.ABANDONED
        }
        val cancelled = batch.actions.filter { it.status == ScheduledActionStatus.PENDING }.mapTo(mutableSetOf()) { it.id }
        return BatchChange(
            batch.copy(
                state = terminalState,
                placement = Placement.None,
                outcome = outcome,
                actions = batch.actions.map {
                    if (it.status == ScheduledActionStatus.PENDING) it.copy(status = ScheduledActionStatus.CANCELLED) else it
                },
            ),
            cancelled,
        )
    }

    fun recordActualHarvestMass(batch: GrowBatch, mass: PositiveGrams?): GrowBatch {
        val harvested = batch.outcome as? BatchOutcome.Harvested ?: error("Batch is not harvested")
        return batch.copy(outcome = harvested.copy(actualMass = mass))
    }

    private fun transition(
        batch: GrowBatch,
        eventId: StageEventId,
        occurredAt: Instant,
        nextState: BatchState,
        placement: Placement,
    ): BatchChange {
        val event = StageEvent(
            eventId, batch.state, nextState, occurredAt, StageEventKind.CONFIRMED, null,
            batch.placement, placement,
        )
        return reschedule(batch.copy(state = nextState, placement = placement, events = batch.events + event), occurredAt, nextState)
    }

    private fun reschedule(batch: GrowBatch, anchor: Instant, state: BatchState): BatchChange {
        val rebuilt = ActionScheduleEngine.rebuild(batch, ActionScheduleCorrection(state, anchor))
        return BatchChange(
            batch.copy(actions = rebuilt.actions),
            rebuilt.cancelledActionIds,
            rebuilt.conflicts,
        )
    }

    private fun allowedSuccessors(state: BatchState, recipe: RecipeSnapshot): Set<BatchState> {
        val configured = recipe.stages.mapNotNull { stage ->
            when (stage.stage) {
                GrowStage.SOAKING -> BatchState.SOAKING
                GrowStage.SOWN -> BatchState.SOWN
                GrowStage.BLACKOUT_OR_WEIGHT -> BatchState.BLACKOUT_OR_WEIGHT
                GrowStage.LIGHT_GROWTH -> BatchState.LIGHT_GROWTH
                GrowStage.READY_FOR_HARVEST -> BatchState.READY_FOR_HARVEST
            }
        }
        val index = configured.indexOf(state)
        return if (index >= 0 && index + 1 < configured.size) setOf(configured[index + 1]) else emptySet()
    }
}
