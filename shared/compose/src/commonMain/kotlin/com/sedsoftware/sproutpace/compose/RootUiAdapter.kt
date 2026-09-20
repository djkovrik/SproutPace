package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sedsoftware.sproutpace.data.ThemePreference
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.arkivanov.decompose.value.Value
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.LayoutMode
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenContract
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenField
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenPhoto
import com.sedsoftware.sproutpace.component.PENDING_PHOTO_PREFIX
import com.sedsoftware.sproutpace.component.REMOVE_PHOTO_VALUE
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.compose.theme.AppTheme
import com.sedsoftware.sproutpace.root.AppRootComponent
import com.sedsoftware.sproutpace.root.ConfirmationComponent
import com.sedsoftware.sproutpace.root.PrimaryDestination
import com.sedsoftware.sproutpace.monetization.AdPlacementRequest
import com.sedsoftware.sproutpace.monetization.AdPreparation
import com.sedsoftware.sproutpace.monetization.AdSurface
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import com.sedsoftware.sproutpace.monetization.InlineAdPlacementPolicy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import sproutpace.shared.compose.generated.resources.Res
import sproutpace.shared.compose.generated.resources.action_back
import sproutpace.shared.compose.generated.resources.action_confirm_delete
import sproutpace.shared.compose.generated.resources.confirmation_body
import sproutpace.shared.compose.generated.resources.confirmation_title

@Composable
internal fun RootApp(
    root: AppRootComponent,
    platformActions: PlatformActionHandler = NoOpPlatformActionHandler,
    onThemeChanged: @Composable (isDark: Boolean) -> Unit,
) {
    val theme = (root as? ThemePreferenceOwner)?.themePreference?.collectAsState()?.value
        ?: ThemePreference.SYSTEM
    val advertisingRuntime = (root as? AdvertisingRuntimeOwner)?.advertisingRuntime
    val privacyState = advertisingRuntime?.privacyState?.collectAsState()?.value
    AppTheme(theme, onThemeChanged) {
    val stack by root.stack.asComposeState()
    val activeComponent = stack.active.instance
    val model by activeComponent.model.asComposeState()
    val confirmationSlot by root.confirmation.asComposeState()
    val baseUiState = model.toUiState(
        selectedPrimary = selectedPrimaryScreen(
            active = stack.active.configuration.screen,
            backStack = stack.backStack.map { it.configuration.screen },
        ),
    )
    var adUnitId by remember(activeComponent) { mutableStateOf<String?>(null) }
    val advertisingVisit = remember(activeComponent) { AdvertisingVisitGate() }
    val placement = baseUiState.adPlacementRequestOrNull()
    LaunchedEffect(activeComponent, placement) {
        if (placement == null || advertisingRuntime == null) {
            adUnitId = null
            return@LaunchedEffect
        }
        val request = advertisingVisit.claim(placement) ?: return@LaunchedEffect
        adUnitId = prepareAdvertisingVisit(advertisingRuntime, request) {
            root.open(ScreenId.ADVERTISING_PRIVACY, null)
        }
    }
    val uiState = baseUiState
        .withAdvertisingPrivacy(privacyState)
        .copy(showInlineAd = adUnitId != null, inlineAdUnitId = adUnitId)
    val router = remember(root, activeComponent, platformActions) {
        RootActionRouter(root, activeComponent, platformActions) { activeComponent.model.value.selectedItemId }
    }

    val scope = rememberCoroutineScope()
    SproutPaceApp(
        state = uiState,
        advertisingRuntime = advertisingRuntime,
        onAction = { action ->
            when (action) {
                UiAction.AcceptAdvertisingConsent -> scope.launch {
                    advertisingRuntime?.let { handleAdvertisingAction(action, it, root::back) }
                }
                UiAction.DeclineAdvertisingConsent -> scope.launch {
                    adUnitId = null
                    advertisingRuntime?.let { handleAdvertisingAction(action, it, root::back) }
                }
                UiAction.WithdrawAdvertisingConsent -> scope.launch {
                    adUnitId = null
                    advertisingRuntime?.let { handleAdvertisingAction(action, it, root::back) }
                }
                else -> router.dispatch(action)
            }
        },
    )
        confirmationSlot.child?.instance?.let { confirmation -> RootConfirmationDialog(confirmation) }
    }
}

@Composable
private fun RootConfirmationDialog(component: ConfirmationComponent) {
    AlertDialog(
        onDismissRequest = component::cancel,
        title = { Text(stringResource(Res.string.confirmation_title)) },
        text = { Text(stringResource(Res.string.confirmation_body)) },
        confirmButton = {
            TextButton(onClick = component::confirm) { Text(stringResource(Res.string.action_confirm_delete)) }
        },
        dismissButton = {
            TextButton(onClick = component::cancel) { Text(stringResource(Res.string.action_back)) }
        },
    )
}

internal class RootActionRouter(
    private val root: AppRootComponent,
    private val active: ScreenComponent,
    private val platformActions: PlatformActionHandler = NoOpPlatformActionHandler,
    private val selectedItemId: () -> String?,
) {
    fun dispatch(action: UiAction) {
        when (action) {
            UiAction.Back -> root.back()
            UiAction.CancelDraft -> {
                discardPendingPhoto(currentPhotoDraft())
                active.onAction(ScreenAction.CancelDraft)
            }
            UiAction.Retry -> active.onAction(ScreenAction.Retry)
            UiAction.AcceptAdvertisingConsent,
            UiAction.DeclineAdvertisingConsent,
            UiAction.WithdrawAdvertisingConsent -> Unit
            is UiAction.Select -> active.onAction(ScreenAction.Select(action.id))
            is UiAction.EditField -> updateField(action.fieldId, action.value, choose = false)
            is UiAction.ChooseOption -> updateField(action.fieldId, action.optionId, choose = true)
            is UiAction.SelectTrayCategory -> updateField(TRAY_CATEGORY_FIELD_ID, action.category.id, choose = true)
            is UiAction.SetLayout -> active.onAction(ScreenAction.SetLayout(action.layout.toComponentLayout()))
            is UiAction.Navigate -> navigate(action.screen, null)
            is UiAction.Submit -> submit(action.actionId)
            is UiAction.SubmitItem -> submitItem(action.operation, action.subjectId)
            is UiAction.RequestPlatform -> platformActions.perform(action.request) { result ->
                action.request.fieldId?.let { fieldId ->
                    if (action.request.kind == PlatformActionKind.CHOOSE_PHOTO) {
                        discardPendingPhoto(currentPhotoDraft())
                    }
                    active.onAction(ScreenAction.EditField(ScreenFieldId(fieldId), result))
                }
            }
            is UiAction.RemovePhoto -> {
                discardPendingPhoto(action.currentValue)
                active.onAction(ScreenAction.EditField(ScreenFieldId(action.fieldId), REMOVE_PHOTO_VALUE))
            }
        }
    }

    private fun currentPhotoDraft(): String? = active.model.value.fields
        .firstOrNull { it.id.value == PHOTO_FIELD_ID }
        ?.value

    private fun discardPendingPhoto(value: String?) {
        if (value?.startsWith(PENDING_PHOTO_PREFIX) != true) return
        platformActions.perform(
            PlatformActionRequest(PlatformActionKind.DISCARD_PENDING_PHOTO, opaqueValue = value),
        ) { }
    }

    private fun updateField(fieldId: String, value: String, choose: Boolean) {
        val id = ScreenFieldId(fieldId)
        active.onAction(if (choose) ScreenAction.ChooseOption(id, value) else ScreenAction.EditField(id, value))
        val operation = when (active.model.value.screen to fieldId) {
            ScreenId.SETTINGS to "theme" -> "set-theme"
            ScreenId.SETTINGS to "enabled" -> "set-crash-reporting"
            else -> return
        }
        val draft = active.model.value.fields.associate { it.id to it.value } + (id to value)
        val preparation = ScreenContractRegistry.prepareCommand(
            screen = ScreenId.SETTINGS,
            actionId = ScreenActionId(operation),
            currentSubjectId = null,
            draft = draft,
        )
        if (preparation is CommandPreparation.Ready) active.onAction(ScreenAction.Execute(preparation.command))
    }

    private fun submitItem(operation: String, subjectId: String) {
        val preparation = ScreenContractRegistry.prepareCommand(
            screen = active.model.value.screen,
            actionId = ScreenActionId(operation),
            currentSubjectId = subjectId,
            draft = emptyMap(),
        )
        if (preparation is CommandPreparation.Ready) {
            active.onAction(ScreenAction.Execute(preparation.command))
        }
    }

    private fun navigate(screen: UiScreen, stableId: String?) {
        val target = screen.toScreenId()
        val primary = PrimaryDestination.entries.firstOrNull { it.screen == target }
        if (primary != null) root.select(primary) else root.open(target, stableId)
    }

    private fun submit(actionId: String) {
        val navigation = navigationActions[actionId]
        if (navigation != null) {
            navigate(navigation.screen, if (navigation.passSelection) selectedItemId() else null)
            return
        }
        val destination = destinationActions[actionId]
        if (destination != null) {
            var consumed = false
            platformActions.perform(
                PlatformActionRequest(
                    kind = PlatformActionKind.CREATE_DOCUMENT,
                    fieldId = DOCUMENT_FIELD_ID,
                    suggestedName = destination.suggestedName,
                    mediaType = destination.mediaType,
                ),
            ) { result ->
                if (consumed || result.isBlank()) return@perform
                consumed = true
                active.onAction(ScreenAction.EditField(ScreenFieldId(DOCUMENT_FIELD_ID), result))
                active.onAction(ScreenAction.Submit(ScreenActionId(actionId)))
            }
            return
        }
        val platform = platformActionIds[actionId]
        if (platform != null) {
            platformActions.perform(PlatformActionRequest(platform)) { }
            return
        }
        active.onAction(ScreenAction.Submit(ScreenActionId(actionId)))
    }

    private data class NavigationTarget(val screen: UiScreen, val passSelection: Boolean = false)
    private data class DestinationDocument(val suggestedName: String, val mediaType: String)

    private companion object {
        val navigationActions = mapOf(
            "new-batch" to NavigationTarget(UiScreen.BATCH_EDITOR),
            "open-batch" to NavigationTarget(UiScreen.BATCH_DETAIL, passSelection = true),
            "add-observation" to NavigationTarget(UiScreen.OBSERVATION_EDITOR, passSelection = true),
            "use-recipe" to NavigationTarget(UiScreen.BATCH_EDITOR, passSelection = true),
            "open-recipe" to NavigationTarget(UiScreen.RECIPE, passSelection = true),
            "create-recipe" to NavigationTarget(UiScreen.RECIPE),
            "open-zones" to NavigationTarget(UiScreen.ZONES),
            "open-tray-sizes" to NavigationTarget(UiScreen.TRAY_SIZES),
            "open-notifications" to NavigationTarget(UiScreen.NOTIFICATIONS),
            "open-backup-restore" to NavigationTarget(UiScreen.BACKUP_RESTORE),
            "open-advertising-privacy" to NavigationTarget(UiScreen.ADVERTISING_PRIVACY),
        )
        val platformActionIds = mapOf(
            "request-notification-permission" to PlatformActionKind.REQUEST_NOTIFICATION_PERMISSION,
            "open-system-settings" to PlatformActionKind.OPEN_SYSTEM_SETTINGS,
            "open-privacy-policy" to PlatformActionKind.OPEN_PRIVACY_POLICY,
        )
        val destinationActions = mapOf(
            "create-backup" to DestinationDocument("sproutpace-backup.zip", "application/zip"),
            "export-csv" to DestinationDocument("sproutpace-history.csv", "text/csv"),
        )
        const val DOCUMENT_FIELD_ID = "documentId"
        const val PHOTO_FIELD_ID = "photoId"
    }
}

internal fun SproutPaceUiState.adPlacementRequestOrNull(): AdPlacementRequest? = when {
    offline -> null
    screen == UiScreen.CATALOG && items.size >= MIN_CATALOG_ITEMS -> AdPlacementRequest(
        surface = AdSurface.CATALOG,
        realItemCount = items.size,
        offline = offline,
        slotIndex = InlineAdPlacementPolicy.CATALOG_INSERTION_INDEX,
    )
    screen == UiScreen.TRAYS && trayCategory == TrayCategory.HISTORY && items.size >= MIN_HISTORY_ITEMS -> {
        AdPlacementRequest(
            surface = AdSurface.HISTORY,
            realItemCount = items.size,
            offline = offline,
            slotIndex = InlineAdPlacementPolicy.HISTORY_INSERTION_INDEX,
        )
    }
    else -> null
}

internal class AdvertisingVisitGate {
    private var attempted = false

    fun claim(request: AdPlacementRequest?): AdPlacementRequest? {
        if (request == null || attempted) return null
        attempted = true
        return request
    }
}

internal suspend fun prepareAdvertisingVisit(
    runtime: AdvertisingRuntime,
    request: AdPlacementRequest,
    onConsentRequired: () -> Unit,
): String? = when (val prepared = runtime.prepare(request)) {
    is AdPreparation.Ready -> prepared.adUnitId
    is AdPreparation.ConsentRequired -> null.also { onConsentRequired() }
    is AdPreparation.Disabled -> null
}

internal suspend fun handleAdvertisingAction(
    action: UiAction,
    runtime: AdvertisingRuntime,
    onDecisionComplete: () -> Unit,
): Boolean = when (action) {
    UiAction.AcceptAdvertisingConsent -> runtime.acceptConsent().also { if (it) onDecisionComplete() }
    UiAction.DeclineAdvertisingConsent -> runtime.declineConsent().also { if (it) onDecisionComplete() }
    UiAction.WithdrawAdvertisingConsent -> runtime.withdrawConsent()
    else -> false
}

internal fun SproutPaceUiState.withAdvertisingPrivacy(
    privacy: AdvertisingPrivacyState?,
): SproutPaceUiState {
    if (screen != UiScreen.ADVERTISING_PRIVACY) return this
    val policy = UiScreenAction(
        id = ACTION_PRIVACY_POLICY,
        labelKey = "action_open_privacy_policy",
        operation = null,
        enabled = true,
        primary = false,
    )
    val actions = when (privacy?.choice) {
        ConsentChoice.ACCEPTED -> listOf(
            UiScreenAction(ACTION_WITHDRAW, "action_withdraw_advertising_consent", null, true, false),
            policy,
        )
        ConsentChoice.UNDECIDED, ConsentChoice.DECLINED, ConsentChoice.WITHDRAWN -> listOf(
            UiScreenAction(ACTION_ACCEPT, "action_accept_advertising_consent", null, true, false),
            UiScreenAction(ACTION_DECLINE, "action_decline_advertising_consent", null, true, false),
            policy,
        )
        null -> listOf(policy)
    }
    return copy(actions = actions)
}

internal fun UiScreenAction.toAdvertisingUiAction(): UiAction? = when (id) {
    ACTION_ACCEPT -> UiAction.AcceptAdvertisingConsent
    ACTION_DECLINE -> UiAction.DeclineAdvertisingConsent
    ACTION_WITHDRAW -> UiAction.WithdrawAdvertisingConsent
    else -> null
}

private const val MIN_CATALOG_ITEMS = 5
private const val MIN_HISTORY_ITEMS = 6
private const val ACTION_ACCEPT = "accept-advertising-consent"
private const val ACTION_DECLINE = "decline-advertising-consent"
private const val ACTION_WITHDRAW = "withdraw-advertising-consent"
private const val ACTION_PRIVACY_POLICY = "open-privacy-policy"

internal fun ScreenModel.toUiState(
    selectedPrimary: UiScreen = screen.toUiScreen().takeIf { it.isPrimary() } ?: UiScreen.TODAY,
): SproutPaceUiState {
    val contract = ScreenContractRegistry.contracts.getValue(screen)
    val trayCategory = trayCategory()
    val variant = uiVariant()
    return SproutPaceUiState(
        screen = screen.toUiScreen(),
        variant = variant,
        selectedPrimary = selectedPrimary,
        busy = busy,
        offline = offline,
        permissionState = permissionState,
        trayCategory = trayCategory,
        showInlineAd = showInlineAdSlot,
        items = visibleItems(trayCategory).map { it.toUiItem(selectedItemId) },
        fields = fields.filterNot { screen == ScreenId.TRAYS && it.id.value == TRAY_CATEGORY_FIELD_ID }
            .map { it.toUiField(validationErrors[it.id]?.value) },
        facts = facts.map { fact ->
            UiFact(fact.id, fact.labelKey.value, fact.valueKey?.value, fact.authoredValue)
        },
        actions = visibleActions(contract, variant),
        conflictKeys = conflictResolutionKeys.map { it.value },
        hasUnsavedChanges = hasUnsavedChanges,
        errorKey = errorKey?.value,
    )
}

private fun ScreenModel.trayCategory(): TrayCategory = fields
    .firstOrNull { it.id.value == TRAY_CATEGORY_FIELD_ID }
    ?.value
    ?.let { value -> TrayCategory.entries.firstOrNull { it.id == value } }
    ?: TrayCategory.PLANNED

private fun ScreenModel.visibleItems(trayCategory: TrayCategory): List<ScreenItem> = if (screen == ScreenId.TRAYS) {
    items.filter { item ->
        when (trayCategory) {
            TrayCategory.PLANNED -> item.stateKey?.value == "option_batch_state_planned"
            TrayCategory.HISTORY -> item.stateKey?.value in TERMINAL_BATCH_STATE_KEYS
            TrayCategory.ACTIVE -> item.stateKey?.value != "option_batch_state_planned" &&
                item.stateKey?.value !in TERMINAL_BATCH_STATE_KEYS
        }
    }
} else {
    items
}

private fun ScreenModel.uiVariant(): UiVariant = when {
    destructiveConfirmationKey != null -> UiVariant.DESTRUCTIVE_CONFIRMATION
    loadState == LoadState.LOADING || busy -> UiVariant.PROGRESS
    loadState == LoadState.ERROR -> UiVariant.ERROR
    validationErrors.isNotEmpty() -> UiVariant.VALIDATION_ERROR
    permissionState == PermissionState.DENIED || permissionState == PermissionState.PERMANENTLY_DENIED -> {
        UiVariant.PERMISSION_DENIED
    }
    screen == ScreenId.BATCH_DETAIL && conflictResolutionKeys.any { it.value == "leave_unassigned" } -> {
        UiVariant.TEMPORARILY_UNASSIGNED
    }
    conflictResolutionKeys.isNotEmpty() -> UiVariant.CONFLICT
    loadState == LoadState.EMPTY -> UiVariant.EMPTY
    screen == ScreenId.BACKUP_RESTORE && facts.any { it.id.startsWith("restore-") } -> UiVariant.RESTORE_PREVIEW
    screen == ScreenId.ADVERTISING_PRIVACY -> UiVariant.PRIVACY_DECISION
    else -> UiVariant.CONTENT
}

private fun ScreenModel.visibleActions(contract: ScreenContract, variant: UiVariant): List<UiScreenAction> {
    val snapshotPrimaryAction = primaryActionKey?.let { key -> primaryAction(key.value, contract) }
    if (variant == UiVariant.EMPTY) return listOfNotNull(snapshotPrimaryAction)
    val mappedActions = actions.map { descriptor ->
        UiScreenAction(
            id = descriptor.id.value,
            labelKey = descriptor.labelKey.value,
            operation = descriptor.operation,
            enabled = descriptor.enabled,
            primary = false,
        )
    }
    return listOfNotNull(snapshotPrimaryAction) + mappedActions.filterNot { it.id == snapshotPrimaryAction?.id }
}

private fun ScreenItem.toUiItem(selectedItemId: String?) = UiItem(
    id = id,
    titleKey = titleKey?.value,
    authoredTitle = authoredTitle,
    supportingKey = supportingKey?.value,
    stateKey = stateKey?.value,
    actionId = actionId,
    selected = id == selectedItemId,
    commands = commands.map { command -> UiItemCommand(command.operation, command.subjectId) },
    photo = photo?.toUiPhoto(),
)

private fun ScreenField.toUiField(errorKey: String?) = UiField(
    id = id.value,
    labelKey = labelKey.value,
    kind = kind,
    value = value,
    required = required,
    readOnly = readOnly,
    options = options.map { option -> UiOption(option.id, option.labelKey?.value, option.authoredLabel) },
    errorKey = errorKey,
)

private fun primaryAction(
    labelKey: String,
    contract: ScreenContract,
): UiScreenAction {
    val id = requireNotNull(primaryActionIds[labelKey]) { "Unsupported primary action key '$labelKey'" }
    return UiScreenAction(
        id = id,
        labelKey = labelKey,
        operation = id.takeIf(contract.operations::containsKey),
        enabled = true,
        primary = true,
    )
}

private fun ScreenPhoto.toUiPhoto() = UiPhoto(id, bytes, contentDescriptionKey.value)

private const val TRAY_CATEGORY_FIELD_ID = "category"
private val primaryActionIds = mapOf(
    "action_continue" to "continue-setup",
    "action_new_batch" to "new-batch",
    "action_save_batch" to "save-batch",
    "action_confirm_stage" to "confirm-stage",
    "action_confirm_suggestion" to "confirm-suggestion",
    "action_use_recipe" to "use-recipe",
    "action_save_observation" to "save-observation",
    "action_save_outcome" to "save-outcome",
    "action_add_zone" to "save-zone",
    "action_add_preset" to "save-tray-size",
    "action_accept" to ACTION_ACCEPT,
    "action_request_permission" to "request-notification-permission",
)
private val TERMINAL_BATCH_STATE_KEYS = setOf(
    "option_batch_state_harvested",
    "option_batch_state_failed",
    "option_batch_state_abandoned",
)

internal fun ScreenId.toUiScreen(): UiScreen = UiScreen.valueOf(name)
internal fun UiScreen.toScreenId(): ScreenId = ScreenId.valueOf(name)

internal fun selectedPrimaryScreen(active: ScreenId, backStack: List<ScreenId>): UiScreen =
    (listOf(active) + backStack.asReversed()).firstOrNull { candidate ->
        PrimaryDestination.entries.any { it.screen == candidate }
    }?.toUiScreen() ?: UiScreen.TODAY

private fun UiScreen.isPrimary() = this in setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN, UiScreen.CATALOG)
private fun UiLayout.toComponentLayout(): LayoutMode = LayoutMode.valueOf(name)

@Composable
private fun <T : Any> Value<T>.asComposeState(): State<T> {
    val state = remember(this) { mutableStateOf(value) }
    DisposableEffect(this) {
        val cancellation = subscribe { state.value = it }
        onDispose(cancellation::cancel)
    }
    return state
}
