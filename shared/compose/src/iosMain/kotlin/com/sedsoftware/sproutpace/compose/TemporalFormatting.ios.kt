@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.timeZoneWithName

@Composable
internal actual fun localizedDate(date: LocalDate): String = civilFormatter().apply {
    dateStyle = NSDateFormatterMediumStyle
    timeStyle = NSDateFormatterNoStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(datePickerToken(date) / MILLIS_PER_SECOND))

@Composable
internal actual fun localizedTime(time: LocalTime): String = civilFormatter().apply {
    dateStyle = NSDateFormatterNoStyle
    timeStyle = NSDateFormatterShortStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970((time.hour * SECONDS_PER_HOUR + time.minute * SECONDS_PER_MINUTE).toDouble()))

@Composable
internal actual fun temporalUses24HourClock(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') != true

private fun civilFormatter() = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
}

private const val MILLIS_PER_SECOND = 1000.0
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

@Composable
internal actual fun localizedTimeZoneName(zoneId: String): String = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.timeZoneWithName(zoneId) ?: NSTimeZone.timeZoneForSecondsFromGMT(0)
    dateFormat = "vvvv"
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(0.0))
