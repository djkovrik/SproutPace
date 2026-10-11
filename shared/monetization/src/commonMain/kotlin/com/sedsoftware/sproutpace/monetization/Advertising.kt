package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AdSurface { CATALOG, HISTORY, OTHER }
enum class AdHostMode { PRODUCTION, DEBUG, PREVIEW, AUTOMATED_TEST }
enum class AdPlatform { ANDROID, IOS }

data class AdConfiguration(
    val platform: AdPlatform,
    val hostMode: AdHostMode,
    val productionAdUnitId: String?,
) {
    val effectiveAdUnitId: String?
        get() = when (hostMode) {
            AdHostMode.DEBUG -> ANDROID_DEMO_BANNER_ID.takeIf { platform == AdPlatform.ANDROID }
            AdHostMode.PRODUCTION -> productionAdUnitId?.trim()?.takeIf(::isProductionAdUnitId)
            AdHostMode.PREVIEW, AdHostMode.AUTOMATED_TEST -> null
        }

    companion object {
        const val ANDROID_DEMO_BANNER_ID = "demo-banner-yandex"
        private val PRODUCTION_ID = Regex("R-M-[0-9]+-[0-9]+")
        fun isProductionAdUnitId(value: String): Boolean = PRODUCTION_ID.matches(value) &&
            value != ANDROID_DEMO_BANNER_ID
    }
}

data class AdPlacementRequest(
    val surface: AdSurface,
    val realItemCount: Int,
    val offline: Boolean,
    val slotIndex: Int,
)

object InlineAdPlacementPolicy {
    const val CATALOG_INSERTION_INDEX = 4
    const val HISTORY_INSERTION_INDEX = 5

    fun permits(request: AdPlacementRequest): Boolean {
        if (request.offline) return false
        val expectedIndex = when (request.surface) {
            AdSurface.CATALOG -> CATALOG_INSERTION_INDEX
            AdSurface.HISTORY -> HISTORY_INSERTION_INDEX
            AdSurface.OTHER -> return false
        }
        return request.slotIndex == expectedIndex && request.realItemCount > expectedIndex
    }
}

interface AdSdkDriver {
    val platform: AdPlatform
    val supported: Boolean
    suspend fun setUserConsent(consent: Boolean)
    suspend fun initialize()
    suspend fun stopNewRequestsAndDispose()
}

sealed interface AdPreparation {
    data class Ready(val adUnitId: String) : AdPreparation
    data class ConsentRequired(val policyVersion: String) : AdPreparation
    data class Disabled(val reason: DisabledReason) : AdPreparation
}

/** Narrow production boundary consumed by the UI; platform SDK types never cross common code. */
interface AdvertisingRuntime {
    val privacyState: StateFlow<AdvertisingPrivacyState?>
    /** Host connectivity is independent of the local catalog's offline-capable content. */
    val networkAvailable: StateFlow<Boolean>
    suspend fun prepare(request: AdPlacementRequest): AdPreparation
    suspend fun acceptConsent(): Boolean
    suspend fun declineConsent(): Boolean
    suspend fun withdrawConsent(): Boolean
    fun close()
}

class AdvertisingController(
    private val privacy: PrivacyCoordinator,
    private val sdk: AdSdkDriver,
    private val configuration: AdConfiguration,
) {
    private val mutex = Mutex()
    private var initialized = false
    private var activeSdkConsent: Boolean? = null

    suspend fun prepare(request: AdPlacementRequest): AdPreparation = mutex.withLock {
        if (!sdk.supported || configuration.platform != AdPlatform.ANDROID) {
            return@withLock AdPreparation.Disabled(DisabledReason.PLATFORM_DISABLED)
        }
        if (configuration.hostMode == AdHostMode.PREVIEW || configuration.hostMode == AdHostMode.AUTOMATED_TEST) {
            return@withLock AdPreparation.Disabled(DisabledReason.HOST_DISABLED)
        }
        val adUnitId = configuration.effectiveAdUnitId
            ?: return@withLock AdPreparation.Disabled(DisabledReason.MISSING_AD_UNIT)
        if (!InlineAdPlacementPolicy.permits(request)) {
            return@withLock AdPreparation.Disabled(DisabledReason.PROHIBITED_PLACEMENT)
        }
        when (val eligibility = privacy.resolve()) {
            is PrivacyEligibility.ConsentRequired -> AdPreparation.ConsentRequired(eligibility.policyVersion)
            is PrivacyEligibility.Disabled -> AdPreparation.Disabled(eligibility.reason)
            is PrivacyEligibility.Eligible -> cancellableResult {
                if (!initialized) {
                    sdk.setUserConsent(eligibility.sdkConsent)
                    sdk.initialize()
                    initialized = true
                    activeSdkConsent = eligibility.sdkConsent
                } else if (activeSdkConsent != eligibility.sdkConsent) {
                    sdk.setUserConsent(eligibility.sdkConsent)
                    activeSdkConsent = eligibility.sdkConsent
                }
                AdPreparation.Ready(adUnitId)
            }.getOrElse {
                AdPreparation.Disabled(DisabledReason.SDK_FAILURE)
            }
        }
    }

    suspend fun acceptConsent(): Boolean = mutex.withLock {
        privacy.recordDecision(ConsentDecision.ACCEPTED)
    }

    suspend fun declineConsent(): Boolean = mutex.withLock {
        val recorded = privacy.recordDecision(ConsentDecision.DECLINED)
        stopImmediately()
        recorded
    }

    suspend fun withdrawConsent(): Boolean = mutex.withLock {
        val recorded = privacy.withdraw()
        stopImmediately()
        recorded
    }

    private suspend fun stopImmediately() {
        cancellableResult { sdk.setUserConsent(false) }
        activeSdkConsent = false
        cancellableResult { sdk.stopNewRequestsAndDispose() }
    }
}

enum class InlineAdState { IDLE, LOADING, LOADED, FAILED, DISPOSED }

class InlineAdSession(private val maxRetries: Int = 1) {
    var state: InlineAdState = InlineAdState.IDLE
        private set
    private var failures = 0

    fun beginLoad(): Boolean {
        if (state == InlineAdState.DISPOSED || state == InlineAdState.LOADING || state == InlineAdState.LOADED) return false
        if (failures > maxRetries) return false
        state = InlineAdState.LOADING
        return true
    }

    fun loaded() {
        if (state == InlineAdState.LOADING) state = InlineAdState.LOADED
    }

    fun failed() {
        if (state == InlineAdState.LOADING) {
            failures += 1
            state = InlineAdState.FAILED
        }
    }

    fun dispose() {
        state = InlineAdState.DISPOSED
    }
}

object IosSafeDisabledAdSdkDriver : AdSdkDriver {
    override val platform = AdPlatform.IOS
    override val supported = false
    override suspend fun setUserConsent(consent: Boolean) = Unit
    override suspend fun initialize() = Unit
    override suspend fun stopNewRequestsAndDispose() = Unit
}
