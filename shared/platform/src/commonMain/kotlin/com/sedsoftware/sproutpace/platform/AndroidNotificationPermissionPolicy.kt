package com.sedsoftware.sproutpace.platform

/** OS observations only: checking authorization must never present the permission prompt. */
internal fun androidNotificationPermissionState(
    runtimePermissionRequired: Boolean,
    notificationsEnabled: Boolean,
    runtimePermissionGranted: Boolean,
    previouslyRequested: Boolean,
    shouldShowRationale: Boolean,
): PermissionState = when {
    !runtimePermissionRequired -> if (notificationsEnabled) PermissionState.Granted else PermissionState.Denied(false)
    !runtimePermissionGranted -> PermissionState.Denied(!previouslyRequested || shouldShowRationale)
    !notificationsEnabled -> PermissionState.Denied(false)
    else -> PermissionState.Granted
}
