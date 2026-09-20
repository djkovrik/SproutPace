package com.sedsoftware.sproutpace.component

import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

@Serializable
enum class ScreenId {
    FIRST_RUN, TODAY, TRAYS, BATCH_EDITOR, BATCH_DETAIL, PLAN, CATALOG, RECIPE,
    OBSERVATION_EDITOR, CLOSE_BATCH, SETTINGS, ZONES, TRAY_SIZES, BACKUP_RESTORE,
    ADVERTISING_PRIVACY, NOTIFICATIONS,
}

enum class LoadState { LOADING, CONTENT, EMPTY, ERROR }
enum class LayoutMode { COMPACT, MEDIUM, EXPANDED, LARGE, EXTRA_LARGE }
enum class PermissionState { NOT_REQUIRED, NOT_REQUESTED, GRANTED, DENIED, PERMANENTLY_DENIED }

/** Stable resource identifier. It is deliberately not a resolved/localized string. */
data class TextKey(val value: String) {
    init { require(value.matches(Regex("[a-z][a-z0-9_]*"))) }
}

data class ScreenItemCommand(
    val operation: String,
    val subjectId: String,
) {
    init {
        require(operation.matches(Regex("[a-z][a-z0-9-]*")))
        require(subjectId.isNotBlank())
    }
}

data class ScreenItem(
    val id: String,
    val titleKey: TextKey? = null,
    val authoredTitle: String? = null,
    val supportingKey: TextKey? = null,
    val stateKey: TextKey? = null,
    val actionId: String? = null,
    val commands: List<ScreenItemCommand> = emptyList(),
    val photo: ScreenPhoto? = null,
) {
    init {
        require(id.isNotBlank())
        require(titleKey != null || !authoredTitle.isNullOrBlank())
        require(commands.map(ScreenItemCommand::operation).distinct().size == commands.size)
    }
}

class ScreenPhoto(
    val id: String,
    bytes: ByteArray,
    val contentDescriptionKey: TextKey,
) {
    val bytes: ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = other is ScreenPhoto &&
        id == other.id && contentDescriptionKey == other.contentDescriptionKey && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + bytes.contentHashCode()) + contentDescriptionKey.hashCode()
}

data class ScreenModel(
    val screen: ScreenId,
    val loadState: LoadState = LoadState.LOADING,
    val titleKey: TextKey,
    val items: List<ScreenItem> = emptyList(),
    val selectedItemId: String? = null,
    val primaryActionKey: TextKey? = null,
    val errorKey: TextKey? = null,
    val permissionState: PermissionState = PermissionState.NOT_REQUIRED,
    val conflictResolutionKeys: List<TextKey> = emptyList(),
    val offline: Boolean = true,
    val busy: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.COMPACT,
    val showInlineAdSlot: Boolean = false,
    val destructiveConfirmationKey: TextKey? = null,
    val fields: List<ScreenField> = emptyList(),
    val facts: List<ScreenFact> = emptyList(),
    val actions: List<ScreenActionDescriptor> = emptyList(),
    val validationErrors: Map<ScreenFieldId, TextKey> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
)

sealed interface ScreenAction {
    data object Retry : ScreenAction
    data object Back : ScreenAction
    data class Select(val id: String) : ScreenAction
    data class EditField(val fieldId: ScreenFieldId, val value: String) : ScreenAction
    data class ChooseOption(val fieldId: ScreenFieldId, val optionId: String) : ScreenAction
    data class Submit(val actionId: ScreenActionId) : ScreenAction
    data object CancelDraft : ScreenAction
    data class Execute(val command: ScreenCommand) : ScreenAction
    /** Trusted post-confirmation path used by the root confirmation slot. */
    data class ExecuteConfirmed(val command: ScreenCommand) : ScreenAction
    data class SetLayout(val mode: LayoutMode) : ScreenAction
}

@Serializable
data class ScreenCommand(
    val operation: String,
    val subjectId: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val deduplicationKey: String = listOfNotNull(operation, subjectId).joinToString(":"),
) {
    init {
        require(operation.matches(Regex("[a-z][a-z0-9_-]*")))
        require(deduplicationKey.isNotBlank())
    }

    val destructiveOperation: DestructiveOperation?
        get() = DestructiveCommandClassifier.classify(operation)
}

@Serializable
enum class DestructiveOperation {
    SAVE_BATCH_CORRECTION,
    UNDO_LAST_TRANSITION,
    DELETE_ZONE,
    DELETE_TRAY_PRESET,
    DELETE_RECIPE,
    DELETE_BATCH,
    DELETE_PLAN,
    DELETE_OBSERVATION,
    REPLACE_RESTORE,
    DELETE_ALL_DATA,
    DISCARD_CHANGES,
}

sealed interface ScreenOutput {
    data object Back : ScreenOutput
    data class Open(val screen: ScreenId, val stableId: String? = null) : ScreenOutput
    data class Completed(val operation: String, val stableId: String?) : ScreenOutput
    data class ConfirmationRequested(
        val sourceScreen: ScreenId,
        val command: ScreenCommand,
        val destructiveOperation: DestructiveOperation,
    ) : ScreenOutput
}

interface ScreenComponent {
    val model: Value<ScreenModel>
    fun onAction(action: ScreenAction)
}

interface FirstRunComponent : ScreenComponent
interface TodayComponent : ScreenComponent
interface TraysComponent : ScreenComponent
interface BatchEditorComponent : ScreenComponent
interface BatchDetailComponent : ScreenComponent
interface PlanComponent : ScreenComponent
interface CatalogComponent : ScreenComponent
interface RecipeComponent : ScreenComponent
interface ObservationEditorComponent : ScreenComponent
interface CloseBatchComponent : ScreenComponent
interface SettingsComponent : ScreenComponent
interface ZonesComponent : ScreenComponent
interface TraySizesComponent : ScreenComponent
interface BackupRestoreComponent : ScreenComponent
interface AdvertisingPrivacyComponent : ScreenComponent
interface NotificationsComponent : ScreenComponent

internal fun titleKey(screen: ScreenId): TextKey = TextKey(
    when (screen) {
        ScreenId.FIRST_RUN -> "screen_first_run_title"
        ScreenId.TODAY -> "screen_today_title"
        ScreenId.TRAYS -> "screen_trays_title"
        ScreenId.BATCH_EDITOR -> "screen_batch_editor_title"
        ScreenId.BATCH_DETAIL -> "screen_batch_detail_title"
        ScreenId.PLAN -> "screen_plan_title"
        ScreenId.CATALOG -> "screen_catalog_title"
        ScreenId.RECIPE -> "screen_recipe_title"
        ScreenId.OBSERVATION_EDITOR -> "screen_observation_editor_title"
        ScreenId.CLOSE_BATCH -> "screen_close_batch_title"
        ScreenId.SETTINGS -> "screen_settings_title"
        ScreenId.ZONES -> "screen_zones_title"
        ScreenId.TRAY_SIZES -> "screen_tray_sizes_title"
        ScreenId.BACKUP_RESTORE -> "screen_backup_restore_title"
        ScreenId.ADVERTISING_PRIVACY -> "screen_advertising_privacy_title"
        ScreenId.NOTIFICATIONS -> "screen_notifications_title"
    },
)
