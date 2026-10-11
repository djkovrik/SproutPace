package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.effectiveActions
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class EffectiveScheduledActionsTest {
    private val assigned = Placement.Assigned(GrowZoneId("zone"), "Shelf")
    private val now = DomainFixtures.instant

    @Test fun actualTransitionsFulfillTasksAndUndoRestoresOnlyAutomaticStatus() {
        val planned = DomainFixtures.batch(actions = GrowStage.entries.map { action(it) })
        val sown = BatchLifecycleEngine.confirmSowing(planned, StageEventId("sow"), now, assigned).batch
        val covered = BatchLifecycleEngine.confirmStage(sown, StageEventId("cover"), now + 1.hours,
            BatchState.BLACKOUT_OR_WEIGHT).batch
        assertEquals(ScheduledActionStatus.COMPLETED, covered.status(GrowStage.SOWN))
        assertEquals(ScheduledActionStatus.COMPLETED, covered.status(GrowStage.BLACKOUT_OR_WEIGHT))
        assertEquals(ScheduledActionStatus.PENDING, covered.actions.first { it.stage == GrowStage.SOWN }.status)
        val undone = BatchLifecycleEngine.undoLastTransition(covered).batch
        assertEquals(ScheduledActionStatus.PENDING, undone.status(GrowStage.BLACKOUT_OR_WEIGHT))
        assertEquals(ScheduledActionStatus.COMPLETED, undone.status(GrowStage.SOWN))
        assertEquals(planned.actions.last().due, covered.effectiveActions().last().due)
    }

    @Test fun skippedIntermediateIsSkippedButReachedTargetCompleted() {
        val sown = DomainFixtures.batch(BatchState.SOWN, assigned, actions = GrowStage.entries.map { action(it) })
        val skipped = BatchLifecycleEngine.skipStage(sown, StageEventId("skip"), now,
            BatchState.BLACKOUT_OR_WEIGHT, "Not needed", BatchState.LIGHT_GROWTH).batch
        assertEquals(ScheduledActionStatus.SKIPPED, skipped.status(GrowStage.BLACKOUT_OR_WEIGHT))
        assertEquals(ScheduledActionStatus.COMPLETED, skipped.status(GrowStage.LIGHT_GROWTH))
        assertEquals(ScheduledActionStatus.PENDING,
            BatchLifecycleEngine.undoLastTransition(skipped).batch.status(GrowStage.BLACKOUT_OR_WEIGHT))
    }

    @Test fun soakEndAndHarvestRemainUsefulUntilTheirActualCompletion() {
        val soakStart = action(GrowStage.SOAKING)
        val soakEnd = soakStart.copy(id = ScheduledActionId("soak-end"), due = DueMoment.ElapsedTarget(now + 12.hours))
        val soaking = DomainFixtures.batch(BatchState.SOAKING, actions = listOf(soakStart, soakEnd))
        assertEquals(listOf(ScheduledActionStatus.COMPLETED, ScheduledActionStatus.PENDING),
            soaking.effectiveActions().map { it.status })
        val sown = BatchLifecycleEngine.confirmSowing(soaking, StageEventId("sown"), now, assigned).batch
        assertEquals(listOf(ScheduledActionStatus.COMPLETED, ScheduledActionStatus.COMPLETED),
            sown.effectiveActions().map { it.status })
        val ready = DomainFixtures.batch(BatchState.READY_FOR_HARVEST, assigned,
            actions = listOf(action(GrowStage.READY_FOR_HARVEST)))
        assertEquals(ScheduledActionStatus.PENDING, ready.status(GrowStage.READY_FOR_HARVEST))
    }

    @Test fun explicitManualDecisionsAreNeverOverwrittenByHistory() {
        val actions = ScheduledActionStatus.entries.filter { it != ScheduledActionStatus.PENDING }.map { status ->
            action(GrowStage.SOWN).copy(id = ScheduledActionId(status.name), status = status)
        }
        assertEquals(actions, DomainFixtures.batch(BatchState.LIGHT_GROWTH, assigned, actions = actions).effectiveActions())
    }

    private fun GrowBatch.status(stage: GrowStage) = effectiveActions().first { it.stage == stage }.status
    private fun action(stage: GrowStage) = ScheduledAction(ScheduledActionId(stage.name), BatchId("batch-1"), stage,
        DueMoment.CivilTime(LocalDateTime(2026, 9, 20, 9, 0), "UTC"), userFixed = true)
}
