package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_ENDPOINT
import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_PAYLOAD_VERSION
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.sedsoftware.sproutpace.data.ProductSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ConsentDecision { ACCEPTED, DECLINED, WITHDRAWN }

interface PrivatePrivacyStore {
    fun readState(): AdvertisingPrivacyState?
    fun writeState(value: AdvertisingPrivacyState)
    fun clearState()
}

/** The only production persistence adapter for region metadata and the policy-bound user choice. */
class ProductSettingsPrivacyStore(private val settings: ProductSettings) : PrivatePrivacyStore {
    override fun readState(): AdvertisingPrivacyState? = settings.privacyState.value
    override fun writeState(value: AdvertisingPrivacyState) = settings.setPrivacyState(value)
    override fun clearState() = settings.setPrivacyState(null)
}

class PrivacyRepository(
    private val store: PrivatePrivacyStore,
) {
    fun read(nowEpochMillis: Long): AdvertisingPrivacyState? = runCatching { store.readState() }
        .getOrNull()?.takeIf { it.isFreshAndValid(nowEpochMillis) }

    fun readForRefresh(nowEpochMillis: Long): AdvertisingPrivacyState? = runCatching { store.readState() }
        .getOrNull()?.takeIf { it.isContractValid(nowEpochMillis) }

    fun write(region: PrivacyRegion, decision: ConsentDecision?) {
        store.writeState(
            AdvertisingPrivacyState(
                payloadVersion = ADVERTISING_PRIVACY_PAYLOAD_VERSION,
                endpoint = ADVERTISING_PRIVACY_ENDPOINT,
                schemaVersion = region.schemaVersion,
                consentRequired = region.consentRequired,
                policyVersion = region.policyVersion,
                fetchedAtEpochMillis = region.fetchedAtEpochMillis,
                expiresAtEpochMillis = region.expiresAtEpochMillis,
                choice = decision?.takeIf { region.consentRequired }?.toStoredChoice() ?: ConsentChoice.UNDECIDED,
            ),
        )
    }

    fun clear() = store.clearState()

    private fun AdvertisingPrivacyState.isFreshAndValid(now: Long): Boolean {
        if (!isContractValid(now)) return false
        return expiresAtEpochMillis > now
    }

    private fun AdvertisingPrivacyState.isContractValid(now: Long): Boolean {
        val lifetime = expiresAtEpochMillis - fetchedAtEpochMillis
        return payloadVersion == ADVERTISING_PRIVACY_PAYLOAD_VERSION &&
            endpoint == PRIVACY_REGION_ENDPOINT &&
            schemaVersion == PRIVACY_REGION_SCHEMA_VERSION &&
            POLICY_VERSION.matches(policyVersion) &&
            fetchedAtEpochMillis in 0..now &&
            lifetime in 1..PRIVACY_CACHE_MAX_AGE_MILLIS
    }

    private companion object {
        val POLICY_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

sealed interface PrivacyEligibility {
    data class Eligible(val sdkConsent: Boolean, val policyVersion: String) : PrivacyEligibility
    data class ConsentRequired(val policyVersion: String) : PrivacyEligibility
    data class Disabled(val reason: DisabledReason) : PrivacyEligibility
}

enum class DisabledReason {
    REGION_UNAVAILABLE,
    CONSENT_DECLINED,
    CONSENT_WITHDRAWN,
    PLATFORM_DISABLED,
    HOST_DISABLED,
    MISSING_AD_UNIT,
    PROHIBITED_PLACEMENT,
    SDK_FAILURE,
}

class PrivacyCoordinator(
    private val api: PrivacyRegionApi,
    private val repository: PrivacyRepository,
    private val clock: EpochMillisClock,
) {
    private val mutex = Mutex()

    suspend fun resolve(): PrivacyEligibility = mutex.withLock {
        runCatching { resolveLocked() }.getOrElse {
            PrivacyEligibility.Disabled(DisabledReason.REGION_UNAVAILABLE)
        }
    }

    private suspend fun resolveLocked(): PrivacyEligibility {
        val now = clock.now()
        val payload = repository.read(now) ?: refresh(now) ?: return PrivacyEligibility.Disabled(
            DisabledReason.REGION_UNAVAILABLE,
        )
        return payload.toEligibility()
    }

    fun recordDecision(decision: ConsentDecision): Boolean = runCatching {
        val now = clock.now()
        val payload = repository.read(now) ?: return@runCatching false
        if (!payload.consentRequired) return@runCatching false
        repository.write(
            payload.toRegion(),
            decision,
        )
        true
    }.getOrDefault(false)

    fun withdraw(): Boolean = recordDecision(ConsentDecision.WITHDRAWN)

    private suspend fun refresh(now: Long): AdvertisingPrivacyState? {
        val previous = repository.readForRefresh(now)
        val region = api.fetch().getOrElse {
            return null
        }
        val preservedConsent = previous?.choice?.toDecision()?.takeIf {
            region.consentRequired && previous.policyVersion == region.policyVersion
        }
        repository.write(region, preservedConsent)
        return repository.read(now)
    }

    private fun AdvertisingPrivacyState.toEligibility(): PrivacyEligibility {
        if (!consentRequired) {
            return PrivacyEligibility.Eligible(sdkConsent = false, policyVersion = policyVersion)
        }
        return when (choice.toDecision()) {
            ConsentDecision.ACCEPTED -> PrivacyEligibility.Eligible(true, policyVersion)
            ConsentDecision.DECLINED -> PrivacyEligibility.Disabled(DisabledReason.CONSENT_DECLINED)
            ConsentDecision.WITHDRAWN -> PrivacyEligibility.Disabled(DisabledReason.CONSENT_WITHDRAWN)
            null -> PrivacyEligibility.ConsentRequired(policyVersion)
        }
    }

    private fun AdvertisingPrivacyState.toRegion() = PrivacyRegion(
        schemaVersion,
        consentRequired,
        policyVersion,
        fetchedAtEpochMillis,
        expiresAtEpochMillis,
    )
}

private fun ConsentDecision.toStoredChoice(): ConsentChoice = when (this) {
    ConsentDecision.ACCEPTED -> ConsentChoice.ACCEPTED
    ConsentDecision.DECLINED -> ConsentChoice.DECLINED
    ConsentDecision.WITHDRAWN -> ConsentChoice.WITHDRAWN
}

private fun ConsentChoice.toDecision(): ConsentDecision? = when (this) {
    ConsentChoice.UNDECIDED -> null
    ConsentChoice.ACCEPTED -> ConsentDecision.ACCEPTED
    ConsentChoice.DECLINED -> ConsentDecision.DECLINED
    ConsentChoice.WITHDRAWN -> ConsentDecision.WITHDRAWN
}
