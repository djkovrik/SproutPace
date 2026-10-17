package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import com.sedsoftware.sproutpace.monetization.AndroidInlineAdHost
import com.sedsoftware.sproutpace.monetization.InlineAdSession
import com.sedsoftware.sproutpace.monetization.InlineAdState
import org.jetbrains.compose.resources.stringResource
import sproutpace.shared.compose.generated.resources.Res
import sproutpace.shared.compose.generated.resources.advertisement_label

@Composable
internal actual fun PlatformInlineAd(
    runtime: AdvertisingRuntime,
    adUnitId: String,
    modifier: Modifier,
) {
    val androidRuntime = runtime as? AndroidInlineAdHost ?: return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        if (widthDp <= 0) return@BoxWithConstraints
        val session = remember(adUnitId, widthDp) { InlineAdSession(maxRetries = 0) }
        var state by remember(adUnitId, widthDp) { mutableStateOf(InlineAdState.IDLE) }
        var banner by remember(adUnitId, widthDp) { mutableStateOf<View?>(null) }
        var disposed by remember(adUnitId, widthDp) { mutableStateOf(false) }

        Column(
            Modifier.fillMaxWidth().then(
                if (state == InlineAdState.LOADED) {
                    Modifier.padding(top = 8.dp).semantics { testTag = "inline-ad" }
                }
                else Modifier.height(0.dp),
            ),
        ) {
            if (state == InlineAdState.LOADED) {
                Text(stringResource(Res.string.advertisement_label), style = MaterialTheme.typography.labelSmall)
            }
            AndroidView(
                factory = { context ->
                    androidRuntime.createInlineBanner(
                        context = context,
                        adUnitId = adUnitId,
                        widthDp = widthDp,
                        maxHeightDp = MAX_INLINE_HEIGHT_DP,
                        session = session,
                        isHostDestroyed = { disposed },
                        onStateChanged = { state = it },
                    ).also { banner = it }
                },
                modifier = Modifier.fillMaxWidth().then(
                    if (state == InlineAdState.LOADED) Modifier.heightIn(max = MAX_INLINE_HEIGHT_DP.dp)
                    else Modifier.height(0.dp),
                ),
            )
        }

        DisposableEffect(androidRuntime, session) {
            onDispose {
                disposed = true
                banner?.let { androidRuntime.disposeInlineBanner(it, session) }
                banner = null
            }
        }
    }
}

private const val MAX_INLINE_HEIGHT_DP = 250

@Composable
internal actual fun PlatformPreviewInlineAd(modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).semantics { testTag = "inline-ad" },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(Res.string.advertisement_label), style = MaterialTheme.typography.labelSmall)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {}
        }
    }
}
