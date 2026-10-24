package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import com.sedsoftware.sproutpace.monetization.InlineAdSession

/** Owned by the screen visit rather than the recyclable lazy item. */
internal val LocalInlineAdSession = staticCompositionLocalOf<InlineAdSession?> { null }

@Composable
internal fun InlineAdSlot(adUnitId: String?, runtime: AdvertisingRuntime?) {
    when {
        adUnitId != null && runtime != null -> LocalInlineAdSession.current?.let {
            PlatformInlineAd(runtime, adUnitId, it)
        }
        LocalInspectionMode.current -> PlatformPreviewInlineAd()
    }
}

@Composable
internal expect fun PlatformPreviewInlineAd(modifier: Modifier = Modifier)

@Composable
internal expect fun PlatformInlineAd(
    runtime: AdvertisingRuntime,
    adUnitId: String,
    session: InlineAdSession,
    modifier: Modifier = Modifier,
)
