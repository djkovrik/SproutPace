package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ContextualFormsScreen(state: SproutPaceUiState, layout: UiLayout, onAction: (UiAction) -> Unit) {
    val hasList = state.screen == UiScreen.ZONES || state.screen == UiScreen.TRAY_SIZES
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (hasList && state.selectedItemId == null) {
            ContextualEntityList(state, onAction)
        } else if (hasList && layout >= UiLayout.EXPANDED) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { ContextualEntityList(state, onAction) }
                Box(Modifier.weight(1f)) { ContextualEditor(state, onAction) }
            }
        } else {
            ContextualEditor(state, onAction)
        }
    }
}

@Composable
private fun ContextualEntityList(state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    val zone = state.screen == UiScreen.ZONES
    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { onAction(UiAction.Submit(if (zone) "new-zone" else "new-tray-size")) }) {
                Text(localizedText(if (zone) "action_add_zone" else "action_add_preset"))
            }
        }
        if (state.items.isEmpty()) item { Text(localizedText("form_empty_list")) }
        itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
            OutlinedCard(onClick = {
                onAction(UiAction.Submit(if (zone) "edit-zone" else "edit-tray-size", item.id))
            }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(
                if (item.id == state.selectedItemId) 2.dp else 1.dp,
                if (item.id == state.selectedItemId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.titleMedium)
                    item.occupancy?.let { OccupancyContent(it, showName = false) }
                    item.stateKey?.let { Text(localizedText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun ContextualEditor(state: SproutPaceUiState, onAction: (UiAction) -> Unit) {
    val fields = state.contextualFormFields()
    val listState = rememberLazyListState()
    val firstError = fields.indexOfFirst { it.errorKey != null }
    val errorFocus = remember { FocusRequester() }
    LaunchedEffect(firstError, fields.getOrNull(firstError)?.errorKey) {
        if (firstError >= 0) {
            listState.scrollToItem(firstError + 1)
            withFrameNanos { }
            errorFocus.requestFocus()
        }
    }
    val existing = state.selectedItemId?.let { selected -> state.items.any { it.id == selected } } == true
    Column(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (existing && state.screen != UiScreen.CLOSE_BATCH) {
                            ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                                LabeledActionContent("edit", contextualFormTitleKey(state.screen, existing))
                            }
                        } else {
                            Text(localizedText(contextualFormTitleKey(state.screen, existing)),
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    if (state.screen == UiScreen.CLOSE_BATCH) {
                        state.items.firstOrNull { it.id == state.selectedItemId }?.let { batch ->
                            Text(localizedOrAuthored(batch.titleKey, batch.authoredTitle),
                                style = MaterialTheme.typography.titleMedium)
                            ProjectionContent(batch)
                        }
                    }
                    if (state.screen == UiScreen.CLOSE_BATCH &&
                        state.fields.firstOrNull { it.id == "outcome" }?.value == "harvested") {
                        StageIllustration("stage_harvest")
                    }
                    val batchField = state.fields.firstOrNull { it.id == "batchId" }
                    batchField?.options?.firstOrNull { it.id == batchField.value }?.let {
                        Text(optionDisplayText(it))
                    }
                    state.errorKey?.let { Text(localizedText(it), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            itemsIndexed(fields, key = { _, field -> field.id }) { index, field ->
                Box(if (index == firstError) Modifier.focusRequester(errorFocus) else Modifier) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (field.id == "photoId") ObservationPhotoPreview(state)
                        FieldControl(field, onAction)
                    }
                }
            }
            if (existing && state.screen != UiScreen.CLOSE_BATCH) item {
                TextButton(onClick = { onAction(UiAction.Submit(contextualDeleteAction(state.screen))) }, enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    LabeledActionContent(contextualDeleteAction(state.screen), "form_delete_record")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onAction(UiAction.Back) }, enabled = !state.busy) { Text(localizedText("temporal_cancel")) }
            Button(onClick = { onAction(UiAction.Submit(contextualSaveAction(state.screen, existing))) },
                modifier = Modifier.weight(1f), enabled = !state.busy) {
                LabeledActionContent(contextualSaveAction(state.screen, existing), "form_save")
            }
        }
    }
}

internal fun contextualSaveAction(screen: UiScreen, existing: Boolean): String = when (screen) {
    UiScreen.ZONES -> if (existing) "update-zone" else "save-zone"
    UiScreen.TRAY_SIZES -> if (existing) "update-tray-size" else "save-tray-size"
    UiScreen.OBSERVATION_EDITOR -> "save-observation"
    UiScreen.CLOSE_BATCH -> "save-outcome"
    else -> error("Not a contextual form: $screen")
}

private fun contextualDeleteAction(screen: UiScreen): String = when (screen) {
    UiScreen.ZONES -> "delete-zone"
    UiScreen.TRAY_SIZES -> "delete-tray-size"
    UiScreen.OBSERVATION_EDITOR -> "delete-observation"
    else -> error("No delete action for $screen")
}

private fun contextualFormTitleKey(screen: UiScreen, existing: Boolean): String = when (screen) {
    UiScreen.ZONES -> if (existing) "form_edit_zone" else "form_new_zone"
    UiScreen.TRAY_SIZES -> if (existing) "form_edit_tray" else "form_new_tray"
    UiScreen.OBSERVATION_EDITOR -> if (existing) "form_edit_observation" else "form_new_observation"
    UiScreen.CLOSE_BATCH -> "screen_close_batch_title"
    else -> error("Not a contextual form: $screen")
}
