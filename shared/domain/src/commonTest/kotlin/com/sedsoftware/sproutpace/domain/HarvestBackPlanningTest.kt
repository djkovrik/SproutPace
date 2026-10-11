package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowStage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HarvestBackPlanningTest {
    @Test
    fun reversePlanUsesMaximumGrowingDurationAndExcludesPresowSoaking() {
        assertEquals(LocalDate(2026, 9, 21), BatchPlanningEngine.sowDateForHarvest(
            DomainFixtures.stages, LocalDate(2026, 9, 30), "UTC",
        ))
    }

    @Test
    fun reverseDatesRoundTripAcrossSpringAndAutumnDaylightSavingChanges() {
        listOf(LocalDate(2026, 4, 2), LocalDate(2026, 10, 29)).forEach { target ->
            val sow = BatchPlanningEngine.sowDateForHarvest(DomainFixtures.stages, target, "Europe/Berlin")
            val actions = BatchPlanningEngine.plannedActions(
                DomainFixtures.batch().id, DomainFixtures.stages, sow, emptyMap(), "Europe/Berlin",
            )
            val harvest = actions.single { it.stage == GrowStage.READY_FOR_HARVEST }
            assertEquals(target, (harvest.due as DueMoment.CivilTime).localDateTime.date)
            val fixed = BatchPlanningEngine.plannedActions(DomainFixtures.batch().id, DomainFixtures.stages, sow,
                mapOf(GrowStage.READY_FOR_HARVEST to target), "Europe/Berlin")
                .single { it.stage == GrowStage.READY_FOR_HARVEST }
            assertTrue(fixed.userFixed)
            assertEquals(target, (fixed.due as DueMoment.CivilTime).localDateTime.date)
        }
    }
}
