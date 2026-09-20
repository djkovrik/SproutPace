package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleEngine
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ScheduleConflict
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ActionScheduleEngineTest {
    @Test fun ac032_correctionPreservesHistoryStableIdsAndFixedDates() {
        val completed = action(
            "completed",
            GrowStage.SOWN,
            DueMoment.ElapsedTarget(DomainFixtures.instant),
            status = ScheduledActionStatus.COMPLETED,
        )
        val flexible = action(
            "flexible",
            GrowStage.LIGHT_GROWTH,
            DueMoment.ElapsedTarget(DomainFixtures.instant + 10.days),
        )
        val fixedDue = DueMoment.CivilTime(LocalDateTime(2026, 9, 29, 9, 0), "Europe/Moscow")
        val fixed = action("fixed", GrowStage.READY_FOR_HARVEST, fixedDue, userFixed = true)
        val batch = DomainFixtures.batch(
            state = BatchState.BLACKOUT_OR_WEIGHT,
            placement = Placement.Unassigned(null),
            actions = listOf(completed, flexible, fixed, flexible),
        )
        val correction = ActionScheduleCorrection(BatchState.BLACKOUT_OR_WEIGHT, DomainFixtures.instant + 1.days)

        val rebuilt = ActionScheduleEngine.rebuild(batch, correction)
        val repeated = ActionScheduleEngine.rebuild(batch.copy(actions = rebuilt.actions), correction)

        assertEquals(completed, rebuilt.actions.first())
        assertEquals(listOf(completed.id, flexible.id, fixed.id), rebuilt.actions.map { it.id })
        assertEquals(DueMoment.ElapsedTarget(DomainFixtures.instant + 4.days), rebuilt.actions[1].due)
        assertEquals(fixedDue, rebuilt.actions[2].due)
        assertEquals(
            listOf(ScheduleConflict(fixed.id, fixedDue, rebuilt.conflicts.single().proposedDue)),
            rebuilt.conflicts,
        )
        assertEquals(rebuilt, repeated)
    }

    @Test fun ac032_correctionKeepsCivilZoneAndRemovesOnlyObsoletePendingObligations() {
        val civil = action(
            "civil",
            GrowStage.LIGHT_GROWTH,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 25, 8, 0), "America/New_York"),
        )
        val obsolete = action("obsolete", GrowStage.SOAKING, DueMoment.ElapsedTarget(DomainFixtures.instant))
        val history = action(
            "history",
            GrowStage.SOAKING,
            DueMoment.ElapsedTarget(DomainFixtures.instant),
            status = ScheduledActionStatus.SKIPPED,
        )
        val recipe = DomainFixtures.recipe.copy(stages = DomainFixtures.recipe.stages.filter { it.stage != GrowStage.SOAKING })
        val batch = DomainFixtures.batch(
            state = BatchState.SOWN,
            placement = Placement.Unassigned(null),
            actions = listOf(civil, obsolete, history),
        ).copy(recipe = recipe)

        val change = BatchLifecycleEngine.applyCorrection(
            batch,
            ActionScheduleCorrection(BatchState.SOWN, DomainFixtures.instant),
        )

        assertEquals(listOf(civil.id, history.id), change.batch.actions.map { it.id })
        assertEquals(history, change.batch.actions.last())
        val civilDue = change.batch.actions.first().due as DueMoment.CivilTime
        assertEquals("America/New_York", civilDue.timeZoneId)
        assertEquals(setOf(obsolete.id), change.cancelledActionIds)
    }

    @Test fun ac032_terminalHistoryIsNeverRescheduled() {
        val pending = action("history", GrowStage.LIGHT_GROWTH, DueMoment.ElapsedTarget(DomainFixtures.instant))
        val terminal = DomainFixtures.batch(actions = listOf(pending)).copy(
            state = BatchState.FAILED,
            outcome = BatchOutcome.Failed(DomainFixtures.instant),
        )
        val rebuilt = ActionScheduleEngine.rebuild(
            terminal,
            ActionScheduleCorrection(BatchState.LIGHT_GROWTH, DomainFixtures.instant + 10.days),
        )
        assertEquals(listOf(pending), rebuilt.actions)
        assertTrue(rebuilt.conflicts.isEmpty())
    }

    private fun action(
        id: String,
        stage: GrowStage,
        due: DueMoment,
        status: ScheduledActionStatus = ScheduledActionStatus.PENDING,
        userFixed: Boolean = false,
    ) = ScheduledAction(ScheduledActionId(id), BatchId("batch-1"), stage, due, status, userFixed)
}
