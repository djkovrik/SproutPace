package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime

@Composable
internal fun InlineAdSlot(adUnitId: String?, runtime: AdvertisingRuntime?) {
    when {
        adUnitId != null && runtime != null -> PlatformInlineAd(runtime, adUnitId)
        LocalInspectionMode.current -> PlatformPreviewInlineAd()
    }
}

@Composable
internal expect fun PlatformPreviewInlineAd(modifier: Modifier = Modifier)

@Composable
internal expect fun PlatformInlineAd(
    runtime: AdvertisingRuntime,
    adUnitId: String,
    modifier: Modifier = Modifier,
)
