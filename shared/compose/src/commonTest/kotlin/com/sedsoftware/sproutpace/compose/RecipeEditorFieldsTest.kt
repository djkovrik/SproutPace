package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeEditorFieldsTest {
    @Test
    fun noneStageHidesItsDetailsWhileKeepingStageSelector() {
        val fields = listOf(field("id", "internal"), field("name", "Recipe"), field("stage2", "none"),
            field("stage2MinimumHours", "12"), field("stage2Guidance", "Hidden"))
        assertEquals(listOf("name", "stage2"), recipeEditorFields(fields, copying = false).map { it.id })
    }

    @Test
    fun builtInCopyAsksOnlyForAuthoredNameAndSubstrate() {
        val fields = listOf(field("name", ""), field("substrate", ""), field("stage1", "sown"), field("newId", "generated"))
        assertEquals(listOf("name", "substrate"), recipeEditorFields(fields, copying = true).map { it.id })
    }

    private fun field(id: String, value: String) = UiField(id, "field_name", ScreenFieldKind.TEXT, value,
        required = false, readOnly = false, options = emptyList(), errorKey = null)
}
