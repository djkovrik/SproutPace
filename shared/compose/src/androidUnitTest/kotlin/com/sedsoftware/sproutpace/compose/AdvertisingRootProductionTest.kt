package com.sedsoftware.sproutpace.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.*
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.sedsoftware.sproutpace.data.ProductSettings
import com.sedsoftware.sproutpace.data.ThemePreference
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.monetization.*
import com.sedsoftware.sproutpace.root.AppRootComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.test.*

/** Composes the real router and Stores; only the external region, network and SDK/view ports record calls. */
class AdvertisingRootProductionTest {
    private var onFrame: () -> Unit = {}
    private var rootDiagnostic: () -> String = { "" }
    private var lastImage: BufferedImage? = null
    @get:Rule val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 37),
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 6000),
        snapshotHandler = object : SnapshotHandler {
            override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) = object : SnapshotHandler.FrameHandler {
                override fun handle(image: BufferedImage) { lastImage = image; onFrame() }
                override fun close() = Unit
            }
            override fun close() = Unit
        },
    )

    @Test
    fun recycledCatalogSlotCannotLoadAnotherAdDuringTheSameVisit() = runTest {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 640))
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG).use { fixture ->
                val runtime = ControllerRuntime(fixture.settings, protected = false)
                val view = rootView(RuntimeRoot(fixture.root, runtime))
                var returnedFrames = 0
                play(view, listOf(
                    { view.scrollCatalogTo(6) },
                    {
                        if (runtime.created != 1 || view.tagged("inline-ad").isEmpty()) false else {
                            view.assertAdAbovePrimaryAction()
                            view.scrollCatalogTo(0)
                        }
                    },
                    { if (runtime.disposed != 1) false else view.scrollCatalogTo(6) },
                    {
                        returnedFrames += 1
                        if (returnedFrames < 4) false else {
                            assertEquals(1, runtime.created, "Lazy recycling must retain the screen-visit load budget")
                            assertTrue(view.tagged("inline-ad").isEmpty())
                            assertEquals(ScreenId.CATALOG, fixture.model().screen)
                            true
                        }
                    },
                ))
            }
        }
    }

    @Test
    fun privacyExpiryCollapsesTheOpenVisitAndPreventsAnyReload() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG).use { fixture ->
                val runtime = ControllerRuntime(fixture.settings, protected = false)
                val expiry = CompletableDeferred<Unit>()
                val view = rootView(RuntimeRoot(fixture.root, runtime)) { remaining ->
                    assertEquals(60_000L, remaining)
                    expiry.await()
                }
                var expiredFrames = 0
                play(view, listOf(
                    {
                        if (runtime.created != 1 || view.tagged("inline-ad").isEmpty()) false else {
                            runtime.now += 60_001
                            expiry.complete(Unit)
                            true
                        }
                    },
                    {
                        expiredFrames += 1
                        if (expiredFrames < 4 || runtime.disposed != 1) false else {
                            assertTrue(view.tagged("inline-ad").isEmpty())
                            assertFalse(runtime.canRequestAd(AdConfiguration.ANDROID_DEMO_BANNER_ID))
                            assertEquals(1, runtime.created)
                            assertEquals(1, runtime.endpointCalls, "Expiry must not refresh/retry within the same visit")
                            assertEquals(ScreenId.CATALOG, fixture.model().screen)
                            assertEquals(8, fixture.model().items.size)
                            true
                        }
                    },
                ))
            }
        }
    }

    @Test
    fun realConsentAcceptWithdrawalAndChangedPolicyGateTheOwnedBanner() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG,
                now = { kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()) }).use { fixture ->
                fixture.seed()
                val before = fixture.database.snapshot()
                val runtime = ControllerRuntime(fixture.settings)
                val root = RuntimeRoot(fixture.root, runtime)
                val view = rootView(root)
                var reconnectedFrames = 0
                play(view, listOf(
                    {
                        if (fixture.model().screen != ScreenId.ADVERTISING_PRIVACY) false else {
                            assertTrue(runtime.events.isEmpty(), "No SDK call may precede the actual consent decision")
                            view.clickTag("action-accept-advertising-consent")
                        }
                    },
                    {
                        if (runtime.created != 1 || view.tagged("inline-ad").isEmpty()) false else {
                            assertEquals(ScreenId.CATALOG, fixture.model().screen)
                            assertEquals(ConsentChoice.ACCEPTED, fixture.settings.privacyState.value?.choice)
                            assertEquals(listOf("consent:true", "initialize", "load"), runtime.events)
                            assertEquals(1, view.tagged("inline-ad").size)
                            view.assertSlotBetween(fixture.model().toUiState().items, 4)
                            runtime.networkAvailable.value = false
                            true
                        }
                    },
                    {
                        if (runtime.disposed != 1 || view.tagged("inline-ad").isNotEmpty()) false else {
                            runtime.networkAvailable.value = true
                            true
                        }
                    },
                    {
                        reconnectedFrames += 1
                        if (reconnectedFrames < 4) false else {
                            assertEquals(1, runtime.created, "Reconnect must not load a second banner during the same visit")
                            fixture.dispatch(UiAction.Navigate(UiScreen.TODAY))
                            true
                        }
                    },
                    {
                        if (view.tagged("screen-today").isEmpty()) false else {
                            fixture.dispatch(UiAction.Navigate(UiScreen.CATALOG)); true
                        }
                    },
                    {
                        if (runtime.created != 2 || view.tagged("inline-ad").isEmpty()) false else {
                            assertEquals(1, runtime.disposed)
                            fixture.dispatch(UiAction.Navigate(UiScreen.SETTINGS))
                            true
                        }
                    },
                    { view.clickTag("settings-open-advertising-privacy") },
                    { view.clickTag("action-withdraw-advertising-consent") },
                    {
                        val withdrawn = org.jetbrains.compose.resources.getString(strictTextResource("privacy_withdrawn"))
                        val statusVisible = view.nodes().any { node ->
                            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == withdrawn } == true
                        }
                        if (fixture.settings.privacyState.value?.choice != ConsentChoice.WITHDRAWN || !statusVisible) false else {
                            assertEquals(listOf("consent:false", "stop"), runtime.events.takeLast(2))
                            assertEquals(2, runtime.disposed, "Each owned banner is disposed once when its real view leaves")
                            fixture.dispatch(UiAction.Navigate(UiScreen.CATALOG))
                            true
                        }
                    },
                    {
                        if (fixture.model().screen != ScreenId.CATALOG || view.tagged("screen-catalog").isEmpty()) false else {
                            assertEquals(2, runtime.created)
                            runtime.policy = "policy-2"
                            fixture.settings.setPrivacyState(assertNotNull(fixture.settings.privacyState.value).copy(
                                fetchedAtEpochMillis = runtime.now - 2_000, expiresAtEpochMillis = runtime.now - 1))
                            fixture.dispatch(UiAction.Navigate(UiScreen.TODAY))
                            true
                        }
                    },
                    {
                        if (view.tagged("screen-today").isEmpty()) false else {
                            fixture.dispatch(UiAction.Navigate(UiScreen.CATALOG)); true
                        }
                    },
                    {
                        if (fixture.model().screen != ScreenId.ADVERTISING_PRIVACY) false else {
                            assertEquals("policy-2", fixture.settings.privacyState.value?.policyVersion)
                            assertEquals(ConsentChoice.UNDECIDED, fixture.settings.privacyState.value?.choice)
                            assertEquals(2, runtime.created)
                            view.clickTag("action-decline-advertising-consent")
                        }
                    },
                    {
                        if (fixture.model().screen != ScreenId.CATALOG) false else {
                            assertEquals(before, fixture.database.snapshot())
                            assertEquals(ConsentChoice.DECLINED, fixture.settings.privacyState.value?.choice)
                            assertEquals(2, runtime.created)
                            assertTrue(view.tagged("inline-ad").isEmpty())
                            true
                        }
                    },
                ))
                assertEquals(2, runtime.created)
            }
        }
    }

    @Test
    fun realHistoryOwnsOneSlotAndOfflineShortAndProhibitedVisitsHaveNone() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                repeat(6) { index ->
                    suspend fun execute(screen: ScreenId, operation: String, arguments: Map<String, String>) {
                        fixture.manager.execute(screen, ScreenCommand(operation, "history-$index", arguments).newInvocation()).getOrThrow()
                    }
                    execute(ScreenId.BATCH_EDITOR, "save-batch", mapOf("cultureId" to "pea", "trayId" to "tray", "substrate" to "Coco"))
                    execute(ScreenId.BATCH_DETAIL, "confirm-sowing", mapOf("zoneId" to "zone", "eventId" to "sow-$index",
                        "occurredAt" to "2026-10-10T09:00:00Z"))
                    execute(ScreenId.CLOSE_BATCH, "close-failed", mapOf("reason" to "Mold", "occurredAt" to "2026-10-10T12:00:00Z"))
                }
                val runtime = ControllerRuntime(fixture.settings, protected = false)
                val view = rootView(RuntimeRoot(fixture.root, runtime))
                play(view, listOf(
                    { assertEquals(0, runtime.created); fixture.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY)); true },
                    {
                        if (runtime.created != 1 || view.tagged("inline-ad").isEmpty()) false else {
                            assertEquals(6, fixture.model().toUiState().items.size)
                            assertEquals(1, view.tagged("inline-ad").size)
                            view.assertSlotBetween(fixture.model().toUiState().items, 5)
                            fixture.dispatch(UiAction.Navigate(UiScreen.TODAY))
                            true
                        }
                    },
                    { fixture.root.open(ScreenId.BATCH_DETAIL, "batch"); true },
                    { assertTrue(view.tagged("inline-ad").isEmpty()); fixture.dispatch(UiAction.Navigate(UiScreen.CATALOG)); true },
                    {
                        if (runtime.created != 2) false else {
                            fixture.dispatch(UiAction.EditField("query", "Raphanus"))
                            true
                        }
                    },
                    {
                        if (view.tagged("inline-ad").isNotEmpty()) false else {
                            assertEquals("Raphanus", fixture.field("query"))
                            assertEquals(2, runtime.created)
                            fixture.dispatch(UiAction.Navigate(UiScreen.TODAY))
                            runtime.networkAvailable.value = false
                            true
                        }
                    },
                    { fixture.dispatch(UiAction.Navigate(UiScreen.CATALOG)); true },
                    { assertEquals(2, runtime.created); assertTrue(view.tagged("inline-ad").isEmpty()); true },
                ))
                assertEquals(runtime.created, runtime.disposed)
            }
        }
    }

    @Test
    fun lateRegionResponseCannotOpenConsentAfterPublicNavigationLeavesTheEligibleVisit() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG).use { fixture ->
                val requested = CompletableDeferred<Unit>()
                val response = CompletableDeferred<Unit>()
                val runtime = ControllerRuntime(fixture.settings, beforeRegionFetch = {
                    requested.complete(Unit)
                    response.await()
                    // The public route changes as the external response arrives, before Compose observes it.
                    fixture.dispatch(UiAction.Navigate(UiScreen.TODAY))
                })
                val view = rootView(RuntimeRoot(fixture.root, runtime))
                play(view, listOf(
                    { if (!requested.isCompleted) false else { response.complete(Unit); true } },
                    {
                        if (view.tagged("screen-today").isEmpty()) false else {
                            assertEquals(ScreenId.TODAY, fixture.model().screen)
                            assertEquals(ConsentChoice.UNDECIDED, fixture.settings.privacyState.value?.choice)
                            assertTrue(runtime.events.isEmpty())
                            assertEquals(0, runtime.created)
                            fixture.dispatch(UiAction.Navigate(UiScreen.CATALOG))
                            true
                        }
                    },
                    {
                        if (fixture.model().screen != ScreenId.ADVERTISING_PRIVACY ||
                            view.tagged("screen-advertising_privacy").isEmpty()) false else {
                            assertEquals(1, runtime.endpointCalls)
                            assertTrue(runtime.events.isEmpty())
                            true
                        }
                    },
                ))
            }
        }
    }

    @Test
    fun noFillHasNoLabelOrReservedHeightAndDoesNotRetryDuringTheVisit() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG).use { fixture ->
                val runtime = ControllerRuntime(fixture.settings, protected = false, fill = false)
                val view = rootView(RuntimeRoot(fixture.root, runtime))
                play(view, listOf({
                    if (runtime.created != 1 || runtime.disposed != 1) false else {
                        assertTrue(view.tagged("inline-ad").isEmpty())
                        assertTrue(view.nodes().none { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Advertisement" } == true })
                        assertEquals(0, runtime.banner?.height)
                        val items = fixture.model().toUiState().items
                        val precedingBottom = items.take(4).maxOf { item ->
                            view.tagged("item-${item.id}").single().boundsInRoot.bottom
                        }
                        val nextTop = view.tagged("item-${items[4].id}").single().boundsInRoot.top
                        assertEquals(12f * view.resources.displayMetrics.density, nextTop - precedingBottom, 1f,
                            "A failed slot must leave only the ordinary grid spacing")
                        true
                    }
                }))
                assertEquals(1, runtime.created)
                assertEquals(1, runtime.disposed)
            }
        }
    }

    @Test
    fun freshShortSearchAndUnavailableAdvertisingLeaveRealCatalogAndGuideUsable() = runTest {
        for (mode in listOf("short", "missing-id", "endpoint-failure", "sdk-failure")) {
            withPublicWorkspace { directory, preferences ->
                PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG).use { fixture ->
                    if (mode == "short") fixture.edit(mapOf("query" to "Raphanus"))
                    val runtime = ControllerRuntime(fixture.settings, protected = mode == "short",
                        configuration = if (mode == "missing-id") AdConfiguration(AdPlatform.ANDROID, AdHostMode.PRODUCTION, null)
                            else AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null),
                        endpointAvailable = mode != "endpoint-failure", failSdk = mode == "sdk-failure")
                    val view = rootView(RuntimeRoot(fixture.root, runtime))
                    var settledFrames = 0
                    play(view, listOf(
                        {
                            settledFrames += 1
                            if (settledFrames < 4) false else {
                                assertEquals(ScreenId.CATALOG, fixture.model().screen, mode)
                                assertEquals(0, runtime.created, mode)
                                assertTrue(view.tagged("inline-ad").isEmpty(), mode)
                                if (mode == "short") assertNull(fixture.settings.privacyState.value)
                                view.clickTag("item-radish")
                            }
                        },
                        { fixture.model().screen == ScreenId.RECIPE },
                    ))
                    assertEquals(0, runtime.created, mode)
                    assertTrue(fixture.database.snapshot().batches.isEmpty())
                }
            }
        }
    }

    @Test
    fun offlineRootKeepsPublicCoreWritesAndReadsAvailableAcrossStorageReopen() = runTest {
        withPublicWorkspace { directory, preferences ->
            val saved = PublicProductionFixture(directory, preferences, initialScreen = ScreenId.CATALOG).use { fixture ->
                var zoneId = ""
                var trayId = ""
                var batchId = ""
                val runtime = ControllerRuntime(fixture.settings).apply { networkAvailable.value = false }
                val view = rootView(RuntimeRoot(fixture.root, runtime))
                play(view, listOf(
                    {
                        assertEquals(8, fixture.model().items.size)
                        fixture.submit("open-recipe", "radish")
                        true
                    },
                    {
                        assertEquals("radish", fixture.model().selectedItemId)
                        assertTrue(fixture.model().items.any { it.recipe?.readOnly == true })
                        fixture.save(ScreenId.ZONES, null, "save-zone", mapOf(
                            "name" to "Offline shelf", "type" to "grow_light", "capacity" to "2"))
                        true
                    },
                    {
                        assertEquals("Offline shelf", fixture.database.snapshot().zones.single().name)
                        zoneId = fixture.database.snapshot().zones.single().id.value
                        fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                            "name" to "Offline tray", "shape" to "rectangle", "widthCm" to "20", "lengthCm" to "30"))
                        true
                    },
                    {
                        trayId = fixture.database.snapshot().trayPresets.single().id.value
                        fixture.save(ScreenId.BATCH_EDITOR, null, "save-batch", mapOf(
                            "cultureId" to "pea", "trayId" to trayId, "substrate" to "Offline coir",
                            "actualSeedMassGrams" to "12.5", "plannedSowDate" to "2026-10-10"))
                        true
                    },
                    {
                        batchId = fixture.database.snapshot().batches.single().id.value
                        fixture.requestOfflineStage(batchId, zoneId, "start-soaking", "2026-10-10T08:00:00Z")
                        true
                    },
                    {
                        assertNull(fixture.model().errorKey)
                        val soaking = fixture.database.snapshot().batches.single().state == BatchState.SOAKING
                        if (soaking) fixture.dispatch(UiAction.Navigate(UiScreen.TODAY))
                        soaking
                    },
                    {
                        val complete = fixture.model().items.flatMap { it.commands }.singleOrNull { it.operation == "complete-action" }
                        if (complete == null || view.tagged("screen-today").isEmpty()) false else {
                            assertEquals(ScreenId.TODAY, fixture.model().screen)
                            assertEquals(BatchState.SOAKING, fixture.database.snapshot().batches.single().state)
                            fixture.dispatch(UiAction.SubmitItem(complete.operation, complete.subjectId))
                            true
                        }
                    },
                    {
                        val task = fixture.database.snapshot().batches.single().actions.singleOrNull { it.id.value == "soak:$batchId" }
                        if (task?.status != ScheduledActionStatus.COMPLETED) false else {
                            fixture.requestOfflineStage(batchId, zoneId, "confirm-sowing", "2026-10-10T10:00:00Z")
                            true
                        }
                    },
                    {
                        assertNull(fixture.model().errorKey)
                        if (fixture.model().stagePreview == null) false else {
                            fixture.dispatch(UiAction.ConfirmStagePreview)
                            true
                        }
                    },
                    {
                        assertEquals(BatchState.SOWN, fixture.database.snapshot().batches.single().state)
                        fixture.save(ScreenId.OBSERVATION_EDITOR, batchId, "save-observation", mapOf("text" to "Offline growth note"))
                        true
                    },
                    {
                        assertEquals("Offline growth note", fixture.database.snapshot().observations.single().text)
                        fixture.save(ScreenId.PLAN, null, "save-plan", mapOf("anchorDate" to "2026-10-30",
                            "cadenceDays" to "2", "cultureIds" to "radish,pea", "mode" to "continuous_queue"))
                        true
                    },
                    {
                        assertEquals(1, fixture.database.snapshot().plans.size)
                        assertTrue(fixture.model().items.any { it.planEntry != null })
                        fixture.dispatch(UiAction.Navigate(UiScreen.SETTINGS))
                        fixture.dispatch(UiAction.ChooseOption("theme", "dark"))
                        true
                    },
                    {
                        assertEquals(ThemePreference.DARK, fixture.settings.theme.value)
                        fixture.save(ScreenId.CLOSE_BATCH, batchId, "save-outcome",
                            mapOf("outcome" to "failed", "reason" to "Mold", "note" to "Offline history"))
                        true
                    },
                    {
                        fixture.open(ScreenId.TRAYS)
                        fixture.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY))
                        assertEquals(batchId, fixture.model().toUiState().items.single().id)
                        assertTrue(view.tagged("inline-ad").isEmpty())
                        assertTrue(runtime.events.isEmpty())
                        assertEquals(0, runtime.endpointCalls)
                        true
                    },
                ))
                fixture.database.snapshot()
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                val runtime = ControllerRuntime(reopened.settings).apply { networkAvailable.value = false }
                val view = rootView(RuntimeRoot(reopened.root, runtime))
                play(view, listOf(
                    {
                        assertEquals(saved, reopened.database.snapshot())
                        assertEquals(ThemePreference.DARK, reopened.settings.theme.value)
                        reopened.open(ScreenId.OBSERVATION_EDITOR, saved.observations.single().id.value)
                        true
                    },
                    {
                        assertEquals("Offline growth note", reopened.field("text"))
                        reopened.open(ScreenId.PLAN, saved.plans.single().id.value)
                        true
                    },
                    {
                        assertEquals("2", reopened.field("cadenceDays"))
                        assertTrue(reopened.model().items.any { it.planEntry != null })
                        assertTrue(runtime.events.isEmpty())
                        assertEquals(0, runtime.endpointCalls)
                        assertTrue(view.tagged("inline-ad").isEmpty())
                        true
                    },
                ))
            }
        }
    }

    @Test
    fun productionRootUsesSystemLocaleFallbackWithoutTranslatingAuthoredTrayName() = runTest {
        val previous = Locale.getDefault()
        try {
            withPublicWorkspace { directory, preferences ->
                PublicProductionFixture(directory, preferences).use { fixture ->
                    val authored = " My tray Ж "
                    fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                        "name" to authored, "shape" to "rectangle", "widthCm" to "20", "lengthCm" to "30"))
                    val tray = fixture.database.snapshot().trayPresets.single()
                    fixture.save(ScreenId.BATCH_EDITOR, null, "save-batch", mapOf("cultureId" to "pea",
                        "trayId" to tray.id.value, "substrate" to "Coir", "plannedSowDate" to "2026-10-10"))
                    fixture.open(ScreenId.TRAYS)
                    fixture.dispatch(UiAction.SelectTrayCategory(TrayCategory.PLANNED))
                    val runtime = ControllerRuntime(fixture.settings).apply { networkAvailable.value = false }
                    val root = RuntimeRoot(fixture.root, runtime)
                    for (tag in listOf("en-US", "ru-RU", "de-DE", "ru-KZ", "en-GB")) {
                        Locale.setDefault(Locale.forLanguageTag(tag))
                        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(
                            screenHeight = 6000, locale = tag.replace("-", "-r")))
                        val view = rootView(root)
                        play(view, listOf({
                            val texts = view.nodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                                .map { it.text }
                            val russian = tag.startsWith("ru")
                            assertTrue((if (russian) "Лотки" else "Trays") in texts, "$tag: $texts")
                            assertTrue((if (russian) "Сегодня" else "Today") in texts, "$tag: $texts")
                            assertTrue(authored in texts, "Authored tray name must remain verbatim in $tag: $texts")
                            assertEquals(authored, fixture.database.snapshot().batches.single().tray.nameSnapshot)
                            assertTrue(runtime.events.isEmpty())
                            true
                        }))
                    }
                }
            }
        } finally { Locale.setDefault(previous) }
    }

    private fun PublicProductionFixture.requestOfflineStage(batchId: String, zoneId: String, operation: String, at: String) {
        open(ScreenId.BATCH_DETAIL, batchId)
        edit(mapOf("occurredAt" to at, "zoneId" to zoneId, "unassigned" to "false"))
        submit(operation, confirm = false)
        assertNull(model().errorKey)
    }

    private fun rootView(root: AppRootComponent, expiryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }): ComposeView {
        paparazzi.configureComposeResources()
        rootDiagnostic = {
            val model = root.stack.value.active.instance.model.value
            "screen=${model.screen}, error=${model.errorKey}, validation=${model.validationErrors}, " +
                "confirmation=${root.confirmation.value.child?.configuration}, " +
                "privacy=${(root as? AdvertisingRuntimeOwner)?.advertisingRuntime?.privacyState?.value}"
        }
        return ComposeView(paparazzi.context).apply {
            setContent { RootApp(root, onThemeChanged = {}, advertisingExpiryDelay = expiryDelay) }
        }
    }

    private fun play(view: View, steps: List<suspend () -> Boolean>) {
        var step = 0
        var diagnostic = "No frame rendered"
        onFrame = {
            if (step < steps.size && kotlinx.coroutines.runBlocking { steps[step]() }) step += 1
            diagnostic = rootDiagnostic() + "; tags=" +
                view.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) } + "; text=" +
                view.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text) }
        }
        try { paparazzi.gif(view, start = 0L, end = 5_000L, fps = 10) } finally { onFrame = {} }
        if (step != steps.size) {
            val target = java.io.File("build/reports/advertising-flow-${java.util.UUID.randomUUID()}.png")
            target.parentFile?.mkdirs()
            lastImage?.let { javax.imageio.ImageIO.write(it, "png", target) }
            diagnostic += "; image=${target.absolutePath}"
        }
        assertEquals(steps.size, step, "Production composition did not reach step $step; $diagnostic")
    }
}

private class RuntimeRoot(delegate: AppRootComponent, override val advertisingRuntime: AdvertisingRuntime) :
    AppRootComponent by delegate, AdvertisingRuntimeOwner

private class ControllerRuntime(
    settings: ProductSettings,
    protected: Boolean = true,
    private val fill: Boolean = true,
    configuration: AdConfiguration = AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null),
    endpointAvailable: Boolean = true,
    failSdk: Boolean = false,
    beforeRegionFetch: suspend () -> Unit = {},
) : AndroidInlineAdHost {
    var now = System.currentTimeMillis()
    override fun nowEpochMillis(): Long = now
    var policy = "policy-1"
    val events = mutableListOf<String>()
    var created = 0
    var disposed = 0
    var endpointCalls = 0
    var banner: View? = null
    override val networkAvailable = MutableStateFlow(true)
    override val privacyState = settings.privacyState
    private val controller = AdvertisingController(PrivacyCoordinator(
        PrivacyRegionApi {
            endpointCalls += 1
            beforeRegionFetch()
            if (endpointAvailable) Result.success(PrivacyRegion(1, protected, policy, now, now + 60_000))
            else Result.failure(IllegalStateException("Endpoint unavailable"))
        },
        PrivacyRepository(ProductSettingsPrivacyStore(settings)), EpochMillisClock { now }),
        object : AdSdkDriver {
            override val platform = AdPlatform.ANDROID
            override val supported = true
            override suspend fun setUserConsent(consent: Boolean) { events += "consent:$consent" }
            override suspend fun initialize() { events += "initialize"; check(!failSdk) { "SDK failed" } }
            override suspend fun stopNewRequestsAndDispose() { events += "stop" }
        }, configuration)
    override suspend fun prepare(request: AdPlacementRequest) = controller.prepare(request.copy(offline = !networkAvailable.value))
    override suspend fun acceptConsent() = controller.acceptConsent()
    override suspend fun declineConsent() = controller.declineConsent()
    override suspend fun withdrawConsent() = controller.withdrawConsent()
    override fun close() = Unit
    override fun createInlineBanner(context: Context, adUnitId: String, widthDp: Int, maxHeightDp: Int,
        session: InlineAdSession, isHostDestroyed: () -> Boolean, onStateChanged: (InlineAdState) -> Unit): View {
        assertTrue(canRequestAd(adUnitId), "The native request boundary must never receive stale eligibility")
        assertTrue(session.beginLoad())
        assertFalse(isHostDestroyed())
        created += 1
        events += "load"
        if (fill) session.loaded() else session.failed()
        onStateChanged(session.state)
        return View(context).apply { layoutParams = FrameLayout.LayoutParams(widthDp, 50) }.also { banner = it }
    }
    override fun disposeInlineBanner(view: View, session: InlineAdSession) { disposed += 1; session.dispose() }
}

private fun View.nodes(): List<SemanticsNode> = buildList {
    if (this@nodes is ViewRootForTest) addAll(semanticsOwner.getAllSemanticsNodes(mergingEnabled = true))
    if (this@nodes is ViewGroup) repeat(childCount) { addAll(getChildAt(it).nodes()) }
}
private fun View.tagged(tag: String) = nodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
private fun View.assertSlotBetween(items: List<UiItem>, precedingCount: Int) {
    val slot = tagged("inline-ad").single().boundsInRoot
    val before = tagged("item-${items[precedingCount - 1].id}").single().boundsInRoot
    val after = tagged("item-${items[precedingCount].id}").single().boundsInRoot
    assertTrue(slot.top >= before.bottom, "Banner must follow the $precedingCount real records")
    assertTrue(slot.bottom <= after.top, "Banner must precede the next real record")
    assertAdAbovePrimaryAction()
}
private fun View.assertAdAbovePrimaryAction() {
    val slot = tagged("inline-ad").single().boundsInRoot
    val footer = tagged("primary-action-footer").single().boundsInRoot
    assertTrue(slot.bottom <= footer.top, "The primary action must have its own space outside the ad viewport")
}
private fun View.clickTag(tag: String): Boolean = tagged(tag).firstOrNull()
    ?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true

private fun View.scrollCatalogTo(index: Int): Boolean = tagged("catalog-grid").firstOrNull()
    ?.config?.getOrNull(SemanticsActions.ScrollToIndex)?.action?.invoke(index) == true
