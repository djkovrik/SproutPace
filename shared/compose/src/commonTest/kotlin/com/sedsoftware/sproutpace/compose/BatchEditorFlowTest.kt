package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchEditorFlowTest {
    @Test
    fun selectionErrorsReturnToTheBasicsStep() {
        assertEquals(BatchEditorStep.BASICS, batchEditorStepForField("recipeId"))
        assertEquals(BatchEditorStep.BASICS, batchEditorStepForField("cultureId"))
        assertEquals(BatchEditorStep.BASICS, batchEditorStepForField("trayId"))
        assertEquals(BatchEditorStep.DETAILS, batchEditorStepForField("plannedSowDate"))
        assertEquals(BatchEditorStep.DETAILS, batchEditorStepForField("actualLightAt"))
    }

    @Test
    fun newAndExistingBatchesExposeOnlyTheirRelevantCommitAction() {
        val actions = listOf(
            UiScreenAction("save-batch", "action_save_batch", "save-batch", enabled = true, primary = true),
            UiScreenAction("correct-batch", "action_correct_batch", "correct-batch", enabled = true, primary = false),
        )
        val newBatch = SproutPaceUiState(selectedItemId = "recipe-source",
            fields = listOf(field("id", "new-batch"), field("anchorState", "")), actions = actions)
        val correction = SproutPaceUiState(selectedItemId = "existing-batch",
            fields = listOf(field("id", "existing-batch"), field("anchorState", "")), actions = actions)

        assertEquals("save-batch", batchEditorSubmitAction(newBatch)?.id)
        assertEquals("correct-batch", batchEditorSubmitAction(correction)?.id)
    }

    private fun field(id: String, value: String) = UiField(
        id = id,
        labelKey = "field_$id",
        kind = ScreenFieldKind.TEXT,
        value = value,
        required = false,
        readOnly = false,
        options = emptyList(),
        errorKey = null,
    )
}
