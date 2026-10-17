package com.sedsoftware.sproutpace.compose

import android.view.View
import android.view.ViewGroup
import android.graphics.Insets
import android.view.WindowInsets as NativeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.HtmlReportWriter
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.SnapshotVerifier
import app.cash.paparazzi.detectEnvironment
import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Supplies only native window geometry; layout, localized labels and navigation callbacks are production. */
class PrimaryNavigationInsetsTest {
    private var onFrame: () -> Unit = {}
    private val output: SnapshotHandler = if (System.getProperty("paparazzi.test.verify")?.toBoolean() == true) {
        SnapshotVerifier(maxPercentDifference = 0.0)
    } else {
        HtmlReportWriter(maxPercentDifference = 0.0)
    }
    @get:Rule
    val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 37),
        deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 2f),
        maxPercentDifference = 0.0,
        snapshotHandler = object : SnapshotHandler {
            override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
                val delegate = output.newFrameHandler(snapshot, frameCount, fps)
                return object : SnapshotHandler.FrameHandler {
                    override fun handle(image: BufferedImage) { onFrame(); delegate.handle(image) }
                    override fun close() = delegate.close()
                }
            }
            override fun close() = output.close()
        },
    )

    @Test
    fun englishLargeTextNavigationKeepsLabelsAndActionsInsideNativeSafeArea() = verify("en", listOf(
        "Today", "Trays", "Plan", "Catalog",
    ))

    @Test
    fun russianLargeTextNavigationKeepsLabelsAndActionsInsideNativeSafeArea() = verify("ru", listOf(
        "Сегодня", "Лотки", "План", "Каталог",
    ))

    private fun verify(locale: String, labels: List<String>) {
        val previousLocale = Locale.getDefault()
        val actions = mutableListOf<UiAction>()
        var observedInsets = Insets.NONE
        var rendered: NavigationBounds? = null
        var actionsInvoked = false
        try {
            Locale.setDefault(Locale.forLanguageTag(locale))
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 2f, locale = locale))
            paparazzi.configureComposeResources()
            val nativeInsets = NativeWindowInsets.Builder()
                .setInsets(NativeWindowInsets.Type.navigationBars(), SAFE_INSETS)
                .setInsetsIgnoringVisibility(NativeWindowInsets.Type.navigationBars(), SAFE_INSETS)
                .setVisible(NativeWindowInsets.Type.navigationBars(), true)
                .build()
            assertEquals(SAFE_INSETS, nativeInsets.getInsets(NativeWindowInsets.Type.navigationBars()),
                "The framework builder must retain the supplied external window geometry")
            val view = ComposeView(paparazzi.context).apply {
                setContent {
                    val nativeView = LocalView.current
                    val density = LocalDensity.current
                    val navigationInsets = WindowInsets.navigationBars
                    val resolvedInsets = Insets.of(
                        navigationInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr), 0,
                        navigationInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr),
                        navigationInsets.getBottom(density),
                    )
                    SideEffect {
                        observedInsets = resolvedInsets
                        nativeView.dispatchApplyWindowInsets(nativeInsets)
                        onFrame = {
                            if (observedInsets == SAFE_INSETS) {
                                val merged = nativeView.nodes(merged = true)
                                val targets = labels.map { label -> merged.single { node ->
                                    node.hasText(label) && node.config.getOrNull(SemanticsActions.OnClick) != null
                                } }
                                val textNodes = nativeView.nodes(merged = false)
                                rendered = NavigationBounds(nativeView.width, nativeView.height,
                                    targets.map { it.boundsInRoot }, labels.mapIndexed { index, label ->
                                        textNodes.single { it.hasText(label) && targets[index].boundsInRoot
                                            .contains(it.boundsInRoot.center) }.boundsInRoot
                                    })
                                if (!actionsInvoked) {
                                    targets.forEach { assertTrue(requireNotNull(it.config
                                        .getOrNull(SemanticsActions.OnClick)?.action).invoke()) }
                                    actionsInvoked = true
                                }
                            }
                        }
                    }
                    PreviewApp(typedPreviewState(UiScreen.TRAYS), actions::add)
                }
            }
            paparazzi.snapshot(view, name = "native-navigation-insets-$locale-font200", offsetMillis = 1_000L)
            assertEquals(SAFE_INSETS, observedInsets, "The real Compose inset resolver must receive native geometry")
            val bounds = assertNotNull(rendered, "A frame with the supplied native insets must actually render")
            (bounds.targets + bounds.labels).forEach { rect ->
                assertTrue(rect.left >= SAFE_INSETS.left, "$locale left safe edge: $rect")
                assertTrue(rect.right <= bounds.width - SAFE_INSETS.right, "$locale right safe edge: $rect")
                assertTrue(rect.bottom <= bounds.height - SAFE_INSETS.bottom, "$locale bottom safe edge: $rect")
                assertTrue(rect.width > 0 && rect.height > 0)
            }
            assertEquals(SAFE_INSETS.left.toFloat(), bounds.targets.first().left, 0.5f)
            assertEquals((bounds.width - SAFE_INSETS.right).toFloat(), bounds.targets.last().right, 0.5f)
            assertEquals((bounds.height - SAFE_INSETS.bottom).toFloat(), bounds.targets.last().bottom, 0.5f,
                "Bottom safe space must be consumed exactly once")
            assertEquals(listOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN, UiScreen.CATALOG),
                actions.filterIsInstance<UiAction.Navigate>().map { it.screen })
        } finally {
            onFrame = {}
            Locale.setDefault(previousLocale)
        }
    }

    private data class NavigationBounds(val width: Int, val height: Int, val targets: List<Rect>, val labels: List<Rect>)

    private fun SemanticsNode.hasText(text: String): Boolean =
        config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }

    private fun View.nodes(merged: Boolean): List<SemanticsNode> = buildList {
        if (this@nodes is ViewRootForTest) addAll(semanticsOwner.getAllSemanticsNodes(mergingEnabled = merged))
        if (this@nodes is ViewGroup) repeat(childCount) { addAll(getChildAt(it).nodes(merged)) }
    }

    private companion object {
        val SAFE_INSETS: Insets = Insets.of(24, 0, 32, 72)
    }
}
