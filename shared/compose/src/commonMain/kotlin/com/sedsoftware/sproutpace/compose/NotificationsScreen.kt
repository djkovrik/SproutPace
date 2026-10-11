package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.PermissionState
import kotlinx.datetime.LocalTime

@Composable
internal fun NotificationsScreen(state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    val daily = state.fields.firstOrNull { it.id == "dailySummaryEnabled" }
    val soak = state.fields.firstOrNull { it.id == "soakRemindersEnabled" }
    val time = state.fields.firstOrNull { it.id == "summaryTime" }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.facts.firstOrNull { it.id == "reminder-scheduling" }?.let { fact -> item {
                val failed = fact.valueKey == "notifications_schedule_failed"
                Text(localizedText(requireNotNull(fact.valueKey)),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (failed) OutlinedButton(onClick = { onAction(UiAction.Submit("update-notifications")) }, enabled = !state.busy) {
                    Text(localizedText("notifications_retry_scheduling"))
                }
            } }
            state.errorKey?.let { key -> item {
                Text(localizedText(key), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } }
            daily?.let { field -> item {
                NotificationSwitch(field, "notifications_daily_summary_body", state.busy, onAction)
            } }
            if (daily?.value == "true" && time != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("notifications_time"), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { timeOpen = true }, enabled = !state.busy) {
                        Text(localizedTime(runCatching { LocalTime.parse(time.value) }.getOrDefault(DEFAULT_SUMMARY_TIME)))
                    }
                    time.errorKey?.let { Text(localizedText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            soak?.let { field -> item {
                NotificationSwitch(field, "notifications_soak_body", state.busy, onAction)
            } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("notifications_permission"), style = MaterialTheme.typography.titleMedium)
                    Text(localizedText(notificationPermissionStatusKey(state.permissionState)),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(localizedText("notifications_permission_body"))
                    state.actions.filter { it.id in NOTIFICATION_PERMISSION_ACTION_IDS }.forEach { action ->
                        Button(onClick = { onAction(UiAction.Submit(action.id)) }, enabled = action.enabled && !state.busy) {
                            LabeledActionContent(action.id, action.labelKey)
                        }
                    }
                }
            }
        }
    }
    if (timeOpen && time != null) TemporalTimeDialog(
        initial = runCatching { LocalTime.parse(time.value) }.getOrDefault(DEFAULT_SUMMARY_TIME),
        onDismiss = { timeOpen = false },
        onApply = { selected ->
            onAction(UiAction.EditField(time.id, selected.toString()))
            timeOpen = false
        },
    )
}

@Composable
private fun NotificationSwitch(field: UiField, summaryKey: String, busy: Boolean, onAction: (UiAction) -> Unit) {
    val checked = field.value == "true"
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked, enabled = !busy && !field.readOnly, role = Role.Switch,
            onValueChange = { onAction(UiAction.EditField(field.id, it.toString())) },
        ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(localizedText(field.labelKey), style = MaterialTheme.typography.titleMedium)
            Text(localizedText(summaryKey), style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = !busy && !field.readOnly)
    }
}

internal fun notificationPermissionActionIds(
    remindersEnabled: Boolean,
    permission: com.sedsoftware.sproutpace.platform.PermissionState,
): Set<String> {
    if (!remindersEnabled) return emptySet()
    return when (permission) {
        com.sedsoftware.sproutpace.platform.PermissionState.Unknown -> setOf("request-notification-permission")
        is com.sedsoftware.sproutpace.platform.PermissionState.Denied -> buildSet {
            if (permission.canRequestAgain) add("request-notification-permission")
            add("open-system-settings")
        }
        else -> emptySet()
    }
}

internal fun notificationPermissionStatusKey(permission: PermissionState): String = when (permission) {
    PermissionState.GRANTED -> "notifications_permission_granted"
    PermissionState.DENIED, PermissionState.PERMANENTLY_DENIED -> "notifications_permission_denied"
    PermissionState.NOT_REQUESTED -> "notifications_permission_not_requested"
    PermissionState.NOT_REQUIRED -> "notifications_permission_not_required"
}

private val NOTIFICATION_PERMISSION_ACTION_IDS = setOf("request-notification-permission", "open-system-settings")

private val DEFAULT_SUMMARY_TIME = LocalTime(9, 0)

