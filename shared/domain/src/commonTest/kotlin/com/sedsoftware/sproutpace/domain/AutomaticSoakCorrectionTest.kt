package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleEngine
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class AutomaticSoakCorrectionTest {
    @Test fun ac032_onlyAutomaticPendingSoakFollowsCorrectedStartAcrossActionOrderingAndReplay() {
        val automatic = target("arbitrary-automatic-id", ScheduledActionOrigin.SOAK_DURATION)
        val snoozed = SchedulingEngine.snooze(automatic.copy(id = ScheduledActionId("snoozed")),
            DueMoment.ElapsedTarget(DomainFixtures.instant + 15.hours))
        assertEquals(ScheduledActionOrigin.UNSPECIFIED, snoozed.origin)
        val preserved = listOf(
            target("soak:batch-1"), // Legacy/custom IDs do not imply automatic intent.
            snoozed,
            target("fixed", ScheduledActionOrigin.SOAK_DURATION).copy(userFixed = true),
            target("completed", ScheduledActionOrigin.SOAK_DURATION).copy(status = ScheduledActionStatus.COMPLETED),
            target("skipped", ScheduledActionOrigin.SOAK_DURATION).copy(status = ScheduledActionStatus.SKIPPED),
            target("cancelled", ScheduledActionOrigin.SOAK_DURATION).copy(status = ScheduledActionStatus.CANCELLED),
        )
        val actions = preserved + automatic
        val correction = ActionScheduleCorrection(BatchState.SOAKING, DomainFixtures.instant + 1.hours)
        for (ordered in listOf(actions, actions.reversed(), actions.drop(2) + actions.take(2))) {
            val batch = DomainFixtures.batch(state = BatchState.SOAKING, actions = ordered)
            val rebuilt = ActionScheduleEngine.rebuild(batch, correction)
            assertEquals(ordered.map { it.id }, rebuilt.actions.map { it.id })
            assertEquals(DueMoment.ElapsedTarget(DomainFixtures.instant + 13.hours),
                rebuilt.actions.single { it.id == automatic.id }.due)
            assertEquals(preserved.toSet(), rebuilt.actions.filterNot { it.id == automatic.id }.toSet())
            assertTrue(rebuilt.cancelledActionIds.isEmpty())
            assertTrue(rebuilt.conflicts.isEmpty())
            assertEquals(rebuilt, ActionScheduleEngine.rebuild(batch.copy(actions = rebuilt.actions), correction))
        }
    }

    @Test fun ac032_automaticSoakDoesNotMoveAfterBatchHasProgressedPastSoaking() {
        val action = target("automatic", ScheduledActionOrigin.SOAK_DURATION)
        val batch = DomainFixtures.batch(state = BatchState.SOWN, placement = Placement.Unassigned(null), actions = listOf(action))
        assertEquals(listOf(action), ActionScheduleEngine.rebuild(batch,
            ActionScheduleCorrection(BatchState.SOAKING, DomainFixtures.instant + 1.hours)).actions)
    }

    private fun target(id: String, origin: ScheduledActionOrigin = ScheduledActionOrigin.UNSPECIFIED) = ScheduledAction(
        ScheduledActionId(id), BatchId("batch-1"), null, DueMoment.ElapsedTarget(DomainFixtures.instant + 12.hours),
        kind = ScheduledActionKind.SOAK_TARGET, origin = origin,
    )
}
