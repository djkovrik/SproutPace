@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.platform.NativeTextKey
import com.sedsoftware.sproutpace.platform.PhysicalReminder
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import com.sedsoftware.sproutpace.platform.ReminderKind
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosReminderTriggerTest {
    @Test
    fun recurringSummaryHasNoFixedDateAndContinuesPastTheQueuedHorizon() {
        val trigger = assertIs<UNCalendarNotificationTrigger>(summary(true).iosNotificationTrigger(0))
        assertTrue(trigger.repeats)
        assertEquals(9L, trigger.dateComponents.hour)
        assertEquals(30L, trigger.dateComponents.minute)
        val unspecified = NSDateComponents()
        assertEquals(unspecified.year, trigger.dateComponents.year)
        assertEquals(unspecified.month, trigger.dateComponents.month)
        assertEquals(unspecified.day, trigger.dateComponents.day)
    }

    @Test
    fun futureOnlyWindowUsesFullDateAndSoakUsesOneElapsedInterval() {
        val window = assertIs<UNCalendarNotificationTrigger>(summary(false).iosNotificationTrigger(0))
        assertFalse(window.repeats)
        assertEquals(2026L, window.dateComponents.year)
        assertEquals(12L, window.dateComponents.month)
        assertEquals(20L, window.dateComponents.day)
        val soak = summary(false).copy(kind = ReminderKind.SOAK, civilDateTime = null, fireAtEpochMillis = 3_601_000)
        val elapsed = assertIs<UNTimeIntervalNotificationTrigger>(soak.iosNotificationTrigger(1_000))
        assertFalse(elapsed.repeats)
        assertEquals(3_600.0, elapsed.timeInterval)
    }

    private fun summary(repeats: Boolean) = PhysicalReminder(
        PhysicalReminderId("sproutpace:daily-summary"), ReminderKind.DAILY_SUMMARY, 1_000,
        NativeTextKey.DAILY_SUMMARY_TITLE, NativeTextKey.DAILY_SUMMARY_BODY,
        LocalDateTime(2026, 12, 20, 9, 30), repeatsDaily = repeats,
    )
}
