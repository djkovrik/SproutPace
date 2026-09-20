package com.sedsoftware.sproutpace.platform

import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ProductPreferences
import com.sedsoftware.sproutpace.data.ProductSettings
import com.sedsoftware.sproutpace.data.SummaryLocalTime
import com.sedsoftware.sproutpace.data.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrashReportingRuntimeTest {
    @Test
    fun configuredReleaseAppliesDefaultAndStoredPreferenceSynchronously() {
        val settings = RuntimeSettings(crashEnabled = true)
        val driver = RuntimeDriver()
        val coordinator = CrashReportingCoordinator(settings, driver, RuntimeEnvironment.RELEASE)

        assertIs<CrashReportingState.Active>(coordinator.applyStoredPreference())
        settings.setCrashReportingEnabled(false)
        assertIs<CrashReportingState.Disabled>(coordinator.applyStoredPreference())
        assertEquals(listOf(true, false), driver.collectionChanges)
    }

    @Test
    fun retainedObserverCanApplyChangedValueWithoutRewritingPreference() {
        val settings = RuntimeSettings(crashEnabled = true)
        val driver = RuntimeDriver()
        val coordinator = CrashReportingCoordinator(settings, driver, RuntimeEnvironment.RELEASE)

        val state = coordinator.applyPreference(false)

        assertIs<CrashReportingState.Disabled>(state)
        assertEquals(true, settings.crashReportingEnabled.value)
        assertEquals(listOf(false), driver.collectionChanges)
    }

    @Test
    fun nonReleaseAlwaysDisablesWhilePreservingUserChoice() {
        val settings = RuntimeSettings(crashEnabled = true)
        val driver = RuntimeDriver(configured = false)
        val coordinator = CrashReportingCoordinator(settings, driver, RuntimeEnvironment.DEBUG)

        val state = coordinator.applyStoredPreference()

        assertIs<CrashReportingState.Disabled>(state)
        assertEquals(DisableReason.NON_RELEASE_ENVIRONMENT, state.reason)
        assertEquals(true, state.userPreference)
        assertEquals(listOf(false), driver.collectionChanges)
    }

    @Test
    fun unavailableOrFailedDriverNeverClaimsCollectionIsActive() {
        val settings = RuntimeSettings(crashEnabled = true)
        assertIs<CrashReportingState.Unavailable>(
            CrashReportingCoordinator(settings, RuntimeDriver(configured = false), RuntimeEnvironment.RELEASE)
                .applyStoredPreference(),
        )
        assertIs<CrashReportingState.Unavailable>(
            CrashReportingCoordinator(
                settings,
                RuntimeDriver(failCollectionChange = true),
                RuntimeEnvironment.RELEASE,
            ).applyStoredPreference(),
        )
    }
}

private class RuntimeDriver(
    private val configured: Boolean = true,
    private val failCollectionChange: Boolean = false,
) : CrashReportingDriver {
    val collectionChanges = mutableListOf<Boolean>()
    override val isConfigured: Boolean get() = configured

    override fun setCollectionEnabled(enabled: Boolean): Result<Unit> = runCatching {
        collectionChanges += enabled
        check(!failCollectionChange)
    }

    override fun recordReviewedTechnicalFailure(failure: Throwable): Result<Unit> = Result.success(Unit)
}

private class RuntimeSettings(crashEnabled: Boolean) : ProductSettings {
    private val mutableTheme = MutableStateFlow(ThemePreference.SYSTEM)
    private val mutableCrash = MutableStateFlow(crashEnabled)
    private val mutableNotifications = MutableStateFlow(true)
    private val mutableSummaryTime = MutableStateFlow(SummaryLocalTime.DEFAULT)
    private val mutableSoakReminders = MutableStateFlow(true)
    private val mutablePrivacy = MutableStateFlow<AdvertisingPrivacyState?>(null)

    override val theme: StateFlow<ThemePreference> = mutableTheme
    override val crashReportingEnabled: StateFlow<Boolean> = mutableCrash
    override val notificationsEnabled: StateFlow<Boolean> = mutableNotifications
    override val summaryTime: StateFlow<SummaryLocalTime> = mutableSummaryTime
    override val soakRemindersEnabled: StateFlow<Boolean> = mutableSoakReminders
    override val privacyState: StateFlow<AdvertisingPrivacyState?> = mutablePrivacy

    override fun setTheme(value: ThemePreference) { mutableTheme.value = value }
    override fun setCrashReportingEnabled(value: Boolean) { mutableCrash.value = value }
    override fun setNotificationsEnabled(value: Boolean) { mutableNotifications.value = value }
    override fun setSummaryTime(value: SummaryLocalTime) { mutableSummaryTime.value = value }
    override fun setSoakRemindersEnabled(value: Boolean) { mutableSoakReminders.value = value }
    override fun setPrivacyState(value: AdvertisingPrivacyState?) { mutablePrivacy.value = value }
    override fun snapshot() = ProductPreferences(
        theme.value,
        crashReportingEnabled.value,
        notificationsEnabled.value,
        privacyState.value,
    )
    override fun replace(value: ProductPreferences) {
        setTheme(value.theme)
        setCrashReportingEnabled(value.crashReportingEnabled)
        setNotificationsEnabled(value.notificationsEnabled)
        setSummaryTime(value.summaryTime)
        setSoakRemindersEnabled(value.soakRemindersEnabled)
        setPrivacyState(value.privacyState)
    }
    override fun clearProductState() = replace(ProductPreferences())
}
