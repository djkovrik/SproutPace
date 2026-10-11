package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
internal actual fun localizedDate(date: LocalDate): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, LocalConfiguration.current.locales[0]).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(date.year, date.monthNumber - 1, date.dayOfMonth)
    }.time)

@Composable
internal actual fun localizedTime(time: LocalTime): String =
    android.text.format.DateFormat.getTimeFormat(LocalContext.current).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(TIME_FORMAT_REFERENCE_YEAR, Calendar.JANUARY, 1, time.hour, time.minute)
    }.time)

@Composable
internal actual fun temporalUses24HourClock(): Boolean =
    android.text.format.DateFormat.is24HourFormat(LocalContext.current)

private const val TIME_FORMAT_REFERENCE_YEAR = 2000

@Composable
internal actual fun localizedTimeZoneName(zoneId: String): String =
    timeZoneDisplayName(zoneId, LocalConfiguration.current.locales[0])

internal fun timeZoneDisplayName(zoneId: String, locale: Locale): String =
    TimeZone.getTimeZone(zoneId).getDisplayName(false, TimeZone.LONG, locale)
