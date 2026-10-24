package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.delay
import com.sedsoftware.sproutpace.monetization.InlineAdSession
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
    advertisingExpiryDelay: suspend (Long) -> Unit = { delay(it) },
) {
    val theme = (root as? ThemePreferenceOwner)?.themePreference?.collectAsState()?.value
        ?: ThemePreference.SYSTEM
    val advertisingRuntime = (root as? AdvertisingRuntimeOwner)?.advertisingRuntime
    val privacyState = advertisingRuntime?.privacyState?.collectAsState()?.value
    val networkAvailable = advertisingRuntime?.networkAvailable?.collectAsState()?.value == true
    AppTheme(theme, onThemeChanged) {
    val stack by root.stack.asComposeState()
    val activeComponent = stack.active.instance
    val model by activeComponent.model.asComposeState()
    val confirmationSlot by root.confirmation.asComposeState()
    val settingsComponent = stack.backStack.lastOrNull { it.configuration.screen == ScreenId.SETTINGS }?.instance
    val settingsModel = settingsComponent?.model?.asComposeState()?.value
    val baseUiState = model.toUiState(
        selectedPrimary = selectedPrimaryScreen(
            active = stack.active.configuration.screen,
            backStack = stack.backStack.map { it.configuration.screen },
        ),
    ).withSettingsContext(settingsModel).withLocalizedCatalogSearch()
    var adUnitId by remember(activeComponent) { mutableStateOf<String?>(null) }
    val advertisingVisit = remember(activeComponent) { AdvertisingVisitGate() }
    val adSession = remember(activeComponent) { InlineAdSession(maxRetries = 0) }
    val adState by adSession.states.collectAsState()
    DisposableEffect(adSession) { onDispose { adSession.dispose() } }
    val placement = baseUiState.adPlacementRequestOrNull(networkAvailable)
    LaunchedEffect(activeComponent, placement) {
        if (placement == null || advertisingRuntime == null) {
            adUnitId = null
            return@LaunchedEffect
        }
        val request = advertisingVisit.claim(placement) ?: return@LaunchedEffect
        val preparedAdUnit = prepareAdvertisingVisit(advertisingRuntime, request) {
            if (root.stack.value.active.instance === activeComponent) root.open(ScreenId.ADVERTISING_PRIVACY, null)
        }
        if (root.stack.value.active.instance === activeComponent) adUnitId = preparedAdUnit
    }
    LaunchedEffect(activeComponent, adUnitId, privacyState) {
        val unit = adUnitId ?: return@LaunchedEffect
        val runtime = advertisingRuntime ?: return@LaunchedEffect
        if (runtime.canRequestAd(unit)) {
            val expiresAt = privacyState?.expiresAtEpochMillis ?: return@LaunchedEffect
            advertisingExpiryDelay((expiresAt - runtime.nowEpochMillis()).coerceAtLeast(0))
        }
        adUnitId = null
        adSession.dispose()
    }
    val adEligible = adUnitId?.let { advertisingRuntime?.canRequestAd(it) } == true &&
        adState != com.sedsoftware.sproutpace.monetization.InlineAdState.FAILED &&
        adState != com.sedsoftware.sproutpace.monetization.InlineAdState.DISPOSED
    val uiState = baseUiState
        .withAdvertisingPrivacy(privacyState, advertisingRuntime?.nowEpochMillis()
            ?: kotlin.time.Clock.System.now().toEpochMilliseconds())
        .copy(showInlineAd = adEligible,
            inlineAdUnitId = adUnitId.takeIf { adEligible })
    val router = remember(root, activeComponent, platformActions) {
        RootActionRouter(root, activeComponent, platformActions) { activeComponent.model.value.selectedItemId }
    }

    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalInlineAdSession provides adSession) {
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
    }
        confirmationSlot.child?.instance?.let { confirmation ->
            RootConfirmationDialog(confirmation, baseUiState)
        }
    }
}

@Composable
internal fun RootConfirmationDialog(component: ConfirmationComponent, source: SproutPaceUiState? = null) {
    val copy = confirmationCopy(component.destructiveOperation)
    AlertDialog(
        onDismissRequest = component::cancel,
        title = { Text(localizedText(copy.title)) },
        text = { ConfirmationBody(component.command, source, copy.body) },
        confirmButton = {
            TextButton(onClick = component::confirm) { Text(localizedText(copy.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = component::cancel) { Text(localizedText("temporal_cancel")) }
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
            is UiAction.Submit -> submit(action.actionId, action.subjectId)
            UiAction.ConfirmStagePreview -> active.onAction(ScreenAction.ConfirmStagePreview)
            is UiAction.SubmitItem -> submitItem(action.operation, action.subjectId, action.arguments)
            is UiAction.RequestPlatform -> platformActions.perform(action.request.withFailureHandler()) { result ->
                if (result.isBlank()) return@perform
                action.request.fieldId?.let { fieldId ->
                    if (action.request.kind == PlatformActionKind.CHOOSE_PHOTO) {
                        discardPendingPhoto(currentPhotoDraft())
                    }
                    active.onAction(ScreenAction.EditField(ScreenFieldId(fieldId), result))
                }
            }
            is UiAction.ClearPickerSelection -> {
                if (action.kind == PlatformActionKind.CHOOSE_PHOTO) {
                    discardPendingPhoto(action.currentValue)
                }
                val clearedValue = if (action.kind == PlatformActionKind.CHOOSE_PHOTO) REMOVE_PHOTO_VALUE else ""
                active.onAction(ScreenAction.EditField(ScreenFieldId(action.fieldId), clearedValue))
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
            ScreenId.NOTIFICATIONS to "dailySummaryEnabled",
            ScreenId.NOTIFICATIONS to "soakRemindersEnabled",
            ScreenId.NOTIFICATIONS to "summaryTime" -> "update-notifications"
            else -> return
        }
        val draft = active.model.value.fields.associate { it.id to it.value } + (id to value)
        val preparation = ScreenContractRegistry.prepareCommand(
            screen = active.model.value.screen,
            actionId = ScreenActionId(operation),
            currentSubjectId = null,
            draft = draft,
        )
        if (preparation is CommandPreparation.Ready) active.onAction(ScreenAction.Execute(preparation.command))
    }

    private fun submitItem(operation: String, subjectId: String, arguments: Map<String, String>) {
        if (operation in navigationActions || operation == "repeat-batch") {
            submit(operation, subjectId)
            return
        }
        val preparation = ScreenContractRegistry.prepareCommand(
            screen = active.model.value.screen,
            actionId = ScreenActionId(operation),
            currentSubjectId = subjectId,
            draft = arguments.mapKeys { ScreenFieldId(it.key) },
        )
        if (preparation is CommandPreparation.Ready) {
            active.onAction(ScreenAction.Execute(preparation.command))
        }
    }

    private fun navigate(screen: UiScreen, stableId: String?) {
        val target = screen.toScreenId()
        if (active.model.value.screen == ScreenId.BATCH_EDITOR && target in setOf(ScreenId.ZONES, ScreenId.TRAY_SIZES)) {
            root.openDependency(target)
            return
        }
        val primary = PrimaryDestination.entries.firstOrNull { it.screen == target }
        if (primary != null) root.select(primary) else root.open(target, stableId)
    }

    private fun submit(actionId: String, subjectId: String? = null) {
        val navigation = navigationActions[actionId]
        val destination = destinationActions[actionId]
        val platform = platformActionIds[actionId]
        when {
            actionId == "repeat-batch" -> (subjectId ?: selectedItemId())?.let {
                root.open(ScreenId.BATCH_EDITOR, com.sedsoftware.sproutpace.component.BatchRepeatDraft.route(it))
            }
            actionId == "edit-plan" && subjectId != null -> root.open(ScreenId.PLAN, subjectId)
            actionId == "new-zone" || actionId == "new-tray-size" -> {
                val target = if (actionId == "new-zone") UiScreen.ZONES else UiScreen.TRAY_SIZES
                val prefix = if (target == UiScreen.ZONES) "zone" else "tray"
                navigate(target, "$prefix-${kotlin.time.Clock.System.now().toEpochMilliseconds()}")
            }
            actionId == "preview-restore" -> selectDocument(actionId, PlatformActionRequest(
                PlatformActionKind.CHOOSE_DOCUMENT, DOCUMENT_FIELD_ID,
            ))
            navigation != null -> navigate(
                navigation.screen, if (navigation.passSelection) subjectId ?: selectedItemId() else null,
            )
            destination != null -> selectDocument(actionId, PlatformActionRequest(
                kind = PlatformActionKind.CREATE_DOCUMENT,
                fieldId = DOCUMENT_FIELD_ID,
                suggestedName = destination.suggestedName,
                mediaType = destination.mediaType,
            ))
            platform != null -> platformActions.perform(PlatformActionRequest(platform)) { }
            else -> active.onAction(ScreenAction.Submit(ScreenActionId(actionId)))
        }
    }

    private fun selectDocument(actionId: String, request: PlatformActionRequest) {
        var consumed = false
        platformActions.perform(request.withFailureHandler()) { result ->
            if (!consumed && result.isNotBlank()) {
                consumed = true
                active.onAction(ScreenAction.EditField(ScreenFieldId(DOCUMENT_FIELD_ID), result))
                active.onAction(ScreenAction.Submit(ScreenActionId(actionId)))
            }
        }
    }

    private fun PlatformActionRequest.withFailureHandler() = copy(onFailure = { failure ->
        active.onAction(ScreenAction.ReportFailure(com.sedsoftware.sproutpace.component.TextKey(failure.textKey)))
    })

    private data class NavigationTarget(val screen: UiScreen, val passSelection: Boolean = false)
    private data class DestinationDocument(val suggestedName: String, val mediaType: String)

    private companion object {
        val navigationActions = mapOf(
            "edit-zone" to NavigationTarget(UiScreen.ZONES, passSelection = true),
            "edit-tray-size" to NavigationTarget(UiScreen.TRAY_SIZES, passSelection = true),
            "resume-setup" to NavigationTarget(UiScreen.FIRST_RUN),
            "open-plan" to NavigationTarget(UiScreen.PLAN),
            "new-batch" to NavigationTarget(UiScreen.BATCH_EDITOR),
            "confirm-suggestion" to NavigationTarget(UiScreen.BATCH_EDITOR, passSelection = true),
            "edit-batch" to NavigationTarget(UiScreen.BATCH_EDITOR, passSelection = true),
            "open-batch" to NavigationTarget(UiScreen.BATCH_DETAIL, passSelection = true),
            "add-observation" to NavigationTarget(UiScreen.OBSERVATION_EDITOR, passSelection = true),
            "close-batch" to NavigationTarget(UiScreen.CLOSE_BATCH, passSelection = true),
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

internal fun SproutPaceUiState.adPlacementRequestOrNull(networkAvailable: Boolean = !offline): AdPlacementRequest? = when {
    !networkAvailable -> null
    screen == UiScreen.CATALOG && items.size >= MIN_CATALOG_ITEMS -> AdPlacementRequest(
        surface = AdSurface.CATALOG,
        realItemCount = items.size,
        offline = false,
        slotIndex = InlineAdPlacementPolicy.CATALOG_INSERTION_INDEX,
    )
    screen == UiScreen.TRAYS && trayCategory == TrayCategory.HISTORY && items.size >= MIN_HISTORY_ITEMS -> {
        AdPlacementRequest(
            surface = AdSurface.HISTORY,
            realItemCount = items.size,
            offline = false,
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
    nowEpochMillis: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
): SproutPaceUiState {
    if (screen != UiScreen.ADVERTISING_PRIVACY) return this
    val policy = UiScreenAction(
        id = ACTION_PRIVACY_POLICY,
        labelKey = "action_open_privacy_policy",
        operation = null,
        enabled = true,
        primary = false,
    )
    val available = privacy?.let {
        com.sedsoftware.sproutpace.monetization.PrivacyRepository.isFresh(it, nowEpochMillis) && it.consentRequired
    } == true
    val actions = when (privacy?.choice.takeIf { available }) {
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
    val status = when {
        privacy == null -> "privacy_endpoint_unavailable"
        com.sedsoftware.sproutpace.monetization.PrivacyRepository.isFresh(privacy, nowEpochMillis) &&
            !privacy.consentRequired -> "privacy_not_required"
        !available -> "privacy_endpoint_stale"
        else -> "privacy_${privacy.choice.name.lowercase()}"
    }
    return copy(actions = actions, items = items.map { if (it.id == "privacy") it.copy(stateKey = status) else it },
        facts = listOfNotNull(
        UiFact("privacy-applicability", "privacy_heading", status, null),
        privacy?.takeIf { available }?.let { UiFact("policy-version", "privacy_policy_version", null, it.policyVersion) },
    ))
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
    val visible = visibleItems(trayCategory)
    val variant = if (screen == ScreenId.TRAYS && loadState == LoadState.CONTENT && visible.isEmpty()) {
        UiVariant.EMPTY
    } else uiVariant()
    return SproutPaceUiState(
        screen = screen.toUiScreen(),
        variant = variant,
        selectedPrimary = selectedPrimary,
        selectedItemId = selectedItemId,
        busy = busy,
        offline = offline,
        permissionState = permissionState,
        trayCategory = trayCategory,
        showInlineAd = showInlineAdSlot,
        items = visible.map { it.toUiItem(selectedItemId) },
        fields = fields.filterNot { screen == ScreenId.TRAYS && it.id.value == TRAY_CATEGORY_FIELD_ID }
            .map { it.toUiField(validationErrors[it.id]?.value) },
        facts = facts.map { fact ->
            if (screen == ScreenId.TRAYS && fact.id == "batch-count") {
                UiFact(fact.id, trayCategory.labelKey, null, visible.size.toString())
            } else UiFact(fact.id, fact.labelKey.value, fact.valueKey?.value, fact.authoredValue)
        },
        actions = visibleActions(contract, variant),
        conflictKeys = conflictResolutionKeys.map { it.value },
        hasUnsavedChanges = hasUnsavedChanges,
        errorKey = errorKey?.value,
        setupState = setupState,
        plans = plans,
        detail = detail?.toUiState(selectedPrimary = selectedPrimary),
        stagePreview = stagePreview,
        completionRevision = completionRevision,
        lastCompletedOperation = lastCompletedOperation,
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
            TrayCategory.PLANNED -> item.batch?.state == com.sedsoftware.sproutpace.domain.model.BatchState.PLANNED
            TrayCategory.HISTORY -> item.batch?.state?.isTerminal == true
            TrayCategory.ACTIVE -> item.batch?.state?.let {
                it != com.sedsoftware.sproutpace.domain.model.BatchState.PLANNED && !it.isTerminal
            } == true
        }
    }
} else {
    items
}

private fun ScreenModel.uiVariant(): UiVariant = when {
    destructiveConfirmationKey != null -> UiVariant.DESTRUCTIVE_CONFIRMATION
    loadState == LoadState.LOADING -> UiVariant.PROGRESS
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
    if (variant == UiVariant.EMPTY && screen != ScreenId.PLAN) return listOfNotNull(snapshotPrimaryAction)
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
    batch = batch,
    task = task,
    occupancy = occupancy,
    planEntry = planEntry,
    recipe = recipe,
    history = history,
    snapshotStage = snapshotStage,
    trayShape = trayShape,
)

private fun ScreenField.toUiField(errorKey: String?) = UiField(
    id = id.value,
    labelKey = labelKey.value,
    kind = kind,
    value = value,
    required = required,
    readOnly = readOnly,
    options = options.map { option -> UiOption(option.id, option.labelKey?.value, option.authoredLabel, option.batchIdentity) },
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
    "action_resume_setup" to "resume-setup",
    "action_open_plan" to "open-plan",
    "action_save_recipe" to "save-recipe",
    "action_continue" to "continue-setup",
    "action_new_batch" to "new-batch",
    "action_save_batch" to "save-batch",
    "action_save_plan" to "save-plan",
    "action_confirm_stage" to "confirm-stage",
    "action_start_soaking" to "start-soaking",
    "action_confirm_sowing" to "confirm-sowing",
    "action_close_batch" to "close-batch",
    "action_confirm_suggestion" to "confirm-suggestion",
    "action_use_recipe" to "use-recipe",
    "action_save_observation" to "save-observation",
    "action_save_outcome" to "save-outcome",
    "action_add_zone" to "save-zone",
    "action_add_preset" to "save-tray-size",
    "action_accept" to ACTION_ACCEPT,
    "action_request_permission" to "request-notification-permission",
)

internal fun ScreenId.toUiScreen(): UiScreen = UiScreen.valueOf(name)
internal fun UiScreen.toScreenId(): ScreenId = ScreenId.valueOf(name)

internal fun SproutPaceUiState.contextualFormFields(): List<UiField> {
    val values = fields.associate { it.id to it.value }
    return fields.filter { field ->
        when {
            field.id == "id" -> false
            screen == UiScreen.OBSERVATION_EDITOR && field.id == "batchId" -> values["batchId"].isNullOrBlank()
            screen == UiScreen.TRAY_SIZES && field.id in setOf("widthCm", "lengthCm") -> values["shape"] != "circle"
            screen == UiScreen.TRAY_SIZES && field.id == "diameterCm" -> values["shape"] == "circle"
            screen == UiScreen.CLOSE_BATCH && field.id in setOf("actualMassGrams", "assessment") -> values["outcome"] == "harvested"
            screen == UiScreen.CLOSE_BATCH && field.id == "reason" -> values["outcome"] != "harvested"
            else -> true
        }
    }
}

internal fun fieldHelpKey(field: UiField): String? = when (field.id) {
    "widthCm", "lengthCm", "diameterCm" -> "form_help_centimetres"
    "actualMassGrams", "actualSeedMassGrams" -> "form_help_grams"
    "capacity" -> "form_help_places"
    "cadenceDays" -> "form_help_days"
    else -> null
}

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
