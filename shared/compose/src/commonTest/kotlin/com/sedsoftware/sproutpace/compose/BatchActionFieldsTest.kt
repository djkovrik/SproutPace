package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchActionFieldsTest {
    @Test
    fun transitionEditsOnlyItsUserFacingInputs() {
        assertEquals(setOf("occurredAt", "zoneId", "unassigned"), batchActionFieldIds("confirm-sowing"))
        assertTrue("reason" in batchActionFieldIds("skip-stage"))
        assertFalse("eventId" in batchActionFieldIds("skip-stage"))
        assertFalse("newId" in batchActionFieldIds("repeat-batch"))
        assertTrue(batchActionFieldIds("edit-batch").isEmpty())
    }
}
