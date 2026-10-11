package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.integration.AdvertisingPrivacyComponentPreview
import com.sedsoftware.sproutpace.component.integration.BackupRestoreComponentPreview
import com.sedsoftware.sproutpace.component.integration.BatchDetailComponentPreview
import com.sedsoftware.sproutpace.component.integration.BatchEditorComponentPreview
import com.sedsoftware.sproutpace.component.integration.CatalogComponentPreview
import com.sedsoftware.sproutpace.component.integration.CloseBatchComponentPreview
import com.sedsoftware.sproutpace.component.integration.FirstRunComponentPreview
import com.sedsoftware.sproutpace.component.integration.NotificationsComponentPreview
import com.sedsoftware.sproutpace.component.integration.ObservationEditorComponentPreview
import com.sedsoftware.sproutpace.component.integration.PlanComponentPreview
import com.sedsoftware.sproutpace.component.integration.RecipeComponentPreview
import com.sedsoftware.sproutpace.component.integration.SettingsComponentPreview
import com.sedsoftware.sproutpace.component.integration.TodayComponentPreview
import com.sedsoftware.sproutpace.component.integration.TraySizesComponentPreview
import com.sedsoftware.sproutpace.component.integration.TraysComponentPreview
import com.sedsoftware.sproutpace.component.integration.ZonesComponentPreview
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures

internal data class UiPreviewCase(
    val screen: UiScreen,
    val variant: UiVariant = UiVariant.CONTENT,
    val locale: String = "en",
    val fontScale: Float = 1f,
    val widthDp: Int = 393,
)

internal val requiredUiPreviewCases = buildList {
    UiScreen.entries.forEach { screen ->
        add(UiPreviewCase(screen))
        add(UiPreviewCase(screen, locale = "ru", fontScale = 2f))
    }
    listOf(
        UiPreviewCase(UiScreen.TODAY, UiVariant.EMPTY),
        UiPreviewCase(UiScreen.TRAYS, UiVariant.EMPTY),
        UiPreviewCase(UiScreen.PLAN, UiVariant.EMPTY),
        UiPreviewCase(UiScreen.FIRST_RUN, UiVariant.VALIDATION_ERROR),
        UiPreviewCase(UiScreen.BACKUP_RESTORE, UiVariant.ERROR),
        UiPreviewCase(UiScreen.NOTIFICATIONS, UiVariant.PERMISSION_DENIED),
        UiPreviewCase(UiScreen.PLAN, UiVariant.CONFLICT),
        UiPreviewCase(UiScreen.BATCH_DETAIL, UiVariant.TEMPORARILY_UNASSIGNED),
        UiPreviewCase(UiScreen.BACKUP_RESTORE, UiVariant.RESTORE_PREVIEW),
        UiPreviewCase(UiScreen.SETTINGS, UiVariant.DESTRUCTIVE_CONFIRMATION),
        UiPreviewCase(UiScreen.ADVERTISING_PRIVACY, UiVariant.PRIVACY_DECISION),
    ).forEach(::add)
    listOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.BATCH_DETAIL, UiScreen.PLAN, UiScreen.CATALOG)
        .forEach { add(UiPreviewCase(it, widthDp = 1100)) }
}

internal fun typedPreviewState(
    screen: UiScreen,
    variant: UiVariant = UiVariant.CONTENT,
    showAd: Boolean = false,
    scenario: ProductionScreenFixtures.Scenario = when (variant) {
        UiVariant.EMPTY -> ProductionScreenFixtures.Scenario.EMPTY
        UiVariant.CONFLICT -> ProductionScreenFixtures.Scenario.CAPACITY_CONFLICT
        UiVariant.TEMPORARILY_UNASSIGNED -> ProductionScreenFixtures.Scenario.UNASSIGNED
        UiVariant.RESTORE_PREVIEW -> ProductionScreenFixtures.Scenario.RESTORE_PREVIEW
        else -> ProductionScreenFixtures.Scenario.CONTENT
    },
    draft: Map<String, String> = emptyMap(),
    permissionOverride: PermissionState? = null,
    selectedId: String? = ProductionScreenFixtures.selectedId(screen.toScreenId()),
    errorKeyOverride: String? = null,
    platformProjection: (com.sedsoftware.sproutpace.component.ScreenSnapshot) ->
        com.sedsoftware.sproutpace.component.ScreenSnapshot = { it },
): SproutPaceUiState {
    val screenId = screen.toScreenId()
    val snapshot = platformProjection(ProductionScreenFixtures.snapshot(screenId, stableId = selectedId, scenario = scenario))
    val draftValues = if (variant == UiVariant.VALIDATION_ERROR) invalidPreviewDraft(screen) + draft else draft
    val fields = snapshot.fields.map { field -> field.copy(value = draftValues[field.id.value] ?: field.value) }
    val errors = if (variant == UiVariant.VALIDATION_ERROR) {
        val operation = requireNotNull(ScreenContractRegistry.contracts.getValue(screenId).primaryOperation)
        val preparation = ScreenContractRegistry.prepareCommand(screenId, ScreenActionId(operation),
            snapshot.selectedItemId ?: fields.firstOrNull { it.id.value == "id" }?.value,
            fields.associate { it.id to it.value })
        check(preparation is CommandPreparation.Invalid)
        preparation.errors
    } else emptyMap()
    val denied = variant == UiVariant.PERMISSION_DENIED
    val permission = permissionOverride ?: if (denied) PermissionState.PERMANENTLY_DENIED else snapshot.permissionState
    val model = ScreenModel(
        screen = screenId,
        loadState = previewLoadState(variant, scenario, errorKeyOverride),
        errorKey = errorKeyOverride?.let(::TextKey) ?: when {
            scenario == ProductionScreenFixtures.Scenario.BACKUP_WRITE_FAILED -> TextKey("error_document_write_failed")
            variant == UiVariant.ERROR -> TextKey("error_backup_invalid")
            else -> null
        },
        titleKey = TextKey("screen_${screen.name.lowercase()}_title"),
        items = snapshot.items,
        selectedItemId = snapshot.selectedItemId ?: selectedId,
        plans = snapshot.plans,
        setupState = snapshot.setupState,
        detail = previewSelectedDetail(screen, selectedId, scenario),
        primaryActionKey = snapshot.primaryActionKey,
        permissionState = permission,
        conflictResolutionKeys = snapshot.conflictResolutionKeys,
        offline = snapshot.offline,
        fields = fields,
        facts = snapshot.facts,
        actions = if (screen == UiScreen.NOTIFICATIONS) snapshot.actions.filter {
            it.id.value in notificationPermissionActionIds(
                fields.any { field -> field.id.value in setOf("dailySummaryEnabled", "soakRemindersEnabled") && field.value == "true" },
                permission.previewPlatformPermission())
        } else snapshot.actions,
        validationErrors = errors,
    )
    val state = previewComponent(screen, model).model.value.toUiState().copy(
        showInlineAd = showAd,
    )
    return if (screen == UiScreen.ADVERTISING_PRIVACY) {
        state.withAdvertisingPrivacy(previewPrivacy(ConsentChoice.UNDECIDED), PREVIEW_PRIVACY_NOW)
    } else state
}

private fun previewLoadState(variant: UiVariant, scenario: ProductionScreenFixtures.Scenario, errorKey: String?): LoadState = when {
    errorKey != null || variant == UiVariant.ERROR || scenario == ProductionScreenFixtures.Scenario.BACKUP_WRITE_FAILED -> LoadState.ERROR
    variant == UiVariant.EMPTY -> LoadState.EMPTY
    variant == UiVariant.PROGRESS -> LoadState.LOADING
    else -> LoadState.CONTENT
}

private fun previewSelectedDetail(
    screen: UiScreen,
    selectedId: String?,
    scenario: ProductionScreenFixtures.Scenario,
): ScreenModel? {
    if (selectedId == null) return null
    val child = when (screen) {
        UiScreen.TRAYS -> UiScreen.BATCH_DETAIL
        UiScreen.CATALOG -> UiScreen.RECIPE
        else -> return null
    }
    val source = ProductionScreenFixtures.snapshot(child.toScreenId(), stableId = selectedId, scenario = scenario)
    val model = ScreenModel(
        screen = child.toScreenId(), loadState = LoadState.CONTENT,
        titleKey = TextKey("screen_${child.name.lowercase()}_title"),
        items = source.items, selectedItemId = source.selectedItemId,
        primaryActionKey = source.primaryActionKey, permissionState = source.permissionState,
        conflictResolutionKeys = source.conflictResolutionKeys, offline = source.offline,
        fields = source.fields, facts = source.facts, actions = source.actions,
        setupState = source.setupState, plans = source.plans,
    )
    return previewComponent(child, model).model.value
}

private fun PermissionState.previewPlatformPermission() = when (this) {
    PermissionState.GRANTED -> com.sedsoftware.sproutpace.platform.PermissionState.Granted
    PermissionState.DENIED -> com.sedsoftware.sproutpace.platform.PermissionState.Denied(true)
    PermissionState.PERMANENTLY_DENIED -> com.sedsoftware.sproutpace.platform.PermissionState.Denied(false)
    PermissionState.NOT_REQUIRED -> com.sedsoftware.sproutpace.platform.PermissionState.Unavailable
    PermissionState.NOT_REQUESTED -> com.sedsoftware.sproutpace.platform.PermissionState.Unknown
}

private fun invalidPreviewDraft(screen: UiScreen): Map<String, String> = when (screen) {
    UiScreen.FIRST_RUN, UiScreen.ZONES -> mapOf("capacity" to "0")
    UiScreen.BATCH_EDITOR -> mapOf("cultureId" to "radish", "trayId" to "shallow",
        "substrate" to "Coconut coir", "actualSeedMassGrams" to "-5")
    UiScreen.RECIPE -> mapOf("stage1MinimumHours" to "24", "stage1MaximumHours" to "2")
    UiScreen.TRAY_SIZES -> mapOf("widthCm" to "0", "diameterCm" to "0")
    UiScreen.CLOSE_BATCH -> mapOf("actualMassGrams" to "-5")
    else -> mapOf("occurredAt" to "invalid")
}

private fun previewComponent(screen: UiScreen, model: ScreenModel): ScreenComponent = when (screen) {
    UiScreen.FIRST_RUN -> FirstRunComponentPreview(model)
    UiScreen.TODAY -> TodayComponentPreview(model)
    UiScreen.TRAYS -> TraysComponentPreview(model)
    UiScreen.BATCH_EDITOR -> BatchEditorComponentPreview(model)
    UiScreen.BATCH_DETAIL -> BatchDetailComponentPreview(model)
    UiScreen.PLAN -> PlanComponentPreview(model)
    UiScreen.CATALOG -> CatalogComponentPreview(model)
    UiScreen.RECIPE -> RecipeComponentPreview(model)
    UiScreen.OBSERVATION_EDITOR -> ObservationEditorComponentPreview(model)
    UiScreen.CLOSE_BATCH -> CloseBatchComponentPreview(model)
    UiScreen.SETTINGS -> SettingsComponentPreview(model)
    UiScreen.ZONES -> ZonesComponentPreview(model)
    UiScreen.TRAY_SIZES -> TraySizesComponentPreview(model)
    UiScreen.BACKUP_RESTORE -> BackupRestoreComponentPreview(model)
    UiScreen.ADVERTISING_PRIVACY -> AdvertisingPrivacyComponentPreview(model)
    UiScreen.NOTIFICATIONS -> NotificationsComponentPreview(model)
}

internal fun previewPrivacy(choice: ConsentChoice) = AdvertisingPrivacyState(
    policyVersion = "2026-10",
    expiresAtEpochMillis = PREVIEW_PRIVACY_NOW + PREVIEW_PRIVACY_LIFETIME,
    choice = choice,
    fetchedAtEpochMillis = PREVIEW_PRIVACY_NOW - PREVIEW_PRIVACY_AGE,
)

internal const val PREVIEW_PRIVACY_NOW = 1_791_619_200_000L
private const val PREVIEW_PRIVACY_LIFETIME = 86_400_000L
private const val PREVIEW_PRIVACY_AGE = 1_000L

private fun historyAdPreviewState() = typedPreviewState(
    UiScreen.TRAYS,
    showAd = true,
    scenario = ProductionScreenFixtures.Scenario.HISTORY,
)

@Composable
private fun PreviewScreen(
    screen: UiScreen,
    variant: UiVariant = UiVariant.CONTENT,
    showAd: Boolean = false,
) = App(state = typedPreviewState(screen, variant, showAd))

@Preview(name = "01 First run Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "01 First run Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "01 First run RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun FirstRunPreviews() = PreviewScreen(UiScreen.FIRST_RUN)

@Preview(name = "02 Today Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "02 Today Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "02 Today RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun TodayPreviews() = PreviewScreen(UiScreen.TODAY)

@Preview(name = "03 Trays Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "03 Trays Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "03 Trays RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun TraysPreviews() = PreviewScreen(UiScreen.TRAYS)

@Preview(name = "03 Trays History Loaded Ad", widthDp = 393, heightDp = 851)
@Composable private fun TraysHistoryAdPreview() = App(state = historyAdPreviewState())

@Preview(name = "03 Trays History Loaded Ad Inspection", widthDp = 393, heightDp = 1300)
@Composable private fun TraysHistoryLoadedAdInspectionPreview() = App(state = historyAdPreviewState())

@Preview(name = "04 Batch editor Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "04 Batch editor Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "04 Batch editor RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun BatchEditorPreviews() = PreviewScreen(UiScreen.BATCH_EDITOR)

@Preview(name = "05 Batch detail Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Batch detail Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "05 Batch detail RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun BatchDetailPreviews() = PreviewScreen(UiScreen.BATCH_DETAIL)

@Preview(name = "06 Plan Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "06 Plan Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "06 Plan RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun PlanPreviews() = PreviewScreen(UiScreen.PLAN)

@Preview(name = "07 Catalog Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "07 Catalog Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "07 Catalog RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun CatalogPreviews() = PreviewScreen(UiScreen.CATALOG, showAd = true)

@Preview(name = "07 Catalog Loaded Ad Inspection", widthDp = 393, heightDp = 1800)
@Composable private fun CatalogLoadedAdInspectionPreview() = PreviewScreen(UiScreen.CATALOG, showAd = true)

@Preview(name = "07 Catalog Hidden Ad Offline Inspection", widthDp = 393, heightDp = 1800)
@Composable private fun CatalogHiddenAdOfflineInspectionPreview() = PreviewScreen(UiScreen.CATALOG, showAd = false)

@Preview(name = "08 Recipe Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "08 Recipe Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "08 Recipe RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun RecipePreviews() = PreviewScreen(UiScreen.RECIPE)

@Preview(name = "08 Recipe Asset Inspection", widthDp = 393, heightDp = 1800)
@Composable private fun RecipeAssetInspectionPreview() = PreviewScreen(UiScreen.RECIPE)

@Preview(name = "09 Observation Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "09 Observation Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "09 Observation RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun ObservationPreviews() = PreviewScreen(UiScreen.OBSERVATION_EDITOR)

@Preview(name = "10 Close batch Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "10 Close batch Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "10 Close batch RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun CloseBatchPreviews() = PreviewScreen(UiScreen.CLOSE_BATCH)

@Preview(name = "11 Settings Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "11 Settings Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "11 Settings RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun SettingsPreviews() = PreviewScreen(UiScreen.SETTINGS)

@Preview(name = "11 Settings Privacy Management", widthDp = 393, heightDp = 851)
@Composable private fun SettingsPrivacyManagementPreview() = App(
    state = typedPreviewState(UiScreen.SETTINGS, scenario = ProductionScreenFixtures.Scenario.PRIVACY_AVAILABLE),
)

@Preview(name = "12 Zones Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "12 Zones Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "12 Zones RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun ZonesPreviews() = PreviewScreen(UiScreen.ZONES)

@Preview(name = "13 Tray sizes Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "13 Tray sizes Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "13 Tray sizes RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun TraySizesPreviews() = PreviewScreen(UiScreen.TRAY_SIZES)

@Preview(name = "14 Backup Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "14 Backup Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "14 Backup RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun BackupPreviews() = PreviewScreen(UiScreen.BACKUP_RESTORE, UiVariant.RESTORE_PREVIEW)

@Preview(name = "15 Privacy Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "15 Privacy Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "15 Privacy RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun PrivacyPreviews() = PreviewScreen(UiScreen.ADVERTISING_PRIVACY, UiVariant.PRIVACY_DECISION)

@Preview(name = "15 Privacy Accepted Withdrawal", widthDp = 393, heightDp = 851)
@Composable private fun PrivacyAcceptedPreview() = App(
    state = typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
        .withAdvertisingPrivacy(previewPrivacy(ConsentChoice.ACCEPTED), PREVIEW_PRIVACY_NOW),
)

@Preview(name = "16 Notifications Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "16 Notifications Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "16 Notifications RU 200%", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f, uiMode = 16)
@Composable private fun NotificationsPreviews() = PreviewScreen(UiScreen.NOTIFICATIONS)

@Preview(name = "Expanded today", widthDp = 1100, heightDp = 800)
@Composable private fun ExpandedTodayPreview() = PreviewScreen(UiScreen.TODAY)

@Preview(name = "Expanded list detail", widthDp = 1100, heightDp = 800)
@Composable private fun ExpandedTraysPreview() = PreviewScreen(UiScreen.TRAYS)

@Preview(name = "Expanded batch detail", widthDp = 1100, heightDp = 800)
@Composable private fun ExpandedBatchDetailPreview() = PreviewScreen(UiScreen.BATCH_DETAIL)

@Preview(name = "Expanded plan", widthDp = 1100, heightDp = 800)
@Composable private fun ExpandedPlanPreview() = PreviewScreen(UiScreen.PLAN)

@Preview(name = "Expanded catalog", widthDp = 1100, heightDp = 800)
@Composable private fun ExpandedCatalogPreview() = PreviewScreen(UiScreen.CATALOG, showAd = true)

@Preview(name = "Empty state Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Empty state Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyTodayPreview() = PreviewScreen(UiScreen.TODAY, UiVariant.EMPTY)

@Preview(name = "03 Trays Empty Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "03 Trays Empty Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyTraysPreview() = PreviewScreen(UiScreen.TRAYS, UiVariant.EMPTY)

@Preview(name = "06 Plan Empty Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "06 Plan Empty Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyPlanPreview() = PreviewScreen(UiScreen.PLAN, UiVariant.EMPTY)

@Preview(name = "Permission denied Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Permission denied Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun PermissionDeniedPreview() = PreviewScreen(UiScreen.NOTIFICATIONS, UiVariant.PERMISSION_DENIED)

@Preview(name = "Capacity conflict Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Capacity conflict Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun CapacityConflictPreview() = PreviewScreen(UiScreen.PLAN, UiVariant.CONFLICT)

@Preview(name = "Destructive confirmation Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Destructive confirmation Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun DestructiveConfirmationPreview() = ProductionConfirmationPreview(DestructiveOperation.DELETE_ALL_DATA)

@Preview(name = "Local error Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Local error Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun LocalErrorPreview() = PreviewScreen(UiScreen.BACKUP_RESTORE, UiVariant.ERROR)

@Preview(name = "Validation error Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Validation error Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun ValidationErrorPreview() = PreviewScreen(UiScreen.FIRST_RUN, UiVariant.VALIDATION_ERROR)

@Preview(name = "Temporarily unassigned Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Temporarily unassigned Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun TemporarilyUnassignedPreview() = PreviewScreen(UiScreen.BATCH_DETAIL, UiVariant.TEMPORARILY_UNASSIGNED)
