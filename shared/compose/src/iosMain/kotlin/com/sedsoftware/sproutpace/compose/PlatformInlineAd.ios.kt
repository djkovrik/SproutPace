package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import com.sedsoftware.sproutpace.monetization.InlineAdSession

/** iOS advertising is intentionally and unconditionally absent until a native project exists. */
@Composable
internal actual fun PlatformInlineAd(
    runtime: AdvertisingRuntime,
    adUnitId: String,
    session: InlineAdSession,
    modifier: Modifier,
) = Unit

@Composable
internal actual fun PlatformPreviewInlineAd(modifier: Modifier) = Unit
