package com.sedsoftware.sproutpace.compose

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.detectEnvironment
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Actual Compose semantics backed by public production components; frames are not new golden artifacts. */
class ReviewAccessibilityProductionTest {
    private var onFrame: () -> Unit = {}
    @get:Rule val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 37),
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000),
        snapshotHandler = object : SnapshotHandler {
            override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) = object : SnapshotHandler.FrameHandler {
                override fun handle(image: BufferedImage) = onFrame()
                override fun close() = Unit
            }
            override fun close() = Unit
        },
    )

    @Test fun planCardsExposeOneEditTargetWithTheirOwnCadenceAndStatusInBothLocalesAtFont200() = runTest {
        eachLocale { russian ->
            val actions = mutableListOf<UiAction>()
            val state = typedPreviewState(UiScreen.PLAN).copy(
                plans = listOf(
                    com.sedsoftware.sproutpace.component.PlanSummaryProjection("weekly", 7, listOf("broccoli"), false),
                    com.sedsoftware.sproutpace.component.PlanSummaryProjection("paused", 5, listOf("radish", "pea"), true),
                ),
            )
            val view = ComposeView(paparazzi.context).apply {
                setContent { App(state = state, onAction = actions::add) }
            }
            play(view, listOf({
                val weekly = view.tagged("plan-summary-weekly").single()
                val paused = view.tagged("plan-summary-paused").single()
                val cadence = if (russian) "Каждые 7 дн." else "Every 7 days"
                assertEquals(1, view.nodes().sumOf { it.texts().count { text -> text == cadence } })
                assertTrue((if (russian) "Брокколи" else "Broccoli") in weekly.texts())
                assertTrue((if (russian) "Активен" else "Active") in weekly.texts())
                assertTrue((if (russian) "Приостановлен" else "Paused") in paused.texts())
                assertTrue((if (russian) "Каждые 5 дн." else "Every 5 days") in paused.texts())
                listOf(weekly, paused).forEach { card ->
                    assertEquals(androidx.compose.ui.semantics.Role.Button, card.config.getOrNull(SemanticsProperties.Role))
                    val click = assertNotNull(card.config.getOrNull(SemanticsActions.OnClick))
                    assertEquals(if (russian) "Обновить план" else "Update plan", click.label)
                    assertTrue(assertNotNull(click.action).invoke())
                }
                assertEquals(listOf<UiAction>(UiAction.Submit("edit-plan", "weekly"), UiAction.Submit("edit-plan", "paused")),
                    actions.filterNot { it is UiAction.SetLayout })
                true
            }))
            // Layout reporting is asynchronous and can precede the first rendered frame.
            assertEquals(listOf(UiAction.SetLayout(UiLayout.COMPACT)), actions.filterIsInstance<UiAction.SetLayout>())
        }
    }

    @Test fun mixedTrayGeometryIsReadableInClickableProductionRowsInBothLocalesAtFont200() = runTest {
        eachLocale { russian ->
            withPublicWorkspace { directory, preferences ->
                PublicProductionFixture(directory, preferences).use { f ->
                    f.seed()
                    f.save(ScreenId.TRAY_SIZES, "circle", "save-tray-size", mapOf(
                        "id" to "circle", "name" to "Round tray", "shape" to "circle", "diameterCm" to "25"))
                    f.open(ScreenId.TRAY_SIZES)
                    val view = ComposeView(paparazzi.context).apply { setContent { RootApp(f.root, onThemeChanged = {}) } }
                    play(view, listOf({
                        val rectangle = if (russian) "Прямоугольник · 20 × 30 см" else "Rectangle · 20 × 30 cm"
                        val circle = if (russian) "Круг · диаметр 25 см" else "Circle · diameter 25 cm"
                        val clickable = view.nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
                        val diagnostic = "locale=${Locale.getDefault()}, selected=${f.model().selectedItemId}, " +
                            "rows=${f.model().toUiState().items.map { it.authoredTitle to it.trayShape }}, " +
                            "clickable=${clickable.map { it.texts() }}, all=${view.nodes().map { it.config }}"
                        assertTrue(clickable.any { "Tray" in it.texts() && rectangle in it.texts() }, diagnostic)
                        assertTrue(clickable.any { "Round tray" in it.texts() && circle in it.texts() }, diagnostic)
                        assertEquals(2, f.model().toUiState().items.count { it.trayShape != null })
                        true
                    }))
                }
            }
        }
    }

    @Test fun twoTemporalFieldsExposeDistinctContextualNamesAndOnlyTheirOwnErrors() = runTest {
        eachLocale { russian ->
            val actions = mutableListOf<UiAction>()
            val occurred = UiField("occurredAt", "field_occurred_at", ScreenFieldKind.INSTANT,
                "2026-10-10T10:00:00Z", false, false, emptyList(), "validation_valid_date_time")
            val due = occurred.copy(id = "dueAt", labelKey = "field_due_at",
                value = "2026-10-11T11:15:00Z", errorKey = null)
            val view = ComposeView(paparazzi.context).apply {
                setContent { MaterialTheme { Column {
                    TemporalFieldControl(occurred, actions::add)
                    TemporalFieldControl(due, actions::add)
                } } }
            }
            play(view, listOf({
                val occurredLabel = if (russian) "Время события" else "Occurred at"
                val dueLabel = if (russian) "Срок" else "Due at"
                val dateSuffix = if (russian) "дата" else "date"
                val timeSuffix = if (russian) "время" else "time"
                val error = org.jetbrains.compose.resources.getString(strictTextResource("validation_valid_date_time"))
                listOf("date" to dateSuffix, "time" to timeSuffix).forEach { (part, suffix) ->
                    val eventControl = view.tagged("temporal-occurredAt-$part").single()
                    val dueControl = view.tagged("temporal-dueAt-$part").single()
                    assertEquals(listOf("$occurredLabel: $suffix"), eventControl.config.getOrNull(SemanticsProperties.ContentDescription))
                    assertEquals(listOf("$dueLabel: $suffix"), dueControl.config.getOrNull(SemanticsProperties.ContentDescription))
                    assertEquals(error, eventControl.config.getOrNull(SemanticsProperties.Error))
                    assertNull(dueControl.config.getOrNull(SemanticsProperties.Error))
                    assertNotNull(eventControl.config.getOrNull(SemanticsActions.OnClick))
                    assertNotNull(dueControl.config.getOrNull(SemanticsActions.OnClick))
                    val eventValue = assertNotNull(eventControl.config.getOrNull(SemanticsProperties.StateDescription),
                        "The contextual name must not hide the selected $part from accessibility")
                    val dueValue = assertNotNull(dueControl.config.getOrNull(SemanticsProperties.StateDescription))
                    assertEquals(eventControl.texts().single(), eventValue)
                    assertEquals(dueControl.texts().single(), dueValue)
                    assertNotEquals(eventValue, dueValue, "Each field must expose its own selected value")
                }
                val clear = view.nodes().single { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                        it.contains(occurredLabel) && it.startsWith(if (russian) "Очистить" else "Clear")
                    } == true
                }
                assertTrue(assertNotNull(clear.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
                assertEquals(listOf<UiAction>(UiAction.EditField("occurredAt", "")), actions)
                true
            }))
        }
    }

    @Test fun emptyTemporalSelectionExposesTheLocalizedPromptRatherThanThePickerFallback() = runTest {
        eachLocale { russian ->
            val field = UiField("dueAt", "field_due_at", ScreenFieldKind.INSTANT,
                "", false, false, emptyList(), null)
            val view = ComposeView(paparazzi.context).apply {
                setContent { MaterialTheme { TemporalFieldControl(field) {} } }
            }
            play(view, listOf({
                val date = view.tagged("temporal-dueAt-date").single()
                val time = view.tagged("temporal-dueAt-time").single()
                assertEquals(if (russian) "Выбрать дату" else "Choose date",
                    date.config.getOrNull(SemanticsProperties.StateDescription))
                assertEquals(if (russian) "Выбрать время" else "Choose time",
                    time.config.getOrNull(SemanticsProperties.StateDescription))
                assertEquals(date.texts().single(), date.config.getOrNull(SemanticsProperties.StateDescription))
                assertEquals(time.texts().single(), time.config.getOrNull(SemanticsProperties.StateDescription))
                assertTrue(view.nodes().none { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                        it.startsWith(if (russian) "Очистить" else "Clear")
                    } == true
                })
                true
            }))
        }
    }

    @Test fun actualStoreFailureHasNoSuccessWhileSuccessfulRescheduleAndCompletionAnnounceAndFocus() = runTest {
        eachLocale { russian ->
            withPublicWorkspace { directory, preferences ->
                val releaseFailure = CompletableDeferred<Unit>()
                var snoozeAttempts = 0
                PublicProductionFixture(directory, preferences, initialScreen = ScreenId.TODAY, decorateManager = { delegate ->
                    object : ScreenDataManager by delegate {
                        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
                            if (command.operation == "snooze" && snoozeAttempts++ == 0) {
                                releaseFailure.await()
                                Result.failure(IOException("Injected durable-write failure"))
                            } else delegate.execute(screen, command)
                    }
                }).use { f ->
                    f.seed()
                    val batch = f.database.snapshot().batches.single()
                    val actionId = ScheduledActionId("review-general-task")
                    f.database.saveBatch(batch.copy(actions = batch.actions + ScheduledAction(
                        actionId, batch.id, null, DueMoment.ElapsedTarget(f.now()))))
                    val before = f.database.snapshot()
                    val view = ComposeView(paparazzi.context).apply { setContent { RootApp(f.root, onThemeChanged = {}) } }
                    val future = "2026-10-10T12:00:00Z"
                    fun reschedule() = f.dispatch(UiAction.SubmitItem("snooze", actionId.value,
                        mapOf("dueKind" to "elapsed", "dueAt" to future)))
                    play(view, listOf(
                        {
                            assertTrue(view.tagged("action-completion-status").isEmpty())
                            reschedule()
                            true
                        },
                        {
                            if (!f.model().busy) false else {
                                assertEquals(0L, f.model().completionRevision)
                                assertTrue(view.tagged("action-completion-status").isEmpty())
                                releaseFailure.complete(Unit)
                                true
                            }
                        },
                        {
                            if (f.model().errorKey == null) false else {
                                assertEquals(before, f.database.snapshot())
                                assertEquals(0L, f.model().completionRevision)
                                assertNull(f.model().lastCompletedOperation)
                                assertTrue(view.tagged("action-completion-status").isEmpty())
                                reschedule()
                                true
                            }
                        },
                        {
                            val status = view.tagged("action-completion-status").singleOrNull()
                            if (status?.config?.getOrNull(SemanticsProperties.Focused) != true) false else {
                                assertEquals("snooze", f.model().lastCompletedOperation)
                                assertEquals(1L, f.model().completionRevision)
                                assertEquals(LiveRegionMode.Polite, status.config.getOrNull(SemanticsProperties.LiveRegion))
                                assertEquals(listOf(if (russian) "Задача перенесена." else "Task rescheduled."), status.texts())
                                val saved = f.database.snapshot().batches.single().actions.single { it.id == actionId }
                                assertEquals(DueMoment.ElapsedTarget(Instant.parse(future)), saved.due)
                                f.dispatch(UiAction.SubmitItem("complete-action", actionId.value))
                                true
                            }
                        },
                        {
                            val status = view.tagged("action-completion-status").singleOrNull()
                            val expected = if (russian) "Задача выполнена." else "Task completed."
                            if (status?.config?.getOrNull(SemanticsProperties.Focused) != true || expected !in status.texts()) false else {
                                assertEquals("complete-action", f.model().lastCompletedOperation)
                                assertEquals(2L, f.model().completionRevision)
                                assertEquals(LiveRegionMode.Polite, status.config.getOrNull(SemanticsProperties.LiveRegion))
                                assertEquals(ScheduledActionStatus.COMPLETED,
                                    f.database.snapshot().batches.single().actions.single { it.id == actionId }.status)
                                true
                            }
                        },
                    ))
                    assertEquals(2, snoozeAttempts)
                }
            }
        }
    }

    private suspend fun eachLocale(block: suspend (Boolean) -> Unit) {
        val previous = Locale.getDefault()
        try {
            listOf("en", "ru").forEach { language ->
                Locale.setDefault(Locale.forLanguageTag(language))
                paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(
                    screenHeight = 3000, fontScale = 2f, locale = language))
                paparazzi.configureComposeResources()
                block(language == "ru")
            }
        } finally { Locale.setDefault(previous) }
    }

    private fun play(view: View, steps: List<suspend () -> Boolean>) {
        var step = 0
        onFrame = { if (step < steps.size && runBlocking { steps[step]() }) step += 1 }
        try { paparazzi.gif(view, start = 0L, end = 5_000L, fps = 10) } finally { onFrame = {} }
        assertEquals(steps.size, step, "Stopped at step $step; semantics=${view.nodes().map { it.config }}")
    }

    private fun View.nodes(): List<SemanticsNode> = buildList {
        if (this@nodes is ViewRootForTest) addAll(semanticsOwner.getAllSemanticsNodes(mergingEnabled = true))
        if (this@nodes is ViewGroup) repeat(childCount) { addAll(getChildAt(it).nodes()) }
    }
    private fun View.tagged(tag: String) = nodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    private fun SemanticsNode.texts() = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
}
