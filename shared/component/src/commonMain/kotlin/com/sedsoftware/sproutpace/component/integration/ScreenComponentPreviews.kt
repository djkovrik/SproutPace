package com.sedsoftware.sproutpace.component.integration

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.sedsoftware.sproutpace.component.AdvertisingPrivacyComponent
import com.sedsoftware.sproutpace.component.BackupRestoreComponent
import com.sedsoftware.sproutpace.component.BatchDetailComponent
import com.sedsoftware.sproutpace.component.BatchEditorComponent
import com.sedsoftware.sproutpace.component.CatalogComponent
import com.sedsoftware.sproutpace.component.CloseBatchComponent
import com.sedsoftware.sproutpace.component.FirstRunComponent
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.NotificationsComponent
import com.sedsoftware.sproutpace.component.ObservationEditorComponent
import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.PlanComponent
import com.sedsoftware.sproutpace.component.RecipeComponent
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenField
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.component.ScreenOption
import com.sedsoftware.sproutpace.component.SettingsComponent
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.TodayComponent
import com.sedsoftware.sproutpace.component.TraySizesComponent
import com.sedsoftware.sproutpace.component.TraysComponent
import com.sedsoftware.sproutpace.component.ZonesComponent
import com.sedsoftware.sproutpace.component.titleKey

abstract class StaticScreenComponent(screen: ScreenId, model: ScreenModel? = null) : ScreenComponent {
    final override val model: Value<ScreenModel> = MutableValue(model ?: previewModel(screen))
    override fun onAction(action: ScreenAction) = Unit
}

private fun previewModel(screen: ScreenId) = ScreenModel(
    screen = screen,
    loadState = when (screen) {
        ScreenId.TODAY -> LoadState.EMPTY
        ScreenId.SETTINGS -> LoadState.ERROR
        else -> LoadState.CONTENT
    },
    titleKey = titleKey(screen),
    items = listOf(
        ScreenItem(
            "preview",
            titleKey = TextKey("preview_item_title"),
            supportingKey = TextKey("preview_item_supporting"),
        ),
    ),
    primaryActionKey = previewPrimaryActionKey(screen),
    errorKey = if (screen == ScreenId.SETTINGS) TextKey("error_operation_failed") else null,
    permissionState = if (screen == ScreenId.NOTIFICATIONS) PermissionState.DENIED else PermissionState.NOT_REQUIRED,
    conflictResolutionKeys = if (screen == ScreenId.PLAN) {
        listOf(TextKey("choose_another_date"), TextKey("choose_another_zone"), TextKey("change_capacity"))
    } else {
        emptyList()
    },
    fields = ScreenContractRegistry.contracts.getValue(screen).fields.map(::previewField),
    facts = ScreenContractRegistry.contracts.getValue(screen).facts,
    actions = ScreenContractRegistry.contracts.getValue(screen).actions,
    validationErrors = if (screen == ScreenId.FIRST_RUN) {
        mapOf(ScreenFieldId("capacity") to TextKey("state_error_body"))
    } else {
        emptyMap()
    },
)

private fun previewPrimaryActionKey(screen: ScreenId): TextKey? = when (screen) {
    ScreenId.FIRST_RUN -> TextKey("action_continue")
    ScreenId.TODAY, ScreenId.TRAYS -> TextKey("action_new_batch")
    ScreenId.BATCH_EDITOR -> TextKey("action_save_batch")
    ScreenId.BATCH_DETAIL -> TextKey("action_confirm_stage")
    ScreenId.PLAN -> TextKey("action_confirm_suggestion")
    ScreenId.CATALOG, ScreenId.SETTINGS, ScreenId.BACKUP_RESTORE -> null
    ScreenId.RECIPE -> TextKey("action_use_recipe")
    ScreenId.OBSERVATION_EDITOR -> TextKey("action_save_observation")
    ScreenId.CLOSE_BATCH -> TextKey("action_save_outcome")
    ScreenId.ZONES -> TextKey("action_add_zone")
    ScreenId.TRAY_SIZES -> TextKey("action_add_preset")
    ScreenId.ADVERTISING_PRIVACY -> TextKey("action_accept")
    ScreenId.NOTIFICATIONS -> TextKey("action_request_permission")
}

private fun previewField(field: ScreenField): ScreenField = field.copy(
    value = when (field.kind) {
        ScreenFieldKind.POSITIVE_INTEGER -> "3"
        ScreenFieldKind.POSITIVE_LONG -> "1893456000000"
        ScreenFieldKind.NON_NEGATIVE_LONG -> "0"
        ScreenFieldKind.POSITIVE_DECIMAL -> "12.5"
        ScreenFieldKind.BOOLEAN -> "true"
        ScreenFieldKind.LOCAL_DATE -> "2026-09-20"
        ScreenFieldKind.LOCAL_DATE_TIME -> "2026-09-20T09:00"
        ScreenFieldKind.INSTANT -> "2026-09-20T06:00:00Z"
        ScreenFieldKind.SINGLE_CHOICE -> field.options.firstOrNull()?.id.orEmpty()
        ScreenFieldKind.MULTI_CHOICE -> field.options.take(2).joinToString(",", transform = ScreenOption::id)
        ScreenFieldKind.DOCUMENT -> "preview-document"
        ScreenFieldKind.TEXT -> if (field.id.value == "id") "preview-id" else "preview-value"
    },
)

class FirstRunComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.FIRST_RUN, model), FirstRunComponent
class TodayComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.TODAY, model), TodayComponent
class TraysComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.TRAYS, model), TraysComponent
class BatchEditorComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.BATCH_EDITOR, model), BatchEditorComponent
class BatchDetailComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.BATCH_DETAIL, model), BatchDetailComponent
class PlanComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.PLAN, model), PlanComponent
class CatalogComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.CATALOG, model), CatalogComponent
class RecipeComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.RECIPE, model), RecipeComponent
class ObservationEditorComponentPreview(model: ScreenModel? = null) :
    StaticScreenComponent(ScreenId.OBSERVATION_EDITOR, model), ObservationEditorComponent
class CloseBatchComponentPreview(model: ScreenModel? = null) :
    StaticScreenComponent(ScreenId.CLOSE_BATCH, model), CloseBatchComponent
class SettingsComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.SETTINGS, model), SettingsComponent
class ZonesComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.ZONES, model), ZonesComponent
class TraySizesComponentPreview(model: ScreenModel? = null) : StaticScreenComponent(ScreenId.TRAY_SIZES, model), TraySizesComponent
class BackupRestoreComponentPreview(model: ScreenModel? = null) :
    StaticScreenComponent(ScreenId.BACKUP_RESTORE, model), BackupRestoreComponent
class AdvertisingPrivacyComponentPreview(model: ScreenModel? = null) :
    StaticScreenComponent(ScreenId.ADVERTISING_PRIVACY, model), AdvertisingPrivacyComponent
class NotificationsComponentPreview(model: ScreenModel? = null) :
    StaticScreenComponent(ScreenId.NOTIFICATIONS, model), NotificationsComponent
