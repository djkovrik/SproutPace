package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/** Success originates from the retained Store, never from an optimistic button click. */
@Composable
internal fun ActionCompletionStatus(state: SproutPaceUiState) {
    val focus = remember(state.screen, state.selectedItemId) { FocusRequester() }
    var observed by remember(state.screen, state.selectedItemId) { mutableStateOf(state.completionRevision) }
    var messageKey by remember(state.screen, state.selectedItemId) { mutableStateOf<String?>(null) }
    val invalid = state.fields.any { it.errorKey != null }
    LaunchedEffect(state.busy, state.errorKey, invalid) {
        if (state.busy || state.errorKey != null || invalid) messageKey = null
    }
    LaunchedEffect(state.completionRevision) {
        if (state.completionRevision > observed) {
            observed = state.completionRevision
            messageKey = completionMessageKey(state.lastCompletedOperation)
            if (messageKey != null) {
                withFrameNanos { }
                focus.requestFocus()
            }
        }
    }
    messageKey?.let { message -> key(state.completionRevision) {
        Text(localizedText(message), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focus).focusable().semantics {
                    testTag = "action-completion-status"
                    liveRegion = LiveRegionMode.Polite
                })
    } }
}

internal fun completionMessageKey(operation: String?): String? = when (operation) {
    "snooze" -> "feedback_action_rescheduled"
    "complete-action" -> "feedback_action_completed"
    "skip-action" -> "feedback_action_skipped"
    "undo-stage" -> "feedback_stage_undone"
    "start-soaking", "confirm-sowing", "confirm-stage", "skip-stage" -> "feedback_stage_saved"
    "assign-overflow" -> "feedback_placement_saved"
    else -> null
}
