package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures.Scenario
import com.sedsoftware.sproutpace.root.ConfirmationComponent

@Composable
private fun ScenarioScreen(screen: UiScreen, scenario: Scenario, variant: UiVariant = UiVariant.CONTENT,
    draft: Map<String, String> = emptyMap()) = App(typedPreviewState(screen, variant, scenario = scenario, draft = draft))

@Preview(name = "03 Selected Batch Expanded Light", widthDp = 1100, heightDp = 1100)
@Preview(name = "03 Selected Batch Expanded Dark RU 200", widthDp = 1100, heightDp = 1400,
    uiMode = 32, locale = "ru", fontScale = 2f)
@Composable private fun SelectedBatchExpandedScenario() =
    App(typedPreviewState(UiScreen.TRAYS, scenario = Scenario.ACTIVE, selectedId = "active-radish"))

@Preview(name = "07 Selected Recipe Expanded Light", widthDp = 1100, heightDp = 1100)
@Preview(name = "07 Selected Recipe Expanded Dark RU 200", widthDp = 1100, heightDp = 1400,
    uiMode = 32, locale = "ru", fontScale = 2f)
@Composable private fun SelectedRecipeExpandedScenario() =
    App(typedPreviewState(UiScreen.CATALOG, selectedId = "radish"))

@Preview(name = "05 Available Photo Light", widthDp = 393, heightDp = 1600)
@Preview(name = "05 Available Photo Dark", widthDp = 700, heightDp = 1600, uiMode = 32)
@Composable private fun PhotoScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.PHOTO)

@Preview(name = "05 Long Names RU 200", widthDp = 393, heightDp = 1400, locale = "ru", fontScale = 2f)
@Composable private fun LongBatchScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.LONG_NAMES)

@Preview(name = "12 Long Names RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun LongZoneScenario() = ScenarioScreen(UiScreen.ZONES, Scenario.LONG_NAMES)

@Preview(name = "14 Backup Created Light", widthDp = 393, heightDp = 851)
@Preview(name = "14 Backup Created Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun BackupCreatedScenario() = ScenarioScreen(UiScreen.BACKUP_RESTORE, Scenario.BACKUP_CREATED)

@Preview(name = "14 Restore Completed RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun BackupRestoredScenario() = ScenarioScreen(UiScreen.BACKUP_RESTORE, Scenario.BACKUP_RESTORED)

@Preview(name = "14 CSV Completed Expanded", widthDp = 1100, heightDp = 851)
@Composable private fun CsvExportedScenario() = ScenarioScreen(UiScreen.BACKUP_RESTORE, Scenario.CSV_EXPORTED)

@Preview(name = "14 Write Failure EN", widthDp = 393, heightDp = 851)
@Preview(name = "14 Write Failure RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun BackupWriteFailureScenario() =
    ScenarioScreen(UiScreen.BACKUP_RESTORE, Scenario.BACKUP_WRITE_FAILED, UiVariant.ERROR)

@Preview(name = "01 Tray Step EN", widthDp = 393, heightDp = 851)
@Preview(name = "01 Tray Step RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun FirstRunTrayScenario() = WizardStepPreview(UiScreen.FIRST_RUN, 1)

@Preview(name = "01 Plan Date Step Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "01 Plan Date Step RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun FirstRunPlanScenario() = WizardStepPreview(UiScreen.FIRST_RUN, 2)

@Preview(name = "04 Tray Step EN", widthDp = 393, heightDp = 1000)
@Preview(name = "04 Tray Step RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun BatchTrayScenario() = WizardStepPreview(UiScreen.BATCH_EDITOR, 1)

@Preview(name = "04 Date Step Dark", widthDp = 393, heightDp = 1000, uiMode = 32)
@Preview(name = "04 Date Step RU 200", widthDp = 393, heightDp = 1200, locale = "ru", fontScale = 2f)
@Composable private fun BatchDateScenario() = WizardStepPreview(UiScreen.BATCH_EDITOR, BatchEditorStep.DETAILS.ordinal)

/** These previews select a real step while keeping the production form, draft and actions intact. */
@Composable internal fun WizardStepPreview(
    screen: UiScreen,
    step: Int,
    state: SproutPaceUiState = typedPreviewState(screen),
) {
    com.sedsoftware.sproutpace.compose.theme.AppTheme(onThemeChanged = {}) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val layout = if (maxWidth >= 840.dp) UiLayout.EXPANDED else if (maxWidth >= 600.dp) UiLayout.MEDIUM else UiLayout.COMPACT
                if (screen == UiScreen.FIRST_RUN) FirstRunScreen(state, {}, initialStepIndex = step)
                else BatchEditorScreen(state, layout, {}, initialStepIndex = step)
            }
        }
    }
}

@Preview(name = "03 Active Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "03 Active Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun ActiveTraysScenario() = ScenarioScreen(UiScreen.TRAYS, Scenario.ACTIVE)

@Preview(name = "03 History Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Preview(name = "03 History RU 200 Outcomes", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun HistoryScenario() = ScenarioScreen(UiScreen.TRAYS, Scenario.HISTORY)

@Preview(name = "04 Correction Light", widthDp = 393, heightDp = 851)
@Preview(name = "04 Correction RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun CorrectionScenario() = ScenarioScreen(UiScreen.BATCH_EDITOR, Scenario.CORRECTION)

@Preview(name = "04 Invalid Mass EN", widthDp = 393, heightDp = 1000)
@Composable private fun InvalidBatchScenario() = ScenarioScreen(UiScreen.BATCH_EDITOR, Scenario.CONTENT, UiVariant.VALIDATION_ERROR)

@Preview(name = "05 Planned Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Planned Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun PlannedScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_PLANNED)

@Preview(name = "05 Soaking Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Soaking Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun SoakingScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_SOAKING)

@Preview(name = "05 Sown Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Sown Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun SownScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_SOWN)

@Preview(name = "05 Blackout Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Blackout Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun BlackoutScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_BLACKOUT)

@Preview(name = "05 Ready Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Ready Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun ReadyScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_READY)

@Preview(name = "05 Harvested Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Harvested Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun HarvestedScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_HARVESTED)

@Preview(name = "05 Failed Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Failed Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun FailedScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_FAILED)

@Preview(name = "05 Abandoned Light", widthDp = 393, heightDp = 851, uiMode = 16)
@Preview(name = "05 Abandoned Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun AbandonedScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.DETAIL_ABANDONED)

@Preview(name = "05 Skipped Stage RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun SkippedScenario() = ScenarioScreen(UiScreen.BATCH_DETAIL, Scenario.SKIPPED)

@Preview(name = "08 Personal Edit Light", widthDp = 393, heightDp = 1000, uiMode = 16)
@Preview(name = "08 Personal Edit Dark", widthDp = 393, heightDp = 1000, uiMode = 32)
@Preview(name = "08 Personal Edit RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun PersonalRecipeScenario() = RecipeEditorPreview(UiVariant.CONTENT)

@Preview(name = "08 Personal Create Medium", widthDp = 700, heightDp = 1000)
@Composable private fun NewRecipeScenario() = ScenarioScreen(UiScreen.RECIPE, Scenario.NEW_RECIPE)

@Preview(name = "08 Garden Cress RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun CressScenario() = ScenarioScreen(UiScreen.RECIPE, Scenario.CRESS_RECIPE)

@Preview(name = "08 Invalid Range RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun InvalidRecipeScenario() = RecipeEditorPreview(UiVariant.VALIDATION_ERROR)

@Composable private fun RecipeEditorPreview(variant: UiVariant) {
    com.sedsoftware.sproutpace.compose.theme.AppTheme(onThemeChanged = {}) {
        Surface(Modifier.fillMaxSize()) {
            RecipeScreen(typedPreviewState(UiScreen.RECIPE, variant, scenario = Scenario.PERSONAL_RECIPE),
                UiLayout.COMPACT, {}, initialEditing = true)
        }
    }
}

@Preview(name = "08 Expanded Stages And Sources", widthDp = 1100, heightDp = 1000)
@Composable private fun ExpandedRecipeScenario() = ScenarioScreen(UiScreen.RECIPE, Scenario.CONTENT)

@Preview(name = "09 Edit Observation RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun EditObservationScenario() = ScenarioScreen(UiScreen.OBSERVATION_EDITOR, Scenario.EDIT_OBSERVATION)

@Preview(name = "10 Failed Outcome Light", widthDp = 393, heightDp = 1000)
@Preview(name = "10 Failed Outcome Dark", widthDp = 393, heightDp = 1000, uiMode = 32)
@Composable private fun FailedOutcomeScenario() = ScenarioScreen(UiScreen.CLOSE_BATCH, Scenario.CONTENT,
    draft = mapOf("outcome" to "failed", "reason" to "Uneven germination"))

@Preview(name = "10 Abandoned Outcome Dark", widthDp = 393, heightDp = 1000, uiMode = 32)
@Preview(name = "10 Abandoned Outcome Light", widthDp = 393, heightDp = 1000)
@Composable private fun AbandonedOutcomeScenario() = ScenarioScreen(UiScreen.CLOSE_BATCH, Scenario.CONTENT,
    draft = mapOf("outcome" to "abandoned", "reason" to "Started another batch"))

@Preview(name = "10 Harvest Mass RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun HarvestMassScenario() = ScenarioScreen(UiScreen.CLOSE_BATCH, Scenario.CONTENT,
    draft = mapOf("outcome" to "harvested", "actualMassGrams" to "184", "assessment" to "successful"))

@Preview(name = "10 Invalid Mass Light", widthDp = 393, heightDp = 1000)
@Composable private fun InvalidOutcomeScenario() = ScenarioScreen(UiScreen.CLOSE_BATCH, Scenario.CONTENT, UiVariant.VALIDATION_ERROR)

@Preview(name = "12 Add Zone Light", widthDp = 393, heightDp = 1000)
@Composable private fun AddZoneScenario() = ScenarioScreen(UiScreen.ZONES, Scenario.NEW_ZONE)

@Preview(name = "12 Edit Zone Expanded", widthDp = 1100, heightDp = 1000)
@Composable private fun EditZoneScenario() = ScenarioScreen(UiScreen.ZONES, Scenario.EDIT_ZONE)

@Preview(name = "12 Invalid Capacity RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun InvalidZoneScenario() = ScenarioScreen(UiScreen.ZONES, Scenario.EDIT_ZONE, UiVariant.VALIDATION_ERROR)

@Preview(name = "13 Rectangle Editor Medium", widthDp = 700, heightDp = 1000)
@Preview(name = "13 Rectangle Editor Expanded", widthDp = 1100, heightDp = 1000)
@Composable private fun RectangleScenario() = ScenarioScreen(UiScreen.TRAY_SIZES, Scenario.EDIT_TRAY)

@Preview(name = "13 Circle Editor RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun CircleScenario() = ScenarioScreen(UiScreen.TRAY_SIZES, Scenario.EDIT_CIRCLE)

@Preview(name = "13 Invalid Dimensions Light", widthDp = 393, heightDp = 1000)
@Composable private fun InvalidTrayScenario() = ScenarioScreen(UiScreen.TRAY_SIZES, Scenario.EDIT_TRAY, UiVariant.VALIDATION_ERROR)

@Preview(name = "14 Actual Restore Counts RU 200", widthDp = 393, heightDp = 1100, locale = "ru", fontScale = 2f)
@Preview(name = "14 Actual Restore Counts Expanded", widthDp = 1100, heightDp = 1000)
@Composable private fun RestoreMetadataScenario() =
    ScenarioScreen(UiScreen.BACKUP_RESTORE, Scenario.RESTORE_PREVIEW, UiVariant.RESTORE_PREVIEW)

@Preview(name = "14 Export Progress Light", widthDp = 393, heightDp = 851)
@Composable private fun BackupProgressScenario() = ScenarioScreen(UiScreen.BACKUP_RESTORE, Scenario.CONTENT, UiVariant.PROGRESS)

@Preview(name = "16 Disabled Notifications Light", widthDp = 393, heightDp = 851)
@Preview(name = "16 Disabled Notifications RU 200", widthDp = 700, heightDp = 1100, locale = "ru", fontScale = 2f)
@Composable private fun DisabledNotificationsScenario() = ScenarioScreen(UiScreen.NOTIFICATIONS, Scenario.NOTIFICATIONS_DISABLED)

@Preview(name = "16 Permission Granted Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun GrantedNotificationsScenario() = App(typedPreviewState(UiScreen.NOTIFICATIONS,
    permissionOverride = PermissionState.GRANTED))

@Preview(name = "16 Permission Retry RU", widthDp = 393, heightDp = 1000, locale = "ru")
@Composable private fun RetryNotificationsScenario() = App(typedPreviewState(UiScreen.NOTIFICATIONS,
    permissionOverride = PermissionState.DENIED))

@Preview(name = "16 Expanded Notifications", widthDp = 1100, heightDp = 900)
@Composable private fun ExpandedNotificationsScenario() = ScenarioScreen(UiScreen.NOTIFICATIONS, Scenario.CONTENT)

@Preview(name = "14 Replace Restore Dialog EN", widthDp = 393, heightDp = 851)
@Preview(name = "14 Replace Restore Dialog RU 200", widthDp = 393, heightDp = 851, locale = "ru", fontScale = 2f)
@Composable private fun RestoreDialogScenario() = ProductionConfirmationPreview(DestructiveOperation.REPLACE_RESTORE)

@Preview(name = "04 Discard Draft Dialog EN", widthDp = 393, heightDp = 851)
@Preview(name = "04 Discard Draft Dialog RU 200", widthDp = 700, heightDp = 1000, locale = "ru", fontScale = 2f)
@Composable private fun DiscardDialogScenario() = ProductionConfirmationPreview(DestructiveOperation.DISCARD_CHANGES)

@Preview(name = "05 Undo Stage Dialog Dark", widthDp = 393, heightDp = 851, uiMode = 32)
@Composable private fun UndoDialogScenario() = ProductionConfirmationPreview(DestructiveOperation.UNDO_LAST_TRANSITION)

@Preview(name = "04 Save Correction Dialog RU", widthDp = 393, heightDp = 851, locale = "ru")
@Composable private fun CorrectionDialogScenario() = ProductionConfirmationPreview(DestructiveOperation.SAVE_BATCH_CORRECTION)

@Composable
internal fun ProductionConfirmationPreview(operation: DestructiveOperation) {
    val screen = when (operation) {
        DestructiveOperation.DELETE_ZONE -> UiScreen.ZONES
        DestructiveOperation.DELETE_TRAY_PRESET -> UiScreen.TRAY_SIZES
        DestructiveOperation.DELETE_OBSERVATION -> UiScreen.OBSERVATION_EDITOR
        else -> UiScreen.BATCH_DETAIL
    }
    val subject = when (operation) {
        DestructiveOperation.REPLACE_RESTORE, DestructiveOperation.DELETE_ALL_DATA, DestructiveOperation.DISCARD_CHANGES -> null
        DestructiveOperation.DELETE_ZONE -> "shelf"
        DestructiveOperation.DELETE_TRAY_PRESET -> "shallow"
        DestructiveOperation.DELETE_OBSERVATION -> "growth"
        else -> "active-radish"
    }
    val source = subject?.let { typedPreviewState(screen, selectedId = it) }
    val component = object : ConfirmationComponent {
        override val command = ScreenCommand("preview-confirmation", subjectId = subject)
        override val destructiveOperation = operation
        override fun confirm() = Unit
        override fun cancel() = Unit
    }
    com.sedsoftware.sproutpace.compose.theme.AppTheme(onThemeChanged = {}) {
        RootConfirmationDialog(component, source)
    }
}
