package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Append to the scanner order to preserve existing indexed golden paths.
@Preview(name = "06 Multiple Plans Light", widthDp = 393, heightDp = 1300)
@Preview(name = "06 Multiple Plans Dark", widthDp = 393, heightDp = 1300, uiMode = 32)
@Preview(name = "06 Multiple Plans RU 200", widthDp = 393, heightDp = 1600, locale = "ru", fontScale = 2f)
@Composable
private fun MultiplePlans() {
    val state = typedPreviewState(UiScreen.PLAN)
    val plan = state.plans.first()
    App(state.copy(plans = listOf(plan, plan.copy(id = "paused-plan", cadenceDays = 5, cultureIds = listOf("pea"), paused = true))))
}
