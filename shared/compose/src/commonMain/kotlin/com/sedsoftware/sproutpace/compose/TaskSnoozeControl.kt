package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sedsoftware.sproutpace.component.ScreenFieldKind

@Composable
internal fun TaskSnoozeControl(item: UiItem, onAction: (UiAction) -> Unit) {
    val task = item.task ?: return
    var open by rememberSaveable(task.actionId) { mutableStateOf(false) }
    var dueAt by rememberSaveable(task.actionId) { mutableStateOf(task.dueAt.toString()) }
    TextButton(onClick = { open = true }) { Text(localizedText("action_snooze")) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(localizedText("task_snooze_title")) },
        text = {
            TemporalFieldControl(
                UiField("dueAt", "field_due_at", ScreenFieldKind.INSTANT, dueAt, true, false, emptyList(), null),
            ) { action -> if (action is UiAction.EditField) dueAt = action.value }
        },
        confirmButton = {
            TextButton(onClick = {
                onAction(UiAction.SubmitItem("snooze", task.actionId, mapOf("dueKind" to "elapsed", "dueAt" to dueAt)))
                open = false
            }) { Text(localizedText("task_snooze_apply")) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(localizedText("temporal_cancel")) } },
    )
}
