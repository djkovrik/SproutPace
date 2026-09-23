package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sedsoftware.sproutpace.compose.theme.DarkColorScheme
import com.sedsoftware.sproutpace.compose.theme.LightColorScheme
import kotlin.test.Test
import kotlin.test.assertTrue

class BrandContrastTest {
    @Test
    fun brandThemesKeepNormalTextReadable() {
        listOf(LightColorScheme, DarkColorScheme).forEach { scheme ->
            textPairs(scheme).forEach { (foreground, background) ->
                assertTrue(contrast(foreground, background) >= 4.5f, "$foreground on $background")
            }
            assertTrue(contrast(scheme.outline, scheme.surface) >= 3f)
        }
    }

    private fun textPairs(scheme: ColorScheme): List<Pair<Color, Color>> = with(scheme) {
        listOf(
            onPrimary to primary, onPrimaryContainer to primaryContainer,
            onSecondary to secondary, onSecondaryContainer to secondaryContainer,
            onTertiary to tertiary, onTertiaryContainer to tertiaryContainer,
            onError to error, onErrorContainer to errorContainer,
            onBackground to background, onSurface to surface,
            onSurfaceVariant to surfaceVariant, inverseOnSurface to inverseSurface,
            inversePrimary to inverseSurface, primary to surface, secondary to surface,
            onPrimaryFixed to primaryFixed, onPrimaryFixed to primaryFixedDim,
            onPrimaryFixedVariant to primaryFixed, onPrimaryFixedVariant to primaryFixedDim,
            onSecondaryFixed to secondaryFixed, onSecondaryFixed to secondaryFixedDim,
            onSecondaryFixedVariant to secondaryFixed, onSecondaryFixedVariant to secondaryFixedDim,
            onTertiaryFixed to tertiaryFixed, onTertiaryFixed to tertiaryFixedDim,
            onTertiaryFixedVariant to tertiaryFixed, onTertiaryFixedVariant to tertiaryFixedDim,
        ) + listOf(
            surfaceDim, surfaceBright, surfaceContainerLowest, surfaceContainerLow,
            surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
        ).flatMap { surface -> listOf(onSurface to surface, onSurfaceVariant to surface) }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
