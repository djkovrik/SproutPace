package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey

internal fun ScreenSnapshot.withNotificationPermissionContext(
    remindersEnabled: Boolean,
    permission: PermissionState,
): ScreenSnapshot {
    val current = if (remindersEnabled) permission else PermissionState.NOT_REQUIRED
    return copy(
        permissionState = current,
        items = items.map { item ->
            if (item.id == "notifications" && current in setOf(
                    PermissionState.DENIED, PermissionState.PERMANENTLY_DENIED,
                )) {
                item.copy(supportingKey = TextKey("notifications_permission_denied"))
            } else item
        },
    )
}
