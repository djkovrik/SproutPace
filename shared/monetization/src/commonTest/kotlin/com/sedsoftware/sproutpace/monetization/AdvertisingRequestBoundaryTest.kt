package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_ENDPOINT
import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_PAYLOAD_VERSION
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ConsentChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvertisingRequestBoundaryTest {
    @Test fun loadBoundaryRechecksTheDeadlineEvenWithoutAnyStateEmission() {
        val runtime = Runtime()
        assertTrue(runtime.canRequestAd(ID))
        runtime.now = 100
        assertFalse(runtime.canRequestAd(ID), "The exact expiry instant is already ineligible")
        runtime.now = 101
        assertFalse(runtime.canRequestAd(ID))
    }

    @Test fun missingDeclinedWithdrawnOrOfflineStateNeverPermitsAnSdkRequest() {
        val runtime = Runtime()
        for (choice in ConsentChoice.entries.filter { it != ConsentChoice.ACCEPTED }) {
            runtime.privacyState.value = payload().copy(choice = choice)
            assertFalse(runtime.canRequestAd(ID), choice.name)
        }
        runtime.privacyState.value = null
        assertFalse(runtime.canRequestAd(ID))
        runtime.privacyState.value = payload()
        runtime.networkAvailable.value = false
        assertFalse(runtime.canRequestAd(ID))
        runtime.networkAvailable.value = true
        assertFalse(runtime.canRequestAd("placeholder"))
        assertTrue(runtime.canRequestAd(ID))
    }

    @Test fun freshNonProtectedStateNeedsNoInventedConsentAndDisposedVisitCannotLoadAgain() {
        val runtime = Runtime()
        runtime.privacyState.value = payload().copy(consentRequired = false, choice = ConsentChoice.UNDECIDED)
        assertTrue(runtime.canRequestAd(ID))
        val session = InlineAdSession(maxRetries = 0)
        assertTrue(session.beginLoad())
        session.loaded()
        session.dispose()
        assertFalse(session.beginLoad(), "Lazy recycling must reuse the disposed visit session")
    }

    private class Runtime : AdvertisingRuntime {
        var now = 1L
        override val privacyState = MutableStateFlow<AdvertisingPrivacyState?>(payload())
        override val networkAvailable = MutableStateFlow(true)
        override fun nowEpochMillis() = now
        override suspend fun prepare(request: AdPlacementRequest) = AdPreparation.Disabled(DisabledReason.HOST_DISABLED)
        override suspend fun acceptConsent() = false
        override suspend fun declineConsent() = false
        override suspend fun withdrawConsent() = false
        override fun close() = Unit
    }

    private companion object {
        const val ID = AdConfiguration.ANDROID_DEMO_BANNER_ID
        fun payload() = AdvertisingPrivacyState(
            schemaVersion = 1, policyVersion = "policy-1", expiresAtEpochMillis = 100,
            choice = ConsentChoice.ACCEPTED, payloadVersion = ADVERTISING_PRIVACY_PAYLOAD_VERSION,
            endpoint = ADVERTISING_PRIVACY_ENDPOINT, consentRequired = true, fetchedAtEpochMillis = 0,
        )
    }
}
