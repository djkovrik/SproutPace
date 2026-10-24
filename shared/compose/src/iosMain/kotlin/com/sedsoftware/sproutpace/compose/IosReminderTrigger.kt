@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.platform.PhysicalReminder
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import kotlin.math.max

internal fun PhysicalReminder.iosNotificationTrigger(nowEpochMillis: Long): UNNotificationTrigger =
    civilDateTime?.let { civil ->
        val components = NSDateComponents().apply {
            calendar = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)
            // A repeating hour/minute follows the device's civil time, including DST. A fixed date
            // would prevent daily repetition; a time interval would drift across civil-time changes.
            if (!repeatsDaily) {
                year = civil.year.toLong()
                month = civil.month.number.toLong()
                day = civil.day.toLong()
            }
            hour = civil.hour.toLong()
            minute = civil.minute.toLong()
        }
        UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = repeatsDaily)
    } ?: UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
        max(1.0, (fireAtEpochMillis - nowEpochMillis) / MILLIS_PER_SECOND), repeats = false,
    )

private const val MILLIS_PER_SECOND = 1_000.0
