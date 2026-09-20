package com.sedsoftware.sproutpace.compose

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvertisingUiContractTest {
    @Test
    fun androidNativeBannerIsLifecycleOwnedAndCollapsedUntilLoaded() {
        val androidSource = source("src/androidMain/kotlin/com/sedsoftware/sproutpace/compose/PlatformInlineAd.android.kt")
        val screenContent = source("src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ScreenContent.kt")

        assertTrue("AndroidView(" in androidSource)
        assertTrue("createInlineBanner(" in androidSource)
        assertTrue("DisposableEffect(" in androidSource)
        assertTrue("disposeInlineBanner(" in androidSource)
        assertTrue("state == InlineAdState.LOADED" in androidSource)
        assertTrue("Modifier.height(0.dp)" in androidSource)
        val insertionBranch = screenContent.substringAfter("if (state.showInlineAd")
            .substringBefore("\n        }")
        assertFalse("Spacer" in insertionBranch)
    }

    @Test
    fun iosInlineHostIsAnUnconditionalNoOp() {
        val source = source("src/iosMain/kotlin/com/sedsoftware/sproutpace/compose/PlatformInlineAd.ios.kt")

        assertTrue(source.trimEnd().endsWith(") = Unit"))
        assertTrue(source.windowed("= Unit".length).count { it == "= Unit" } == 2)
        assertFalse("AndroidView" in source)
        assertFalse("BannerAdView" in source)
        assertFalse("loadAd" in source)
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("shared/compose/$relativePath"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Missing source contract: $relativePath")
    }
}
