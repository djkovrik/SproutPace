package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.isInputValid
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Calendar and clock inputs keep ISO serialization outside the visible editing experience. */
@Composable
internal fun TemporalFieldControl(field: UiField, onAction: (UiAction) -> Unit) {
    val zone = TimeZone.currentSystemDefault()
    val civil = temporalCivilValue(field.kind, field.value, zone)
    var dateOpen by rememberSaveable(field.id) { mutableStateOf(false) }
    var timeOpen by rememberSaveable(field.id) { mutableStateOf(false) }
    var unavailableTime by rememberSaveable(field.id, field.value) { mutableStateOf(false) }
    val fallback = remember(field.id, field.value) { Clock.System.now().toLocalDateTime(zone) }
    val selection = civil ?: fallback
    fun update(value: LocalDateTime) {
        unavailableTime = !temporalSelectionExists(field.kind, value, zone)
        if (!unavailableTime) onAction(UiAction.EditField(
            field.id, temporalStorageValue(field.kind, value, zone),
        ))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(localizedText(field.labelKey), style = MaterialTheme.typography.labelLarge)
        if (field.kind == ScreenFieldKind.INSTANT) TimeZoneContext(zone, schedule = false)
        OutlinedButton(onClick = { dateOpen = true }, enabled = !field.readOnly) {
            Text(civil?.let { localizedDate(it.date) } ?: localizedText("temporal_choose_date"))
        }
        if (field.kind != ScreenFieldKind.LOCAL_DATE) {
            OutlinedButton(onClick = { timeOpen = true }, enabled = !field.readOnly) {
                Text(civil?.let { localizedTime(it.time) } ?: localizedText("temporal_choose_time"))
            }
        }
        if (!field.required && !field.readOnly && field.value.isNotEmpty()) {
            TextButton(onClick = { onAction(UiAction.EditField(field.id, "")) }) {
                Text(localizedText("temporal_clear"))
            }
        }
        field.errorKey?.let { Text(localizedText(it), color = MaterialTheme.colorScheme.error) }
        if (unavailableTime) {
            Text(localizedText("temporal_time_unavailable"), color = MaterialTheme.colorScheme.error)
        }
    }
    if (dateOpen) {
        TemporalDateDialog(selection, onDismiss = { dateOpen = false }) { selected ->
            update(LocalDateTime(selected.date, selection.time))
            dateOpen = false
        }
    }
    if (timeOpen) {
        TemporalTimeDialog(selection.time, onDismiss = { timeOpen = false }) { selected ->
            update(LocalDateTime(selection.date, selected))
            timeOpen = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemporalDateDialog(
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onApply: (LocalDateTime) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = datePickerToken(initial.date))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onApply(LocalDateTime(dateFromPickerToken(it), initial.time)) }
            }) { Text(localizedText("temporal_apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("temporal_cancel")) } },
    ) { DatePicker(state = state, showModeToggle = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemporalTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onApply: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, temporalUses24HourClock())
    var manual by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("temporal_choose_time")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (manual) TimeInput(state) else TimePicker(state)
                TextButton(onClick = { manual = !manual }) {
                    Text(localizedText(if (manual) "temporal_clock_input" else "temporal_manual_input"))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = state.isInputValid, onClick = { onApply(LocalTime(state.hour, state.minute)) }) {
                Text(localizedText("temporal_apply"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("temporal_cancel")) } },
    )
}
