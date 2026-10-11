package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BatchDetailProjectionTest {
    @Test
    fun detailRetainsIdentityActualDatesCapacityAndSnapshotInstructions() {
        val detail = ProductionScreenFixtures.snapshot(ScreenId.BATCH_DETAIL)
        val identity = detail.items.first { it.batch != null && it.task == null }
        assertEquals("active-radish", identity.batch?.batchId)
        assertNotNull(identity.batch?.sowDate)
        assertEquals(2, identity.occupancy?.occupied)
        assertTrue(detail.items.filter { it.id.startsWith("event:") }.all { it.history != null })
        assertTrue(detail.items.any { it.snapshotStage?.guidanceKey != null })
        assertTrue(detail.actions.any { it.id.value == "edit-batch" })
    }

    @Test
    fun terminalOutcomesHaveDatedHistoryAndRepeatWithoutLiveTransitionActions() {
        listOf("history-0", "history-1", "history-2").forEach { id ->
            val detail = ProductionScreenFixtures.snapshot(ScreenId.BATCH_DETAIL, stableId = id)
            assertNotNull(detail.items.single { it.id == "event:outcome" }.history?.occurredAt)
            assertTrue(detail.actions.any { it.id.value == "repeat-batch" })
            assertTrue(detail.actions.none { it.id.value in setOf("edit-batch", "confirm-stage", "start-soaking") })
        }
    }
}
