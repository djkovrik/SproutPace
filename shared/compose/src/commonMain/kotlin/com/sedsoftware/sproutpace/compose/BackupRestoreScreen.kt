package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant

internal fun backupScreenPhase(state: SproutPaceUiState): BackupScreenPhase = when {
    state.busy || state.variant == UiVariant.PROGRESS -> BackupScreenPhase.PROGRESS
    state.errorKey != null || state.variant == UiVariant.ERROR -> BackupScreenPhase.ERROR
    state.facts.any { it.id == "backup-result" } -> BackupScreenPhase.RESULT
    state.facts.any { it.id == "restore-created-at" } &&
        state.facts.any { it.id == "restore-format-version" } -> BackupScreenPhase.VALID_PREVIEW
    else -> BackupScreenPhase.IDLE
}

@Composable
internal fun BackupRestoreScreen(state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    val phase = backupScreenPhase(state)
    val available = phase != BackupScreenPhase.PROGRESS
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(localizedText("backup_local_title"), style = MaterialTheme.typography.titleLarge)
                Text(localizedText("backup_local_body"))
            }
            if (phase == BackupScreenPhase.PROGRESS) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("backup_progress"), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (phase == BackupScreenPhase.ERROR) item {
                Text(localizedText(state.errorKey ?: "backup_failed"), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (phase == BackupScreenPhase.RESULT) item {
                val result = state.facts.first { it.id == "backup-result" }
                Text(localizedText(result.valueKey ?: "backup_completed"),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (phase == BackupScreenPhase.VALID_PREVIEW) item {
                BackupPreview(state.facts)
                TextButton(onClick = { onAction(UiAction.Submit("cancel-restore-preview")) }) {
                    Text(localizedText("temporal_cancel"))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("backup_create_body"))
                    OutlinedButton(onClick = { onAction(UiAction.Submit("create-backup")) }, enabled = available) {
                        LabeledActionContent("create-backup", "action_create_backup")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("backup_restore_body"))
                    OutlinedButton(onClick = { onAction(UiAction.Submit("preview-restore")) }, enabled = available) {
                        LabeledActionContent("preview-restore", "backup_choose_archive")
                    }
                    Button(onClick = { onAction(UiAction.Submit("restore-backup")) },
                        enabled = phase == BackupScreenPhase.VALID_PREVIEW) {
                        LabeledActionContent("restore-backup", "action_confirm_restore")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("backup_csv_body"))
                    OutlinedButton(onClick = { onAction(UiAction.Submit("export-csv")) }, enabled = available) {
                        LabeledActionContent("export-csv", "action_export_csv")
                    }
                }
            }
            item {
                TextButton(onClick = { onAction(UiAction.Submit("delete-all-data")) }, enabled = available) {
                    Text(localizedText("action_delete_all_data"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun BackupPreview(facts: List<UiFact>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(localizedText("restore_preview_title"), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        facts.filter { it.id.startsWith("restore-") }.forEach { fact ->
            val value = if (fact.id == "restore-created-at") {
                runCatching { Instant.parse(fact.authoredValue.orEmpty()) }.getOrNull()?.let { localizedInstant(it) }
                    ?: localizedText("temporal_not_set")
            } else localizedOrAuthored(fact.valueKey, fact.authoredValue)
            Text("${localizedText(fact.labelKey)}: $value")
        }
        Text(localizedText("restore_replace_title"), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium)
        Text(localizedText("restore_replace_body"))
    }
}
