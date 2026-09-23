package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import com.sedsoftware.sproutpace.component.REMOVE_PHOTO_VALUE
import coil3.compose.AsyncImage
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sproutpace.shared.compose.generated.resources.*

@Composable
internal fun ScreenContent(
    state: SproutPaceUiState,
    layout: UiLayout,
    padding: PaddingValues,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime? = null,
) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        when (state.variant) {
            UiVariant.ERROR -> MessageState(
                titleKey = "state_error_title",
                bodyKey = state.errorKey ?: "state_error_body",
                actionKey = "action_retry",
                onClick = { onAction(UiAction.Retry) },
            )
            UiVariant.PROGRESS -> ProgressState()
            UiVariant.DESTRUCTIVE_CONFIRMATION -> MessageState(
                titleKey = "confirmation_title",
                bodyKey = "confirmation_body",
                actionKey = "action_back",
                onClick = { onAction(UiAction.Back) },
            )
            else -> ModelDrivenScreen(
                state, layout, contextFieldsExpanded, onContextFieldsExpandedChange, onAction, advertisingRuntime,
            )
        }
    }
}

@Composable
private fun ModelDrivenScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    val twoPane = layout >= UiLayout.EXPANDED && state.screen in twoPaneScreens
    if (twoPane) {
        Row(Modifier.fillMaxSize().semantics { testTag = "adaptive-two-pane" }) {
            ModelListPane(state, onAction, advertisingRuntime, Modifier.weight(LIST_PANE_WEIGHT).fillMaxHeight())
            VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
            ModelDetailPane(
                state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
                Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxHeight(),
            )
        }
    } else {
        ModelSinglePane(state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction, advertisingRuntime)
    }
}

@Composable
private fun ModelSinglePane(
    state: SproutPaceUiState,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().semantics { testTag = "adaptive-single-pane" },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        screenHero(state)
        trayCategoryTabs(state, onAction)
        statusItems(state)
        itemCollection(state, onAction, advertisingRuntime)
        if (!state.hidesContextForEmptyState() && state.facts.isNotEmpty()) {
            item { SectionHeading("section_details") }
            items(state.facts, key = { fact -> modelListKey(ModelListEntryKind.FACT, fact.id) }) { fact ->
                FactCard(fact)
            }
        }
        actionItems(state, onAction)
        if (!state.hidesContextForEmptyState()) {
            fieldItems(state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction)
        }
    }
}

@Composable
private fun ModelListPane(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        trayCategoryTabs(state, onAction)
        statusItems(state)
        itemCollection(state, onAction, advertisingRuntime)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.trayCategoryTabs(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
) {
    if (state.screen != UiScreen.TRAYS) return
    item {
        PrimaryTabRow(selectedTabIndex = TrayCategory.entries.indexOf(state.trayCategory)) {
            TrayCategory.entries.forEach { category ->
                Tab(
                    selected = category == state.trayCategory,
                    onClick = { onAction(UiAction.SelectTrayCategory(category)) },
                    text = { Text(localizedText(category.labelKey)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .semantics { testTag = "trays-category-${category.id}" },
                )
            }
        }
    }
}

@Composable
private fun ModelDetailPane(
    state: SproutPaceUiState,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        screenHero(state)
        if (!state.hidesContextForEmptyState() && state.facts.isNotEmpty()) {
            item { SectionHeading("section_details") }
            items(state.facts, key = { fact -> modelListKey(ModelListEntryKind.FACT, fact.id) }) { fact ->
                FactCard(fact)
            }
        }
        actionItems(state, onAction)
        if (!state.hidesContextForEmptyState()) {
            fieldItems(state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.fieldItems(
    state: SproutPaceUiState,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
) {
    if (state.screen == UiScreen.ADVERTISING_PRIVACY) return
    if (state.fields.isEmpty()) return
    val progressivelyDisclosed = state.screen in contextFieldScreens
    if (!progressivelyDisclosed || contextFieldsExpanded) {
        item { SectionHeading(if (progressivelyDisclosed) "section_action_details" else "section_form") }
        items(state.fields, key = { field -> modelListKey(ModelListEntryKind.FIELD, field.id) }) { field ->
            FieldControl(field, onAction)
        }
    }
    if (progressivelyDisclosed && state.variant != UiVariant.VALIDATION_ERROR) {
        item {
            OutlinedButton(
                onClick = { onContextFieldsExpandedChange(!contextFieldsExpanded) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { testTag = "toggle-context-fields" },
            ) {
                Text(localizedText(if (contextFieldsExpanded) "action_hide_action_details" else "action_show_action_details"))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.screenHero(state: SproutPaceUiState) {
    when (state.screen) {
        UiScreen.FIRST_RUN -> item {
            Image(
                painter = painterResource(Res.drawable.logo_sproutpace),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
        }
        UiScreen.SETTINGS -> item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo_sproutpace),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        UiScreen.RECIPE -> state.fields.firstOrNull { it.id == "cultureId" }
            ?.value
            ?.takeIf(String::isNotBlank)
            ?.let(::cultureImage)
            ?.let { cover ->
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 192.dp, height = 120.dp),
                        )
                    }
                }
            }
        else -> Unit
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statusItems(state: SproutPaceUiState) {
    if (state.variant == UiVariant.EMPTY) {
        if (state.showsEmptyIllustration()) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        imageVector = vectorResource(Res.drawable.illustration_empty_today),
                        contentDescription = null,
                        modifier = Modifier.size(width = 240.dp, height = 160.dp),
                    )
                }
            }
        }
        val (titleKey, bodyKey) = state.screen.emptyStateCopy()
        item { StatusCard(titleKey, bodyKey) }
    }
    if (state.variant == UiVariant.PERMISSION_DENIED) {
        item { StatusCard("state_permission_title", "state_permission_body") }
    }
    if (state.variant == UiVariant.CONFLICT || state.variant == UiVariant.TEMPORARILY_UNASSIGNED) {
        item {
            StatusCard(
                titleKey = if (state.variant == UiVariant.TEMPORARILY_UNASSIGNED) {
                    "state_temporarily_unassigned_title"
                } else {
                    "capacity_conflict_title"
                },
                bodyKey = if (state.variant == UiVariant.TEMPORARILY_UNASSIGNED) {
                    "state_temporarily_unassigned_body"
                } else {
                    "capacity_conflict_body"
                },
                warning = true,
            )
        }
        items(state.conflictKeys) { key -> Text(localizedText(key), style = MaterialTheme.typography.bodyMedium) }
    }
    if (state.variant == UiVariant.RESTORE_PREVIEW) {
        item { StatusCard("restore_preview_title", "restore_replace_body", warning = true) }
    }
    if (state.variant == UiVariant.PRIVACY_DECISION) {
        item { StatusCard("privacy_heading", "privacy_decision_body") }
    }
    if (state.variant == UiVariant.VALIDATION_ERROR) {
        item { StatusCard("state_validation_title", "state_validation_body", warning = true) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemCollection(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    if (state.items.isNotEmpty()) item { SectionHeading("section_items") }
    itemsIndexed(
        state.items,
        key = { _, item -> modelListKey(ModelListEntryKind.ITEM, item.id) },
    ) { index, item ->
        ItemCard(state.screen, item, onAction)
        val insertionIndex = state.inlineAdInsertionIndexOrNull()
        if (state.showInlineAd && insertionIndex != null && index == insertionIndex - 1) {
            InlineAdSlot(state.inlineAdUnitId, advertisingRuntime)
        }
    }
}

internal fun SproutPaceUiState.inlineAdInsertionIndexOrNull(): Int? = when {
    screen == UiScreen.CATALOG && items.size >= CATALOG_AD_MINIMUM_ITEMS -> CATALOG_AD_INSERTION_INDEX
    screen == UiScreen.TRAYS && trayCategory == TrayCategory.HISTORY && items.size >= HISTORY_AD_MINIMUM_ITEMS ->
        HISTORY_AD_INSERTION_INDEX
    else -> null
}

private const val LIST_PANE_WEIGHT = 1.1f
private const val DETAIL_PANE_WEIGHT = 0.9f
private const val CATALOG_AD_MINIMUM_ITEMS = 5
private const val CATALOG_AD_INSERTION_INDEX = 4
private const val HISTORY_AD_MINIMUM_ITEMS = 6
private const val HISTORY_AD_INSERTION_INDEX = 5

internal fun SproutPaceUiState.showsEmptyIllustration(): Boolean =
    variant == UiVariant.EMPTY && screen in emptyIllustrationScreens

internal fun SproutPaceUiState.hidesContextForEmptyState(): Boolean =
    variant == UiVariant.EMPTY && screen in emptyIllustrationScreens

internal fun UiScreen.emptyStateCopy(): Pair<String, String> = when (this) {
    UiScreen.TODAY -> "today_empty_title" to "today_empty_body"
    UiScreen.TRAYS -> "trays_empty_title" to "trays_empty_body"
    UiScreen.PLAN -> "plan_empty_title" to "plan_empty_body"
    else -> "state_empty_title" to "state_empty_body"
}

private fun androidx.compose.foundation.lazy.LazyListScope.actionItems(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
) {
    if (state.actions.isNotEmpty()) item { SectionHeading("section_actions") }
    items(state.actions, key = { action -> modelListKey(ModelListEntryKind.ACTION, action.id) }) { action ->
        val dispatch = { onAction(action.toAdvertisingUiAction() ?: UiAction.Submit(action.id)) }
        if (action.primary) {
            Button(
                onClick = dispatch,
                enabled = action.enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { testTag = "action-${action.id}" },
            ) { LabeledActionContent(action.id, action.labelKey) }
        } else {
            val destructive = action.id in destructiveActionIds
            val destructiveDescription = if (destructive) localizedText("state_destructive_action") else null
            OutlinedButton(
                onClick = dispatch,
                enabled = action.enabled,
                colors = if (destructive) {
                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics {
                        testTag = "action-${action.id}"
                        destructiveDescription?.let { stateDescription = it }
                    },
            ) { LabeledActionContent(action.id, action.labelKey) }
        }
    }
    if (state.hasUnsavedChanges) {
        item {
            TextButton(
                onClick = { onAction(UiAction.CancelDraft) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(localizedText("action_discard_changes")) }
        }
    }
}

internal enum class ModelListEntryKind(private val keyPrefix: String) {
    ITEM("item"),
    FACT("fact"),
    ACTION("action"),
    FIELD("field");

    fun key(id: String): String = "$keyPrefix:$id"
}

internal fun modelListKey(kind: ModelListEntryKind, id: String): String = kind.key(id)

@Composable
private fun ItemCard(screen: UiScreen, item: UiItem, onAction: (UiAction) -> Unit) {
    val selectionDescription = localizedText(
        if (item.selected) "state_selected" else "state_not_selected",
    )
    Card(
        onClick = { onAction(UiAction.Select(item.id)) },
        colors = CardDefaults.cardColors(
            containerColor = if (item.selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = selectionDescription
            testTag = "item-${item.id}"
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (screen in stageContentScreens) {
                stageImage(item)?.let { image ->
                    Image(
                        imageVector = vectorResource(image),
                        contentDescription = null,
                        modifier = Modifier.size(width = 96.dp, height = 72.dp),
                    )
                }
            }
            item.photo?.let { photo ->
                if (photo.bytes != null) {
                    AsyncImage(
                        model = photo.bytes,
                        contentDescription = localizedText(photo.contentDescriptionKey),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(144.dp)
                            .semantics { testTag = "photo-${photo.id}" },
                    )
                } else {
                    Image(
                        imageVector = vectorResource(Res.drawable.ic_add_photo),
                        contentDescription = localizedText(photo.contentDescriptionKey),
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            cultureImage(item.id)?.let { image ->
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                )
            }
            Text(
                text = localizedOrAuthored(item.titleKey, item.authoredTitle),
                style = MaterialTheme.typography.titleMedium,
            )
            item.supportingKey?.let { Text(localizedText(it), style = MaterialTheme.typography.bodyMedium) }
            item.stateKey?.let { Text(localizedText(it), style = MaterialTheme.typography.labelMedium) }
            item.actionId?.let { actionId ->
                TextButton(
                    onClick = { onAction(UiAction.Submit(actionId)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { LabeledActionContent(actionId, "action_${actionId.replace('-', '_')}") }
            }
            item.commands.forEach { command ->
                TextButton(
                    onClick = { onAction(UiAction.SubmitItem(command.operation, command.subjectId)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .semantics { testTag = "item-${item.id}-${command.operation}" },
                ) { LabeledActionContent(command.operation, "action_${command.operation.replace('-', '_')}") }
            }
        }
    }
}

@Composable
private fun FactCard(fact: UiFact) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(localizedText(fact.labelKey), style = MaterialTheme.typography.labelLarge)
            Text(localizedOrAuthored(fact.valueKey, fact.authoredValue), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FieldControl(field: UiField, onAction: (UiAction) -> Unit) {
    when (field.kind) {
        ScreenFieldKind.BOOLEAN -> BooleanField(field, onAction)
        ScreenFieldKind.SINGLE_CHOICE -> SingleChoiceField(field, onAction)
        ScreenFieldKind.MULTI_CHOICE -> MultiChoiceField(field, onAction)
        ScreenFieldKind.DOCUMENT -> PlatformPickerField(
            field, PlatformActionKind.CHOOSE_DOCUMENT, "action_choose_file", onAction,
        )
        else -> if (field.id == "photoId") {
            PlatformPickerField(field, PlatformActionKind.CHOOSE_PHOTO, "action_choose_photo", onAction)
        } else {
            TextEntryField(field, onAction)
        }
    }
}

@Composable
private fun TextEntryField(field: UiField, onAction: (UiAction) -> Unit) {
    val errorText = field.errorKey?.let { localizedText(it) }
    val keyboardType = when (field.kind) {
        ScreenFieldKind.POSITIVE_INTEGER, ScreenFieldKind.POSITIVE_LONG,
        ScreenFieldKind.NON_NEGATIVE_LONG -> KeyboardType.Number
        ScreenFieldKind.POSITIVE_DECIMAL -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
    OutlinedTextField(
        value = field.value,
        onValueChange = { onAction(UiAction.EditField(field.id, it)) },
        modifier = Modifier.fillMaxWidth().semantics {
            testTag = "field-${field.id}"
            errorText?.let { error(it) }
        },
        enabled = !field.readOnly,
        label = { Text(localizedText(field.labelKey)) },
        supportingText = {
            Text(errorText ?: localizedText(if (field.required) "field_required_supporting" else "field_optional_supporting"))
        },
        isError = errorText != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = field.kind != ScreenFieldKind.TEXT || field.id !in multilineFieldIds,
        minLines = if (field.id in multilineFieldIds) 3 else 1,
    )
}

@Composable
private fun BooleanField(field: UiField, onAction: (UiAction) -> Unit) {
    val checked = field.value.toBooleanStrictOrNull() == true
    val stateText = localizedText(if (checked) "state_enabled" else "state_disabled")
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                enabled = !field.readOnly,
                role = Role.Switch,
                onValueChange = { onAction(UiAction.EditField(field.id, it.toString())) },
            )
            .semantics {
                testTag = "field-${field.id}"
                stateDescription = stateText
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(localizedText(field.labelKey), style = MaterialTheme.typography.titleMedium)
            Text(stateText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = !field.readOnly)
    }
}

@Composable
private fun SingleChoiceField(field: UiField, onAction: (UiAction) -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { testTag = "field-${field.id}" }) {
        FieldHeading(field)
        field.options.forEach { option ->
            val selected = option.id == field.value
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        enabled = !field.readOnly,
                        role = Role.RadioButton,
                        onClick = { onAction(UiAction.ChooseOption(field.id, option.id)) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = !field.readOnly)
                Text(localizedOrAuthored(option.labelKey, option.authoredLabel), Modifier.padding(start = 8.dp))
            }
        }
        FieldError(field)
    }
}

@Composable
private fun MultiChoiceField(field: UiField, onAction: (UiAction) -> Unit) {
    val selected = field.value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    Column(Modifier.fillMaxWidth().semantics { testTag = "field-${field.id}" }) {
        FieldHeading(field)
        field.options.forEach { option ->
            val checked = option.id in selected
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(
                        value = checked,
                        enabled = !field.readOnly,
                        role = Role.Checkbox,
                        onValueChange = { becomesChecked ->
                            val updated = if (becomesChecked) selected + option.id else selected - option.id
                            onAction(UiAction.EditField(field.id, updated.sorted().joinToString(",")))
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = !field.readOnly)
                Text(localizedOrAuthored(option.labelKey, option.authoredLabel), Modifier.padding(start = 8.dp))
            }
        }
        FieldError(field)
    }
}

@Composable
private fun PlatformPickerField(
    field: UiField,
    kind: PlatformActionKind,
    actionLabelKey: String,
    onAction: (UiAction) -> Unit,
) {
    val hasPhoto = field.value.isNotBlank() && field.value != REMOVE_PHOTO_VALUE
    val statusKey = when {
        field.value == REMOVE_PHOTO_VALUE -> "observation_photo_removed"
        hasPhoto -> "observation_photo_selected"
        else -> "observation_photo_none"
    }
    Column(Modifier.fillMaxWidth().semantics { testTag = "field-${field.id}" }) {
        OutlinedTextField(
            value = localizedText(statusKey),
            onValueChange = {},
            readOnly = true,
            label = { Text(localizedText(field.labelKey)) },
            modifier = Modifier.fillMaxWidth(),
            isError = field.errorKey != null,
        )
        OutlinedButton(
            onClick = {
                onAction(
                    UiAction.RequestPlatform(
                        PlatformActionRequest(kind, field.id),
                    ),
                )
            },
            enabled = !field.readOnly,
            modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp),
        ) {
            LabeledActionContent(
                actionId = if (hasPhoto) "replace-photo" else "choose-photo",
                labelKey = if (hasPhoto) "action_replace_photo" else actionLabelKey,
            )
        }
        if (hasPhoto) {
            TextButton(
                onClick = { onAction(UiAction.RemovePhoto(field.id, field.value)) },
                enabled = !field.readOnly,
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { testTag = "remove-${field.id}" },
            ) { LabeledActionContent("remove-photo", "action_remove_photo") }
        }
        FieldError(field)
    }
}

@Composable
private fun FieldHeading(field: UiField) {
    Text(localizedText(field.labelKey), style = MaterialTheme.typography.titleMedium)
    Text(
        localizedText(if (field.required) "field_required_supporting" else "field_optional_supporting"),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FieldError(field: UiField) {
    field.errorKey?.let { key ->
        val text = localizedText(key)
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { error(text) },
        )
    }
}

@Composable
private fun SectionHeading(key: String) = Text(
    text = localizedText(key),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
)

@Composable
private fun StatusCard(titleKey: String, bodyKey: String, warning: Boolean = false) {
    val container = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(localizedText(titleKey), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(localizedText(bodyKey), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MessageState(titleKey: String, bodyKey: String, actionKey: String, onClick: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text(localizedText(titleKey), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
        item { Spacer(Modifier.height(8.dp)) }
        item { Text(localizedText(bodyKey), style = MaterialTheme.typography.bodyLarge) }
        item { Spacer(Modifier.height(16.dp)) }
        item { Button(onClick, Modifier.heightIn(min = 48.dp)) { Text(localizedText(actionKey)) } }
    }
}

@Composable
private fun ProgressState() = Column(
    Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.state_progress_body))
}

@Composable
private fun LabeledActionContent(actionId: String, labelKey: String) {
    actionIcon(actionId)?.let { icon ->
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(localizedText(labelKey))
}

internal fun actionIcon(actionId: String): DrawableResource? = when (actionId) {
    in addActionIds -> Res.drawable.ic_add
    in editActionIds -> Res.drawable.ic_edit
    in deleteActionIds, "remove-photo" -> Res.drawable.ic_delete
    "choose-photo", "replace-photo" -> Res.drawable.ic_add_photo
    in notificationActionIds -> Res.drawable.ic_notifications
    in backupActionIds -> Res.drawable.ic_backup
    in repeatActionIds -> Res.drawable.ic_repeat
    in completeActionIds -> Res.drawable.ic_check_circle
    else -> null
}

private fun stageImage(item: UiItem): DrawableResource? = sequenceOf(
    item.titleKey,
    item.supportingKey,
    item.stateKey,
).filterNotNull().mapNotNull { key ->
    when (key) {
        "stage_soaking" -> Res.drawable.stage_soaking
        "stage_sown" -> Res.drawable.stage_sown
        "stage_blackout" -> Res.drawable.stage_blackout
        "stage_light_growth" -> Res.drawable.stage_light_growth
        "stage_harvest" -> Res.drawable.stage_harvest
        else -> null
    }
}.firstOrNull()

internal fun cultureImage(id: String): DrawableResource? = when (id.substringAfterLast(':')) {
    "radish" -> Res.drawable.culture_radish
    "broccoli" -> Res.drawable.culture_broccoli
    "arugula" -> Res.drawable.culture_arugula
    "mustard" -> Res.drawable.culture_mustard
    "pea" -> Res.drawable.culture_pea
    "sunflower" -> Res.drawable.culture_sunflower
    "basil" -> Res.drawable.culture_basil
    "garden_cress", "cress" -> Res.drawable.culture_garden_cress
    else -> null
}

private val twoPaneScreens = setOf(
    UiScreen.FIRST_RUN, UiScreen.TRAYS, UiScreen.BATCH_EDITOR, UiScreen.BATCH_DETAIL,
    UiScreen.PLAN, UiScreen.CATALOG, UiScreen.RECIPE, UiScreen.OBSERVATION_EDITOR,
    UiScreen.SETTINGS, UiScreen.ZONES, UiScreen.TRAY_SIZES, UiScreen.BACKUP_RESTORE,
    UiScreen.ADVERTISING_PRIVACY,
)

private val multilineFieldIds = setOf("text", "note", "reason", "assessment")

internal val contextFieldScreens = setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.BATCH_DETAIL, UiScreen.PLAN)

private val destructiveActionIds = setOf(
    "delete-batch", "delete-observation", "delete-plan", "delete-recipe", "delete-tray-size",
    "delete-zone", "erase-all", "delete-all-data", "restore-backup", "undo-stage",
)

private val stageContentScreens = setOf(UiScreen.BATCH_DETAIL, UiScreen.RECIPE, UiScreen.CLOSE_BATCH)
private val emptyIllustrationScreens = setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN)

private val addActionIds = setOf(
    "new-batch", "save-batch", "add-observation", "create-recipe", "add-zone", "add-preset",
)
private val editActionIds = setOf(
    "edit", "edit-cadence", "correct-batch", "update-plan", "update-recipe", "update-zone", "update-tray-size",
)
private val deleteActionIds = setOf(
    "delete-batch", "delete-observation", "delete-plan", "delete-recipe", "delete-tray-size", "delete-zone",
    "erase-all", "delete-all-data",
)
private val notificationActionIds = setOf(
    "open-notifications", "request-notification-permission", "update-notifications",
)
private val backupActionIds = setOf(
    "open-backup-restore", "create-backup", "preview-restore", "restore-backup", "export-csv",
)
private val repeatActionIds = setOf("repeat-batch", "duplicate-recipe", "undo-stage")
private val completeActionIds = setOf(
    "complete-action", "confirm-stage", "confirm-sowing", "confirm-suggestion", "save-outcome",
)
