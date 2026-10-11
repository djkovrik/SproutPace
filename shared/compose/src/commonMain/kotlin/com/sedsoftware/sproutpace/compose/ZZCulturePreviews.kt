package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Full production catalog: lower rows must remain visible for artwork clipping review.
@Preview(name = "07 All Cultures Light", widthDp = 393, heightDp = 1800, uiMode = 16)
@Preview(name = "07 All Cultures Dark", widthDp = 393, heightDp = 1800, uiMode = 32)
@Composable
private fun AllCultureArtwork() = App(typedPreviewState(UiScreen.CATALOG))

