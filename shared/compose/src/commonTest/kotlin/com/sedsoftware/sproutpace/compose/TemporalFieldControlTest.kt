package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemporalFieldControlTest {
    @Test
    fun scheduleContextUsesStoredZoneWithoutRewritingTheDraft() {
        val field = UiField("timeZoneId", "field_time_zone", ScreenFieldKind.TEXT, "America/Los_Angeles",
            required = false, readOnly = false, options = emptyList(), errorKey = null)
        assertEquals("America/Los_Angeles", scheduleTimeZone(listOf(field)).id)
        assertEquals("America/Los_Angeles", field.value)
    }

    @Test
    fun instantAfterLocalMidnightKeepsTheChosenLocalDateAndTime() {
        val moscow = TimeZone.of("Europe/Moscow")
        val chosen = LocalDateTime.parse("2026-10-11T00:15")
        val stored = temporalStorageValue(ScreenFieldKind.INSTANT, chosen, moscow)
        assertEquals("2026-10-10T21:15:00Z", stored)
        assertEquals(chosen, temporalCivilValue(ScreenFieldKind.INSTANT, stored, moscow))
    }

    @Test
    fun westOfUtcPickerTokenDoesNotShiftTheChosenCalendarDay() {
        val date = LocalDate.parse("2026-10-10")
        assertEquals(date, dateFromPickerToken(datePickerToken(date)))
        val losAngeles = TimeZone.of("America/Los_Angeles")
        assertEquals("2026-10-11T06:45:00Z", temporalStorageValue(
            ScreenFieldKind.INSTANT, LocalDateTime.parse("2026-10-10T23:45"), losAngeles,
        ))
    }

    @Test
    fun civilFieldsRetainTheirValuesWhenTheDeviceTimeZoneChanges() {
        val dateTime = LocalDateTime.parse("2026-10-10T23:45")
        listOf("Europe/Moscow", "America/Los_Angeles").forEach { zoneId ->
            val zone = TimeZone.of(zoneId)
            assertEquals("2026-10-10T23:45", temporalStorageValue(ScreenFieldKind.LOCAL_DATE_TIME, dateTime, zone))
            assertEquals("2026-10-10", temporalStorageValue(ScreenFieldKind.LOCAL_DATE, dateTime, zone))
            assertEquals(dateTime, temporalCivilValue(ScreenFieldKind.LOCAL_DATE_TIME, "2026-10-10T23:45", zone))
        }
    }

    @Test
    fun winterAndSummerInstantsRespectTheSelectedZoneOffset() {
        val zone = TimeZone.of("America/New_York")
        assertEquals("2026-01-10T14:30:00Z", temporalStorageValue(
            ScreenFieldKind.INSTANT, LocalDateTime.parse("2026-01-10T09:30"), zone,
        ))
        assertEquals("2026-07-10T13:30:00Z", temporalStorageValue(
            ScreenFieldKind.INSTANT, LocalDateTime.parse("2026-07-10T09:30"), zone,
        ))
    }

    @Test
    fun emptyOrInvalidValuesRemainUnselectedUntilTheUserAppliesAPickerValue() {
        assertNull(temporalCivilValue(ScreenFieldKind.INSTANT, "", TimeZone.UTC))
        assertNull(temporalCivilValue(ScreenFieldKind.LOCAL_DATE, "2026-02-30", TimeZone.UTC))
    }

    @Test
    fun missingSpringForwardHourCannotSilentlyChangeTheSelectedInstant() {
        val missingTime = LocalDateTime.parse("2026-03-08T02:30")
        val zone = TimeZone.of("America/New_York")
        assertFalse(temporalSelectionExists(ScreenFieldKind.INSTANT, missingTime, zone))
        assertTrue(temporalSelectionExists(ScreenFieldKind.LOCAL_DATE_TIME, missingTime, zone))
        assertTrue(temporalSelectionExists(ScreenFieldKind.INSTANT,
            LocalDateTime.parse("2026-03-08T03:30"), zone))
    }
}
