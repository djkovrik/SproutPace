package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_ENDPOINT
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrivacyCoordinatorTest {
    private val now = 1_000_000L

    @Test
    fun freshCacheAvoidsNetworkAndProtectedRegionRequiresChoice() = runTest {
        val store = MemoryPrivacyStore()
        val repository = PrivacyRepository(store)
        repository.write(region(required = true), null)
        var calls = 0
        val coordinator = PrivacyCoordinator(
            PrivacyRegionApi { calls += 1; Result.failure(AssertionError("network must not run")) },
            repository,
            EpochMillisClock { now },
        )

        assertIs<PrivacyEligibility.ConsentRequired>(coordinator.resolve())
        assertEquals(0, calls)
    }

    @Test
    fun acceptedChoiceRestoresOnlyForFreshMatchingPolicy() = runTest {
        val store = MemoryPrivacyStore()
        val repository = PrivacyRepository(store)
        repository.write(region(required = true), ConsentDecision.ACCEPTED)
        val coordinator = coordinator(repository) { Result.failure(AssertionError()) }

        assertIs<PrivacyEligibility.Eligible>(coordinator.resolve())

        repository.write(region(required = true, policy = "policy-2"), null)
        assertIs<PrivacyEligibility.ConsentRequired>(coordinator.resolve())
    }

    @Test
    fun changedPolicyFromFreshEndpointInvalidatesPreviouslyAcceptedChoice() = runTest {
        val store = MemoryPrivacyStore()
        val repository = PrivacyRepository(store)
        repository.write(
            region(required = true, policy = "policy-1", fetchedAt = 1L, expiresAt = now - 1L),
            ConsentDecision.ACCEPTED,
        )
        val coordinator = coordinator(repository) {
            Result.success(region(required = true, policy = "policy-2"))
        }

        assertEquals(
            "policy-2",
            assertIs<PrivacyEligibility.ConsentRequired>(coordinator.resolve()).policyVersion,
        )
        assertEquals(com.sedsoftware.sproutpace.data.ConsentChoice.UNDECIDED, store.value?.choice)
    }

    @Test
    fun declineAndWithdrawalDisableAdsImmediately() = runTest {
        val repository = PrivacyRepository(MemoryPrivacyStore()).also { it.write(region(required = true), null) }
        val coordinator = coordinator(repository) { Result.failure(AssertionError()) }

        assertTrue(coordinator.recordDecision(ConsentDecision.DECLINED))
        assertEquals(
            DisabledReason.CONSENT_DECLINED,
            assertIs<PrivacyEligibility.Disabled>(coordinator.resolve()).reason,
        )
        assertTrue(coordinator.recordDecision(ConsentDecision.ACCEPTED))
        assertTrue(coordinator.withdraw())
        assertEquals(
            DisabledReason.CONSENT_WITHDRAWN,
            assertIs<PrivacyEligibility.Disabled>(coordinator.resolve()).reason,
        )
    }

    @Test
    fun staleAndNetworkFailureFailClosedWithoutLosingPolicyBoundChoice() = runTest {
        val store = MemoryPrivacyStore()
        val repository = PrivacyRepository(store)
        repository.write(
            region(required = true, fetchedAt = 1L, expiresAt = now - 1L),
            ConsentDecision.ACCEPTED,
        )
        var response: Result<PrivacyRegion> = Result.failure(IllegalStateException("offline"))
        val coordinator = coordinator(repository) { response }
        assertEquals(
            DisabledReason.REGION_UNAVAILABLE,
            assertIs<PrivacyEligibility.Disabled>(coordinator.resolve()).reason,
        )

        response = Result.success(region(required = true))
        assertIs<PrivacyEligibility.Eligible>(coordinator.resolve())
    }

    @Test
    fun nonProtectedRegionNeedsNoPopupAndIgnoresObsoleteChoice() = runTest {
        val repository = PrivacyRepository(MemoryPrivacyStore()).also {
            it.write(region(required = false), ConsentDecision.DECLINED)
        }
        val eligible = assertIs<PrivacyEligibility.Eligible>(coordinator(repository) { Result.failure(AssertionError()) }.resolve())
        assertFalse(eligible.sdkConsent)
    }

    @Test
    fun privateSettingsFailuresFailClosedWithoutCrashingCoreFlow() = runTest {
        val repository = PrivacyRepository(object : PrivatePrivacyStore {
            override fun readState(): AdvertisingPrivacyState? = null
            override fun writeState(value: AdvertisingPrivacyState) = error("disk unavailable")
            override fun clearState() = Unit
        })
        val coordinator = coordinator(repository) { Result.success(region(required = true)) }

        assertEquals(
            DisabledReason.REGION_UNAVAILABLE,
            assertIs<PrivacyEligibility.Disabled>(coordinator.resolve()).reason,
        )
        assertFalse(coordinator.recordDecision(ConsentDecision.ACCEPTED))
    }

    @Test
    fun mismatchedEndpointVersionFutureFetchAndOverlongCacheAllFailClosed() = runTest {
        val invalid = listOf(
            state(endpoint = "https://example.invalid/privacy"),
            state(payloadVersion = 2),
            state(fetchedAt = now + 1L, expiresAt = now + 60_000L),
            state(fetchedAt = now, expiresAt = now + PRIVACY_CACHE_MAX_AGE_MILLIS + 1L),
        )

        invalid.forEach { value ->
            val coordinator = coordinator(PrivacyRepository(MemoryPrivacyStore(value))) {
                Result.failure(IllegalStateException("offline"))
            }
            assertEquals(
                DisabledReason.REGION_UNAVAILABLE,
                assertIs<PrivacyEligibility.Disabled>(coordinator.resolve()).reason,
            )
        }
    }

    private fun coordinator(repository: PrivacyRepository, fetch: suspend () -> Result<PrivacyRegion>) =
        PrivacyCoordinator(PrivacyRegionApi { fetch() }, repository, EpochMillisClock { now })

    private fun region(
        required: Boolean,
        policy: String = "policy-1",
        fetchedAt: Long = now,
        expiresAt: Long = now + 60_000L,
    ) = PrivacyRegion(1, required, policy, fetchedAt, expiresAt)

    private fun state(
        endpoint: String = ADVERTISING_PRIVACY_ENDPOINT,
        payloadVersion: Int = 1,
        fetchedAt: Long = now,
        expiresAt: Long = now + 60_000L,
    ) = AdvertisingPrivacyState(
        policyVersion = "policy-1",
        expiresAtEpochMillis = expiresAt,
        choice = com.sedsoftware.sproutpace.data.ConsentChoice.ACCEPTED,
        payloadVersion = payloadVersion,
        endpoint = endpoint,
        consentRequired = true,
        fetchedAtEpochMillis = fetchedAt,
    )
}

private class MemoryPrivacyStore(initial: AdvertisingPrivacyState? = null) : PrivatePrivacyStore {
    var value: AdvertisingPrivacyState? = initial
        private set
    override fun readState(): AdvertisingPrivacyState? = value
    override fun writeState(value: AdvertisingPrivacyState) { this.value = value }
    override fun clearState() { value = null }
}
