package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalPresentationTest {
    @Test fun eitherDecimalKeypadSeparatorProducesCanonicalValidatorInput() {
        listOf("12,5", "12.5").forEach { entered ->
            val canonical = canonicalFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, entered)
            assertEquals("12.5", canonical)
            assertEquals(12.5, canonical.toDoubleOrNull())
            assertEquals("12,5", displayFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, canonical, ","))
            assertEquals("12.5", displayFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, canonical, "."))
        }
    }

    @Test fun partialInputIsPreservedAndAuthoredTextAndIntegersAreUntouched() {
        assertEquals("12.", canonicalFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, "12,"))
        assertEquals("", canonicalFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, ""))
        listOf(ScreenFieldKind.TEXT, ScreenFieldKind.POSITIVE_INTEGER, ScreenFieldKind.NON_NEGATIVE_LONG).forEach { kind ->
            val authored = "Lot 1.2, 42"
            assertEquals(authored, canonicalFieldInput(kind, authored))
            assertEquals(authored, displayFieldInput(kind, authored, ","))
        }
    }
}
