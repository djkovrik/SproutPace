package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.CapacityEngine
import com.sedsoftware.sproutpace.domain.engine.PlannerEngine
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

class PlannerAndSchedulingTest {
    @Test fun ac060_dueAndOverdueActionsRemainDistinguishableInLocalState() {
        val overdue = ScheduledAction(
            ScheduledActionId("overdue"), BatchId("batch-1"), GrowStage.SOWN,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 19, 9, 0), "UTC"),
        )
        val today = ScheduledAction(
            ScheduledActionId("today"), BatchId("batch-1"), GrowStage.LIGHT_GROWTH,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 20, 9, 0), "UTC"),
        )

        assertTrue(SchedulingEngine.resolvedInstant(overdue) < SchedulingEngine.resolvedInstant(today))
        assertEquals(ScheduledActionStatus.PENDING, overdue.status)
        assertEquals(ScheduledActionStatus.PENDING, today.status)
    }

    private val feasible: (LocalDate, CultureId) -> CapacityDecision = { _, _ -> CapacityDecision.Feasible }

    @Test fun ac050_positiveCadenceAndRotationGenerateSuggestionsNotBatches() {
        val plan = plan()
        val suggestions = PlannerEngine.suggestions(plan, 3, feasible)
        assertEquals(3, suggestions.size)
        assertEquals(listOf("radish", "pea", "radish"), suggestions.map { it.cultureId.value })
        assertEquals(listOf(20, 23, 26), suggestions.map { it.targetHarvestDate.day })
        assertFailsWith<IllegalArgumentException> { HarvestCadenceDays(0) }
    }

    @Test fun ac052_planUpdateChangesFutureSuggestionsWithoutMutatingConfirmedBatch() {
        val confirmed = DomainFixtures.batch()
        val before = PlannerEngine.suggestions(plan(), 2, feasible)
        val updatedPlan = plan().copy(cadence = HarvestCadenceDays(7))
        val after = PlannerEngine.suggestions(updatedPlan, 2, feasible)
        assertNotEquals(before[1].targetHarvestDate, after[1].targetHarvestDate)
        assertEquals(plan().anchorDate, updatedPlan.anchorDate)
        assertEquals(DomainFixtures.batch(), confirmed)
    }

    @Test fun ac051_restartAfterCalendarRolloverKeepsSuggestionIdentityDatesAndConflicts() {
        val plan = plan()
        val conflictDate = LocalDate(2026, 9, 23)
        val capacity: (LocalDate, CultureId) -> CapacityDecision = { date, _ ->
            if (date == conflictDate) {
                CapacityDecision.NeedsResolution(
                    CapacityConflict(
                        zoneId = null,
                        date = date,
                        requiredPlaces = 2,
                        availablePlaces = 1,
                        resolutions = setOf(CapacityResolution.CHOOSE_ANOTHER_DATE),
                    ),
                )
            } else {
                CapacityDecision.Feasible
            }
        }

        val beforeRestart = PlannerEngine.suggestions(plan, 3, capacity)
        val afterRestartOnLaterDate = PlannerEngine.suggestions(plan, 3, capacity)

        assertEquals(beforeRestart, afterRestartOnLaterDate)
        assertEquals(listOf("plan-1:0", "plan-1:1", "plan-1:2"), afterRestartOnLaterDate.map { it.id })
        assertIs<SuggestionStatus.NeedsResolution>(afterRestartOnLaterDate[1].status)
    }

    @Test fun ac054_pauseStopsOnlyFutureSuggestions() {
        val activeBatch = DomainFixtures.batch()
        val suggestions = PlannerEngine.suggestions(plan().copy(paused = true), 2, feasible)
        assertTrue(suggestions.isEmpty())
        assertEquals(BatchState.PLANNED, activeBatch.state)
    }

    @Test fun ac055_capacityConflictIsExhaustiveAndNeverFeasible() {
        val zone = GrowZone(GrowZoneId("dark"), "Dark shelf", GrowZoneType.DARK, 1)
        val conflictDecision = CapacityEngine.validate(
            zone,
            listOf(com.sedsoftware.sproutpace.domain.engine.Occupancy(zone.id, BatchId("occupied"))),
            date = LocalDate(2026, 9, 20),
        )
        val suggestion = PlannerEngine.suggestions(plan(), 1) { _, _ -> conflictDecision }.single()
        val conflict = assertIs<SuggestionStatus.NeedsResolution>(suggestion.status).conflict
        assertEquals(
            setOf(CapacityResolution.CHOOSE_ANOTHER_DATE, CapacityResolution.CHOOSE_ANOTHER_ZONE, CapacityResolution.CHANGE_CAPACITY),
            conflict.resolutions,
        )
    }

    @Test fun ac057_continuousQueueCreatesOneNextSuggestionWithoutConsumptionState() {
        val plan = plan().copy(mode = HarvestPlanMode.CONTINUOUS_QUEUE)
        val suggestion = PlannerEngine.nextContinuousSuggestion(plan, LocalDate(2026, 9, 20), feasible)
        assertNotNull(suggestion)
        assertEquals(LocalDate(2026, 9, 23), suggestion.targetHarvestDate)
        assertIs<SuggestionStatus.Feasible>(suggestion.status)
    }

    @Test fun ac057_continuousQueueUsesLaterOfPersistedAnchorAndActualHarvest() {
        val plan = plan().copy(mode = HarvestPlanMode.CONTINUOUS_QUEUE)

        val beforeAnchor = PlannerEngine.nextContinuousSuggestion(plan, LocalDate(2026, 9, 18), feasible)
        val afterAnchor = PlannerEngine.nextContinuousSuggestion(plan, LocalDate(2026, 9, 25), feasible)

        assertEquals(LocalDate(2026, 9, 23), assertNotNull(beforeAnchor).targetHarvestDate)
        assertEquals(LocalDate(2026, 9, 28), assertNotNull(afterAnchor).targetHarvestDate)
        assertEquals(afterAnchor, PlannerEngine.nextContinuousSuggestion(plan, LocalDate(2026, 9, 25), feasible))
    }

    @Test fun ac062_snoozePreservesStableActionIdentity() {
        val original = ScheduledAction(
            ScheduledActionId("action-1"), BatchId("batch-1"), null,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 20, 9, 0), "UTC"),
        )
        val newDue = DueMoment.CivilTime(LocalDateTime(2026, 9, 20, 11, 0), "UTC")
        val snoozed = SchedulingEngine.snooze(original, newDue)
        assertEquals(original.id, snoozed.id)
        assertEquals(newDue, snoozed.due)
    }

    @Test fun ac066_soakReminderUsesOneStableElapsedTarget() {
        val target = Instant.parse("2026-09-20T16:00:00Z")
        val reminder = ScheduledAction(
            ScheduledActionId("soak:batch-1"), BatchId("batch-1"), GrowStage.SOAKING,
            DueMoment.ElapsedTarget(target),
        )
        val recalculated = SchedulingEngine.afterTimeZoneChange(reminder, "Europe/Moscow")

        assertEquals(reminder.id, recalculated.id)
        assertEquals(target, SchedulingEngine.resolvedInstant(recalculated))
        assertIs<DueMoment.ElapsedTarget>(recalculated.due)
    }

    @Test fun ac068_civilActionsFollowNewZoneWhileElapsedSoakKeepsInstant() {
        val civil = ScheduledAction(
            ScheduledActionId("civil"), BatchId("batch-1"), null,
            DueMoment.CivilTime(LocalDateTime(2026, 9, 20, 9, 0), "UTC"),
        )
        val movedCivil = SchedulingEngine.afterTimeZoneChange(civil, "Europe/Moscow")
        assertNotEquals(SchedulingEngine.resolvedInstant(civil), SchedulingEngine.resolvedInstant(movedCivil))
        assertEquals(LocalDateTime(2026, 9, 20, 9, 0), (movedCivil.due as DueMoment.CivilTime).localDateTime)

        val soakTarget = Instant.parse("2026-09-20T08:00:00Z") + 8.hours
        val soak = ScheduledAction(ScheduledActionId("soak"), BatchId("batch-1"), GrowStage.SOAKING, DueMoment.ElapsedTarget(soakTarget))
        val movedSoak = SchedulingEngine.afterTimeZoneChange(soak, "Europe/Moscow")
        assertEquals(soakTarget, SchedulingEngine.resolvedInstant(movedSoak))
    }

    private fun plan() = HarvestPlan(
        HarvestPlanId("plan-1"), LocalDate(2026, 9, 20), HarvestCadenceDays(3),
        listOf(CultureId("radish"), CultureId("pea")), HarvestPlanMode.CADENCE,
    )
}
