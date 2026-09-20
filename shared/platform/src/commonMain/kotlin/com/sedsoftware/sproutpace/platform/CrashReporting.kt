package com.sedsoftware.sproutpace.platform

import com.sedsoftware.sproutpace.data.ProductSettings

enum class RuntimeEnvironment { RELEASE, DEBUG, PREVIEW, TEST }

sealed interface CrashReportingState {
    data class Active(val userPreference: Boolean) : CrashReportingState
    data class Disabled(val userPreference: Boolean, val reason: DisableReason) : CrashReportingState
    data class Unavailable(val userPreference: Boolean) : CrashReportingState
}

enum class DisableReason { USER_CHOICE, NON_RELEASE_ENVIRONMENT }

/** Privacy-minimal bridge: deliberately has no log, breadcrumb, key, user-id or attachment API. */
interface CrashReportingDriver {
    val isConfigured: Boolean
    fun setCollectionEnabled(enabled: Boolean): Result<Unit>
    fun recordReviewedTechnicalFailure(failure: Throwable): Result<Unit>
}

class CrashReportingCoordinator(
    private val settings: ProductSettings,
    private val driver: CrashReportingDriver,
    private val environment: RuntimeEnvironment,
) {
    fun applyStoredPreference(): CrashReportingState = apply(settings.crashReportingEnabled.value)

    /** Applies an already-persisted value without writing it again. Used by retained runtime observers. */
    fun applyPreference(preference: Boolean): CrashReportingState = apply(preference)

    fun updatePreference(enabled: Boolean): CrashReportingState {
        settings.setCrashReportingEnabled(enabled)
        return apply(enabled)
    }

    fun recordReviewedTechnicalFailure(failure: Throwable): Result<Unit> {
        val state = applyStoredPreference()
        if (state !is CrashReportingState.Active) return Result.success(Unit)
        return driver.recordReviewedTechnicalFailure(failure)
    }

    private fun apply(preference: Boolean): CrashReportingState {
        return when {
            environment != RuntimeEnvironment.RELEASE -> {
                driver.setCollectionEnabled(false)
                CrashReportingState.Disabled(preference, DisableReason.NON_RELEASE_ENVIRONMENT)
            }
            !driver.isConfigured -> CrashReportingState.Unavailable(preference)
            !preference -> driver.setCollectionEnabled(false).fold(
                onSuccess = { CrashReportingState.Disabled(false, DisableReason.USER_CHOICE) },
                onFailure = { CrashReportingState.Unavailable(false) },
            )
            else -> driver.setCollectionEnabled(true).fold(
                onSuccess = { CrashReportingState.Active(true) },
                onFailure = { CrashReportingState.Unavailable(true) },
            )
        }
    }
}
