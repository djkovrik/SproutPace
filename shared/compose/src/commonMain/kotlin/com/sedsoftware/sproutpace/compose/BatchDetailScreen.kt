package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BatchDetailScreen(state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    val batch = state.items.firstOrNull { it.batch != null && it.task == null }
    val primary = state.actions.firstOrNull { it.primary } ?: state.actions.firstOrNull { it.id == "repeat-batch" }
    var operation by rememberSaveable(state.selectedItemId) { mutableStateOf<String?>(null) }
    var openedAtRevision by rememberSaveable(state.selectedItemId) { mutableStateOf(state.completionRevision) }
    LaunchedEffect(state.actions, state.completionRevision) {
        if (operation != null && (state.completionRevision > openedAtRevision || state.actions.none { it.id == operation })) {
            operation = null
        }
    }
    val choose: (UiScreenAction) -> Unit = { action ->
        if (batchActionFieldIds(action.id).isEmpty()) onAction(UiAction.Submit(action.id, state.selectedItemId))
        else {
            openedAtRevision = state.completionRevision
            operation = action.id
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            batch?.let { item { BatchIdentityHeader(it) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    primary?.let { action ->
                        val next = state.items.firstOrNull { it.task != null && it.stateKey == "action_status_pending" }
                        next?.let { Text(localizedText("today_next_action"), style = MaterialTheme.typography.titleMedium)
                            Text(localizedOrAuthored(it.titleKey, it.authoredTitle))
                            ProjectionContent(it.copy(batch = null)) }
                        Button(onClick = { choose(action) }, enabled = !state.busy && action.enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "action-${action.id}" }) {
                            LabeledActionContent(action.id, action.labelKey)
                        }
                    }
                    BatchMoreActions(state, primary?.id, choose)
                }
            }
            operation?.let { id -> item {
                BatchOperationEditor(state, id, onAction, onCancel = { operation = null; onAction(UiAction.CancelDraft) })
            } }
            state.errorKey?.let { key -> item { Text(localizedText(key), color = MaterialTheme.colorScheme.error) } }
            if (state.conflictKeys.isNotEmpty() || batch?.occupancy != null) item { BatchCapacitySection(batch, state, onAction) }
            item { BatchSection("batch_timeline") }
            items(state.items.filter { it.id.startsWith("event:") }, key = { "timeline:${it.id}" }) {
                BatchHistoryCard(it)
            }
            if (state.items.any { it.task != null }) item { BatchSection("batch_detail_scheduled_tasks") }
            items(state.items.filter { it.task != null }, key = { "task:${it.id}" }) { task ->
                BatchScheduledTask(task, state, onAction)
            }
            item { BatchSection("backup_count_observations") }
            state.actions.firstOrNull { it.id == "add-observation" }?.let { action -> item {
                TextButton(onClick = { choose(action) }, enabled = !state.busy) {
                    LabeledActionContent("choose-photo", action.labelKey)
                }
            } }
            items(state.items.filter { it.id.startsWith("observation:") }, key = { it.id }) { observation ->
                BatchHistoryCard(observation)
                if (state.actions.isNotEmpty()) TextButton(onClick = {
                    onAction(UiAction.Submit("add-observation", observation.id.removePrefix("observation:")))
                }, enabled = !state.busy) { LabeledActionContent("edit", "action_edit_observation") }
            }
            item { BatchSection("batch_snapshot_title")
                Text(localizedText("batch_detail_snapshot_preserved"), style = MaterialTheme.typography.bodyMedium) }
            items(state.facts.filter { it.id != "batch-state" && it.id != "observation-count" }, key = { "fact:${it.id}" }) { fact ->
                Column { Text(localizedText(fact.labelKey), style = MaterialTheme.typography.labelLarge)
                    Text(localizedOrAuthored(fact.valueKey, fact.authoredValue)) }
            }
            items(state.items.filter { it.snapshotStage != null }, key = { it.id }) { BatchHistoryCard(it) }
        }
    }
}

@Composable
private fun BatchCapacitySection(batch: UiItem?, state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    if (state.conflictKeys.isNotEmpty()) {
        val key = when {
            batch?.occupancy?.overflow?.let { it > 0 } == true -> "capacity_conflict_title"
            batch?.supportingKey == "state_temporarily_unassigned_title" -> "batch_detail_unassigned"
            else -> state.conflictKeys.first()
        }
        Text(localizedText(key), color = MaterialTheme.colorScheme.error)
    }
    batch?.occupancy?.let { zone ->
        OccupancyContent(zone)
        if (zone.overflow > 0 && state.actions.isNotEmpty()) TextButton(
            onClick = { onAction(UiAction.Submit("edit-zone", zone.zoneId)) }, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(localizedText("change_capacity")) }
    }
}

@Composable
private fun BatchScheduledTask(task: UiItem, state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    BatchHistoryCard(task)
    if (state.actions.isEmpty()) return
    task.commands.filter { it.operation in setOf("complete-action", "skip-action") }.forEach { command ->
        TextButton(onClick = { onAction(UiAction.SubmitItem(command.operation, command.subjectId)) },
            enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)
                .semantics { testTag = "item-${task.id}-${command.operation}" }) {
            LabeledActionContent(command.operation, "action_${command.operation.replace('-', '_')}")
        }
    }
}

@Composable
private fun BatchIdentityHeader(item: UiItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        item.stateKey?.let { Text(localizedText(it), style = MaterialTheme.typography.titleMedium) }
        ProjectionContent(item.copy(occupancy = null))
    }
}

@Composable
private fun BatchMoreActions(state: SproutPaceUiState, primaryId: String?, choose: (UiScreenAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(localizedText("batch_more_actions")) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.actions.filter { it.id != primaryId && it.id != "add-observation" }.forEach { action ->
                val label = if (action.id == "edit-batch" && state.items.firstOrNull { it.batch != null }?.batch?.state !=
                    com.sedsoftware.sproutpace.domain.model.BatchState.PLANNED) "action_correct_batch" else action.labelKey
                DropdownMenuItem(text = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledActionContent(if (action.id == "edit-batch") "edit" else action.id, label)
                    }
                }, enabled = !state.busy && action.enabled,
                    onClick = { expanded = false; choose(action) })
            }
        }
    }
}

@Composable
private fun BatchOperationEditor(state: SproutPaceUiState, id: String, onAction: (UiAction) -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val unassigned = state.fields.firstOrNull { it.id == "unassigned" }?.value == "true"
            state.fields.filter { it.id in batchActionFieldIds(id) && !(it.id == "zoneId" && unassigned) }
                .forEach { FieldControl(it, onAction) }
            Button(onClick = { onAction(UiAction.Submit(id, state.selectedItemId)) }, enabled = !state.busy) {
                Text(localizedText(if (id in com.sedsoftware.sproutpace.component.previewedStageOperations)
                    "stage_preview_action" else "action_${id.replace('-', '_')}"))
            }
            state.stagePreview?.takeIf { it.command.operation == id }?.let { preview ->
                Text(localizedText("stage_preview_title"), style = MaterialTheme.typography.titleMedium)
                preview.dates.forEach { date ->
                    Text(localizedText(date.stageKey.value), style = MaterialTheme.typography.labelLarge)
                    date.previous?.let { Text(stringResource(strictTextResource("stage_preview_previous"), localizedInstant(it))) }
                    Text(stringResource(strictTextResource(if (date.fixed) "stage_preview_fixed" else "stage_preview_result"),
                        localizedInstant(date.resulting)))
                    date.proposed?.let { Text(stringResource(strictTextResource("stage_preview_proposed"), localizedInstant(it)),
                        color = MaterialTheme.colorScheme.error) }
                }
                Button(onClick = { onAction(UiAction.ConfirmStagePreview) }, enabled = !state.busy,
                    modifier = Modifier.semantics { testTag = "confirm-stage-preview" }) {
                    Text(localizedText("stage_preview_confirm"))
                }
            }
            TextButton(onClick = onCancel) { Text(localizedText("temporal_cancel")) }
        }
    }
}

internal fun batchActionFieldIds(id: String): Set<String> = when (id) {
    "start-soaking" -> setOf("occurredAt")
    "confirm-sowing" -> setOf("occurredAt", "zoneId", "unassigned")
    "confirm-stage" -> setOf("occurredAt", "nextState", "zoneId", "unassigned")
    "skip-stage" -> setOf("occurredAt", "skippedState", "reason", "resultingState")
    "schedule-soak-target" -> setOf("dueAt")
    "record-actual-mass" -> setOf("actualMassGrams")
    else -> emptySet()
}

@Composable
internal fun BatchHistoryCard(item: UiItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StageIllustration(item.snapshotStage?.titleKey?.value ?: item.titleKey)
            Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.titleMedium)
            item.history?.let { history ->
                Text(localizedInstant(history.occurredAt))
                if (item.id.startsWith("event:")) {
                    Text(localizedText(if (history.skipped) "batch_detail_reached_after_skip" else "stage_actual"))
                }
                history.reason?.let { Text(it) }
                history.actualHarvestMassGrams?.let {
                    Text(stringResource(strictTextResource("batch_actual_harvest_mass"), localizedDecimal(it.toString())))
                }
                history.priorOccurredAt.forEach {
                    Text(stringResource(strictTextResource("batch_detail_prior_date"), localizedInstant(it)))
                }
            }
            item.task?.let { task ->
                if (task.pending) Text(localizedText("stage_expected"))
                ProjectionContent(item.copy(batch = null))
            }
            item.stateKey?.let { Text(localizedText(it), style = MaterialTheme.typography.labelMedium) }
            item.snapshotStage?.let { stage ->
                Text(stringResource(strictTextResource("recipe_duration_hours"),
                    stage.minimumHours.toString(), stage.maximumHours.toString()))
                Text(localizedOrAuthored(stage.guidanceKey?.value, stage.authoredGuidance))
            }
            item.photo?.let { photo -> photo.bytes?.let { bytes ->
                AsyncImage(model = bytes, contentDescription = localizedText(photo.contentDescriptionKey), contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(144.dp))
            } }
        }
    }
}

@Composable
private fun BatchSection(key: String) = Text(localizedText(key), style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.semantics { heading() })
