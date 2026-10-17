package com.sedsoftware.sproutpace.compose

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.*
import com.android.resources.NightMode
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures
import com.sedsoftware.sproutpace.root.AppRootComponentDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.Locale
import kotlin.test.*

/** Real error/retry state is produced by a cold failing read flow and routed through the production RootApp. */
class StoreReadFailureRenderingTest {
    private var onFrame: () -> Unit = {}
    private val output: SnapshotHandler = if (System.getProperty("paparazzi.test.verify")?.toBoolean() == true) {
        SnapshotVerifier(maxPercentDifference = 0.0)
    } else HtmlReportWriter(maxPercentDifference = 0.0)
    @get:Rule val paparazzi = Paparazzi(environment = detectEnvironment().copy(compileSdkVersion = 37),
        deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.0,
        snapshotHandler = object : SnapshotHandler {
            override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
                val delegate = output.newFrameHandler(snapshot, frameCount, fps)
                return object : SnapshotHandler.FrameHandler {
                    override fun handle(image: BufferedImage) { onFrame(); delegate.handle(image) }
                    override fun close() = delegate.close()
                }
            }
            override fun close() = output.close()
        })

    @Test
    fun retainedEditorReadFailureExposesActualRetryWithoutLosingAuthoredDraft() = render(detail = false)

    @Test
    fun russianExpandedDetailReadFailureKeepsCollectionAndExposesExactRetryAtFont200() = render(detail = true)

    private fun render(detail: Boolean) {
        val previous = Locale.getDefault()
        val lifecycle = LifecycleRegistry().apply { resume() }
        val manager = FailingReadManager(detail)
        val root = AppRootComponentDefault(DefaultComponentContext(lifecycle), ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), manager, Dispatchers.Unconfined)),
            if (detail) ScreenId.TRAYS else ScreenId.OBSERVATION_EDITOR)
        val component = root.stack.value.active.instance
        try {
            val language = if (detail) "ru" else "en"
            Locale.setDefault(Locale.forLanguageTag(language))
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(locale = language,
                screenWidth = if (detail) 3025 else DeviceConfig.PIXEL_5.screenWidth,
                fontScale = if (detail) 2f else 1f,
                nightMode = if (detail) NightMode.NIGHT else NightMode.NOTNIGHT))
            paparazzi.configureComposeResources()
            if (detail) component.onAction(ScreenAction.Select("active-radish")) else {
                component.onAction(ScreenAction.EditField(ScreenFieldId("text"), "Keep my observation Ж"))
            }
            assertEquals(LoadState.ERROR, if (detail) component.model.value.detail?.loadState else component.model.value.loadState)
            var clicked = false
            val view = ComposeView(paparazzi.context).apply { setContent { RootApp(root, onThemeChanged = {}) } }
            onFrame = {
                val nodes = view.nodes()
                if (detail) assertTrue(nodes.any { it.config.getOrNull(SemanticsProperties.TestTag) == "trays-list-detail" })
                assertTrue(nodes.any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any {
                    it.text.contains(if (detail) "Ваши данные сохранены" else "Your data is safe")
                } == true }, "The localized read failure must be visible with retained content")
                if (!detail) assertTrue(nodes.any {
                    it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Keep my observation Ж"
                }, "The actual editor must display the retained authored draft")
                val tag = if (detail) "action-retry-detail" else "action-retry"
                val retry = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                assertTrue(retry.boundsInRoot.width > 0 && retry.boundsInRoot.height > 0)
                assertTrue(requireNotNull(retry.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
                clicked = true
            }
            paparazzi.snapshot(view, name = if (detail) "ru-font200-selected-detail-read-error" else "en-retained-editor-read-error")
            assertTrue(clicked, "The visible production Retry control must invoke the public action")
            assertEquals(2, manager.failedTargetAttempts)
            if (detail) {
                assertEquals(LoadState.CONTENT, component.model.value.loadState)
                assertNull(component.model.value.detail?.errorKey)
                assertEquals("active-radish", component.model.value.detail?.selectedItemId)
                assertEquals(1, manager.collectionAttempts)
            } else {
                assertNull(component.model.value.errorKey)
                assertEquals("Keep my observation Ж", component.model.value.fields.single { it.id.value == "text" }.value)
                assertTrue(component.model.value.hasUnsavedChanges)
            }
        } finally {
            onFrame = {}
            lifecycle.destroy()
            Locale.setDefault(previous)
        }
    }

    private class FailingReadManager(private val detail: Boolean) : ScreenDataManager {
        var failedTargetAttempts = 0
        var collectionAttempts = 0
        override fun watch(screen: ScreenId, stableId: String?) = flow {
            val target = if (detail) screen == ScreenId.BATCH_DETAIL else screen == ScreenId.OBSERVATION_EDITOR
            if (target) failedTargetAttempts += 1 else collectionAttempts += 1
            emit(ProductionScreenFixtures.snapshot(screen, stableId,
                if (screen == ScreenId.TRAYS) ProductionScreenFixtures.Scenario.ACTIVE else ProductionScreenFixtures.Scenario.CONTENT))
            if (target && failedTargetAttempts == 1) throw IOException("Transient local read")
            awaitCancellation()
        }
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
            error("Read Retry must not execute a write")
    }

    private fun View.nodes(): List<SemanticsNode> = buildList {
        if (this@nodes is ViewRootForTest) addAll(semanticsOwner.getAllSemanticsNodes(mergingEnabled = true))
        if (this@nodes is ViewGroup) repeat(childCount) { addAll(getChildAt(it).nodes()) }
    }
}
