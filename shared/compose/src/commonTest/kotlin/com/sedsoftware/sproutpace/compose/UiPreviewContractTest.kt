package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures

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
    fun typedFixturesPreserveProductionFieldsAndFactsRatherThanInventingContent() {
        UiScreen.entries.forEach { screen ->
            val state = typedPreviewState(screen)
            val source = ProductionScreenFixtures.snapshot(ScreenId.valueOf(screen.name))
            assertEquals(screen, state.screen)
            assertEquals(source.fields.filterNot { screen == UiScreen.TRAYS && it.id.value == "category" }
                .associate { it.id.value to it.value }, state.fields.associate { it.id to it.value }, "$screen fields")
            if (screen == UiScreen.ADVERTISING_PRIVACY) {
                assertEquals("2026-10", state.facts.single { it.id == "policy-version" }.authoredValue)
                assertEquals("privacy_undecided", state.facts.single { it.id == "privacy-applicability" }.valueKey)
            } else if (screen == UiScreen.TRAYS) {
                assertEquals(state.items.size.toString(), state.facts.single { it.id == "batch-count" }.authoredValue)
                assertEquals(source.facts.filterNot { it.id == "batch-count" }.map { Triple(it.id, it.valueKey?.value, it.authoredValue) },
                    state.facts.filterNot { it.id == "batch-count" }.map { Triple(it.id, it.valueKey, it.authoredValue) })
            } else if (screen == UiScreen.BATCH_EDITOR) {
                assertNewBatchPreviewFacts(source, state)
            } else {
                assertEquals(source.facts.map { Triple(it.id, it.valueKey?.value, it.authoredValue) },
                    state.facts.map { Triple(it.id, it.valueKey, it.authoredValue) }, "$screen facts")
            }
            assertTrue(state.fields.none { it.errorKey != null }, "$screen success fixture must not fake validation")
            assertTrue(state.items.all { item -> source.items.any { it.id == item.id } }, "$screen must retain real item identities")
        }
    }

    @Test
    fun validationPreviewsUseActionableErrorsFromProductionCommandValidation() {
        val scenarios = listOf(
            UiScreen.FIRST_RUN to ProductionScreenFixtures.Scenario.CONTENT,
            UiScreen.BATCH_EDITOR to ProductionScreenFixtures.Scenario.CONTENT,
            UiScreen.RECIPE to ProductionScreenFixtures.Scenario.PERSONAL_RECIPE,
            UiScreen.ZONES to ProductionScreenFixtures.Scenario.EDIT_ZONE,
            UiScreen.TRAY_SIZES to ProductionScreenFixtures.Scenario.EDIT_TRAY,
            UiScreen.CLOSE_BATCH to ProductionScreenFixtures.Scenario.CONTENT,
        )
        scenarios.forEach { (screen, scenario) ->
            val errors = typedPreviewState(screen, UiVariant.VALIDATION_ERROR, scenario = scenario)
                .fields.mapNotNull { it.errorKey }
            assertTrue(errors.isNotEmpty(), "$screen needs a real invalid draft")
            assertTrue(errors.all { it.startsWith("validation_") }, "$screen must use corrective copy")
        }
    }

    @Test
    fun taskScreensProgressivelyDiscloseContextualCommandInputs() {
        assertEquals(
            setOf(UiScreen.TODAY, UiScreen.BATCH_DETAIL, UiScreen.PLAN),
            contextFieldScreens,
        )
        contextFieldScreens.filterNot { it == UiScreen.TODAY }.forEach { screen ->
            assertTrue(typedPreviewState(screen).fields.isNotEmpty())
        }
        assertTrue(typedPreviewState(UiScreen.TODAY).fields.isEmpty(), "Today uses row-targeted time controls")
        assertTrue(typedPreviewState(UiScreen.TRAYS).fields.isEmpty(), "Repeat opens its own retained editor")
    }
}
