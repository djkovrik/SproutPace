package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Kept after existing preview files so their indexed golden paths remain stable.
@Preview(name = "08 Recipe Asset Inspection Dark", widthDp = 393, heightDp = 1800, uiMode = 32)
@Composable
private fun RecipeArtworkDark() = App(typedPreviewState(UiScreen.RECIPE))
