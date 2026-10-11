package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.ContinuousQueueEngine
import com.sedsoftware.sproutpace.domain.engine.ContinuousQueueSlot
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.HarvestCadenceDays
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanId
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ContinuousQueueTest {
    private val radish = CultureId("radish")
    private val pea = CultureId("pea")
    private val plan = HarvestPlan(HarvestPlanId("p"), date(1), HarvestCadenceDays(5),
        listOf(radish, pea), HarvestPlanMode.CONTINUOUS_QUEUE)
    private fun date(day: Int) = LocalDate(2026, 10, day)

    @Test fun initialTargetUsesPersistedStartAndLegacyAcceptedActiveSlotSuppressesNewCandidate() {
        val first = assertNotNull(ContinuousQueueEngine.next(plan, emptyList()))
        assertEquals(date(6), first.targetHarvestDate)
        assertEquals(first, ContinuousQueueEngine.next(plan, emptyList()))
        assertNull(ContinuousQueueEngine.next(plan, listOf(ContinuousQueueSlot(radish, date(15), null))))
        assertNull(ContinuousQueueEngine.next(plan.copy(paused = true), emptyList()))
    }

    @Test fun terminalSlotsAdvanceRotationUsingLaterOfFixedTargetAndActualOutcome() {
        val slots = listOf(ContinuousQueueSlot(radish, date(6), date(4)))
        val second = assertNotNull(ContinuousQueueEngine.next(plan, slots))
        assertEquals(date(11), second.targetHarvestDate)
        assertEquals(pea, second.cultureId)
        val third = assertNotNull(ContinuousQueueEngine.next(plan, slots + ContinuousQueueSlot(pea, date(11), date(14))))
        assertEquals(date(19), third.targetHarvestDate)
        assertEquals(radish, third.cultureId)
    }

    @Test fun unrelatedCultureAndOlderTargetsDoNotAdvanceAndActiveDuplicatePreventsAdvance() {
        val unrelated = listOf(ContinuousQueueSlot(CultureId("basil"), date(20), date(22)),
            ContinuousQueueSlot(radish, date(2), date(20)))
        assertEquals(date(6), assertNotNull(ContinuousQueueEngine.next(plan, unrelated)).targetHarvestDate)
        assertNull(ContinuousQueueEngine.next(plan, listOf(ContinuousQueueSlot(radish, date(6), date(7)),
            ContinuousQueueSlot(radish, date(6), null))))
    }
}
