package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenFieldKind
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
): SproutPaceUiState {
    val component = previewComponent(screen)
    val base = component.model.value.toUiState()
    val state = base.copy(
        variant = variant,
        selectedPrimary = screen.takeIf { it in setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN, UiScreen.CATALOG) }
            ?: UiScreen.TODAY,
        showInlineAd = showAd,
        items = if (variant == UiVariant.EMPTY) emptyList() else realisticPreviewItems(screen),
        actions = if (variant == UiVariant.EMPTY) listOfNotNull(emptyPreviewPrimaryAction(screen)) else base.actions,
        fields = base.fields.map { field ->
            field.copy(
                value = if (screen == UiScreen.BATCH_EDITOR && field.id in batchEditorCorrectionPreviewFieldIds) {
                    ""
                } else {
                    realisticPreviewFieldValue(field.id)
                },
                options = field.options.ifEmpty { realisticPreviewOptions(field.id, field.kind) },
                errorKey = if (variant == UiVariant.VALIDATION_ERROR && field.required) "state_error_body" else null,
            )
        },
        facts = base.facts.map { fact -> fact.copy(valueKey = realisticPreviewFactKey(screen), authoredValue = null) },
        conflictKeys = if (variant == UiVariant.CONFLICT || variant == UiVariant.TEMPORARILY_UNASSIGNED) {
            listOf("capacity_conflict_body")
        } else {
            component.model.value.conflictResolutionKeys.map { it.value }
        },
    )
    return if (screen == UiScreen.ADVERTISING_PRIVACY) {
        state.withAdvertisingPrivacy(previewPrivacy(ConsentChoice.UNDECIDED))
    } else {
        state
    }
}

private fun previewComponent(screen: UiScreen): ScreenComponent = when (screen) {
    UiScreen.FIRST_RUN -> FirstRunComponentPreview()
    UiScreen.TODAY -> TodayComponentPreview()
    UiScreen.TRAYS -> TraysComponentPreview()
    UiScreen.BATCH_EDITOR -> BatchEditorComponentPreview()
    UiScreen.BATCH_DETAIL -> BatchDetailComponentPreview()
    UiScreen.PLAN -> PlanComponentPreview()
    UiScreen.CATALOG -> CatalogComponentPreview()
    UiScreen.RECIPE -> RecipeComponentPreview()
    UiScreen.OBSERVATION_EDITOR -> ObservationEditorComponentPreview()
    UiScreen.CLOSE_BATCH -> CloseBatchComponentPreview()
    UiScreen.SETTINGS -> SettingsComponentPreview()
    UiScreen.ZONES -> ZonesComponentPreview()
    UiScreen.TRAY_SIZES -> TraySizesComponentPreview()
    UiScreen.BACKUP_RESTORE -> BackupRestoreComponentPreview()
    UiScreen.ADVERTISING_PRIVACY -> AdvertisingPrivacyComponentPreview()
    UiScreen.NOTIFICATIONS -> NotificationsComponentPreview()
}

private val batchEditorCorrectionPreviewFieldIds = setOf(
    "anchorState", "anchorAt", "actualSoakingAt", "actualSownAt", "actualBlackoutAt",
    "actualLightAt", "actualHarvestReadyAt",
)

private fun emptyPreviewPrimaryAction(screen: UiScreen): UiScreenAction? = when (screen) {
    UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN ->
        UiScreenAction("new-batch", "action_new_batch", null, enabled = true, primary = true)
    UiScreen.CATALOG -> UiScreenAction("create-recipe", "action_create_recipe", null, enabled = true, primary = true)
    UiScreen.ZONES -> UiScreenAction("save-zone", "action_add_zone", "save-zone", enabled = true, primary = true)
    UiScreen.TRAY_SIZES ->
        UiScreenAction("save-tray-size", "action_add_preset", "save-tray-size", enabled = true, primary = true)
    else -> null
}

private fun previewPrivacy(choice: ConsentChoice) = AdvertisingPrivacyState(
    policyVersion = "2026-09",
    expiresAtEpochMillis = 1_893_456_000_000,
    choice = choice,
    fetchedAtEpochMillis = 1_756_800_000_000,
)

private fun historyAdPreviewState() = typedPreviewState(UiScreen.TRAYS).copy(
    trayCategory = TrayCategory.HISTORY,
    showInlineAd = true,
    items = (1..6).map { index ->
        previewItem(
            id = "history-$index",
            authored = "Harvested batch $index",
            supporting = "stage_harvest",
            state = "option_batch_state_harvested",
        )
    },
)

private fun realisticPreviewItems(screen: UiScreen): List<UiItem> = when (screen) {
    UiScreen.FIRST_RUN -> listOf(previewItem("setup", authored = "Window shelf", supporting = "first_run_preview_body"))
    UiScreen.TODAY -> listOf(
        previewItem("batch:radish-01", "culture_radish_name", "today_due", "action_status_pending", "complete-action"),
        previewItem("batch:pea-02", "culture_pea_name", "today_moisture_overdue", "action_status_pending", "snooze"),
    )
    UiScreen.TRAYS -> listOf(
        previewItem("batch:radish-01", authored = "Radish · Shallow tray", supporting = "stage_light_growth"),
        previewItem("batch:pea-02", authored = "Pea · Deep tray", supporting = "stage_soaking"),
        previewItem("batch:broccoli-03", authored = "Broccoli · Shallow tray", supporting = "stage_blackout"),
        previewItem("batch:sunflower-04", authored = "Sunflower · Deep tray", supporting = "stage_sown"),
        previewItem("batch:cress-05", authored = "Garden cress · Round bowl", supporting = "stage_harvest"),
    )
    UiScreen.BATCH_EDITOR -> listOf(previewItem("recipe:radish", "culture_radish_name", "batch_editor_help"))
    UiScreen.BATCH_DETAIL -> listOf(
        previewItem("event:sown", "stage_sown", "stage_actual"),
        previewItem("event:light", "stage_light_growth", "stage_expected"),
        previewItem(
            "observation:preview-1",
            authored = "Healthy roots and even growth",
            supporting = "observation_photo_selected",
            photo = UiPhoto("photo-preview", null, "observation_photo_content_description"),
        ),
        previewItem(
            "observation:missing-photo",
            authored = "Observation text remains available",
            state = "observation_photo_unavailable",
        ),
    )
    UiScreen.PLAN -> listOf(
        previewItem("plan:radish", "plan_confirmed_radish", state = "plan_active"),
        previewItem("suggestion:broccoli", "plan_suggested_broccoli", action = "confirm-suggestion"),
    )
    UiScreen.CATALOG -> listOf(
        "radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress",
    ).map { id -> previewItem("culture:$id", "culture_${id}_name", "catalog_typical_window", action = "open-recipe") }
    UiScreen.RECIPE -> listOf(
        previewItem("stage:soaking", "stage_soaking", "guidance_soaking"),
        previewItem("stage:sowing", "stage_sown", "guidance_sowing"),
        previewItem("stage:blackout", "stage_blackout", "guidance_blackout"),
        previewItem("stage:light", "stage_light_growth", "guidance_light_growth"),
        previewItem("stage:harvest", "stage_harvest", "guidance_harvest"),
    )
    UiScreen.OBSERVATION_EDITOR -> listOf(
        previewItem("batch:radish-01", authored = "Radish · Shallow tray", supporting = "observation_photo_help"),
    )
    UiScreen.CLOSE_BATCH -> listOf(previewItem("batch:radish-01", "culture_radish_name", "close_batch_help", "stage_harvest"))
    UiScreen.SETTINGS -> listOf(
        previewItem("zones", "screen_zones_title", "settings_zones_summary", action = "open-zones"),
        previewItem("notifications", "screen_notifications_title", "settings_notifications_summary", action = "open-notifications"),
        previewItem("backup", "screen_backup_restore_title", "settings_backup_summary", action = "open-backup-restore"),
    )
    UiScreen.ZONES -> listOf(
        previewItem("zone:window", authored = "Window shelf", supporting = "zone_light_capacity"),
        previewItem("zone:cupboard", authored = "Kitchen cupboard", supporting = "zone_dark_capacity"),
    )
    UiScreen.TRAY_SIZES -> listOf(
        previewItem("tray:shallow", authored = "Shallow rectangle · 25 × 35 cm", supporting = "tray_preset_rectangular"),
        previewItem("tray:round", authored = "Round bowl · 20 cm", supporting = "tray_preset_circular"),
    )
    UiScreen.BACKUP_RESTORE -> listOf(
        previewItem("create", "action_create_backup", "backup_create_body", action = "create-backup"),
        previewItem("restore", "action_preview_restore", "backup_restore_body", action = "preview-restore"),
        previewItem("csv", "action_export_csv", "backup_csv_body", action = "export-csv"),
    )
    UiScreen.ADVERTISING_PRIVACY -> listOf(previewItem("privacy", "privacy_heading", "privacy_context_body", "privacy_undecided"))
    UiScreen.NOTIFICATIONS -> listOf(
        previewItem("daily", "notifications_daily_summary", "notifications_daily_summary_body", "state_enabled"),
        previewItem("soak", "notifications_soak_reminder", "notifications_soak_body", "state_enabled"),
    )
}

private fun previewItem(
    id: String,
    title: String? = null,
    supporting: String? = null,
    state: String? = null,
    action: String? = null,
    authored: String? = null,
    photo: UiPhoto? = null,
) = UiItem(id, title, authored, supporting, state, action, selected = false, photo = photo)

private fun realisticPreviewFieldValue(id: String): String =
    REALISTIC_PREVIEW_FIELD_VALUES[id] ?: error("Missing realistic preview value for field '$id'")

private val REALISTIC_PREVIEW_FIELD_VALUES = mapOf(
    "id" to "radish-2026-09-20",
    "zoneName" to "Window shelf",
    "name" to "Window shelf",
    "trayName" to "Shallow tray",
    "type" to "natural_light",
    "capacity" to "4",
    "shape" to "rectangle",
    "widthCm" to "25",
    "lengthCm" to "35",
    "diameterCm" to "20",
    "cadenceDays" to "7",
    "cultureIds" to "radish,broccoli",
    "mode" to "cadence",
    "dueKind" to "elapsed",
    "dueAt" to "2026-09-20T09:00:00Z",
    "anchorAt" to "2026-09-20T09:00:00Z",
    "actualSoakingAt" to "2026-09-20T09:00:00Z",
    "actualSownAt" to "2026-09-21T09:00:00Z",
    "actualBlackoutAt" to "2026-09-22T09:00:00Z",
    "actualLightAt" to "2026-09-24T09:00:00Z",
    "actualHarvestReadyAt" to "2026-09-28T09:00:00Z",
    "occurredAt" to "2026-09-20T09:00:00Z",
    "localDateTime" to "2026-09-20T09:00",
    "timeZoneId" to "Europe/Moscow",
    "newId" to "radish-repeat-02",
    "recipeId" to "recipe-radish",
    "cultureId" to "radish",
    "trayId" to "tray-shallow",
    "substrate" to "Coconut coir",
    "actualSeedMassGrams" to "18",
    "plannedSowDate" to "2026-09-21",
    "fixedSoakingDate" to "2026-09-21",
    "fixedSownDate" to "2026-09-22",
    "fixedBlackoutDate" to "2026-09-23",
    "fixedLightDate" to "2026-09-25",
    "fixedHarvestDate" to "2026-09-29",
    "zoneId" to "zone-window",
    "anchorDate" to "2026-09-20",
    "anchorState" to "light_growth",
    "nextState" to "light_growth",
    "resultingState" to "light_growth",
    "skippedState" to "blackout_or_weight",
    "eventId" to "event-light-01",
    "unassigned" to "true",
    "enabled" to "true",
    "dailySummaryEnabled" to "true",
    "soakRemindersEnabled" to "true",
    "summaryTime" to "09:00",
    "reason" to "Adjusted after observation",
    "actionId" to "check-moisture",
    "actualMassGrams" to "184",
    "query" to "",
    "supplier" to "Local seed shop",
    "cultivar" to "Sango",
    "seedLot" to "2026-08",
    "germinationPercentage" to "92",
    "batchId" to "batch-radish-01",
    "text" to "Even growth and fresh leaves",
    "photoId" to "radish-observation.jpg",
    "outcome" to "harvested",
    "assessment" to "successful",
    "note" to "Crisp harvest",
    "theme" to "system",
    "paused" to "false",
    "documentId" to "sproutpace-backup-2026-09-20.zip",
    "choice" to "declined",
    "expiresAtEpochMillis" to "1893456000000",
    "policyVersion" to "2026-09",
    "stage1" to "soaking",
    "stage1MinimumHours" to "8",
    "stage1MaximumHours" to "12",
    "stage1Guidance" to "Rinse and soak",
    "stage2" to "sown",
    "stage2MinimumHours" to "0",
    "stage2MaximumHours" to "0",
    "stage2Guidance" to "Spread seed evenly",
    "stage3" to "blackout_or_weight",
    "stage3MinimumHours" to "48",
    "stage3MaximumHours" to "72",
    "stage3Guidance" to "Keep covered and weighted",
    "stage4" to "light_growth",
    "stage4MinimumHours" to "72",
    "stage4MaximumHours" to "120",
    "stage4Guidance" to "Keep under light",
    "stage5" to "ready_for_harvest",
    "stage5MinimumHours" to "0",
    "stage5MaximumHours" to "0",
    "stage5Guidance" to "Harvest above the medium",
)

private fun realisticPreviewOptions(id: String, kind: ScreenFieldKind): List<UiOption> {
    if (kind != ScreenFieldKind.SINGLE_CHOICE && kind != ScreenFieldKind.MULTI_CHOICE) return emptyList()
    return when (id) {
        "cultureId", "cultureIds" -> listOf(
            UiOption("radish", "culture_radish_name", null),
            UiOption("broccoli", "culture_broccoli_name", null),
        )
        "recipeId" -> listOf(UiOption("recipe-radish", null, "Radish house recipe"))
        "trayId" -> listOf(UiOption("tray-shallow", null, "Shallow tray · 25 × 35 cm"))
        "zoneId" -> listOf(UiOption("zone-window", null, "Window shelf"))
        "batchId" -> listOf(UiOption("batch-radish-01", null, "Radish · Shallow tray"))
        else -> emptyList()
    }
}

private fun realisticPreviewFactKey(screen: UiScreen): String = when (screen) {
    UiScreen.FIRST_RUN -> "first_run_preview_body"
    UiScreen.TODAY -> "today_due"
    UiScreen.TRAYS -> "trays_next_action"
    UiScreen.BATCH_EDITOR -> "batch_editor_help"
    UiScreen.BATCH_DETAIL -> "batch_next_stage_body"
    UiScreen.PLAN -> "plan_frequency_value"
    UiScreen.CATALOG -> "catalog_source_body"
    UiScreen.RECIPE -> "recipe_summary"
    UiScreen.OBSERVATION_EDITOR -> "observation_photo_help"
    UiScreen.CLOSE_BATCH -> "close_batch_help"
    UiScreen.SETTINGS -> "settings_backup_summary"
    UiScreen.ZONES -> "settings_zones_summary"
    UiScreen.TRAY_SIZES -> "tray_preset_rectangular"
    UiScreen.BACKUP_RESTORE -> "restore_archive_contents_value"
    UiScreen.ADVERTISING_PRIVACY -> "privacy_context_body"
    UiScreen.NOTIFICATIONS -> "notifications_default_time"
}

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
    state = typedPreviewState(UiScreen.SETTINGS).let { state ->
        state.copy(
            items = state.items + previewItem(
                "privacy",
                "screen_advertising_privacy_title",
                action = "open-advertising-privacy",
            ),
        )
    },
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
        .withAdvertisingPrivacy(previewPrivacy(ConsentChoice.ACCEPTED)),
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
@Composable private fun DestructiveConfirmationPreview() = PreviewScreen(UiScreen.SETTINGS, UiVariant.DESTRUCTIVE_CONFIRMATION)

@Preview(name = "Local error Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Local error Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun LocalErrorPreview() = PreviewScreen(UiScreen.BACKUP_RESTORE, UiVariant.ERROR)

@Preview(name = "Validation error Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Validation error Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun ValidationErrorPreview() = PreviewScreen(UiScreen.FIRST_RUN, UiVariant.VALIDATION_ERROR)

@Preview(name = "Temporarily unassigned Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "Temporarily unassigned Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun TemporarilyUnassignedPreview() = PreviewScreen(UiScreen.BATCH_DETAIL, UiVariant.TEMPORARILY_UNASSIGNED)
