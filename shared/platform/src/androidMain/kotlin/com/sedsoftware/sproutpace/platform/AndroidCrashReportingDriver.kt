package com.sedsoftware.sproutpace.platform

/**
 * Uses reflection so the open-source/default build remains Firebase-free. When the optional
 * Crashlytics SDK is linked, only the minimal collection/technical-nonfatal API is exposed.
 */
class AndroidCrashReportingDriver : CrashReportingDriver {
    private val instance: Any? = runCatching {
        val type = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
        type.getMethod("getInstance").invoke(null)
    }.getOrNull()

    override val isConfigured: Boolean get() = instance != null

    override fun setCollectionEnabled(enabled: Boolean): Result<Unit> = invoke(
        methodName = "setCrashlyticsCollectionEnabled",
        parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
        arguments = arrayOf(enabled),
    )

    override fun recordReviewedTechnicalFailure(failure: Throwable): Result<Unit> = invoke(
        methodName = "recordException",
        parameterTypes = arrayOf(Throwable::class.java),
        arguments = arrayOf(failure),
    )

    private fun invoke(
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<Any>,
    ): Result<Unit> = runCatching {
        val target = requireNotNull(instance) { "crash_reporting_unavailable" }
        target.javaClass.getMethod(methodName, *parameterTypes).invoke(target, *arguments)
    }
}
