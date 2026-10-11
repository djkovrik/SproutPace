package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.data.ConsentChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogAndPrivacyProjectionTest {
    @Test fun catalogMatchesLocalizedScientificAndPersonalNamesAndClearsQueryWithoutLosingItems() {
        val names = mapOf("radish" to listOf("Редис", "Raphanus sativus"),
            "house-radish" to listOf("House radish"))
        listOf("ред" to "radish", "RAPHANUS" to "radish", "house" to "house-radish").forEach { (query, id) ->
            val state = typedPreviewState(UiScreen.CATALOG, draft = mapOf("query" to query)).filterCatalogSearch(names)
            assertEquals(listOf(id), state.items.map { it.id })
        }
        val empty = typedPreviewState(UiScreen.CATALOG, draft = mapOf("query" to "zzzz")).filterCatalogSearch(names)
        assertTrue(empty.items.isEmpty())
        assertEquals(UiVariant.EMPTY, empty.variant)
        val original = typedPreviewState(UiScreen.CATALOG)
        assertEquals(original.items, original.filterCatalogSearch(names).items)
    }

    @Test fun staleMissingAndInvalidPrivacyCannotExposeConsentActions() {
        val base = typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
        val valid = previewPrivacy(ConsentChoice.ACCEPTED)
        listOf(null, valid.copy(expiresAtEpochMillis = PREVIEW_PRIVACY_NOW),
            valid.copy(endpoint = "https://invalid.example/")).forEach { privacy ->
            val state = base.withAdvertisingPrivacy(privacy, PREVIEW_PRIVACY_NOW)
            assertEquals(listOf("open-privacy-policy"), state.actions.map { it.id })
            assertTrue(state.facts.none { it.id == "policy-version" })
        }
        val changed = base.withAdvertisingPrivacy(previewPrivacy(ConsentChoice.UNDECIDED)
            .copy(policyVersion = "2026-11"), PREVIEW_PRIVACY_NOW)
        assertEquals("2026-11", changed.facts.single { it.id == "policy-version" }.authoredValue)
        assertEquals(listOf("policy-version"), changed.supportingFacts().map { it.id })
        assertEquals("2026-11", changed.supportingFacts().single().authoredValue)
        assertEquals(listOf("accept-advertising-consent", "decline-advertising-consent", "open-privacy-policy"),
            changed.actions.map { it.id })
    }

    @Test fun outcomeDateValidationDoesNotIncludeAnUnrelatedMassError() {
        val state = typedPreviewState(UiScreen.CLOSE_BATCH, UiVariant.VALIDATION_ERROR,
            draft = mapOf("occurredAt" to "invalid", "actualMassGrams" to "120"))
        assertEquals(listOf("occurredAt"), state.fields.filter { it.errorKey != null }.map { it.id })
    }
}
