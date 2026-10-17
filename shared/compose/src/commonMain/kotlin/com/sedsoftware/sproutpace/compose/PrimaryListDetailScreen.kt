package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime

private const val DETAIL_PANE_WEIGHT = 1.7f

/** Expanded primary screens keep a real selected record beside their collection. */
@Composable
internal fun PrimaryListDetailScreen(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime? = null,
) {
    val selectedItem = state.items.firstOrNull { it.id == state.selectedItemId }
    Row(Modifier.fillMaxSize().semantics { testTag = "${state.screen.name.lowercase()}-list-detail" }) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.screen == UiScreen.TRAYS) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrayCategory.entries.forEach { category ->
                        FilterChip(selected = category == state.trayCategory,
                            onClick = { onAction(UiAction.SelectTrayCategory(category)) },
                            label = { Text(localizedText(category.labelKey)) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            state.fields.firstOrNull { it.id == "query" }?.let { field -> item { FieldControl(field, onAction) } }
            item {
                val action = if (state.screen == UiScreen.TRAYS) "new-batch" else "create-recipe"
                TextButton(onClick = { onAction(UiAction.Submit(action)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(localizedText("action_${action.replace('-', '_')}"))
                }
            }
            if (state.items.isEmpty()) item {
                val copy = state.screen.emptyStateCopy()
                Text(localizedText(copy.first), style = MaterialTheme.typography.titleMedium)
                Text(localizedText(copy.second))
            }
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                SelectableRecordCard(item, item.id == selectedItem?.id, onAction) { onAction(UiAction.Select(item.id)) }
                val insertion = state.inlineAdInsertionIndexOrNull()
                if (state.showInlineAd && insertion != null && index == insertion - 1) {
                    InlineAdSlot(state.inlineAdUnitId, advertisingRuntime)
                }
            }
        }
        VerticalDivider(Modifier.fillMaxHeight())
        Column(Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxHeight()) {
            if (selectedItem == null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("list_detail_select_title"), style = MaterialTheme.typography.titleLarge)
                    Text(localizedText("list_detail_select_body"))
                }
            } else key(selectedItem.id) {
                val operation = if (state.screen == UiScreen.TRAYS) "open-batch" else "open-recipe"
                OutlinedButton(onClick = { onAction(UiAction.Submit(operation, selectedItem.id)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                    Text(localizedText("action_open_details"))
                }
                state.detail?.takeIf { it.selectedItemId == selectedItem.id }?.let { detail ->
                    if (detail.variant == UiVariant.ERROR) {
                        TextButton(onClick = { onAction(UiAction.Retry) }, modifier = Modifier.padding(horizontal = 16.dp)
                            .semantics { testTag = "action-retry-detail" }) {
                            Text(localizedText("action_retry"))
                        }
                    }
                    if (state.screen == UiScreen.TRAYS) {
                        BatchDetailScreen(detail.copy(actions = emptyList()), onAction = { })
                    } else {
                        RecipeScreen(detail, UiLayout.COMPACT, onAction, readOnlyPane = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableRecordCard(item: UiItem, isSelected: Boolean, onAction: (UiAction) -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().semantics { selected = isSelected; testTag = "select-${item.id}" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            item.stateKey?.let { Text(localizedText(it), style = MaterialTheme.typography.bodyMedium) }
            if (item.batch != null) ProjectionContent(item)
            if (item.occupancy?.overflow?.let { it > 0 } == true) {
                Text(localizedText("capacity_conflict_title"), color = MaterialTheme.colorScheme.error)
                item.commands.forEach { command ->
                    TextButton(onClick = { onAction(UiAction.SubmitItem(command.operation, command.subjectId)) }) {
                        Text(localizedText("change_capacity"))
                    }
                }
            }
            item.recipe?.scientificNameKey?.let { Text(localizedText(it.value), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
