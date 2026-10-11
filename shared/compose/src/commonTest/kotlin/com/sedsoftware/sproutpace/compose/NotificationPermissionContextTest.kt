package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionContextTest {
    @Test fun settingsRowExplainsDeniedPermissionWithoutReplacingOtherSettings() {
        val row = ScreenItem("notifications", titleKey = TextKey("screen_notifications_title"))
        val other = ScreenItem("privacy", titleKey = TextKey("screen_advertising_privacy_title"))
        val snapshot = ScreenSnapshot(items = listOf(row, other))
        val projected = snapshot.withNotificationPermissionContext(true, PermissionState.PERMANENTLY_DENIED)
        assertEquals(TextKey("notifications_permission_denied"), projected.items.first().supportingKey)
        assertEquals(other, projected.items.last())
        assertEquals(snapshot, snapshot.withNotificationPermissionContext(false, PermissionState.DENIED))
    }
}
