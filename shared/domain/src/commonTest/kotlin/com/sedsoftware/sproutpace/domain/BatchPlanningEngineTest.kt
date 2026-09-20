package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.StageEvent
import com.sedsoftware.sproutpace.domain.model.StageEventId
import com.sedsoftware.sproutpace.domain.model.StageEventKind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchPlanningEngineTest {
    @Test
    fun ac030_plannedSowAndFixedDatesCreateStableOrderedActions() {
        val fixedDates = mapOf(
            GrowStage.SOAKING to LocalDate(2026, 9, 20),
            GrowStage.SOWN to LocalDate(2026, 9, 21),
            GrowStage.BLACKOUT_OR_WEIGHT to LocalDate(2026, 9, 22),
            GrowStage.LIGHT_GROWTH to LocalDate(2026, 9, 23),
            GrowStage.READY_FOR_HARVEST to LocalDate(2026, 9, 30),
        )
        val actions = BatchPlanningEngine.plannedActions(
            batchId = DomainFixtures.batch().id,
            stages = DomainFixtures.stages,
            plannedSowDate = LocalDate(2026, 9, 21),
            fixedStageDates = fixedDates,
            timeZoneId = "Europe/Moscow",
        )

        assertEquals(DomainFixtures.stages.map { it.stage }, actions.map { it.stage })
        assertEquals(5, actions.map { it.id }.distinct().size)
        actions.forEach { action ->
            val expectedDate = fixedDates.getValue(requireNotNull(action.stage))
            assertEquals(
                LocalDateTime(expectedDate.year, expectedDate.month, expectedDate.day, 9, 0),
                (action.due as DueMoment.CivilTime).localDateTime,
            )
            assertTrue(action.userFixed)
        }
    }

    @Test
    fun ac032_actualDateCorrectionPreservesEventIdentityAndTimestampHistory() {
        val originalAt = Instant.parse("2026-09-20T09:00:00Z")
        val event = StageEvent(
            StageEventId("event-sown"), BatchState.PLANNED, BatchState.SOWN, originalAt,
            StageEventKind.CONFIRMED, null, Placement.None, Placement.Unassigned(null),
        )
        val batch = DomainFixtures.batch(
            state = BatchState.SOWN,
            placement = Placement.Unassigned(null),
            events = listOf(event),
        )
        val correctedAt = Instant.parse("2026-09-20T10:00:00Z")

        val corrected = BatchPlanningEngine.correctActualEventDates(
            batch,
            mapOf(BatchState.SOWN to correctedAt),
        )

        assertEquals(event.id, corrected.events.single().id)
        assertEquals(correctedAt, corrected.events.single().occurredAt)
        assertEquals(listOf(originalAt), corrected.events.single().priorOccurredAt)
        assertEquals(event.placementAfter, corrected.events.single().placementAfter)
    }
}
