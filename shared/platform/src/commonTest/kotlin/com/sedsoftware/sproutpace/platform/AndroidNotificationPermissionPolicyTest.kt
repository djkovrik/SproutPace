package com.sedsoftware.sproutpace.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidNotificationPermissionPolicyTest {
    @Test
    fun ac064_notificationsDisabledDoesNotMaskPermanentDenialOnResume() {
        assertEquals(PermissionState.Denied(true), authorization(requested = false, rationale = false))
        assertEquals(PermissionState.Denied(true), authorization(requested = true, rationale = true))
        assertEquals(PermissionState.Denied(false), authorization(requested = true, rationale = false))
        // A fresh service reads the same persisted request flag and OS observations after restart.
        assertEquals(PermissionState.Denied(false), authorization(requested = true, rationale = false))
        assertEquals(PermissionState.Granted, authorization(requested = true, rationale = false,
            runtimeGranted = true, enabled = true))
    }

    @Test
    fun settingsDisableRequiresSettingsRecoveryEvenWithGrantedRuntimePermission() {
        assertEquals(PermissionState.Denied(false), authorization(requested = false, rationale = false,
            runtimeGranted = true))
        assertEquals(PermissionState.Denied(false), androidNotificationPermissionState(
            runtimePermissionRequired = false, notificationsEnabled = false, runtimePermissionGranted = false,
            previouslyRequested = false, shouldShowRationale = false))
        assertEquals(PermissionState.Granted, androidNotificationPermissionState(
            runtimePermissionRequired = false, notificationsEnabled = true, runtimePermissionGranted = false,
            previouslyRequested = false, shouldShowRationale = false))
    }

    @Test
    fun runtimeDenialRemainsDeniedEvenIfEnabledObservationIsTrue() {
        assertEquals(PermissionState.Denied(false), authorization(requested = true, rationale = false, enabled = true))
        assertEquals(PermissionState.Denied(true), authorization(requested = false, rationale = false, enabled = true))
    }

    private fun authorization(
        requested: Boolean,
        rationale: Boolean,
        runtimeGranted: Boolean = false,
        enabled: Boolean = false,
    ) = androidNotificationPermissionState(
        runtimePermissionRequired = true, notificationsEnabled = enabled, runtimePermissionGranted = runtimeGranted,
        previouslyRequested = requested, shouldShowRationale = rationale,
    )
}
