package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Composable
internal expect fun localizedDate(date: LocalDate): String

@Composable
internal expect fun localizedTime(time: LocalTime): String

@Composable
internal expect fun temporalUses24HourClock(): Boolean

@Composable
internal expect fun localizedTimeZoneName(zoneId: String): String

@Composable
internal fun localizedInstant(value: Instant): String {
    val civil = value.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localizedDate(civil.date)} ${localizedTime(civil.time)}"
}

/** The DatePicker uses UTC midnight as a date token, never as a local scheduled instant. */
internal fun datePickerToken(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun dateFromPickerToken(token: Long): LocalDate =
    Instant.fromEpochMilliseconds(token).toLocalDateTime(TimeZone.UTC).date

internal fun temporalCivilValue(kind: ScreenFieldKind, value: String, zone: TimeZone): LocalDateTime? =
    runCatching {
        when (kind) {
            ScreenFieldKind.LOCAL_DATE -> LocalDateTime(LocalDate.parse(value), LocalTime(0, 0))
            ScreenFieldKind.LOCAL_DATE_TIME -> LocalDateTime.parse(value)
            ScreenFieldKind.INSTANT -> Instant.parse(value).toLocalDateTime(zone)
            else -> null
        }
    }.getOrNull()

internal fun temporalStorageValue(kind: ScreenFieldKind, value: LocalDateTime, zone: TimeZone): String =
    when (kind) {
        ScreenFieldKind.LOCAL_DATE -> value.date.toString()
        ScreenFieldKind.LOCAL_DATE_TIME -> value.toString()
        ScreenFieldKind.INSTANT -> value.toInstant(zone).toString()
        else -> error("Unsupported temporal field kind: $kind")
    }

internal fun temporalSelectionExists(kind: ScreenFieldKind, value: LocalDateTime, zone: TimeZone): Boolean =
    kind != ScreenFieldKind.INSTANT || value.toInstant(zone).toLocalDateTime(zone) == value
