package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.detectEnvironment
import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Render intermediate frames, rather than inferring motion from its configuration. */
class NavigationMotionTest {
    private var onFrame: (BufferedImage) -> Unit = {}
    @get:Rule val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 37),
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 320, screenHeight = 320),
        snapshotHandler = object : SnapshotHandler {
            override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) = object : SnapshotHandler.FrameHandler {
                override fun handle(image: BufferedImage) = onFrame(image)
                override fun close() = Unit
            }
            override fun close() = Unit
        },
    )

    @Test fun tabsSlideForwardInVisualOrder() {
        verifyTransition(UiScreen.TODAY, UiScreen.CATALOG, slidesForward = true)
    }

    @Test fun tabsSlideBackwardInVisualOrder() {
        verifyTransition(UiScreen.CATALOG, UiScreen.TODAY, slidesForward = false)
    }

    @Test fun openingScreensFadesAndScales() {
        verifyTransition(UiScreen.TODAY, UiScreen.SETTINGS)
    }

    @Test fun closingScreensFadesAndScales() {
        verifyTransition(UiScreen.SETTINGS, UiScreen.TODAY)
    }

    @Test fun anotherIdentityOfTheSameScreenAlsoFadesAndScales() {
        verifyTransition(UiScreen.BATCH_DETAIL, UiScreen.BATCH_DETAIL)
    }

    @Test fun modelChangesWithinOneDestinationDoNotAnimate() {
        verifyTransition(UiScreen.TODAY, UiScreen.TODAY, sameKey = true)
    }

    @Test fun trayCategoriesSlideWithinTheSameDestination() {
        verifyTransition(UiScreen.TRAYS, UiScreen.TRAYS, slidesForward = true, categories = true)
    }

    private fun verifyTransition(
        from: UiScreen,
        to: UiScreen,
        slidesForward: Boolean? = null,
        sameKey: Boolean = false,
        categories: Boolean = false,
    ) {
        val destination = mutableStateOf(0)
        val frames = mutableListOf<List<Int>>()
        var clicks = 0
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    NavigationContent(
                        SproutPaceUiState(screen = if (destination.value == 0) from else to,
                            selectedItemId = destination.value.toString(),
                            trayCategory = if (destination.value == 0) TrayCategory.PLANNED else TrayCategory.HISTORY),
                        navigationKey = if (sameKey || categories) 0 else destination.value,
                        onAction = { clicks++ },
                    ) { state, action ->
                        Box(Modifier.fillMaxSize().background(if (state.selectedItemId == "0") Color.Red else Color.Blue)
                            .semantics { testTag = "content-${state.selectedItemId}" }
                            .clickable { action(UiAction.Retry) })
                    }
                }
            }
        }
        onFrame = { image ->
            frames += listOf(image.getRGB(image.width / 4, image.height / 2) and 0xffffff,
                image.getRGB(image.width * 3 / 4, image.height / 2) and 0xffffff,
                image.getRGB(image.width / 2, image.height / 2) and 0xffffff,
                image.getRGB(0, image.height / 2) and 0xffffff)
            if (frames.size == 2) destination.value = 1
            if (frames.size > 3 && !sameKey) {
                val nodes = view.nodes()
                nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag) == "content-0" }
                    .forEach { it.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() }
            }
        }
        try { paparazzi.gif(view, start = 0, end = 700, fps = 30) } finally { onFrame = {} }
        assertEquals(List(4) { RED }, frames.first())
        assertEquals(List(4) { BLUE }, frames.last())
        when {
            sameKey -> assertTrue(frames.all { it == List(4) { RED } || it == List(4) { BLUE } })
            slidesForward == true -> assertTrue(frames.any { it[0] == RED && it[1] == BLUE }, "Expected slide to the left")
            slidesForward == false -> assertTrue(frames.any { it[0] == BLUE && it[1] == RED }, "Expected slide to the right")
            else -> {
                assertTrue(frames.any { it[2] != RED && it[2] != BLUE }, "Expected intermediate fade frames")
                assertTrue(frames.any { it[3] == WHITE }, "Expected scaled content to expose the background at its edge")
            }
        }
        // Even direct invocations of an outgoing semantic action must be ignored.
        assertEquals(0, clicks)
    }

    @Test fun dialogRemainsMountedDuringExitAndThenDisposes() {
        val item = mutableStateOf<String?>(null)
        val samples = mutableListOf<Int>()
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    AnimatedNavigationDialog(item.value) { _, modifier ->
                        Box(modifier.size(160.dp).background(Color.Blue))
                    }
                }
            }
        }
        onFrame = { image ->
            samples += image.getRGB(image.width / 2, image.height / 2) and 0xffffff
            if (samples.size == 2) item.value = "confirmation"
            if (samples.size == 15) item.value = null
        }
        try { paparazzi.gif(view, start = 0, end = 1_000, fps = 30) } finally { onFrame = {} }
        assertEquals(WHITE, samples.first())
        assertTrue(samples.take(15).any { it == BLUE }, "Dialog must finish entering")
        assertTrue(samples.drop(15).any { it != WHITE && it != BLUE }, "Dialog must remain visible during exit")
        assertEquals(WHITE, samples.last())
    }

    @Test fun productionBottomNavigationStaysStationary() = verifyProductionNavigation(width = 393)

    @Test fun productionRailStaysStationary() = verifyProductionNavigation(width = 1200)

    private fun verifyProductionNavigation(width: Int) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = width, screenHeight = 851, density = Density.MEDIUM, xdpi = 160, ydpi = 160,
            orientation = if (width > 851) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
        ))
        paparazzi.configureComposeResources()
        val state = mutableStateOf(typedPreviewState(UiScreen.TODAY))
        val view = ComposeView(paparazzi.context).apply { setContent { App(state.value) } }
        val bounds = mutableListOf<androidx.compose.ui.geometry.Rect>()
        var frames = 0
        onFrame = { image ->
            frames++
            val tag = if (width < 600) "primary-navigation-bottom" else "primary-navigation-rail"
            bounds += view.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.boundsInRoot
            if (frames == 2) state.value = typedPreviewState(UiScreen.TRAYS)
            if (frames in listOf(2, 5, 12)) {
                val file = java.io.File("build/reports/navigation-motion/$width-$frames.png")
                file.parentFile.mkdirs()
                javax.imageio.ImageIO.write(image, "png", file)
            }
        }
        try { paparazzi.gif(view, start = 0, end = 700, fps = 30) } finally { onFrame = {} }
        assertTrue(frames > 12)
        assertTrue(bounds.all { it == bounds.first() }, "Navigation chrome must remain stationary throughout the slide")
    }

    private fun ComposeView.nodes() = (getChildAt(0) as? ViewRootForTest)
        ?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = true).orEmpty()

    private companion object {
        const val RED = 0xff0000
        const val BLUE = 0x0000ff
        const val WHITE = 0xffffff
    }
}
