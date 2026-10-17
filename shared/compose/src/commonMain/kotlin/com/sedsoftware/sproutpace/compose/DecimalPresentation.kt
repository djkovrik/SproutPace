package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlin.math.round

internal fun canonicalFieldInput(kind: ScreenFieldKind, value: String): String =
    if (kind == ScreenFieldKind.POSITIVE_DECIMAL) value.replace(',', '.') else value

internal fun displayFieldInput(kind: ScreenFieldKind, value: String, separator: String): String =
    if (kind == ScreenFieldKind.POSITIVE_DECIMAL) value.replace(".", separator) else value

/** Derived guidance is compact; editable measurements keep their original decimal text. */
internal fun recommendedSeedMassDisplay(value: String, separator: String): String {
    val grams = value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return value
    val rounded = round(grams * RECOMMENDATION_FRACTION_SCALE) / RECOMMENDATION_FRACTION_SCALE
    val canonical = rounded.takeIf { it.isFinite() && it > 0 }?.toString()?.removeSuffix(".0")
        ?.takeUnless { it.contains('E', ignoreCase = true) } ?: significantMass(grams.toString())
    return displayFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, canonical, separator)
}

private fun significantMass(value: String): String {
    val scientific = value.uppercase()
    val mantissa = scientific.substringBefore('E')
    val decimalPosition = mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length
    val digits = mantissa.replace(".", "")
    val firstNonzero = digits.indexOfFirst { it != '0' }
    var exponent = scientific.substringAfter('E', "0").toInt() + decimalPosition - firstNonzero - 1
    val significant = digits.drop(firstNonzero)
    var rounded = significant.take(RECOMMENDATION_SIGNIFICANT_DIGITS)
        .padEnd(RECOMMENDATION_SIGNIFICANT_DIGITS, '0').toInt()
    if (significant.getOrNull(RECOMMENDATION_SIGNIFICANT_DIGITS)?.let { it >= '5' } == true) rounded += 1
    if (rounded == RECOMMENDATION_SIGNIFICANT_CARRY) {
        rounded /= DECIMAL_RADIX
        exponent += 1
    }
    val compact = rounded.toString().trimEnd('0')
    if (exponent in MIN_PLAIN_RECOMMENDATION_EXPONENT..-1) return "0." + "0".repeat(-exponent - 1) + compact
    val fraction = compact.drop(1).takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
    return "${compact.first()}${fraction}e$exponent"
}

private const val RECOMMENDATION_FRACTION_SCALE = 100.0
private const val RECOMMENDATION_SIGNIFICANT_DIGITS = 3
private const val RECOMMENDATION_SIGNIFICANT_CARRY = 1000
private const val DECIMAL_RADIX = 10
private const val MIN_PLAIN_RECOMMENDATION_EXPONENT = -6

@Composable
internal fun localizedDecimal(value: String): String =
    displayFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, value, localizedText("number_decimal_separator"))
