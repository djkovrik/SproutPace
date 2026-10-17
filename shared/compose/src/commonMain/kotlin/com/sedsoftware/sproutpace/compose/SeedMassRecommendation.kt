package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SeedMassRecommendation(state: SproutPaceUiState) {
    val mass = state.facts.firstOrNull { it.id == "recommended-seed-mass" }?.authoredValue ?: return
    val display = recommendedSeedMassDisplay(mass, localizedText("number_decimal_separator"))
    Text(stringResource(strictTextResource("batch_seed_recommendation"), display),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
