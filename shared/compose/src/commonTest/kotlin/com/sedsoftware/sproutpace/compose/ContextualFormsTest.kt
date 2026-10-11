package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextualFormsTest {
    @Test
    fun circleAndRectangleExposeOnlyApplicableDimensions() {
        val fields = listOf(field("shape", "circle"), field("widthCm", "bad"), field("lengthCm", "bad"), field("diameterCm", "20"))
        val circle = SproutPaceUiState(screen = UiScreen.TRAY_SIZES, fields = fields)
        assertEquals(listOf("shape", "diameterCm"), circle.contextualFormFields().map { it.id })
        val rectangle = circle.copy(fields = fields.map { if (it.id == "shape") it.copy(value = "rectangle") else it })
        assertEquals(listOf("shape", "widthCm", "lengthCm"), rectangle.contextualFormFields().map { it.id })
    }

    @Test
    fun closeBatchExposesOnlyOutcomeRelevantFields() {
        val fields = listOf(field("outcome", "harvested"), field("actualMassGrams", "184"),
            field("assessment", "successful"), field("reason", ""), field("note", ""))
        val harvested = SproutPaceUiState(screen = UiScreen.CLOSE_BATCH, fields = fields)
        assertFalse(harvested.contextualFormFields().any { it.id == "reason" })
        listOf("failed", "abandoned").forEach { outcome ->
            val state = harvested.copy(fields = fields.map { if (it.id == "outcome") it.copy(value = outcome) else it })
            assertEquals(listOf("outcome", "reason", "note"), state.contextualFormFields().map { it.id })
        }
    }

    @Test
    fun observationWithBatchContextDoesNotAskForTheBatchAgain() {
        val state = SproutPaceUiState(screen = UiScreen.OBSERVATION_EDITOR,
            fields = listOf(field("id", "generated"), field("batchId", "batch-1"), field("text", "Leaves")))
        assertEquals(listOf("text"), state.contextualFormFields().map { it.id })
        assertTrue(state.copy(fields = state.fields.map { if (it.id == "batchId") it.copy(value = "") else it })
            .contextualFormFields().any { it.id == "batchId" })
    }

    @Test
    fun newAndExistingEntitiesUseDifferentSaveOperations() {
        assertEquals("save-zone", contextualSaveAction(UiScreen.ZONES, false))
        assertEquals("update-zone", contextualSaveAction(UiScreen.ZONES, true))
        assertEquals("save-tray-size", contextualSaveAction(UiScreen.TRAY_SIZES, false))
        assertEquals("update-tray-size", contextualSaveAction(UiScreen.TRAY_SIZES, true))
        assertEquals(160f, navigationBarMinimumHeight(2f))
        assertEquals(80f, navigationBarMinimumHeight(1f))
    }

    private fun field(id: String, value: String) = UiField(id, "field_note", ScreenFieldKind.TEXT,
        value, required = false, readOnly = false, options = emptyList(), errorKey = null)
}
