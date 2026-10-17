package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        if (state.keepsContentOnFailure()) {
            Column(Modifier.fillMaxSize()) {
                if (state.errorKey != null && (state.screen in setOf(UiScreen.FIRST_RUN, UiScreen.BATCH_EDITOR, UiScreen.PLAN) ||
                        (state.screen in setOf(UiScreen.ZONES, UiScreen.TRAY_SIZES) && state.selectedItemId == null))) {
                    Text(localizedText(state.errorKey), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                if (state.variant == UiVariant.ERROR) {
                    TextButton(onClick = { onAction(UiAction.Retry) }, modifier = Modifier.padding(horizontal = 16.dp)
                        .semantics { testTag = "action-retry" }) {
                        Text(localizedText("action_retry"))
                    }
                }
                Box(Modifier.weight(1f)) {
                    ModelDrivenScreen(state, layout, contextFieldsExpanded, onContextFieldsExpandedChange, onAction, advertisingRuntime)
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            return@Box
        }
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
        if (state.busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
}

internal fun SproutPaceUiState.keepsContentOnFailure(): Boolean =
    screen in setOf(UiScreen.NOTIFICATIONS, UiScreen.BACKUP_RESTORE) ||
        ((fields.isNotEmpty() || items.isNotEmpty()) && screen in setOf(
            UiScreen.FIRST_RUN, UiScreen.BATCH_EDITOR, UiScreen.BATCH_DETAIL, UiScreen.RECIPE, UiScreen.PLAN,
            UiScreen.OBSERVATION_EDITOR, UiScreen.CLOSE_BATCH, UiScreen.ZONES, UiScreen.TRAY_SIZES,
        ))

@Composable
private fun ModelDrivenScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    if (layout >= UiLayout.EXPANDED && (state.screen == UiScreen.SETTINGS || state.settingsContext != null)) {
        SettingsListDetail(state, onAction) {
            ModelDrivenScreen(state, UiLayout.MEDIUM, contextFieldsExpanded, onContextFieldsExpandedChange,
                onAction, advertisingRuntime)
        }
        return
    }
    if (layout >= UiLayout.EXPANDED && state.screen in setOf(UiScreen.TRAYS, UiScreen.CATALOG)) {
        PrimaryListDetailScreen(state, onAction, advertisingRuntime)
        return
    }
    when (state.screen) {
        UiScreen.FIRST_RUN -> FirstRunScreen(state, onAction)
        UiScreen.TODAY -> TodayScreen(
            state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
        )
        UiScreen.TRAYS -> TraysScreen(
            state, layout, contextFieldsExpanded, onContextFieldsExpandedChange, onAction, advertisingRuntime,
        )
        UiScreen.PLAN -> PlanScreen(
            state, layout, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
        )
        UiScreen.CATALOG -> CatalogScreen(state, onAction, advertisingRuntime)
        UiScreen.BATCH_EDITOR -> BatchEditorScreen(state, layout, onAction)
        UiScreen.BATCH_DETAIL -> BatchDetailScreen(state, onAction)
        UiScreen.SETTINGS -> SettingsScreen(state, layout, onAction)
        UiScreen.RECIPE -> RecipeScreen(state, layout, onAction)
        UiScreen.NOTIFICATIONS -> NotificationsScreen(state, onAction)
        UiScreen.BACKUP_RESTORE -> BackupRestoreScreen(state, onAction)
        UiScreen.OBSERVATION_EDITOR, UiScreen.CLOSE_BATCH, UiScreen.ZONES, UiScreen.TRAY_SIZES ->
            ContextualFormsScreen(state, layout, onAction)
        else -> Unit
    }
    if (state.screen in dedicatedScreenLayouts) return
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

internal enum class FirstRunStep(
    val titleKey: String,
    val bodyKey: String,
) {
    ZONE("first_run_step", "first_run_step_zone_body"),
    TRAY("first_run_step_tray", "first_run_step_tray_body"),
    PLAN("first_run_step_plan", "first_run_step_plan_body"),
}

@Composable
internal fun FirstRunScreen(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    initialStepIndex: Int = 0,
) {
    val firstInvalidStep = state.fields.firstNotNullOfOrNull { field ->
        field.errorKey?.let { firstRunStepForField(field.id).ordinal }
    }
    var stepIndex by rememberSaveable(state.selectedItemId) {
        mutableIntStateOf(firstInvalidStep ?: initialStepIndex.coerceIn(0, FirstRunStep.entries.lastIndex))
    }
    LaunchedEffect(firstInvalidStep) {
        if (firstInvalidStep != null) stepIndex = firstInvalidStep
    }
    val step = FirstRunStep.entries[stepIndex]
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(step) { focusRequester.requestFocus() }
    val shape = state.fields.firstOrNull { it.id == "shape" }?.value
    val visibleFields = state.fields.filter { field -> field.id in firstRunFieldIds(step, shape) &&
        (field.id !in setOf("darkZoneName", "darkZoneCapacity") || state.fields.any { it.id == "darkZoneEnabled" && it.value == "true" }) &&
        (field.id != "summaryTime" || state.fields.any { it.id == "dailySummaryEnabled" && it.value == "true" }) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .semantics { testTag = "first-run-steps" },
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.variant == UiVariant.VALIDATION_ERROR) {
                    item { StatusCard("state_validation_title", "state_validation_body", warning = true) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            painter = painterResource(Res.drawable.logo_sproutpace),
                            contentDescription = null,
                            modifier = Modifier.size(88.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        )
                        Text(localizedText("first_run_welcome"), style = MaterialTheme.typography.headlineMedium)
                        Text(localizedText("first_run_promise"), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item { FirstRunProgress(step, focusRequester) }
                items(visibleFields, key = { field -> modelListKey(ModelListEntryKind.FIELD, field.id) }) { field ->
                    FieldControl(field, onAction)
                }
                if (step == FirstRunStep.PLAN) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("notifications_permission"), style = MaterialTheme.typography.titleMedium)
                        Text(localizedText(notificationPermissionStatusKey(state.permissionState)))
                        Text(localizedText("notifications_permission_body"))
                        if (state.permissionState in setOf(
                                com.sedsoftware.sproutpace.component.PermissionState.NOT_REQUESTED,
                                com.sedsoftware.sproutpace.component.PermissionState.DENIED,
                            )) {
                            OutlinedButton(onClick = { onAction(UiAction.Submit("request-notification-permission")) }) {
                                Text(localizedText("action_request_permission"))
                            }
                        }
                        if (state.permissionState in setOf(
                                com.sedsoftware.sproutpace.component.PermissionState.DENIED,
                                com.sedsoftware.sproutpace.component.PermissionState.PERMANENTLY_DENIED,
                            )) {
                            TextButton(onClick = { onAction(UiAction.Submit("open-system-settings")) }) {
                                Text(localizedText("action_open_settings"))
                            }
                        }
                    }
                }
            }
            FirstRunBottomActions(
                step = step,
                onNext = {
                    if (step == FirstRunStep.PLAN) onAction(UiAction.Submit("continue-setup")) else stepIndex += 1
                },
                onBack = { stepIndex -= 1 },
                onSkip = { onAction(UiAction.Navigate(UiScreen.CATALOG)) },
            )
        }
    }
}

@Composable
private fun FirstRunBottomActions(
    step: FirstRunStep,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    testTag = if (step == FirstRunStep.PLAN) "action-continue-setup" else "first-run-next"
                },
            ) { Text(localizedText(if (step == FirstRunStep.PLAN) "action_finish_setup" else "action_continue")) }
            if (step != FirstRunStep.ZONE) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { testTag = "first-run-back" },
                ) { Text(localizedText("action_back")) }
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { testTag = "first-run-browse-catalog" },
            ) {
                Text(localizedText(if (step == FirstRunStep.ZONE) "action_browse_catalog" else "action_skip_setup"))
            }
        }
    }
}

@Composable
private fun FirstRunProgress(step: FirstRunStep, focusRequester: FocusRequester) {
    val progressText = localizedText(step.titleKey)
    Column(
        Modifier.fillMaxWidth().focusRequester(focusRequester).focusable()
            .semantics {
                heading()
                stateDescription = progressText
                testTag = "first-run-step-${step.ordinal + 1}"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(progressText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(
            progress = { (step.ordinal + 1) / FirstRunStep.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Text(localizedText(step.bodyKey), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun firstRunStepForField(fieldId: String): FirstRunStep = when (fieldId) {
    in firstRunZoneFieldIds -> FirstRunStep.ZONE
    in firstRunTrayAllFieldIds -> FirstRunStep.TRAY
    else -> FirstRunStep.PLAN
}

internal fun firstRunFieldIds(step: FirstRunStep, shape: String?): Set<String> = when (step) {
    FirstRunStep.ZONE -> firstRunZoneFieldIds
    FirstRunStep.TRAY -> firstRunTrayFieldIds + when (shape) {
        "circle" -> setOf("diameterCm")
        else -> setOf("widthCm", "lengthCm")
    }
    FirstRunStep.PLAN -> firstRunPlanFieldIds
}

private val firstRunZoneFieldIds = setOf("zoneName", "type", "capacity", "darkZoneEnabled", "darkZoneName", "darkZoneCapacity")
private val firstRunTrayFieldIds = setOf("trayName", "shape")
private val firstRunTrayAllFieldIds = firstRunTrayFieldIds + setOf("widthCm", "lengthCm", "diameterCm")
private val firstRunPlanFieldIds = setOf("cadenceDays", "cultureIds", "mode", "dailySummaryEnabled", "summaryTime")

@Composable
private fun SettingsScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    onAction: (UiAction) -> Unit,
) {
    val themeField = state.fields.firstOrNull { it.id == "theme" }
    val crashField = state.fields.firstOrNull { it.id == "enabled" }
    val privacyVisible = state.items.any { it.id == "privacy" }
    var themePickerOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = SETTINGS_CONTENT_MAX_WIDTH).fillMaxSize()
                .semantics { testTag = "settings-list" },
            contentPadding = PaddingValues(
                start = if (layout >= UiLayout.EXPANDED) 24.dp else 16.dp,
                top = 8.dp,
                end = if (layout >= UiLayout.EXPANDED) 24.dp else 16.dp,
                bottom = 32.dp,
            ),
        ) {
            item { SettingsBrandHeader() }
            settingsGrowingSection(onAction)
            settingsPreferencesSection(state, themeField, crashField, { themePickerOpen = true }, onAction)
            settingsDataSection(privacyVisible, onAction)
            settingsResetSection(onAction)
        }
    }

    if (themePickerOpen && themeField != null) {
        ThemePickerDialog(
            field = themeField,
            onDismiss = { themePickerOpen = false },
            onChoose = { value ->
                onAction(UiAction.ChooseOption(themeField.id, value))
                themePickerOpen = false
            },
        )
    }
}

@Composable
private fun SettingsBrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(Res.drawable.logo_sproutpace), null, Modifier.size(64.dp))
        Icon(vectorResource(Res.drawable.ic_settings), null, Modifier.size(24.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsGrowingSection(onAction: (UiAction) -> Unit) {
    item { SettingsSectionHeading("settings_growing_setup") }
    item {
        SettingsNavigationRow(
            "screen_zones_title", "settings_zones_summary", Res.drawable.ic_light_mode, "open-zones", onAction,
        )
    }
    item { SettingsDivider() }
    item {
        SettingsNavigationRow(
            "screen_tray_sizes_title", "settings_trays_summary", Res.drawable.ic_nav_trays, "open-tray-sizes", onAction,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsPreferencesSection(
    state: SproutPaceUiState,
    themeField: UiField?,
    crashField: UiField?,
    onChooseTheme: () -> Unit,
    onAction: (UiAction) -> Unit,
) {
    item { SettingsSectionHeading("settings_preferences") }
    themeField?.let { field ->
        item {
            SettingsNavigationRow(
                titleKey = "settings_theme",
                summaryKey = field.options.firstOrNull { it.id == field.value }?.labelKey ?: "settings_theme_system",
                icon = Res.drawable.ic_dark_mode,
                actionId = "choose-theme",
                onAction = { onChooseTheme() },
            )
        }
        item { SettingsDivider() }
    }
    item {
        val notifications = state.items.firstOrNull { it.id == "notifications" }
        SettingsNavigationRow(
            titleKey = "screen_notifications_title",
            summaryKey = notifications?.supportingKey ?: notifications?.stateKey ?: "settings_notifications_summary",
            icon = Res.drawable.ic_notifications,
            actionId = "open-notifications",
            onAction = onAction,
        )
    }
    crashField?.let { field ->
        item { SettingsDivider() }
        item {
            SettingsSwitchRow(
                titleKey = "settings_crash_reporting",
                summaryKey = "settings_crash_reporting_body",
                stateKey = state.items.firstOrNull { it.id == "crash-reporting" }?.stateKey,
                icon = Res.drawable.ic_check_circle,
                checked = field.value.toBooleanStrictOrNull() ?: false,
                enabled = !field.readOnly,
                onCheckedChange = { enabled -> onAction(UiAction.EditField(field.id, enabled.toString())) },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsDataSection(
    privacyVisible: Boolean,
    onAction: (UiAction) -> Unit,
) {
    item { SettingsSectionHeading("settings_data") }
    item {
        SettingsNavigationRow(
            "screen_backup_restore_title", "settings_backup_summary", Res.drawable.ic_backup,
            "open-backup-restore", onAction,
        )
    }
    if (privacyVisible) {
        item { SettingsDivider() }
        item {
            SettingsNavigationRow(
                "screen_advertising_privacy_title", "settings_privacy_summary", Res.drawable.ic_privacy,
                "open-advertising-privacy", onAction,
            )
        }
    }
    item { SettingsDivider() }
    item {
        SettingsNavigationRow(
            "action_privacy_policy", "settings_privacy_policy_summary", Res.drawable.ic_privacy,
            "open-privacy-policy", onAction,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsResetSection(onAction: (UiAction) -> Unit) {
    item { SettingsSectionHeading("settings_danger_zone", error = true) }
    item {
        SettingsActionRow(
            "action_delete_all_data", "settings_delete_data_summary", Res.drawable.ic_delete,
            "delete-all-data", onAction,
        )
    }
}

@Composable
private fun SettingsSectionHeading(key: String, error: Boolean = false) {
    Text(
        text = localizedText(key),
        style = MaterialTheme.typography.labelLarge,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsNavigationRow(
    titleKey: String,
    summaryKey: String,
    icon: DrawableResource,
    actionId: String,
    onAction: (UiAction) -> Unit,
) {
    ListItem(
        headlineContent = { Text(localizedText(titleKey)) },
        supportingContent = { Text(localizedText(summaryKey)) },
        leadingContent = { SettingsIcon(icon) },
        trailingContent = {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable { onAction(UiAction.Submit(actionId)) }
            .semantics { testTag = "settings-$actionId" },
    )
}

@Composable
private fun SettingsSwitchRow(
    titleKey: String,
    summaryKey: String,
    stateKey: String?,
    icon: DrawableResource,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accessibilityState = localizedText(if (checked) "state_enabled" else "state_disabled")
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                stateDescription = accessibilityState
                testTag = "settings-crash-reporting"
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(localizedText(titleKey), style = MaterialTheme.typography.bodyLarge)
            Text(
                localizedText(summaryKey),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stateKey?.let {
                Text(
                    localizedText(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingsActionRow(
    titleKey: String,
    summaryKey: String,
    icon: DrawableResource,
    actionId: String,
    onAction: (UiAction) -> Unit,
) {
    val destructiveDescription = localizedText("state_destructive_action")
    ListItem(
        headlineContent = { Text(localizedText(titleKey), color = MaterialTheme.colorScheme.error) },
        supportingContent = {
            Text(localizedText(summaryKey), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = { SettingsIcon(icon, tint = MaterialTheme.colorScheme.error) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable { onAction(UiAction.Submit(actionId)) }
            .semantics {
                stateDescription = destructiveDescription
                testTag = "settings-$actionId"
            },
    )
}

@Composable
private fun SettingsIcon(
    icon: DrawableResource,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(imageVector = vectorResource(icon), contentDescription = null, tint = tint)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = SETTINGS_DIVIDER_INSET))
}

@Composable
private fun ThemePickerDialog(
    field: UiField,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("settings_theme")) },
        text = {
            Column {
                field.options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(
                                selected = option.id == field.value,
                                role = Role.RadioButton,
                                onClick = { onChoose(option.id) },
                            )
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.id == field.value, onClick = null)
                        Text(optionDisplayText(option))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("action_back")) } },
    )
}

internal enum class BatchEditorStep { BASICS, DETAILS, REVIEW }

internal fun batchEditorStepForField(fieldId: String): BatchEditorStep = when (fieldId) {
    "recipeId", "cultureId", "trayId" -> BatchEditorStep.BASICS
    else -> BatchEditorStep.DETAILS
}

internal fun batchEditorSubmitAction(state: SproutPaceUiState): UiScreenAction? {
    val correcting = state.selectedItemId != null && state.fields.firstOrNull { it.id == "id" }?.value == state.selectedItemId
    val actionId = if (correcting) "correct-batch" else "save-batch"
    return state.actions.firstOrNull { it.id == actionId }
}

private val batchEditorBasicFieldIds = setOf("recipeId", "cultureId", "trayId")
private val batchEditorCoreDetailFieldIds = setOf("substrate", "actualSeedMassGrams", "plannedSowDate", "zoneId")
private val batchEditorHiddenFieldIds = setOf("id", "timeZoneId")

@Composable
internal fun BatchEditorScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    onAction: (UiAction) -> Unit,
    initialStepIndex: Int = 0,
) {
    state.facts.firstOrNull { it.id == "suggestion-unavailable" }?.let { fact ->
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(localizedOrAuthored(fact.valueKey, fact.authoredValue))
            TextButton(onClick = { onAction(UiAction.Back) }) { Text(localizedText("action_back")) }
        }
        return
    }
    val firstErrorField = state.fields.firstOrNull { it.errorKey != null }
    var stepIndex by rememberSaveable(state.selectedItemId) {
        mutableIntStateOf(firstErrorField?.let { batchEditorStepForField(it.id).ordinal }
            ?: initialStepIndex.coerceIn(0, BatchEditorStep.entries.lastIndex))
    }
    var advancedExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(firstErrorField?.id) {
        firstErrorField?.let { stepIndex = batchEditorStepForField(it.id).ordinal }
    }
    val step = BatchEditorStep.entries[stepIndex]
    val contentWidth = if (layout >= UiLayout.EXPANDED) BATCH_EDITOR_EXPANDED_WIDTH else BATCH_EDITOR_COMPACT_WIDTH

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = contentWidth).fillMaxSize()) {
            BatchEditorProgress(step)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .semantics { testTag = "batch-editor-${step.name.lowercase()}" },
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.variant in setOf(UiVariant.VALIDATION_ERROR, UiVariant.CONFLICT, UiVariant.TEMPORARILY_UNASSIGNED)) {
                    statusItems(state.copy(conflictKeys = emptyList()))
                }
                if (state.variant == UiVariant.CONFLICT || state.conflictKeys.isNotEmpty()) item {
                    Column {
                        TextButton(onClick = { stepIndex = BatchEditorStep.DETAILS.ordinal }) {
                            Text(localizedText("choose_another_date"))
                        }
                        TextButton(onClick = { stepIndex = BatchEditorStep.DETAILS.ordinal }) {
                            Text(localizedText("choose_another_zone"))
                        }
                        TextButton(onClick = { onAction(UiAction.Navigate(UiScreen.ZONES)) }) {
                            Text(localizedText("change_capacity"))
                        }
                    }
                }
                item { BatchEditorStepIntro(step, batchEditorSubmitAction(state)?.id == "correct-batch") }
                when (step) {
                    BatchEditorStep.BASICS -> batchEditorBasics(state, onAction)
                    BatchEditorStep.DETAILS -> batchEditorDetails(
                        state = state,
                        advancedExpanded = advancedExpanded,
                        onAdvancedExpandedChange = { advancedExpanded = it },
                        onAction = onAction,
                    )
                    BatchEditorStep.REVIEW -> batchEditorReview(state)
                }
            }
            BatchEditorBottomActions(
                state = state,
                step = step,
                onPrevious = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                onNext = { stepIndex = (stepIndex + 1).coerceAtMost(BatchEditorStep.entries.lastIndex) },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun BatchEditorProgress(step: BatchEditorStep) {
    val progressKey = when (step) {
        BatchEditorStep.BASICS -> "batch_editor_progress_basics"
        BatchEditorStep.DETAILS -> "batch_editor_progress_details"
        BatchEditorStep.REVIEW -> "batch_editor_progress_review"
    }
    val progressText = localizedText(progressKey)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { stateDescription = progressText },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(progressText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(
            progress = { (step.ordinal + 1) / BatchEditorStep.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
    }
}

@Composable
private fun BatchEditorStepIntro(step: BatchEditorStep, correcting: Boolean) {
    val titleKey = when (step) {
        BatchEditorStep.BASICS -> if (correcting) "batch_editor_basics_correction_title" else "batch_editor_basics_title"
        BatchEditorStep.DETAILS -> "batch_editor_details_title"
        BatchEditorStep.REVIEW -> "batch_editor_review_title"
    }
    val bodyKey = when (step) {
        BatchEditorStep.BASICS -> if (correcting) "batch_editor_basics_correction_body" else "batch_editor_basics_body"
        BatchEditorStep.DETAILS -> "batch_editor_details_body"
        BatchEditorStep.REVIEW -> "batch_editor_review_body"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            localizedText(titleKey),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(localizedText(bodyKey), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.batchEditorBasics(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
) {
    val cultureField = state.fields.firstOrNull { it.id == "cultureId" }
    val recipeField = state.fields.firstOrNull { it.id == "recipeId" }
    recipeField?.takeIf { it.value.isNotBlank() }?.let { selectedRecipe ->
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(localizedText("batch_editor_selected_recipe"), style = MaterialTheme.typography.labelLarge)
                    Text(batchEditorFieldDisplayValue(selectedRecipe), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    } ?: cultureField?.value?.takeIf(String::isNotBlank)?.let { cultureId ->
        cultureImage(cultureId)?.let { cover ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(width = 112.dp, height = 84.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(localizedText("batch_editor_selected_culture"), style = MaterialTheme.typography.labelLarge)
                            Text(batchEditorFieldDisplayValue(cultureField), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
    val trayField = state.fields.firstOrNull { it.id == "trayId" }
    item {
        TextButton(onClick = { onAction(UiAction.Navigate(UiScreen.TRAY_SIZES)) }) {
            LabeledActionContent("add-preset", "action_add_preset")
        }
        TextButton(onClick = { onAction(UiAction.Navigate(UiScreen.ZONES)) }) {
            LabeledActionContent("add-zone", "action_add_zone")
        }
    }
    recipeField?.let { field ->
        item(key = modelListKey(ModelListEntryKind.FIELD, field.id)) { FieldControl(field, onAction) }
    }
    if (recipeField?.value.isNullOrBlank()) {
        cultureField?.let { field ->
            item(key = modelListKey(ModelListEntryKind.FIELD, field.id)) { FieldControl(field, onAction) }
        }
    } else {
        item {
            OutlinedButton(
                onClick = { onAction(UiAction.EditField("recipeId", "")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(localizedText("action_choose_catalog_culture")) }
        }
    }
    trayField?.let { field ->
        item(key = modelListKey(ModelListEntryKind.FIELD, field.id)) { FieldControl(field, onAction) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.batchEditorDetails(
    state: SproutPaceUiState,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
) {
    item { TimeZoneContext(scheduleTimeZone(state.fields)) }
    state.fields.filter { it.id in batchEditorCoreDetailFieldIds }.forEach { field ->
        item(key = modelListKey(ModelListEntryKind.FIELD, field.id)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (field.id == "actualSeedMassGrams") SeedMassRecommendation(state)
                FieldControl(field, onAction)
            }
        }
    }
    val advancedFields = state.fields.filter {
        it.id !in batchEditorBasicFieldIds && it.id !in batchEditorCoreDetailFieldIds && it.id !in batchEditorHiddenFieldIds
    }
    if (advancedFields.isNotEmpty()) {
        item {
            OutlinedButton(
                onClick = { onAdvancedExpandedChange(!advancedExpanded) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { testTag = "toggle-batch-advanced-fields" },
            ) {
                Text(localizedText(if (advancedExpanded) "action_hide_advanced_details" else "action_show_advanced_details"))
            }
        }
        if (advancedExpanded) {
            items(advancedFields, key = { field -> modelListKey(ModelListEntryKind.FIELD, field.id) }) { field ->
                FieldControl(field, onAction)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.batchEditorReview(state: SproutPaceUiState) {
    item { SeedMassRecommendation(state) }
    state.items.firstOrNull { it.planEntry != null }?.let { suggestion -> item {
        ProjectionContent(suggestion)
    } }
    item { TimeZoneContext(scheduleTimeZone(state.fields)) }
    val identityId = if (state.fields.firstOrNull { it.id == "recipeId" }?.value.isNullOrBlank()) "cultureId" else "recipeId"
    val reviewIds = listOf(identityId, "trayId", "substrate", "actualSeedMassGrams", "plannedSowDate", "zoneId")
    reviewIds.mapNotNull { id -> state.fields.firstOrNull { it.id == id } }
        .filter { it.value.isNotBlank() }
        .forEach { field ->
            item(key = modelListKey(ModelListEntryKind.FIELD, "review-${field.id}")) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(localizedText(field.labelKey), style = MaterialTheme.typography.labelLarge)
                        Text(batchEditorFieldDisplayValue(field), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
}

@Composable
private fun batchEditorFieldDisplayValue(field: UiField): String {
    val civil = temporalCivilValue(field.kind, field.value, kotlinx.datetime.TimeZone.currentSystemDefault())
    return when {
        field.kind == ScreenFieldKind.POSITIVE_DECIMAL -> localizedDecimal(field.value)
        civil != null && field.kind == ScreenFieldKind.LOCAL_DATE -> localizedDate(civil.date)
        civil != null -> "${localizedDate(civil.date)} ${localizedTime(civil.time)}"
        else -> field.options.firstOrNull { it.id == field.value }
            ?.let { optionDisplayText(it) } ?: field.value
    }
}

@Composable
private fun BatchEditorBottomActions(
    state: SproutPaceUiState,
    step: BatchEditorStep,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAction: (UiAction) -> Unit,
) {
    val submitAction = batchEditorSubmitAction(state)
    val largeText = LocalDensity.current.fontScale >= NAVIGATION_ICON_ONLY_FONT_SCALE
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (step) {
                BatchEditorStep.BASICS -> Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "batch-editor-next" },
                ) { Text(localizedText("action_continue")) }
                BatchEditorStep.DETAILS -> if (largeText) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "batch-editor-review" },
                    ) { Text(localizedText("action_review_batch")) }
                    OutlinedButton(onClick = onPrevious, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(localizedText("action_back"))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(localizedText("action_back"))
                        }
                        Button(
                            onClick = onNext,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { testTag = "batch-editor-review" },
                        ) { Text(localizedText("action_review_batch")) }
                    }
                }
                BatchEditorStep.REVIEW -> {
                    submitAction?.let { action ->
                        Button(
                            onClick = { onAction(UiAction.Submit(action.id)) },
                            enabled = action.enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .semantics { testTag = "action-${action.id}" },
                        ) { LabeledActionContent(action.id, action.labelKey) }
                    }
                    OutlinedButton(onClick = onPrevious, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(localizedText("action_edit_batch_details"))
                    }
                    if (state.hasUnsavedChanges) {
                        TextButton(
                            onClick = { onAction(UiAction.CancelDraft) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(localizedText("action_discard_changes")) }
                    }
                }
            }
        }
    }
}

private val BATCH_EDITOR_COMPACT_WIDTH = 720.dp
private val BATCH_EDITOR_EXPANDED_WIDTH = 840.dp

@Composable
private fun TodayScreen(
    state: SproutPaceUiState,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
) {
    val hero = state.items.firstOrNull()
    val showPrimaryActionInContent = LocalDensity.current.fontScale >= NAVIGATION_ICON_ONLY_FONT_SCALE
    val remaining = state.items.drop(1)
    val overdue = remaining.filter { it.todayGroup() == TodayItemGroup.OVERDUE }
    val dueToday = remaining.filter { it.todayGroup() == TodayItemGroup.DUE_TODAY }
    val upcoming = remaining.filter { it.todayGroup() == TodayItemGroup.UPCOMING }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = TODAY_CONTENT_MAX_WIDTH).fillMaxSize()
                .semantics { testTag = "today-feed" },
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (state.variant == UiVariant.EMPTY) 24.dp else 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            statusItems(state)
            hero?.let { item -> item { TodayHeroCard(item, onAction) } }
            todayGroup("today_overdue_group", overdue, onAction)
            todayGroup("today_due_group", dueToday, onAction)
            todayGroup("today_upcoming_group", upcoming, onAction)

            if (state.variant == UiVariant.EMPTY || showPrimaryActionInContent) {
                todayScreenActions(state.actions, onAction)
            } else {
                todayScreenActions(state.actions.filterNot(UiScreenAction::primary), onAction)
            }

            if (!state.hidesContextForEmptyState()) {
                fieldItems(
                    state = state,
                    contextFieldsExpanded = contextFieldsExpanded,
                    onContextFieldsExpandedChange = onContextFieldsExpandedChange,
                    onAction = onAction,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todayGroup(
    headingKey: String,
    groupItems: List<UiItem>,
    onAction: (UiAction) -> Unit,
) {
    if (groupItems.isEmpty()) return
    item { SectionHeading(headingKey) }
    items(groupItems, key = { item -> modelListKey(ModelListEntryKind.ITEM, item.id) }) { item ->
        TodayTaskCard(item, onAction)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todayScreenActions(
    actions: List<UiScreenAction>,
    onAction: (UiAction) -> Unit,
) {
    items(actions, key = { action -> modelListKey(ModelListEntryKind.ACTION, action.id) }) { action ->
        val dispatch = { onAction(action.toAdvertisingUiAction() ?: UiAction.Submit(action.id)) }
        if (action.primary) {
            Button(
                onClick = dispatch,
                enabled = action.enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { testTag = "action-${action.id}" },
            ) { LabeledActionContent(action.id, action.labelKey) }
        } else {
            OutlinedButton(
                onClick = dispatch,
                enabled = action.enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { testTag = "action-${action.id}" },
            ) { LabeledActionContent(action.id, action.labelKey) }
        }
    }
}

@Composable
private fun TodayHeroCard(item: UiItem, onAction: (UiAction) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "today-next-action" },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = localizedText("today_next_action"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = localizedOrAuthored(item.titleKey, item.authoredTitle),
                style = MaterialTheme.typography.headlineSmall,
            )
            item.supportingKey?.let { key ->
                Text(localizedText(key), style = MaterialTheme.typography.bodyLarge)
            }
            item.stateKey?.let { key ->
                Text(localizedText(key), style = MaterialTheme.typography.labelLarge)
            }
            ProjectionContent(item)
            TodayItemActions(item, emphasized = true, onAction = onAction)
        }
    }
}

@Composable
private fun TodayTaskCard(item: UiItem, onAction: (UiAction) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "item-${item.id}" },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = localizedOrAuthored(item.titleKey, item.authoredTitle),
                style = MaterialTheme.typography.titleMedium,
            )
            item.supportingKey?.let { key ->
                Text(
                    text = localizedText(key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.todayGroup() == TodayItemGroup.OVERDUE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            item.stateKey?.let { key ->
                Text(
                    text = localizedText(key),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProjectionContent(item)
            TodayItemActions(item, emphasized = false, onAction = onAction)
        }
    }
}

@Composable
private fun TodayItemActions(item: UiItem, emphasized: Boolean, onAction: (UiAction) -> Unit) {
    if (item.task != null) {
        TaskSnoozeControl(item, onAction)
        item.batch?.takeUnless { item.commands.any { command -> command.operation == "open-batch" } }?.let { batch ->
            TextButton(onClick = { onAction(UiAction.Submit("open-batch", batch.batchId)) }) {
                Text(localizedText("action_open_batch"))
            }
        }
    }
    item.actionId?.let { actionId ->
        if (emphasized) {
            Button(
                onClick = { onAction(UiAction.Submit(actionId, item.id)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { LabeledActionContent(actionId, "action_${actionId.replace('-', '_')}") }
        } else {
            TextButton(
                onClick = { onAction(UiAction.Submit(actionId, item.id)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { LabeledActionContent(actionId, "action_${actionId.replace('-', '_')}") }
        }
    }
    item.commands.forEachIndexed { index, command ->
        if (emphasized && index == 0) {
            Button(
                onClick = { onAction(UiAction.SubmitItem(command.operation, command.subjectId)) },
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { testTag = "item-${item.id}-${command.operation}" },
            ) { LabeledActionContent(command.operation, "action_${command.operation.replace('-', '_')}") }
        } else {
            TextButton(
                onClick = { onAction(UiAction.SubmitItem(command.operation, command.subjectId)) },
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { testTag = "item-${item.id}-${command.operation}" },
            ) { LabeledActionContent(command.operation, "action_${command.operation.replace('-', '_')}") }
        }
    }
}

internal enum class TodayItemGroup { OVERDUE, DUE_TODAY, UPCOMING }

internal fun UiItem.todayGroup(): TodayItemGroup = when (task?.group) {
    com.sedsoftware.sproutpace.component.TaskDayGroup.OVERDUE -> TodayItemGroup.OVERDUE
    com.sedsoftware.sproutpace.component.TaskDayGroup.TODAY -> TodayItemGroup.DUE_TODAY
    else -> TodayItemGroup.UPCOMING
}

private val TODAY_CONTENT_MAX_WIDTH = 720.dp

@Composable
private fun TraysScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    val inlineCreationAction = state.variant == UiVariant.EMPTY ||
        LocalDensity.current.fontScale >= NAVIGATION_ICON_ONLY_FONT_SCALE
    val compactExcludedActions = state.actions.mapNotNull { action ->
        action.id.takeIf { it != "new-batch" || !inlineCreationAction }
    }.toSet()
    if (layout >= UiLayout.EXPANDED) {
        Row(Modifier.fillMaxSize().semantics { testTag = "trays-list-detail" }) {
            LazyColumn(
                modifier = Modifier.weight(LIST_PANE_WEIGHT).fillMaxHeight().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                trayCategoryTabs(state, onAction)
                statusItems(state)
                trayItems(state, onAction, advertisingRuntime)
            }
            VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
            LazyColumn(
                modifier = Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxHeight().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                trayOverview(state)
                actionItems(
                    state,
                    onAction,
                    headingKey = null,
                    excludedIds = if (inlineCreationAction) emptySet() else setOf("new-batch"),
                )
                if (!state.hidesContextForEmptyState()) {
                    fieldItems(
                        state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
                    )
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = PRIMARY_CONTENT_MAX_WIDTH).fillMaxSize()
                .semantics { testTag = "trays-feed" },
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = if (inlineCreationAction) 24.dp else 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            trayCategoryTabs(state, onAction)
            statusItems(state)
            trayOverview(state)
            trayItems(state, onAction, advertisingRuntime)
            actionItems(
                state,
                onAction,
                headingKey = null,
                excludedIds = compactExcludedActions,
            )
            if (!state.hidesContextForEmptyState()) {
                fieldItems(
                    state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trayOverview(state: SproutPaceUiState) {
    if (state.hidesContextForEmptyState()) return
    state.facts.firstOrNull()?.let { fact ->
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_nav_trays),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(localizedText(state.trayCategory.labelKey), style = MaterialTheme.typography.titleMedium)
                        Text(
                            localizedOrAuthored(fact.valueKey, fact.authoredValue),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trayItems(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    itemsIndexed(state.items, key = { _, item -> modelListKey(ModelListEntryKind.ITEM, item.id) }) { index, item ->
        TrayBatchCard(item, onAction)
        val insertionIndex = state.inlineAdInsertionIndexOrNull()
        if (state.showInlineAd && insertionIndex != null && index == insertionIndex - 1) {
            InlineAdSlot(state.inlineAdUnitId, advertisingRuntime)
        }
    }
}

@Composable
private fun TrayBatchCard(item: UiItem, onAction: (UiAction) -> Unit) {
    val selectionDescription = localizedText(if (item.selected) "state_selected" else "state_not_selected")
    Card(
        onClick = { onAction(UiAction.Submit("open-batch", item.id)) },
        colors = selectableCardColors(item.selected),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = selectionDescription
            testTag = "item-${item.id}"
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_nav_trays),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.titleMedium)
                item.supportingKey?.let { key ->
                    Text(
                        localizedText(key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.stateKey?.let { key ->
                    Text(localizedText(key), style = MaterialTheme.typography.labelMedium)
                }
                ProjectionContent(item)
            }
        }
    }
}

@Composable
private fun PlanScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    contextFieldsExpanded: Boolean,
    onContextFieldsExpandedChange: (Boolean) -> Unit,
    onAction: (UiAction) -> Unit,
) {
    val itemActionIds = state.items.mapNotNull(UiItem::actionId).toSet()
    val excludedActionIds = buildSet {
        addAll(itemActionIds)
        if (state.fields.any { it.id == "paused" && it.value == "false" }) add("resume-plan")
        if (state.fields.any { it.id == "paused" && it.value == "true" }) add("pause-plan")
    }
    val timeline: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            statusItems(state)
            planSummary(state)
            items(state.plans, key = { "plan-summary-${it.id}" }) { plan ->
                OutlinedButton(onClick = { onAction(UiAction.Submit("edit-plan", plan.id)) }) {
                    Column {
                        Text(localizedText("projection_cadence").replace("%1\$s", plan.cadenceDays.toString()))
                        val cultureNames = plan.cultureIds.map { localizedText("culture_${it}_name") }
                        Text(cultureNames.joinToString(" · "))
                        Text(localizedText(if (plan.paused) "plan_paused" else "plan_active"))
                    }
                }
            }
            items(state.items, key = { item -> modelListKey(ModelListEntryKind.ITEM, item.id) }) { item ->
                PlanTimelineCard(item, onAction)
            }
            if (layout < UiLayout.EXPANDED) {
                actionItems(state, onAction, headingKey = null, excludedIds = excludedActionIds)
                if (!state.hidesContextForEmptyState()) {
                    fieldItems(
                        state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
                    )
                }
            }
        }
    }
    if (layout >= UiLayout.EXPANDED) {
        Row(Modifier.fillMaxSize().semantics { testTag = "plan-timeline-capacity" }) {
            timeline(Modifier.weight(LIST_PANE_WEIGHT).fillMaxHeight())
            VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
            LazyColumn(
                modifier = Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items.mapNotNull { it.planEntry?.zone }.distinctBy { it.zoneId }, key = { "capacity-${it.zoneId}" }) { zone ->
                    OccupancyContent(zone)
                }
                actionItems(state, onAction, headingKey = null, excludedIds = excludedActionIds)
                if (!state.hidesContextForEmptyState()) {
                    fieldItems(
                        state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction,
                    )
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            timeline(Modifier.widthIn(max = PRIMARY_CONTENT_MAX_WIDTH).fillMaxSize().semantics { testTag = "plan-timeline" })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.planSummary(state: SproutPaceUiState) {
    if (state.hidesContextForEmptyState()) return
    state.facts.firstOrNull { it.id == "cadence-days" }?.let { fact ->
        item {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(localizedText("plan_frequency"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        localizedText("projection_cadence").replace("%1\$s", fact.authoredValue.orEmpty()),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanTimelineCard(item: UiItem, onAction: (UiAction) -> Unit) {
    val suggested = item.actionId == "confirm-suggestion"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (suggested) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "item-${item.id}" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            cultureImage(item.planEntry?.cultureId ?: item.id)?.let { image ->
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp),
                )
            } ?: Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(vectorResource(Res.drawable.ic_nav_plan), null, Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.titleMedium)
                item.supportingKey?.let { key ->
                    Text(localizedText(key), style = MaterialTheme.typography.bodyMedium)
                }
                item.stateKey?.let { key ->
                    Text(
                        localizedText(key),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProjectionContent(item)
                ItemTextActions(item, onAction, emphasized = suggested)
                if (item.planEntry?.status == com.sedsoftware.sproutpace.component.PlanEntryStatus.NEEDS_RESOLUTION) {
                    TextButton(onClick = { onAction(UiAction.Submit("confirm-suggestion", item.id)) }) {
                        Text(localizedText("choose_another_date"))
                    }
                    TextButton(onClick = { onAction(UiAction.Navigate(UiScreen.ZONES)) }) {
                        Text(localizedText("change_capacity"))
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    val inlineCreationAction = state.variant == UiVariant.EMPTY ||
        LocalDensity.current.fontScale >= NAVIGATION_ICON_ONLY_FONT_SCALE
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CATALOG_CARD_MIN_WIDTH),
            modifier = Modifier.widthIn(max = CATALOG_CONTENT_MAX_WIDTH).fillMaxSize()
                .semantics { testTag = "catalog-grid" },
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = if (inlineCreationAction) 24.dp else 104.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.fields.firstOrNull { it.id == "query" }?.let { field ->
                item(span = { GridItemSpan(maxLineSpan) }) { CatalogSearchField(field, onAction) }
            }
            if (state.variant == UiVariant.EMPTY) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StatusCard("catalog_empty_title", "catalog_empty_body")
                }
            }
            if (!state.hidesContextForEmptyState()) {
                state.facts.firstOrNull()?.let { fact ->
                    item(span = { GridItemSpan(maxLineSpan) }) { CatalogSourceCard(fact) }
                }
            }
            state.items.forEachIndexed { index, item ->
                item(key = modelListKey(ModelListEntryKind.ITEM, item.id)) {
                    CatalogCultureCard(item, onAction)
                }
                val insertionIndex = state.inlineAdInsertionIndexOrNull()
                if (state.showInlineAd && insertionIndex != null && index == insertionIndex - 1) {
                    item(
                        key = "catalog-inline-ad",
                        span = { GridItemSpan(maxLineSpan) },
                    ) { InlineAdSlot(state.inlineAdUnitId, advertisingRuntime) }
                }
            }
            val excluded = buildSet {
                add("open-recipe")
                if (!inlineCreationAction) add("create-recipe")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.actions.filterNot { it.id in excluded }.forEach { action ->
                        ScreenActionButton(action, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSearchField(field: UiField, onAction: (UiAction) -> Unit) {
    OutlinedTextField(
        value = field.value,
        onValueChange = { onAction(UiAction.EditField(field.id, it)) },
        placeholder = { Text(localizedText(field.labelKey)) },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { testTag = "field-${field.id}" },
    )
}

@Composable
private fun CatalogSourceCard(fact: UiFact) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(localizedText("catalog_source_title"), style = MaterialTheme.typography.titleMedium)
            Text(
                localizedText("catalog_version_label") + ": " + localizedOrAuthored(fact.valueKey, fact.authoredValue),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CatalogCultureCard(item: UiItem, onAction: (UiAction) -> Unit) {
    Card(
        onClick = { onAction(UiAction.Submit("open-recipe", item.id)) },
        colors = selectableCardColors(item.selected),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "item-${item.id}" },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cultureImage(item.id)?.let { image ->
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(124.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.titleMedium)
                item.supportingKey?.let { key ->
                    Text(
                        localizedText(key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemTextActions(item: UiItem, onAction: (UiAction) -> Unit, emphasized: Boolean = false) {
    item.actionId?.let { actionId ->
        if (emphasized) {
            Button(
                onClick = { onAction(UiAction.Submit(actionId, item.id)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { LabeledActionContent(actionId, "action_${actionId.replace('-', '_')}") }
        } else {
            TextButton(
                onClick = { onAction(UiAction.Submit(actionId, item.id)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { LabeledActionContent(actionId, "action_${actionId.replace('-', '_')}") }
        }
    }
    item.commands.forEach { command ->
        TextButton(
            onClick = { onAction(UiAction.SubmitItem(command.operation, command.subjectId)) },
            modifier = Modifier.heightIn(min = 48.dp)
                .semantics { testTag = "item-${item.id}-${command.operation}" },
        ) { LabeledActionContent(command.operation, "action_${command.operation.replace('-', '_')}") }
    }
}

@Composable
private fun selectableCardColors(selected: Boolean) = CardDefaults.cardColors(
    containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    },
)

private val focusedPrimaryScreens = setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN, UiScreen.CATALOG)
private val dedicatedScreenLayouts = focusedPrimaryScreens + setOf(
    UiScreen.FIRST_RUN, UiScreen.BATCH_EDITOR, UiScreen.SETTINGS,
    UiScreen.BATCH_DETAIL,
    UiScreen.RECIPE, UiScreen.NOTIFICATIONS, UiScreen.BACKUP_RESTORE,
    UiScreen.OBSERVATION_EDITOR, UiScreen.CLOSE_BATCH, UiScreen.ZONES, UiScreen.TRAY_SIZES,
)
private val PRIMARY_CONTENT_MAX_WIDTH = 720.dp
private val CATALOG_CONTENT_MAX_WIDTH = 1040.dp
private val CATALOG_CARD_MIN_WIDTH = 240.dp
private val SETTINGS_CONTENT_MAX_WIDTH = 720.dp
private val SETTINGS_DIVIDER_INSET = 56.dp

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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        screenHero(state)
        privacyExplanation(state)
        trayCategoryTabs(state, onAction)
        statusItems(state)
        itemCollection(state, onAction, advertisingRuntime)
        if (!state.hidesContextForEmptyState() && state.showsSupportingFacts()) {
            item { SectionHeading("section_details") }
            items(state.supportingFacts(), key = { fact -> modelListKey(ModelListEntryKind.FACT, fact.id) }) { fact ->
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
        val selectedIndex = TrayCategory.entries.indexOf(state.trayCategory)
        val tabs: @Composable () -> Unit = {
            TrayCategory.entries.forEach { category ->
                Tab(
                    selected = category == state.trayCategory,
                    onClick = { onAction(UiAction.SelectTrayCategory(category)) },
                    text = {
                        Text(
                            text = localizedText(category.labelKey),
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .semantics { testTag = "trays-category-${category.id}" },
                )
            }
        }
        if (LocalDensity.current.fontScale >= NAVIGATION_ICON_ONLY_FONT_SCALE) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                minTabWidth = LARGE_TEXT_TAB_WIDTH_DP.dp,
                scrollState = rememberScrollState(with(LocalDensity.current) {
                    (selectedIndex * LARGE_TEXT_TAB_WIDTH_DP).dp.roundToPx()
                }),
                tabs = tabs,
            )
        } else {
            PrimaryTabRow(selectedTabIndex = selectedIndex, tabs = tabs)
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
        privacyExplanation(state)
        if (!state.hidesContextForEmptyState() && state.showsSupportingFacts()) {
            item { SectionHeading("section_details") }
            items(state.supportingFacts(), key = { fact -> modelListKey(ModelListEntryKind.FACT, fact.id) }) { fact ->
                FactCard(fact)
            }
        }
        actionItems(state, onAction)
        if (!state.hidesContextForEmptyState()) {
            fieldItems(state, contextFieldsExpanded, onContextFieldsExpandedChange, onAction)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.privacyExplanation(state: SproutPaceUiState) {
    if (state.screen != UiScreen.ADVERTISING_PRIVACY) return
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localizedText(if (state.actions.any {
                    it.id in setOf("accept-advertising-consent", "withdraw-advertising-consent")
                }) "privacy_decision_body" else "privacy_stale_body"))
            Text(localizedText("privacy_choice_consequences"))
            Text(localizedText("privacy_context_title"), style = MaterialTheme.typography.titleMedium)
            Text(localizedText("privacy_context_body"))
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
    val visibleFields = state.fields.filterNot { it.id in hiddenTechnicalFieldIds }
    if (visibleFields.isEmpty()) return
    val progressivelyDisclosed = state.screen in contextFieldScreens
    if (!progressivelyDisclosed || contextFieldsExpanded) {
        item { SectionHeading(if (progressivelyDisclosed) "section_action_details" else "section_form") }
        items(visibleFields, key = { field -> modelListKey(ModelListEntryKind.FIELD, field.id) }) { field ->
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
                            contentScale = ContentScale.Fit,
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
                        painter = painterResource(Res.drawable.illustration_empty_today),
                        contentDescription = null,
                        modifier = Modifier.size(width = 240.dp, height = 160.dp),
                    )
                }
            }
        }
        val (titleKey, bodyKey) = when (state.setupState) {
            com.sedsoftware.sproutpace.component.WorkspaceSetupState.NOT_CONFIGURED -> "setup_resume_title" to "setup_resume_body"
            com.sedsoftware.sproutpace.component.WorkspaceSetupState.NO_PLAN -> "setup_plan_title" to "setup_plan_body"
            com.sedsoftware.sproutpace.component.WorkspaceSetupState.NO_BATCHES -> "setup_batch_title" to "setup_batch_body"
            com.sedsoftware.sproutpace.component.WorkspaceSetupState.ALL_CLEAR -> "setup_clear_title" to "setup_clear_body"
            null -> state.screen.emptyStateCopy()
        }
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
    if (state.variant == UiVariant.VALIDATION_ERROR) {
        item { StatusCard("state_validation_title", "state_validation_body", warning = true) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemCollection(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    advertisingRuntime: AdvertisingRuntime?,
) {
    if (!state.showsItemCollection()) return
    state.itemSectionKey()?.let { headingKey ->
        if (state.items.isNotEmpty()) item { SectionHeading(headingKey) }
    }
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

internal fun SproutPaceUiState.showsItemCollection(): Boolean = screen != UiScreen.NOTIFICATIONS

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
    variant == UiVariant.EMPTY && screen in setOf(UiScreen.TODAY, UiScreen.TRAYS)

internal fun UiScreen.emptyStateCopy(): Pair<String, String> = when (this) {
    UiScreen.TODAY -> "today_empty_title" to "today_empty_body"
    UiScreen.TRAYS -> "trays_empty_title" to "trays_empty_body"
    UiScreen.PLAN -> "plan_empty_title" to "plan_empty_body"
    else -> "state_empty_title" to "state_empty_body"
}

private fun androidx.compose.foundation.lazy.LazyListScope.actionItems(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    headingKey: String? = null,
    excludedIds: Set<String> = emptySet(),
) {
    val visibleActions = state.visibleScreenActions(excludedIds)
    if (visibleActions.isNotEmpty() && headingKey != null) item { SectionHeading(headingKey) }
    items(visibleActions, key = { action -> modelListKey(ModelListEntryKind.ACTION, action.id) }) { action ->
        ScreenActionButton(action, onAction)
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

internal fun SproutPaceUiState.visibleScreenActions(
    excludedIds: Set<String> = emptySet(),
): List<UiScreenAction> {
    val itemActionIds = items.mapNotNull(UiItem::actionId).toSet()
    val redundantFormActionIds = when (screen) {
        UiScreen.CLOSE_BATCH -> closeBatchOutcomeShortcutIds
        else -> emptySet()
    }
    val hiddenActionIds = excludedIds + itemActionIds + redundantFormActionIds
    return actions.filterNot { it.id in hiddenActionIds }
}

@Composable
private fun ScreenActionButton(action: UiScreenAction, onAction: (UiAction) -> Unit) {
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
        val actionModifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                testTag = "action-${action.id}"
                destructiveDescription?.let { stateDescription = it }
            }
        if (destructive) {
            TextButton(
                onClick = dispatch,
                enabled = action.enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = actionModifier,
            ) { LabeledActionContent(action.id, action.labelKey) }
        } else if (action.id in lowEmphasisActionIds) {
            OutlinedButton(
                onClick = dispatch,
                enabled = action.enabled,
                modifier = actionModifier,
            ) { LabeledActionContent(action.id, action.labelKey) }
        } else {
            FilledTonalButton(
                onClick = dispatch,
                enabled = action.enabled,
                modifier = actionModifier,
            ) { LabeledActionContent(action.id, action.labelKey) }
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
    val selectableCard = itemCardIsSelectable(screen, item)
    val selectionDescription = localizedText(
        if (item.selected) "state_selected" else "state_not_selected",
    )
    val cardColors = CardDefaults.cardColors(
        containerColor = if (item.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    )
    val cardModifier = Modifier.fillMaxWidth().semantics {
        if (selectableCard) stateDescription = selectionDescription
        testTag = "item-${item.id}"
    }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (screen in stageContentScreens) {
                stageImage(item)?.let { image ->
                    Image(
                        painter = painterResource(image),
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
            ProjectionContent(item)
            item.actionId?.let { actionId ->
                TextButton(
                    onClick = { onAction(UiAction.Submit(actionId, item.id)) },
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
    if (selectableCard) {
        Card(
            onClick = { onAction(UiAction.Select(item.id)) },
            colors = cardColors,
            modifier = cardModifier,
        ) { content() }
    } else {
        Card(colors = cardColors, modifier = cardModifier) { content() }
    }
}

internal fun itemCardIsSelectable(screen: UiScreen, item: UiItem): Boolean =
    screen != UiScreen.FIRST_RUN && item.actionId == null && item.commands.isEmpty()

@Composable
private fun FactCard(fact: UiFact) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
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
internal fun FieldControl(field: UiField, onAction: (UiAction) -> Unit) {
    if (field.id == "summaryTime") {
        SummaryTimeField(field, onAction)
        return
    }
    if (field.id == "timeZoneId") {
        TimeZoneContext(scheduleTimeZone(listOf(field)))
        return
    }
    when (field.kind) {
        ScreenFieldKind.INSTANT, ScreenFieldKind.LOCAL_DATE, ScreenFieldKind.LOCAL_DATE_TIME ->
            TemporalFieldControl(field, onAction)
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
    val displayedValue = displayFieldInput(field.kind, field.value, localizedText("number_decimal_separator"))
    val keyboardType = when (field.kind) {
        ScreenFieldKind.POSITIVE_INTEGER, ScreenFieldKind.POSITIVE_LONG,
        ScreenFieldKind.NON_NEGATIVE_LONG -> KeyboardType.Number
        ScreenFieldKind.POSITIVE_DECIMAL -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
    OutlinedTextField(
        value = displayedValue,
        onValueChange = { onAction(UiAction.EditField(field.id, canonicalFieldInput(field.kind, it))) },
        modifier = Modifier.fillMaxWidth().semantics {
            testTag = "field-${field.id}"
            errorText?.let { error(it) }
        },
        enabled = !field.readOnly,
        label = { Text(localizedText(field.labelKey)) },
        supportingText = if (errorText != null || fieldHelpKey(field) != null) {
            { Text(errorText ?: localizedText(requireNotNull(fieldHelpKey(field)))) }
        } else null,
        isError = errorText != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
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
                Text(optionDisplayText(option), Modifier.padding(start = 8.dp))
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
                Text(optionDisplayText(option), Modifier.padding(start = 8.dp))
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
    val isPhoto = kind == PlatformActionKind.CHOOSE_PHOTO
    val hasSelection = field.value.isNotBlank() && field.value != REMOVE_PHOTO_VALUE
    val statusKey = when {
        isPhoto && field.value == REMOVE_PHOTO_VALUE -> "observation_photo_removed"
        isPhoto && hasSelection -> "observation_photo_selected"
        isPhoto -> "observation_photo_none"
        hasSelection -> "document_file_selected"
        else -> "document_file_none"
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
                actionId = when {
                    !hasSelection -> if (isPhoto) "choose-photo" else "choose-file"
                    isPhoto -> "replace-photo"
                    else -> "replace-file"
                },
                labelKey = when {
                    !hasSelection -> actionLabelKey
                    isPhoto -> "action_replace_photo"
                    else -> "action_replace_file"
                },
            )
        }
        if (hasSelection) {
            TextButton(
                onClick = {
                    onAction(UiAction.ClearPickerSelection(field.id, field.value, kind))
                },
                enabled = !field.readOnly,
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { testTag = "remove-${field.id}" },
            ) {
                LabeledActionContent(
                    actionId = if (isPhoto) "remove-photo" else "remove-file",
                    labelKey = if (isPhoto) "action_remove_photo" else "action_remove_file",
                )
            }
        }
        FieldError(field)
    }
}

@Composable
private fun FieldHeading(field: UiField) {
    Text(localizedText(field.labelKey), style = MaterialTheme.typography.titleMedium)
    fieldHelpKey(field)?.let { Text(localizedText(it), style = MaterialTheme.typography.bodySmall) }
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
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
)

@Composable
private fun StatusCard(titleKey: String, bodyKey: String, warning: Boolean = false) {
    val container = if (warning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(localizedText(titleKey), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(localizedText(bodyKey), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MessageState(titleKey: String, bodyKey: String, actionKey: String, onClick: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
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
internal fun LabeledActionContent(actionId: String, labelKey: String) {
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
).filterNotNull().mapNotNull(::stageImage).firstOrNull()

internal fun stageImage(key: String): DrawableResource? = when (key) {
        "stage_soaking" -> Res.drawable.stage_soaking
        "stage_sown" -> Res.drawable.stage_sown
        "stage_blackout" -> Res.drawable.stage_blackout
        "stage_light_growth" -> Res.drawable.stage_light_growth
        "stage_harvest" -> Res.drawable.stage_harvest
        else -> null
}

@Composable
internal fun StageIllustration(stageKey: String?) {
    stageKey?.let(::stageImage)?.let { image ->
        Image(painterResource(image), null, Modifier.size(width = 96.dp, height = 72.dp))
    }
}

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

private val supportingFactScreens = setOf(
    UiScreen.BATCH_DETAIL,
    UiScreen.RECIPE,
    UiScreen.BACKUP_RESTORE,
    UiScreen.ADVERTISING_PRIVACY,
)

private fun SproutPaceUiState.showsSupportingFacts(): Boolean =
    supportingFacts().isNotEmpty() && screen in supportingFactScreens

internal fun SproutPaceUiState.supportingFacts(): List<UiFact> =
    if (screen == UiScreen.ADVERTISING_PRIVACY) facts.filter { it.id == "policy-version" } else facts

private fun SproutPaceUiState.itemSectionKey(): String? = when (screen) {
    UiScreen.BATCH_DETAIL, UiScreen.RECIPE -> "batch_timeline"
    else -> null
}

private val multilineFieldIds = setOf("text", "note", "reason", "assessment")
private val hiddenTechnicalFieldIds = setOf("id", "newId", "eventId", "actionId")

internal val contextFieldScreens = setOf(UiScreen.TODAY, UiScreen.BATCH_DETAIL, UiScreen.PLAN)

private val destructiveActionIds = setOf(
    "delete-batch", "delete-observation", "delete-plan", "delete-recipe", "delete-tray-size",
    "delete-zone", "erase-all", "delete-all-data", "restore-backup", "undo-stage",
)

private val lowEmphasisActionIds = setOf(
    "pause-plan", "resume-plan", "open-system-settings", "open-privacy-policy",
    "close-harvest", "close-failed", "close-abandoned", "record-actual-mass",
)

private val closeBatchOutcomeShortcutIds = setOf(
    "close-harvest", "close-failed", "close-abandoned", "record-actual-mass",
)

private val stageContentScreens = setOf(UiScreen.BATCH_DETAIL, UiScreen.RECIPE, UiScreen.CLOSE_BATCH)
private val emptyIllustrationScreens = setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN)

private val addActionIds = setOf(
    "new-batch", "save-batch", "add-observation", "create-recipe", "add-zone", "add-preset",
)
private const val LARGE_TEXT_TAB_WIDTH_DP = 132

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
    "close-batch", "complete-action", "confirm-stage", "confirm-sowing", "confirm-suggestion", "save-outcome",
)
