package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdvertisingLifecycleTest {
    @Test
    fun permitsExactlyTheApprovedSingleInlinePositions() {
        assertTrue(InlineAdPlacementPolicy.permits(AdPlacementRequest(AdSurface.CATALOG, 8, false, 4)))
        assertTrue(InlineAdPlacementPolicy.permits(AdPlacementRequest(AdSurface.HISTORY, 6, false, 5)))
        assertFalse(InlineAdPlacementPolicy.permits(AdPlacementRequest(AdSurface.CATALOG, 4, false, 4)))
        assertFalse(InlineAdPlacementPolicy.permits(AdPlacementRequest(AdSurface.HISTORY, 8, true, 5)))
        assertFalse(InlineAdPlacementPolicy.permits(AdPlacementRequest(AdSurface.OTHER, 20, false, 4)))
        assertFalse(InlineAdPlacementPolicy.permits(AdPlacementRequest(AdSurface.CATALOG, 8, false, 5)))
    }

    @Test
    fun debugUsesOfficialDemoIdAndReleaseRejectsMissingPlaceholderOrDemo() {
        assertEquals(
            AdConfiguration.ANDROID_DEMO_BANNER_ID,
            AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null).effectiveAdUnitId,
        )
        assertEquals(null, AdConfiguration(AdPlatform.ANDROID, AdHostMode.PRODUCTION, null).effectiveAdUnitId)
        assertEquals(null, AdConfiguration(AdPlatform.ANDROID, AdHostMode.PRODUCTION, "R-M-XXXX-Y").effectiveAdUnitId)
        assertEquals("R-M-123-4", AdConfiguration(AdPlatform.ANDROID, AdHostMode.PRODUCTION, "R-M-123-4").effectiveAdUnitId)
    }

    @Test
    fun consentIsAppliedBeforeEveryPermittedInitialization() = runTest {
        val events = mutableListOf<String>()
        val driver = RecordingDriver(events)
        val controller = controller(driver, protected = true, accepted = true)

        assertIs<AdPreparation.Ready>(controller.prepare(catalogRequest()))
        assertEquals(listOf("consent:true", "initialize"), events)
        assertIs<AdPreparation.Ready>(controller.prepare(catalogRequest()))
        assertEquals(listOf("consent:true", "initialize"), events)
    }

    @Test
    fun unresolvedProtectedConsentMakesNoSdkCallUntilAcceptanceIsRecorded() = runTest {
        val events = mutableListOf<String>()
        val controller = controller(RecordingDriver(events), protected = true, accepted = false)

        assertEquals("policy-1", assertIs<AdPreparation.ConsentRequired>(controller.prepare(catalogRequest())).policyVersion)
        assertTrue(events.isEmpty())
        assertTrue(controller.acceptConsent())
        assertIs<AdPreparation.Ready>(controller.prepare(catalogRequest()))
        assertEquals(listOf("consent:true", "initialize"), events)
    }

    @Test
    fun withdrawalStopsRequestsDisposesOwnedAdsAndPreventsNewInitialization() = runTest {
        val events = mutableListOf<String>()
        val driver = RecordingDriver(events)
        val controller = controller(driver, protected = true, accepted = true)
        assertIs<AdPreparation.Ready>(controller.prepare(catalogRequest()))

        assertTrue(controller.withdrawConsent())
        assertEquals(listOf("consent:true", "initialize", "consent:false", "dispose"), events)
        assertEquals(
            DisabledReason.CONSENT_WITHDRAWN,
            assertIs<AdPreparation.Disabled>(controller.prepare(catalogRequest())).reason,
        )

        assertTrue(controller.acceptConsent())
        assertIs<AdPreparation.Ready>(controller.prepare(catalogRequest()))
        assertEquals(
            listOf("consent:true", "initialize", "consent:false", "dispose", "consent:true"),
            events,
        )
    }

    @Test
    fun withdrawalDisposesOwnedAdUiEvenWhenPersistenceCannotConfirmTheWrite() = runTest {
        val events = mutableListOf<String>()
        val controller = AdvertisingController(
            emptyCoordinator {},
            RecordingDriver(events),
            AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null),
        )

        assertFalse(controller.withdrawConsent())
        assertEquals(listOf("consent:false", "dispose"), events)
    }

    @Test
    fun previewAutomatedAndIosHostsNeverResolvePrivacyOrInitialize() = runTest {
        for (mode in listOf(AdHostMode.PREVIEW, AdHostMode.AUTOMATED_TEST)) {
            var privacyCalls = 0
            val coordinator = emptyCoordinator { privacyCalls += 1 }
            val controller = AdvertisingController(
                coordinator,
                RecordingDriver(mutableListOf()),
                AdConfiguration(AdPlatform.ANDROID, mode, null),
            )
            assertEquals(DisabledReason.HOST_DISABLED, assertIs<AdPreparation.Disabled>(controller.prepare(catalogRequest())).reason)
            assertEquals(0, privacyCalls)
        }
        val ios = AdvertisingController(
            emptyCoordinator {},
            IosSafeDisabledAdSdkDriver,
            AdConfiguration(AdPlatform.IOS, AdHostMode.PRODUCTION, null),
        )
        assertEquals(DisabledReason.PLATFORM_DISABLED, assertIs<AdPreparation.Disabled>(ios.prepare(catalogRequest())).reason)
    }

    @Test
    fun ineligibleUnavailableAndSdkFailureNeverBecomeReady() = runTest {
        var privacyCalls = 0
        val missingId = AdvertisingController(
            emptyCoordinator { privacyCalls += 1 },
            RecordingDriver(mutableListOf()),
            AdConfiguration(AdPlatform.ANDROID, AdHostMode.PRODUCTION, null),
        )
        assertEquals(
            DisabledReason.MISSING_AD_UNIT,
            assertIs<AdPreparation.Disabled>(missingId.prepare(catalogRequest())).reason,
        )
        assertEquals(0, privacyCalls)

        val prohibitedEvents = mutableListOf<String>()
        val prohibited = controller(RecordingDriver(prohibitedEvents), protected = true, accepted = true)
        assertEquals(
            DisabledReason.PROHIBITED_PLACEMENT,
            assertIs<AdPreparation.Disabled>(
                prohibited.prepare(AdPlacementRequest(AdSurface.OTHER, 20, offline = false, slotIndex = 4)),
            ).reason,
        )
        assertTrue(prohibitedEvents.isEmpty())

        val declinedStore = MemoryStore()
        val declinedRepository = PrivacyRepository(declinedStore).also { repository ->
            repository.write(
                PrivacyRegion(1, true, "policy-1", 1_000L, 2_000L),
                ConsentDecision.DECLINED,
            )
        }
        val declinedEvents = mutableListOf<String>()
        val declined = AdvertisingController(
            PrivacyCoordinator(
                PrivacyRegionApi { Result.failure(AssertionError()) },
                declinedRepository,
                EpochMillisClock { 1_500L },
            ),
            RecordingDriver(declinedEvents),
            AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null),
        )
        assertEquals(
            DisabledReason.CONSENT_DECLINED,
            assertIs<AdPreparation.Disabled>(declined.prepare(catalogRequest())).reason,
        )
        assertTrue(declinedEvents.isEmpty())

        val sdkFailure = controller(FailingInitializationDriver, protected = true, accepted = true)
        assertEquals(
            DisabledReason.SDK_FAILURE,
            assertIs<AdPreparation.Disabled>(sdkFailure.prepare(catalogRequest())).reason,
        )
    }

    @Test
    fun inlineSessionLimitsRetryAndDisposesDeterministically() {
        val session = InlineAdSession(maxRetries = 1)
        assertTrue(session.beginLoad())
        session.failed()
        assertTrue(session.beginLoad())
        session.failed()
        assertFalse(session.beginLoad())
        session.dispose()
        assertEquals(InlineAdState.DISPOSED, session.state)
        assertFalse(session.beginLoad())
    }

    private fun controller(driver: AdSdkDriver, protected: Boolean, accepted: Boolean): AdvertisingController {
        val repository = PrivacyRepository(MemoryStore()).also {
            val region = PrivacyRegion(1, protected, "policy-1", 1_000L, 2_000L)
            it.write(region, ConsentDecision.ACCEPTED.takeIf { accepted })
        }
        return AdvertisingController(
            PrivacyCoordinator(PrivacyRegionApi { Result.failure(AssertionError()) }, repository, EpochMillisClock { 1_500L }),
            driver,
            AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null),
        )
    }

    private fun emptyCoordinator(onFetch: () -> Unit): PrivacyCoordinator = PrivacyCoordinator(
        PrivacyRegionApi { onFetch(); Result.failure(IllegalStateException()) },
        PrivacyRepository(MemoryStore()),
        EpochMillisClock { 1_500L },
    )

    private fun catalogRequest() = AdPlacementRequest(AdSurface.CATALOG, 8, false, 4)
}

private class RecordingDriver(private val events: MutableList<String>) : AdSdkDriver {
    override val platform = AdPlatform.ANDROID
    override val supported = true
    override suspend fun setUserConsent(consent: Boolean) { events += "consent:$consent" }
    override suspend fun initialize() { events += "initialize" }
    override suspend fun stopNewRequestsAndDispose() { events += "dispose" }
}

private object FailingInitializationDriver : AdSdkDriver {
    override val platform = AdPlatform.ANDROID
    override val supported = true
    override suspend fun setUserConsent(consent: Boolean) = Unit
    override suspend fun initialize(): Unit = error("expected SDK failure")
    override suspend fun stopNewRequestsAndDispose() = Unit
}

private class MemoryStore : PrivatePrivacyStore {
    private var value: AdvertisingPrivacyState? = null
    override fun readState(): AdvertisingPrivacyState? = value
    override fun writeState(value: AdvertisingPrivacyState) { this.value = value }
    override fun clearState() { value = null }
}
