package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class ConsentChoice { UNDECIDED, ACCEPTED, DECLINED, WITHDRAWN }

const val ADVERTISING_PRIVACY_PAYLOAD_VERSION = 1
const val ADVERTISING_PRIVACY_ENDPOINT = "https://168.222.252.178/v1/privacy/region"
const val ADVERTISING_PRIVACY_SCHEMA_VERSION = 1
const val ADVERTISING_PRIVACY_MAX_AGE_MILLIS = 72L * 60L * 60L * 1_000L

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60

@Serializable
data class SummaryLocalTime(val hour: Int = 9, val minute: Int = 0) {
    init {
        require(hour in 0 until HOURS_PER_DAY)
        require(minute in 0 until MINUTES_PER_HOUR)
    }

    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val DEFAULT = SummaryLocalTime()

        fun parseOrDefault(value: String?): SummaryLocalTime {
            val parts = value?.split(':') ?: return DEFAULT
            if (parts.size != 2) return DEFAULT
            return runCatching { SummaryLocalTime(parts[0].toInt(), parts[1].toInt()) }.getOrDefault(DEFAULT)
        }
    }
}

@Serializable
data class AdvertisingPrivacyState(
    val schemaVersion: Int = ADVERTISING_PRIVACY_SCHEMA_VERSION,
    val policyVersion: String,
    val expiresAtEpochMillis: Long,
    val choice: ConsentChoice,
    val payloadVersion: Int = ADVERTISING_PRIVACY_PAYLOAD_VERSION,
    val endpoint: String = ADVERTISING_PRIVACY_ENDPOINT,
    val consentRequired: Boolean = true,
    val fetchedAtEpochMillis: Long = 0L,
)

interface ProductSettings {
    val theme: StateFlow<ThemePreference>
    val crashReportingEnabled: StateFlow<Boolean>
    /** Legacy name retained as the daily-summary preference contract. */
    val notificationsEnabled: StateFlow<Boolean>
    val summaryTime: StateFlow<SummaryLocalTime>
    val soakRemindersEnabled: StateFlow<Boolean>
    val privacyState: StateFlow<AdvertisingPrivacyState?>
    fun setTheme(value: ThemePreference)
    fun setCrashReportingEnabled(value: Boolean)
    fun setNotificationsEnabled(value: Boolean)
    fun setSummaryTime(value: SummaryLocalTime)
    fun setSoakRemindersEnabled(value: Boolean)
    fun setPrivacyState(value: AdvertisingPrivacyState?)
    fun snapshot(): ProductPreferences
    fun replace(value: ProductPreferences)
    fun clearProductState()
}

data class ProductPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val crashReportingEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val privacyState: AdvertisingPrivacyState? = null,
    val summaryTime: SummaryLocalTime = SummaryLocalTime.DEFAULT,
    val soakRemindersEnabled: Boolean = true,
)

class MultiplatformProductSettings(
    private val settings: Settings,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = false },
) : ProductSettings {
    private object Keys {
        const val THEME = "theme.v1"
        const val LEGACY_DARK = "dark_mode"
        const val CRASH = "crash_reporting_enabled.v1"
        const val NOTIFICATIONS = "notifications_enabled.v1"
        const val SUMMARY_TIME = "notification_summary_time.v1"
        const val SOAK_REMINDERS = "soak_reminders_enabled.v1"
        const val PRIVACY = "advertising_privacy.v1"
    }

    private val mutableTheme = MutableStateFlow(readTheme())
    private val mutableCrash = MutableStateFlow(settings.getBoolean(Keys.CRASH, true))
    private val mutableNotifications = MutableStateFlow(settings.getBoolean(Keys.NOTIFICATIONS, true))
    private val mutableSummaryTime = MutableStateFlow(SummaryLocalTime.parseOrDefault(settings.getStringOrNull(Keys.SUMMARY_TIME)))
    private val mutableSoakReminders = MutableStateFlow(settings.getBoolean(Keys.SOAK_REMINDERS, true))
    private val mutablePrivacy = MutableStateFlow(readPrivacy())
    override val theme = mutableTheme.asStateFlow()
    override val crashReportingEnabled = mutableCrash.asStateFlow()
    override val notificationsEnabled = mutableNotifications.asStateFlow()
    override val summaryTime = mutableSummaryTime.asStateFlow()
    override val soakRemindersEnabled = mutableSoakReminders.asStateFlow()
    override val privacyState = mutablePrivacy.asStateFlow()

    override fun setTheme(value: ThemePreference) {
        settings.putString(Keys.THEME, value.name); settings.remove(Keys.LEGACY_DARK); mutableTheme.value = value
    }
    override fun setCrashReportingEnabled(value: Boolean) {
        settings.putBoolean(Keys.CRASH, value); mutableCrash.value = value
    }
    override fun setNotificationsEnabled(value: Boolean) {
        settings.putBoolean(Keys.NOTIFICATIONS, value); mutableNotifications.value = value
    }
    override fun setSummaryTime(value: SummaryLocalTime) {
        settings.putString(Keys.SUMMARY_TIME, value.toString()); mutableSummaryTime.value = value
    }
    override fun setSoakRemindersEnabled(value: Boolean) {
        settings.putBoolean(Keys.SOAK_REMINDERS, value); mutableSoakReminders.value = value
    }
    override fun setPrivacyState(value: AdvertisingPrivacyState?) {
        if (value == null) settings.remove(Keys.PRIVACY)
        else settings.putString(Keys.PRIVACY, json.encodeToString(AdvertisingPrivacyState.serializer(), value))
        mutablePrivacy.value = value
    }
    override fun snapshot() = ProductPreferences(
        mutableTheme.value, mutableCrash.value, mutableNotifications.value, mutablePrivacy.value,
        mutableSummaryTime.value, mutableSoakReminders.value,
    )
    override fun replace(value: ProductPreferences) {
        setTheme(value.theme)
        setCrashReportingEnabled(value.crashReportingEnabled)
        setNotificationsEnabled(value.notificationsEnabled)
        setPrivacyState(value.privacyState)
        setSummaryTime(value.summaryTime)
        setSoakRemindersEnabled(value.soakRemindersEnabled)
    }
    override fun clearProductState() {
        settings.clear()
        mutableTheme.value = ThemePreference.SYSTEM
        mutableCrash.value = true
        mutableNotifications.value = true
        mutableSummaryTime.value = SummaryLocalTime.DEFAULT
        mutableSoakReminders.value = true
        mutablePrivacy.value = null
    }

    private fun readTheme(): ThemePreference {
        settings.getStringOrNull(Keys.THEME)?.let { encoded ->
            ThemePreference.entries.firstOrNull { it.name == encoded }?.let { return it }
        }
        return if (settings.getBooleanOrNull(Keys.LEGACY_DARK) == true) ThemePreference.DARK else ThemePreference.SYSTEM
    }
    private fun readPrivacy(): AdvertisingPrivacyState? = settings.getStringOrNull(Keys.PRIVACY)?.let { encoded ->
        runCatching { json.decodeFromString(AdvertisingPrivacyState.serializer(), encoded) }
            .getOrNull()?.takeIf(AdvertisingPrivacyState::isStructurallyValid)
    }
}

private fun AdvertisingPrivacyState.isStructurallyValid(): Boolean {
    val lifetime = expiresAtEpochMillis - fetchedAtEpochMillis
    return payloadVersion == ADVERTISING_PRIVACY_PAYLOAD_VERSION &&
        endpoint == ADVERTISING_PRIVACY_ENDPOINT &&
        schemaVersion == ADVERTISING_PRIVACY_SCHEMA_VERSION &&
        POLICY_VERSION.matches(policyVersion) &&
        fetchedAtEpochMillis >= 0L &&
        expiresAtEpochMillis > fetchedAtEpochMillis &&
        lifetime <= ADVERTISING_PRIVACY_MAX_AGE_MILLIS
}

private val POLICY_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
