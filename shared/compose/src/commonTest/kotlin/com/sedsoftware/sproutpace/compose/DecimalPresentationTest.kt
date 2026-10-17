package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test fun derivedRecommendationUsesCompactPrecisionAndLocalizedSeparator() {
        mapOf("28.481306962613925" to "28.48", "12.5" to "12.5", "12.0" to "12", "0.009876" to "0.01")
            .forEach { (exact, display) -> assertEquals(display, recommendedSeedMassDisplay(exact, ".")) }
        assertEquals("28.48", recommendedSeedMassDisplay("28.481306962613925", "."), "English")
        assertEquals("28,48", recommendedSeedMassDisplay("28.481306962613925", ","), "Russian")
        assertEquals("28.481306962613925", displayFieldInput(ScreenFieldKind.POSITIVE_DECIMAL,
            "28.481306962613925", "."), "Editable measured mass remains exact")
    }

    @Test fun positiveTinyAndExtremeRecommendationsNeverRoundToZeroOrLoseCompactness() {
        mapOf("0.004123456" to "0.00412", "0.00000123456" to "0.00000123",
            "1.234567E-20" to "1.23e-20", "1.234567E300" to "1.23e300")
            .forEach { (exact, display) -> assertEquals(display, recommendedSeedMassDisplay(exact, ".")) }
        assertEquals("1,23e-20", recommendedSeedMassDisplay("1.234567E-20", ","))
        listOf(Double.MIN_VALUE.toString(), "1.0E-300", "0.00000099999", "0.0049999").forEach { exact ->
            val display = recommendedSeedMassDisplay(exact, ".")
            assertTrue(display.toDouble() > 0, "$exact must retain a positive displayed amount: $display")
            assertTrue(display.length <= 12, "$exact must remain compact: $display")
        }
    }
}
