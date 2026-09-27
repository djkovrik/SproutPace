package com.sedsoftware.sproutpace.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The product type scale deliberately stays close to Material 3 while giving
 * headings and controls a little more authority. A platform sans-serif keeps
 * EN/RU glyph coverage deterministic on Android and iOS.
 */
internal val SproutPaceTypography = Typography(
    displaySmall = sproutText(36, 44, FontWeight.Bold),
    headlineLarge = sproutText(32, 40, FontWeight.SemiBold),
    headlineMedium = sproutText(28, 36, FontWeight.SemiBold),
    headlineSmall = sproutText(24, 32, FontWeight.SemiBold),
    titleLarge = sproutText(22, 28, FontWeight.SemiBold),
    titleMedium = sproutText(16, 24, FontWeight.SemiBold),
    titleSmall = sproutText(14, 20, FontWeight.SemiBold),
    bodyLarge = sproutText(16, 24, FontWeight.Normal),
    bodyMedium = sproutText(14, 20, FontWeight.Normal),
    bodySmall = sproutText(12, 16, FontWeight.Normal),
    labelLarge = sproutText(14, 20, FontWeight.SemiBold),
    labelMedium = sproutText(12, 16, FontWeight.Medium),
    labelSmall = sproutText(11, 16, FontWeight.Medium),
)

internal val SproutPaceShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun sproutText(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)
