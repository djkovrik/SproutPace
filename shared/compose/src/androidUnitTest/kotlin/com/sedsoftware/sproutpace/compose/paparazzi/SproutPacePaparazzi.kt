package com.sedsoftware.sproutpace.compose.paparazzi

import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.device.DevicePreviewInfoParser
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import java.security.MessageDigest
import kotlin.math.ceil

internal data class PreviewCase(
    val id: String,
    val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    override fun toString(): String = id
}

internal object SproutPacePreviewCatalog {
    val cases: List<PreviewCase> by lazy {
        AndroidComposablePreviewScanner()
            .scanPackageTrees(PREVIEW_PACKAGE)
            .includePrivatePreviews()
            .getPreviews()
            .map { preview -> PreviewCase(stableId(preview), preview) }
            .sortedBy(PreviewCase::id)
            .also { discovered ->
                check(discovered.isNotEmpty()) { "No production Compose previews were discovered" }
                val collisions = discovered.groupBy(PreviewCase::id).filterValues { it.size > 1 }
                check(collisions.isEmpty()) { "Duplicate screenshot IDs: ${collisions.keys}" }
            }
    }

    private fun stableId(preview: ComposablePreview<AndroidPreviewInfo>): String {
        val unbounded = AndroidPreviewScreenshotIdBuilder(preview)
            .doNotIgnoreMethodParametersType()
            .encodeUnsafeCharacters()
            .build()
            .removePrefix("$PREVIEW_PACKAGE.")
            .replace(Regex("[^A-Za-z0-9._%-]"), "_")
        if (unbounded.length <= MAX_ID_LENGTH) return unbounded

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(unbounded.toByteArray())
            .take(6)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "${unbounded.take(MAX_ID_LENGTH - digest.length - 1)}-$digest"
    }

    private const val PREVIEW_PACKAGE = "com.sedsoftware.sproutpace.compose"
    private const val MAX_ID_LENGTH = 180
}

internal object SproutPacePaparazziPreviewRule {
    fun createFor(preview: ComposablePreview<AndroidPreviewInfo>): Paparazzi {
        val info = preview.previewInfo
        return Paparazzi(
            environment = detectEnvironment().copy(compileSdkVersion = 37),
            deviceConfig = deviceFor(info),
            renderingMode = if (info.widthDp > 0 && info.heightDp > 0) {
                SessionParams.RenderingMode.FULL_EXPAND
            } else {
                SessionParams.RenderingMode.SHRINK
            },
            supportsRtl = true,
            showSystemUi = info.showSystemUi,
            maxPercentDifference = 0.0,
        )
    }
}

@Composable
internal fun SproutPacePreviewContent(
    previewInfo: AndroidPreviewInfo,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        PreviewContextConfigurationEffect()
        ResizeComposable(previewInfo.widthDp, previewInfo.heightDp) {
            if (previewInfo.showBackground) {
                val color = if (previewInfo.backgroundColor == 0L) Color.White else Color(previewInfo.backgroundColor)
                Box(Modifier.background(color)) { content() }
            } else {
                content()
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
internal fun Paparazzi.configureComposeResources() {
    setResourceReaderAndroidContext(context)
}

@Composable
private fun ResizeComposable(
    widthDp: Int,
    heightDp: Int,
    content: @Composable () -> Unit,
) {
    val modifier = when {
        widthDp > 0 && heightDp > 0 -> Modifier.size(widthDp.dp, heightDp.dp)
        widthDp > 0 -> Modifier.width(widthDp.dp)
        heightDp > 0 -> Modifier.height(heightDp.dp)
        else -> Modifier
    }
    Box(modifier) { content() }
}

private fun deviceFor(info: AndroidPreviewInfo): DeviceConfig =
    baseDevice(info.device)
        .resizeToPreview(info.widthDp, info.heightDp)
        .copy(
            locale = info.locale.ifBlank { "en" },
            fontScale = info.fontScale,
            nightMode = if (info.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES) {
                NightMode.NIGHT
            } else {
                NightMode.NOTNIGHT
            },
        )

private fun baseDevice(previewDevice: String): DeviceConfig {
    val device = DevicePreviewInfoParser.parse(previewDevice) ?: return DeviceConfig.PIXEL_5
    return DeviceConfig(
        screenHeight = device.dimensions.height.toInt(),
        screenWidth = device.dimensions.width.toInt(),
        xdpi = device.densityDpi,
        ydpi = device.densityDpi,
        ratio = ScreenRatio.valueOf(device.screenRatio.name),
        size = ScreenSize.valueOf(device.screenSize.name),
        density = Density(device.densityDpi),
        screenRound = ScreenRound.valueOf(device.shape.name),
    )
}

private fun DeviceConfig.resizeToPreview(widthDp: Int, heightDp: Int): DeviceConfig {
    val densityScale = density.dpiValue / 160f
    return copy(
        screenWidth = if (widthDp > 0) ceil(widthDp * densityScale).toInt() else screenWidth,
        screenHeight = if (heightDp > 0) ceil(heightDp * densityScale).toInt() else screenHeight,
    )
}
