package com.sedsoftware.sproutpace.monetization

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_ENDPOINT
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductSettingsPrivacyStoreTest {
    @Test
    fun canonicalPayloadSurvivesProcessRestartThroughProductSettingsAdapter() {
        val persisted = MapSettings()
        val state = AdvertisingPrivacyState(
            policyVersion = "policy-7",
            expiresAtEpochMillis = 1_200_000L,
            choice = ConsentChoice.ACCEPTED,
            endpoint = ADVERTISING_PRIVACY_ENDPOINT,
            consentRequired = true,
            fetchedAtEpochMillis = 1_000_000L,
        )
        ProductSettingsPrivacyStore(MultiplatformProductSettings(persisted)).writeState(state)

        val restarted = ProductSettingsPrivacyStore(MultiplatformProductSettings(persisted))

        assertEquals(state, restarted.readState())
        restarted.clearState()
        assertNull(MultiplatformProductSettings(persisted).privacyState.value)
    }

    @Test
    fun malformedOrMismatchedPersistedPayloadIsNeverRestored() {
        val persisted = MapSettings().apply {
            putString("advertising_privacy.v1", """{"schemaVersion":1,"policyVersion":"policy-7","expiresAtEpochMillis":1200000,"choice":"ACCEPTED","payloadVersion":1,"endpoint":"https://example.invalid/privacy","consentRequired":true,"fetchedAtEpochMillis":1000000}""")
        }

        assertNull(ProductSettingsPrivacyStore(MultiplatformProductSettings(persisted)).readState())
    }
}
