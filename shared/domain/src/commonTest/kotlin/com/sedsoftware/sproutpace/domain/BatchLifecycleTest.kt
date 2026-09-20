package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.CapacityEngine
import com.sedsoftware.sproutpace.domain.engine.Occupancy
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class BatchLifecycleTest {
    private val zone = GrowZone(GrowZoneId("zone-1"), "Shelf", GrowZoneType.GROW_LIGHT, 1)
    private val assigned = Placement.Assigned(zone.id, zone.name)

    @Test fun ac032_correctionRecalculatesDependentFutureActionsButPreservesFixedDates() {
        val flexible = action(
            "light",
            GrowStage.LIGHT_GROWTH,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 25, 9, 0), "UTC"),
        )
        val fixed = action(
            "harvest",
            GrowStage.READY_FOR_HARVEST,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 28, 9, 0), "UTC"),
            fixed = true,
        )
        val batch = DomainFixtures.batch(BatchState.SOWN, assigned, actions = listOf(flexible, fixed))
        val change = BatchLifecycleEngine.confirmStage(
            batch,
            StageEventId("event-1"),
            DomainFixtures.instant,
            BatchState.BLACKOUT_OR_WEIGHT,
        )
        assertNotEquals(flexible.due, change.batch.actions.first().due)
        assertEquals(fixed.due, change.batch.actions.last().due)
        assertEquals(listOf(fixed.id), change.conflicts.map { it.actionId })
    }

    @Test fun ac035_soakingRecordsActualEventWithoutOccupyingPlace() {
        val changed = BatchLifecycleEngine.startSoaking(DomainFixtures.batch(), StageEventId("soak"), DomainFixtures.instant)
        assertEquals(BatchState.SOAKING, changed.batch.state)
        assertEquals(Placement.None, changed.batch.placement)
        assertEquals(DomainFixtures.instant, changed.batch.events.single().occurredAt)
    }

    @Test fun ac036_sowingOccupiesExactlyOneConfiguredPlace() {
        val changed = BatchLifecycleEngine.confirmSowing(DomainFixtures.batch(), StageEventId("sow"), DomainFixtures.instant, assigned)
        assertEquals(BatchState.SOWN, changed.batch.state)
        assertEquals(assigned, changed.batch.placement)
        val decision = CapacityEngine.validate(zone, listOf(Occupancy(zone.id, changed.batch.id)))
        assertIs<CapacityDecision.NeedsResolution>(decision)
    }

    @Test fun ac037_actualTransitionShiftsOnlyDependentFutureActions() {
        val completed = action("past", GrowStage.SOWN, DueMoment.ElapsedTarget(DomainFixtures.instant), ScheduledActionStatus.COMPLETED)
        val future = action("future", GrowStage.LIGHT_GROWTH, DueMoment.ElapsedTarget(DomainFixtures.instant + 10.days))
        val batch = DomainFixtures.batch(BatchState.SOWN, assigned, actions = listOf(completed, future))
        val actual = DomainFixtures.instant + 1.days
        val change = BatchLifecycleEngine.confirmStage(batch, StageEventId("blackout"), actual, BatchState.BLACKOUT_OR_WEIGHT)
        assertEquals(completed, change.batch.actions.first())
        assertEquals(DueMoment.ElapsedTarget(actual + 3.days), change.batch.actions.last().due)
    }

    @Test fun ac038_realMoveCanBecomeVisiblyUnassignedWhenCapacityIsFull() {
        val full = CapacityEngine.validate(zone, listOf(Occupancy(zone.id, BatchId("other"))), recordingReality = true)
        assertTrue(CapacityResolution.LEAVE_UNASSIGNED in assertIs<CapacityDecision.NeedsResolution>(full).conflict.resolutions)
        val batch = DomainFixtures.batch(BatchState.SOWN, assigned)
        val change = BatchLifecycleEngine.confirmStage(
            batch, StageEventId("move"), DomainFixtures.instant, BatchState.BLACKOUT_OR_WEIGHT,
            Placement.Unassigned(zone.name),
        )
        assertIs<Placement.Unassigned>(change.batch.placement)
    }

    @Test fun ac039_undoReversesOnlyLatestTransitionAndRebuildsActionsIdempotently() {
        val sown = BatchLifecycleEngine.confirmSowing(DomainFixtures.batch(), StageEventId("sow"), DomainFixtures.instant, assigned).batch
        val stable = action("stable", GrowStage.LIGHT_GROWTH, DueMoment.ElapsedTarget(DomainFixtures.instant + 10.days))
        val obsolete = action("obsolete", GrowStage.SOAKING, DueMoment.ElapsedTarget(DomainFixtures.instant + 1.days))
        val recipeWithoutSoaking = sown.recipe.copy(stages = sown.recipe.stages.filter { it.stage != GrowStage.SOAKING })
        val blackout = BatchLifecycleEngine.confirmStage(
            sown.copy(recipe = recipeWithoutSoaking, actions = listOf(stable, obsolete)),
            StageEventId("blackout"),
            DomainFixtures.instant + 1.days,
            BatchState.BLACKOUT_OR_WEIGHT,
        ).batch.copy(actions = listOf(stable, obsolete))
        val undone = BatchLifecycleEngine.undoLastTransition(blackout)
        val repeated = BatchLifecycleEngine.undoLastTransition(blackout)
        assertEquals(BatchState.SOWN, undone.batch.state)
        assertEquals(listOf(StageEventId("sow")), undone.batch.events.map { it.id })
        assertEquals(assigned, undone.batch.placement)
        assertEquals(listOf(stable.id), undone.batch.actions.map { it.id })
        assertEquals(DueMoment.ElapsedTarget(DomainFixtures.instant + 3.days), undone.batch.actions.single().due)
        assertEquals(setOf(obsolete.id), undone.cancelledActionIds)
        assertEquals(undone, repeated)
    }

    @Test fun ac040_skipIsExplicitAndContinuesAtNextApplicableStage() {
        val batch = DomainFixtures.batch(BatchState.SOWN, assigned)
        val changed = BatchLifecycleEngine.skipStage(
            batch, StageEventId("skip"), DomainFixtures.instant,
            BatchState.BLACKOUT_OR_WEIGHT, "Not used for this batch", BatchState.LIGHT_GROWTH,
        )
        assertEquals(BatchState.LIGHT_GROWTH, changed.batch.state)
        assertEquals(StageEventKind.SKIPPED, changed.batch.events.single().kind)
        assertEquals("Not used for this batch", changed.batch.events.single().reason)
    }

    @Test fun ac080_harvestReleasesCapacityAndCancelsFutureActions() {
        val pending = action("pending", GrowStage.READY_FOR_HARVEST, DueMoment.ElapsedTarget(DomainFixtures.instant + 1.days))
        val batch = DomainFixtures.batch(BatchState.READY_FOR_HARVEST, assigned, actions = listOf(pending))
        val change = BatchLifecycleEngine.close(batch, BatchOutcome.Harvested(DomainFixtures.instant))
        assertEquals(BatchState.HARVESTED, change.batch.state)
        assertEquals(Placement.None, change.batch.placement)
        assertEquals(ScheduledActionStatus.CANCELLED, change.batch.actions.single().status)
        assertEquals(setOf(pending.id), change.cancelledActionIds)
    }

    @Test fun ac081_failedOutcomeReleasesCapacityAndKeepsOptionalReason() {
        val batch = DomainFixtures.batch(BatchState.LIGHT_GROWTH, assigned)
        val outcome = BatchOutcome.Failed(DomainFixtures.instant, reason = "Mold")
        val closed = BatchLifecycleEngine.close(batch, outcome).batch
        assertEquals(BatchState.FAILED, closed.state)
        assertEquals(Placement.None, closed.placement)
        assertEquals("Mold", (closed.outcome as BatchOutcome.Failed).reason)
    }

    @Test fun ac085_actualHarvestMassExistsOnlyOnHarvestedOutcome() {
        val closed = BatchLifecycleEngine.close(
            DomainFixtures.batch(BatchState.READY_FOR_HARVEST, assigned),
            BatchOutcome.Harvested(DomainFixtures.instant),
        ).batch
        val updated = BatchLifecycleEngine.recordActualHarvestMass(closed, PositiveGrams(125.5))
        assertEquals(125.5, (updated.outcome as BatchOutcome.Harvested).actualMass?.value)
        assertFailsWith<IllegalStateException> {
            BatchLifecycleEngine.recordActualHarvestMass(
                BatchLifecycleEngine.close(
                    DomainFixtures.batch(BatchState.SOWN, assigned),
                    BatchOutcome.Failed(DomainFixtures.instant),
                ).batch,
                PositiveGrams(1.0),
            )
        }
    }

    private fun action(
        id: String,
        stage: GrowStage,
        due: DueMoment,
        status: ScheduledActionStatus = ScheduledActionStatus.PENDING,
        fixed: Boolean = false,
    ) = ScheduledAction(ScheduledActionId(id), BatchId("batch-1"), stage, due, status, fixed)
}
