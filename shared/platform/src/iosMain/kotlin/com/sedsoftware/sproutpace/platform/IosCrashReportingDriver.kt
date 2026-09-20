package com.sedsoftware.sproutpace.platform

/**
 * Minimal Swift-facing bridge. It deliberately cannot attach keys, user identifiers, breadcrumbs,
 * messages, files, or product content.
 */
interface IosCrashReportingBridge {
    val isConfigured: Boolean

    fun setCollectionEnabled(enabled: Boolean): Boolean

    fun recordReviewedTechnicalFailure(typeName: String): Boolean
}

class IosCrashReportingDriver(
    private val bridge: IosCrashReportingBridge,
) : CrashReportingDriver {
    override val isConfigured: Boolean get() = bridge.isConfigured

    override fun setCollectionEnabled(enabled: Boolean): Result<Unit> = bridgeResult {
        bridge.setCollectionEnabled(enabled)
    }

    override fun recordReviewedTechnicalFailure(failure: Throwable): Result<Unit> = bridgeResult {
        bridge.recordReviewedTechnicalFailure(failure::class.simpleName ?: TECHNICAL_FAILURE)
    }

    private inline fun bridgeResult(block: () -> Boolean): Result<Unit> = runCatching {
        check(block()) { BRIDGE_FAILURE }
    }

    private companion object {
        const val TECHNICAL_FAILURE = "TechnicalFailure"
        const val BRIDGE_FAILURE = "crash_reporting_bridge_failure"
    }
}
