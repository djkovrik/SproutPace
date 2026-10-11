package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class PrivacyCancellationTest {
    @Test
    fun transportCancellationEscapesInsteadOfBecomingNetworkFailure() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("screen left") })
        try {
            assertFailsWith<CancellationException> {
                KtorPrivacyRegionApi(client, EpochMillisClock { 100L }).fetch()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun thrownAndWrappedCancellationLeavePrivacyUnresolvedAndCanRetry() = runTest {
        for (wrapped in listOf(false, true)) {
            val store = CancellationPrivacyStore()
            var cancelled = true
            val coordinator = PrivacyCoordinator(PrivacyRegionApi {
                if (cancelled) {
                    val failure = CancellationException("owner stopped")
                    if (wrapped) Result.failure(failure) else throw failure
                } else Result.success(PrivacyRegion(1, true, "policy-1", 100L, 200L))
            }, PrivacyRepository(store), EpochMillisClock { 100L })
            assertFailsWith<CancellationException> { coordinator.resolve() }
            assertNull(store.value)
            cancelled = false
            assertIs<PrivacyEligibility.ConsentRequired>(coordinator.resolve())
        }
    }

    @Test
    fun cancelledSdkInitializationCanRetryAndConsentStillPrecedesInitialization() = runTest {
        val events = mutableListOf<String>()
        var cancel = true
        val driver = object : AdSdkDriver {
            override val platform = AdPlatform.ANDROID
            override val supported = true
            override suspend fun setUserConsent(consent: Boolean) { events += "consent:$consent" }
            override suspend fun initialize() {
                events += "initialize"
                if (cancel) throw CancellationException("screen stopped")
            }
            override suspend fun stopNewRequestsAndDispose() = Unit
        }
        val repository = PrivacyRepository(CancellationPrivacyStore())
        repository.write(PrivacyRegion(1, true, "policy-1", 100L, 200L), ConsentDecision.ACCEPTED)
        val controller = AdvertisingController(
            PrivacyCoordinator(PrivacyRegionApi { error("fresh cache") }, repository, EpochMillisClock { 100L }),
            driver, AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null),
        )
        val request = AdPlacementRequest(AdSurface.CATALOG, 8, false, 4)
        assertFailsWith<CancellationException> { controller.prepare(request) }
        cancel = false
        assertIs<AdPreparation.Ready>(controller.prepare(request))
        assertEquals(listOf("consent:true", "initialize", "consent:true", "initialize"), events)
    }
}

private class CancellationPrivacyStore : PrivatePrivacyStore {
    var value: AdvertisingPrivacyState? = null
    override fun readState() = value
    override fun writeState(value: AdvertisingPrivacyState) { this.value = value }
    override fun clearState() { value = null }
}
