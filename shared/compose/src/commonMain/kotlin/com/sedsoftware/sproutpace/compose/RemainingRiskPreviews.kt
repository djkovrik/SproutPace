package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures.Scenario

@Preview(name = "03 Empty Active Light", widthDp = 393, heightDp = 851)
@Preview(name = "03 Empty Active Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyActive() = App(typedPreviewState(UiScreen.TRAYS, UiVariant.EMPTY,
    draft = mapOf("category" to "active")))

@Preview(name = "03 Empty History Light", widthDp = 393, heightDp = 851)
@Preview(name = "03 Empty History Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyHistory() = App(typedPreviewState(UiScreen.TRAYS, UiVariant.EMPTY,
    draft = mapOf("category" to "history")))

@Preview(name = "12 Empty Zones Light", widthDp = 393, heightDp = 851)
@Preview(name = "12 Empty Zones Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyZones() = App(typedPreviewState(UiScreen.ZONES, UiVariant.EMPTY))

@Preview(name = "13 Empty Trays Light", widthDp = 393, heightDp = 851)
@Preview(name = "13 Empty Trays Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun EmptyTrays() = App(typedPreviewState(UiScreen.TRAY_SIZES, UiVariant.EMPTY))

@Preview(name = "07 Search Empty Light", widthDp = 393, heightDp = 851)
@Preview(name = "07 Search Empty Dark RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun SearchEmpty() = App(typedPreviewState(UiScreen.CATALOG, draft = mapOf("query" to "zzzz")))

@Preview(name = "07 Scientific Search", widthDp = 393, heightDp = 851)
@Composable private fun ScientificSearch() = App(typedPreviewState(UiScreen.CATALOG, draft = mapOf("query" to "Raphanus")))

@Preview(name = "02 Long Names RU 200", widthDp = 393, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun LongToday() = App(typedPreviewState(UiScreen.TODAY, scenario = Scenario.LONG_NAMES))

@Preview(name = "03 Long Names RU 200", widthDp = 393, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun LongTrays() = App(typedPreviewState(UiScreen.TRAYS, scenario = Scenario.LONG_NAMES))

@Preview(name = "09 Actual Photo Expanded", widthDp = 1100, heightDp = 1000)
@Preview(name = "09 Actual Photo Dark", widthDp = 393, heightDp = 1100, uiMode = 32)
@Composable private fun ObservationPhoto() = App(typedPreviewState(UiScreen.OBSERVATION_EDITOR,
    scenario = Scenario.PHOTO, selectedId = "growth"))

@Preview(name = "09 Long Note RU 200", widthDp = 700, heightDp = 1200, locale = "ru", fontScale = 2f)
@Composable private fun ObservationLongNote() = App(typedPreviewState(UiScreen.OBSERVATION_EDITOR,
    scenario = Scenario.LONG_NAMES, selectedId = "growth"))

@Preview(name = "10 Expanded Outcome", widthDp = 1100, heightDp = 1100)
@Composable private fun ExpandedOutcome() = App(typedPreviewState(UiScreen.CLOSE_BATCH))

@Preview(name = "10 Invalid Date RU 200", widthDp = 700, heightDp = 1200, locale = "ru", fontScale = 2f)
@Composable private fun InvalidOutcomeDate() = App(typedPreviewState(UiScreen.CLOSE_BATCH, UiVariant.VALIDATION_ERROR,
    draft = mapOf("occurredAt" to "invalid", "actualMassGrams" to "120")))

@Preview(name = "09 Delete Observation Dialog RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun DeleteObservationDialog() = ProductionConfirmationPreview(DestructiveOperation.DELETE_OBSERVATION)

@Preview(name = "12 Referenced Zone Delete Dialog", widthDp = 393, heightDp = 851)
@Composable private fun DeleteZoneDialog() = ProductionConfirmationPreview(DestructiveOperation.DELETE_ZONE)

@Preview(name = "13 Referenced Tray Delete Dialog", widthDp = 393, heightDp = 851)
@Composable private fun DeleteTrayDialog() = ProductionConfirmationPreview(DestructiveOperation.DELETE_TRAY_PRESET)

@Preview(name = "15 Stale Privacy Light", widthDp = 393, heightDp = 851)
@Preview(name = "15 Stale Privacy Dark RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun StalePrivacy() = App(typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
    .withAdvertisingPrivacy(previewPrivacy(ConsentChoice.ACCEPTED), PREVIEW_PRIVACY_NOW + PRIVACY_PREVIEW_EXPIRY))

@Preview(name = "15 Endpoint Failure Light", widthDp = 393, heightDp = 851)
@Preview(name = "15 Endpoint Failure Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun FailedPrivacy() = App(typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
    .withAdvertisingPrivacy(null, PREVIEW_PRIVACY_NOW))

@Preview(name = "15 Changed Policy Expanded", widthDp = 1100, heightDp = 1000)
@Composable private fun ChangedPrivacyPolicy() = App(typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
    .withAdvertisingPrivacy(previewPrivacy(ConsentChoice.UNDECIDED).copy(policyVersion = "2026-11"), PREVIEW_PRIVACY_NOW))

@Preview(name = "15 Declined Privacy Light", widthDp = 393, heightDp = 851)
@Preview(name = "15 Declined Privacy Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun DeclinedPrivacy() = App(typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
    .withAdvertisingPrivacy(previewPrivacy(ConsentChoice.DECLINED), PREVIEW_PRIVACY_NOW))

@Preview(name = "16 Schedule Failure Light", widthDp = 393, heightDp = 1000)
@Preview(name = "16 Schedule Failure Dark RU 200", widthDp = 700, heightDp = 1200, locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun ScheduleFailure() = App(typedPreviewState(UiScreen.NOTIFICATIONS,
    platformProjection = { it.withReminderScheduling(ReminderSchedulingState.FAILED) }))

@Preview(name = "11 Crash Reporting Unavailable Light", widthDp = 393, heightDp = 1100)
@Preview(name = "11 Crash Reporting Unavailable RU 200", widthDp = 700, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun CrashUnavailable() = App(typedPreviewState(UiScreen.SETTINGS,
    platformProjection = { it.withCrashReporting(com.sedsoftware.sproutpace.platform.CrashReportingState.Unavailable(true)) }))

@Preview(name = "05 Ready Fixed Date Light", widthDp = 393, heightDp = 1100)
@Preview(name = "05 Ready Fixed Date Dark", widthDp = 393, heightDp = 1100, uiMode = 32)
@Composable private fun FixedReadyDate() = App(typedPreviewState(UiScreen.BATCH_DETAIL, scenario = Scenario.FIXED_READY))

@Preview(name = "08 Outdated Source Review RU 200", widthDp = 700, heightDp = 1800, locale = "ru", fontScale = 2f)
@Composable private fun OutdatedRecipe() = App(typedPreviewState(UiScreen.RECIPE, scenario = Scenario.OUTDATED_RECIPE))

@Preview(name = "07 Long Personal Recipe RU 200", widthDp = 700, heightDp = 1200, locale = "ru", fontScale = 2f)
@Composable private fun LongPersonalRecipe() = App(typedPreviewState(UiScreen.CATALOG, scenario = Scenario.LONG_NAMES))

@Preview(name = "13 Long Tray Name RU 200", widthDp = 393, heightDp = 1200, locale = "ru", fontScale = 2f)
@Composable private fun LongTrayPreset() = App(typedPreviewState(UiScreen.TRAY_SIZES, scenario = Scenario.LONG_NAMES))

@Preview(name = "02 Capacity Conflict RU 200", widthDp = 393, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun TodayCapacityConflict() = App(typedPreviewState(UiScreen.TODAY, UiVariant.CONFLICT))

@Preview(name = "12 Future Conflict Expanded", widthDp = 1100, heightDp = 1100)
@Composable private fun FutureZoneConflict() = App(typedPreviewState(UiScreen.ZONES, scenario = Scenario.FUTURE_ZONE_CONFLICT))

@Preview(name = "01 Invalid Tray Dimensions RU 200", widthDp = 393, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun InvalidSetupDimensions() = App(typedPreviewState(UiScreen.FIRST_RUN, UiVariant.VALIDATION_ERROR,
    draft = mapOf("zoneName" to "Window shelf", "trayName" to "Shallow tray",
        "capacity" to "8", "widthCm" to "0", "lengthCm" to "0")))

@Preview(name = "01 Setup Skipped Today Light", widthDp = 393, heightDp = 851)
@Preview(name = "01 Setup Skipped Today Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun SkippedSetup() = App(typedPreviewState(UiScreen.TODAY, UiVariant.EMPTY))

@Preview(name = "01 Expanded Completion", widthDp = 1100, heightDp = 1100)
@Composable private fun SetupExpandedCompletion() = WizardStepPreview(UiScreen.FIRST_RUN, 2)

@Preview(name = "04 Expanded Summary", widthDp = 1100, heightDp = 1100)
@Composable private fun BatchExpandedSummary() = WizardStepPreview(UiScreen.BATCH_EDITOR, 2)

@Preview(name = "04 Repeat Production Inputs Light", widthDp = 393, heightDp = 1100)
@Preview(name = "04 Repeat Production Inputs Dark", widthDp = 393, heightDp = 1100, uiMode = 32)
@Composable private fun RepeatBatchInputs() = App(typedPreviewState(UiScreen.BATCH_EDITOR, scenario = Scenario.REPEATED_BATCH))

@Preview(name = "04 Invalid Date RU 200", widthDp = 700, heightDp = 1300, locale = "ru", fontScale = 2f)
@Composable private fun InvalidBatchDate() = App(typedPreviewState(UiScreen.BATCH_EDITOR, UiVariant.VALIDATION_ERROR,
    draft = mapOf("actualSeedMassGrams" to "20", "plannedSowDate" to "invalid")))

@Preview(name = "04 Capacity Conflict Summary", widthDp = 700, heightDp = 1100)
@Composable private fun BatchConflictSummary() = WizardStepPreview(UiScreen.BATCH_EDITOR, 2,
    typedPreviewState(UiScreen.BATCH_EDITOR, scenario = Scenario.NEEDS_RESOLUTION))

@Preview(name = "04 Long Recipe Name", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun LongRecipeBatch() = App(typedPreviewState(UiScreen.BATCH_EDITOR,
    scenario = Scenario.LONG_NAMES, selectedId = "house-radish"))

@Preview(name = "07 Personal Recipe Missing Optional Image", widthDp = 393, heightDp = 1200)
@Composable private fun NoImageCatalog() = App(typedPreviewState(UiScreen.CATALOG, scenario = Scenario.NO_IMAGE_RECIPE))

@Preview(name = "08 Source Open Failure Expanded", widthDp = 1100, heightDp = 1200)
@Composable private fun SourceOpenFailure() {
    com.sedsoftware.sproutpace.compose.theme.AppTheme(onThemeChanged = {}) {
        Surface(Modifier.fillMaxSize()) {
            RecipeScreen(typedPreviewState(UiScreen.RECIPE), UiLayout.EXPANDED, {}, initialSourceError = true)
        }
    }
}

@Preview(name = "09 Save Failure Retains Note", widthDp = 393, heightDp = 1200)
@Composable private fun ObservationSaveFailure() = App(typedPreviewState(UiScreen.OBSERVATION_EDITOR,
    scenario = Scenario.EDIT_OBSERVATION, errorKeyOverride = "error_operation_failed"))

@Preview(name = "10 Save Failure Retains Outcome", widthDp = 393, heightDp = 1200)
@Composable private fun OutcomeSaveFailure() = App(typedPreviewState(UiScreen.CLOSE_BATCH,
    errorKeyOverride = "error_operation_failed", draft = mapOf("actualMassGrams" to "184")))

@Preview(name = "10 Long Reason RU 200", widthDp = 700, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun LongOutcomeReason() = App(typedPreviewState(UiScreen.CLOSE_BATCH,
    draft = mapOf("outcome" to "failed", "reason" to
        "Всходы появились неравномерно после изменения условий выращивания. Лоток закрыт, чтобы начать новую партию.")))

@Preview(name = "09 Photo Import Failure Light", widthDp = 393, heightDp = 1200)
@Preview(name = "09 Photo Import Failure Dark RU 200", widthDp = 700, heightDp = 1400, locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun PhotoImportFailure() = App(typedPreviewState(UiScreen.OBSERVATION_EDITOR,
    scenario = Scenario.PHOTO, selectedId = "growth", errorKeyOverride = "error_photo_import_failed"))

@Preview(name = "06 Fixed Harvest Date Light", widthDp = 393, heightDp = 1200)
@Preview(name = "06 Fixed Harvest Date Dark", widthDp = 700, heightDp = 1200, uiMode = 32)
@Composable private fun PlanFixedDate() = App(typedPreviewState(UiScreen.PLAN, scenario = Scenario.FIXED_READY))

@Preview(name = "08 Longest Bundled Source URL RU 200", widthDp = 1100, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun LongestSourceUrl() = App(typedPreviewState(UiScreen.RECIPE))

@Preview(name = "02 Permission Denied Work Remains Light", widthDp = 393, heightDp = 1200)
@Preview(name = "02 Permission Denied Work Remains Dark", widthDp = 393, heightDp = 1200, uiMode = 32)
@Composable private fun TodayPermissionDenied() = App(typedPreviewState(UiScreen.TODAY, UiVariant.PERMISSION_DENIED,
    permissionOverride = com.sedsoftware.sproutpace.component.PermissionState.DENIED,
    platformProjection = { it.withNotificationPermissionContext(true, com.sedsoftware.sproutpace.component.PermissionState.DENIED) }))

@Preview(name = "06 Longest Culture Names RU 200 Compact", widthDp = 393, heightDp = 1400, locale = "ru", fontScale = 2f)
@Preview(name = "06 Longest Culture Names RU 200 Expanded", widthDp = 1100, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun PlanLongCultureNames() = App(typedPreviewState(UiScreen.PLAN, scenario = Scenario.LONG_CULTURE_NAMES))

@Preview(name = "04 Needs Resolution Suggestion Light", widthDp = 393, heightDp = 1300)
@Preview(name = "04 Needs Resolution Suggestion Dark RU 200", widthDp = 700, heightDp = 1400, locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun NeedsResolutionSuggestion() = WizardStepPreview(UiScreen.BATCH_EDITOR, 2,
    typedPreviewState(UiScreen.BATCH_EDITOR, scenario = Scenario.NEEDS_RESOLUTION))

@Preview(name = "01 Permission Denied Optional Step Light", widthDp = 393, heightDp = 1400)
@Preview(name = "01 Permission Denied Optional Step Dark RU 200", widthDp = 700, heightDp = 1600,
    locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun SetupPermissionDenied() = WizardStepPreview(UiScreen.FIRST_RUN, 2,
    permissionDeniedPreview(UiScreen.FIRST_RUN))

@Preview(name = "11 Permission Denied Settings Light", widthDp = 393, heightDp = 1100)
@Preview(name = "11 Permission Denied Settings Dark RU 200", widthDp = 700, heightDp = 1600,
    locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun SettingsPermissionDenied() = App(permissionDeniedPreview(UiScreen.SETTINGS))

@Preview(name = "11 Settings Notification Detail Expanded", widthDp = 1100, heightDp = 1100)
@Preview(name = "11 Settings Notification Detail Expanded Dark RU 200", widthDp = 1100, heightDp = 1400,
    locale = "ru", fontScale = 2f, uiMode = 32)
@Composable private fun SettingsNotificationDetail() = App(permissionDeniedPreview(UiScreen.NOTIFICATIONS).copy(
    settingsContext = permissionDeniedPreview(UiScreen.SETTINGS),
))

@Preview(name = "11 Settings Backup Detail Expanded", widthDp = 1100, heightDp = 1100)
@Composable private fun SettingsBackupDetail() = App(typedPreviewState(UiScreen.BACKUP_RESTORE, UiVariant.RESTORE_PREVIEW).copy(
    settingsContext = typedPreviewState(UiScreen.SETTINGS, scenario = Scenario.PRIVACY_AVAILABLE),
))

private fun permissionDeniedPreview(screen: UiScreen) = typedPreviewState(screen, UiVariant.PERMISSION_DENIED,
    permissionOverride = com.sedsoftware.sproutpace.component.PermissionState.DENIED,
    platformProjection = { it.withNotificationPermissionContext(true, com.sedsoftware.sproutpace.component.PermissionState.DENIED) })

private const val PRIVACY_PREVIEW_EXPIRY = 86_400_001L
