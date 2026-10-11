package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

internal fun scheduleTimeZone(fields: List<UiField>): TimeZone = fields.firstOrNull { it.id == "timeZoneId" }
    ?.value?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()

@Composable
internal fun TimeZoneContext(zone: TimeZone, schedule: Boolean = true) {
    Text(stringResource(strictTextResource(if (schedule) "schedule_timezone_context" else "event_timezone_context"),
        localizedTimeZoneName(zone.id)), style = MaterialTheme.typography.bodySmall)
}
