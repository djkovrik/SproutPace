package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypedUiPreviewMatrixTest {
    @Test
    fun matrixCoversEveryScreenContentAndRussianLargeText() {
        UiScreen.entries.forEach { screen ->
            assertTrue(requiredUiPreviewCases.any { it.screen == screen && it.variant == UiVariant.CONTENT })
            assertTrue(requiredUiPreviewCases.any { it.screen == screen && it.locale == "ru" && it.fontScale == 2f })
        }
        assertEquals(16, UiScreen.entries.size)
    }

    @Test
    fun matrixCoversRequiredBehavioralAndAdaptiveStates() {
        val variants = requiredUiPreviewCases.mapTo(mutableSetOf(), UiPreviewCase::variant)
        assertTrue(
            variants.containsAll(
                setOf(
                    UiVariant.CONTENT, UiVariant.EMPTY, UiVariant.VALIDATION_ERROR, UiVariant.ERROR,
                    UiVariant.PERMISSION_DENIED, UiVariant.CONFLICT, UiVariant.TEMPORARILY_UNASSIGNED,
                    UiVariant.RESTORE_PREVIEW, UiVariant.DESTRUCTIVE_CONFIRMATION, UiVariant.PRIVACY_DECISION,
                ),
            ),
        )
        assertTrue(requiredUiPreviewCases.count { it.widthDp >= 840 } >= 5)
        assertEquals(
            setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN),
            requiredUiPreviewCases.filter { it.variant == UiVariant.EMPTY }.mapTo(mutableSetOf(), UiPreviewCase::screen),
        )
    }

    @Test
    fun typedFixturesCarryContractFieldsFactsAndActions() {
        UiScreen.entries.forEach { screen ->
            val state = typedPreviewState(screen)
            assertEquals(screen, state.screen)
            assertTrue(state.fields.isNotEmpty(), "$screen fields")
            assertTrue(state.facts.isNotEmpty(), "$screen facts")
            assertTrue(state.actions.isNotEmpty(), "$screen actions")
            assertTrue(state.fields.none { it.errorKey != null }, "$screen success fixture must not fake validation")
            assertTrue(state.items.none { it.id == "preview" || it.authoredTitle?.contains("preview", ignoreCase = true) == true })
        }
    }

    @Test
    fun taskScreensProgressivelyDiscloseContextualCommandInputs() {
        assertEquals(
            setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.BATCH_DETAIL, UiScreen.PLAN),
            contextFieldScreens,
        )
        contextFieldScreens.forEach { screen -> assertTrue(typedPreviewState(screen).fields.isNotEmpty()) }
    }
}
