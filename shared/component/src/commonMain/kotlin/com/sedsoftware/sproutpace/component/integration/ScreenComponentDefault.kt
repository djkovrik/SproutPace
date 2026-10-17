package com.sedsoftware.sproutpace.component.integration

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.rx.observer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
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
import com.sedsoftware.sproutpace.component.PlanComponent
import com.sedsoftware.sproutpace.component.RecipeComponent
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.component.ScreenOutput
import com.sedsoftware.sproutpace.component.ScreenVariant
import com.sedsoftware.sproutpace.component.SettingsComponent
import com.sedsoftware.sproutpace.component.TodayComponent
import com.sedsoftware.sproutpace.component.TraySizesComponent
import com.sedsoftware.sproutpace.component.TraysComponent
import com.sedsoftware.sproutpace.component.ZonesComponent
import com.sedsoftware.sproutpace.component.titleKey
import com.sedsoftware.sproutpace.component.store.ScreenStore
import com.sedsoftware.sproutpace.component.store.ScreenStoreProvider
import com.sedsoftware.sproutpace.component.store.SavedScreenDraft
import kotlin.coroutines.CoroutineContext

data class ScreenComponentDependencies(
    val storeFactory: StoreFactory,
    val manager: ScreenDataManager,
    val mainContext: CoroutineContext,
)

abstract class StoredScreenComponent(
    componentContext: ComponentContext,
    private val screen: ScreenId,
    stableId: String?,
    dependencies: ScreenComponentDependencies,
    private val output: (ScreenOutput) -> Unit,
) : ScreenComponent, ComponentContext by componentContext {
    private val restoredDraft = stateKeeper.consume("screen-draft", SavedScreenDraft.serializer())
    private val store = instanceKeeper.getStore(key = "${screen.name}:${stableId.orEmpty()}") {
        ScreenStoreProvider(
            dependencies.storeFactory, dependencies.manager, screen, stableId, dependencies.mainContext,
            restoredDraft = restoredDraft,
        ).provide()
    }

    override val model: Value<ScreenModel> = StoreValue(store) { it.toModel() }

    init {
        stateKeeper.register("screen-draft", SavedScreenDraft.serializer()) {
            SavedScreenDraft(store.state.draftValues.mapKeys { it.key.value },
                store.state.dirtyFields.mapTo(mutableSetOf()) { it.value }, store.state.snapshot.selectedItemId)
        }
        val disposable = store.labels(observer(onNext = { label ->
            when (label) {
                is ScreenStore.Label.Completed -> output(ScreenOutput.Completed(label.command.operation, label.command.subjectId))
                is ScreenStore.Label.ConfirmationRequested -> output(
                    ScreenOutput.ConfirmationRequested(
                        sourceScreen = screen,
                        command = label.command,
                        destructiveOperation = requireNotNull(label.command.destructiveOperation),
                    ),
                )
            }
        }))
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks {
            override fun onDestroy() {
                disposable.dispose()
                stateKeeper.unregister("screen-draft")
            }
        })
    }

    override fun onAction(action: ScreenAction) {
        when (action) {
            ScreenAction.Back -> output(ScreenOutput.Back)
            ScreenAction.Retry -> store.accept(ScreenStore.Intent.Retry)
            is ScreenAction.ReportFailure -> store.accept(ScreenStore.Intent.ReportFailure(action.key))
            is ScreenAction.Select -> store.accept(ScreenStore.Intent.Select(action.id))
            is ScreenAction.EditField -> store.accept(ScreenStore.Intent.EditField(action.fieldId, action.value))
            is ScreenAction.ChooseOption -> store.accept(ScreenStore.Intent.EditField(action.fieldId, action.optionId))
            is ScreenAction.Submit -> store.accept(ScreenStore.Intent.Submit(action.actionId))
            ScreenAction.CancelDraft -> store.accept(ScreenStore.Intent.CancelDraft)
            ScreenAction.ConfirmStagePreview -> store.accept(ScreenStore.Intent.ConfirmStagePreview)
            is ScreenAction.SetLayout -> store.accept(ScreenStore.Intent.SetLayout(action.mode))
            is ScreenAction.Execute -> {
                val destructiveOperation = action.command.destructiveOperation
                if (destructiveOperation == null) {
                    store.accept(ScreenStore.Intent.Execute(action.command))
                } else {
                    output(ScreenOutput.ConfirmationRequested(screen, action.command, destructiveOperation))
                }
            }
            is ScreenAction.ExecuteConfirmed -> {
                requireNotNull(action.command.destructiveOperation) { "Only classified destructive commands may use the confirmed path" }
                store.accept(ScreenStore.Intent.Execute(action.command))
            }
        }
    }
}

private fun ScreenStore.State.toModel(): ScreenModel {
    val contract = ScreenContractRegistry.contracts.getValue(screen)
    val sourceFields = if (snapshot.hasInteractionContract) snapshot.fields else snapshot.fields.ifEmpty { contract.fields }
    val fields = sourceFields.map { value -> value.copy(value = draftValues[value.id] ?: value.value) }
    val facts = if (snapshot.hasInteractionContract) snapshot.facts else snapshot.facts.ifEmpty { contract.facts }
    val actions = if (snapshot.hasInteractionContract) snapshot.actions else snapshot.actions.ifEmpty { contract.actions }
    val visibleError = errorKey ?: observationErrorKey
    return ScreenModel(
        screen = screen,
        loadState = when {
            loading -> LoadState.LOADING
            visibleError != null -> LoadState.ERROR
            ScreenVariant.EMPTY in contract.variants && snapshot.items.isEmpty() -> LoadState.EMPTY
            else -> LoadState.CONTENT
        },
        titleKey = titleKey(screen),
        items = snapshot.items + listOfNotNull(photoPreview?.takeIf {
            photoPreviewValue == draftValues[ScreenFieldId("photoId")]
        }?.let { com.sedsoftware.sproutpace.component.ScreenItem("draft-photo",
            titleKey = TextKey("observation_photo_content_description"), photo = it) }),
        selectedItemId = snapshot.selectedItemId,
        plans = snapshot.plans,
        primaryActionKey = snapshot.primaryActionKey,
        errorKey = visibleError,
        permissionState = snapshot.permissionState,
        conflictResolutionKeys = snapshot.conflictResolutionKeys,
        offline = snapshot.offline,
        busy = busy,
        layoutMode = layoutMode,
        showInlineAdSlot = snapshot.showInlineAdSlot,
        destructiveConfirmationKey = snapshot.destructiveConfirmationKey,
        fields = fields,
        facts = facts.withDraftSeedRecommendation(screen, fields, actions),
        actions = actions,
        validationErrors = validationErrors,
        hasUnsavedChanges = dirtyFields.isNotEmpty(),
        setupState = snapshot.setupState,
        stagePreview = stagePreview,
        completionRevision = completionRevision,
        detail = snapshot.selectedItemId?.takeIf { detailSnapshot != null || detailErrorKey != null }?.let { selected ->
            ScreenStore.State(
                screen = if (screen == ScreenId.TRAYS) ScreenId.BATCH_DETAIL else ScreenId.RECIPE,
                snapshot = detailSnapshot ?: com.sedsoftware.sproutpace.component.ScreenSnapshot(selectedItemId = selected),
                loading = false,
                errorKey = detailErrorKey,
            ).toModel()
        },
    )
}

private class StoreValue<S : Any, M : Any>(
    private val store: Store<*, S, *>,
    private val mapper: (S) -> M,
) : Value<M>() {
    override val value: M get() = mapper(store.state)
    override fun subscribe(callback: (M) -> Unit): Cancellation {
        val disposable = store.states(observer(onNext = { callback(mapper(it)) }))
        return Cancellation(disposable::dispose)
    }
}

class FirstRunComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.FIRST_RUN, id, d, o), FirstRunComponent
class TodayComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.TODAY, id, d, o), TodayComponent
class TraysComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.TRAYS, id, d, o), TraysComponent
class BatchEditorComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.BATCH_EDITOR, id, d, o), BatchEditorComponent
class BatchDetailComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.BATCH_DETAIL, id, d, o), BatchDetailComponent
class PlanComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.PLAN, id, d, o), PlanComponent
class CatalogComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.CATALOG, id, d, o), CatalogComponent
class RecipeComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.RECIPE, id, d, o), RecipeComponent
class ObservationEditorComponentDefault(
    c: ComponentContext,
    id: String?,
    d: ScreenComponentDependencies,
    o: (ScreenOutput) -> Unit,
) : StoredScreenComponent(c, ScreenId.OBSERVATION_EDITOR, id, d, o), ObservationEditorComponent
class CloseBatchComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.CLOSE_BATCH, id, d, o), CloseBatchComponent
class SettingsComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.SETTINGS, id, d, o), SettingsComponent
class ZonesComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.ZONES, id, d, o), ZonesComponent
class TraySizesComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.TRAY_SIZES, id, d, o), TraySizesComponent
class BackupRestoreComponentDefault(
    c: ComponentContext,
    id: String?,
    d: ScreenComponentDependencies,
    o: (ScreenOutput) -> Unit,
) : StoredScreenComponent(c, ScreenId.BACKUP_RESTORE, id, d, o), BackupRestoreComponent
class AdvertisingPrivacyComponentDefault(
    c: ComponentContext,
    id: String?,
    d: ScreenComponentDependencies,
    o: (ScreenOutput) -> Unit,
) : StoredScreenComponent(c, ScreenId.ADVERTISING_PRIVACY, id, d, o), AdvertisingPrivacyComponent
class NotificationsComponentDefault(c: ComponentContext, id: String?, d: ScreenComponentDependencies, o: (ScreenOutput) -> Unit) :
    StoredScreenComponent(c, ScreenId.NOTIFICATIONS, id, d, o), NotificationsComponent

class ScreenComponentFactory(private val dependencies: ScreenComponentDependencies) {
    fun create(
        screen: ScreenId,
        context: ComponentContext,
        stableId: String? = null,
        output: (ScreenOutput) -> Unit,
    ): ScreenComponent = when (screen) {
        ScreenId.FIRST_RUN -> FirstRunComponentDefault(context, stableId, dependencies, output)
        ScreenId.TODAY -> TodayComponentDefault(context, stableId, dependencies, output)
        ScreenId.TRAYS -> TraysComponentDefault(context, stableId, dependencies, output)
        ScreenId.BATCH_EDITOR -> BatchEditorComponentDefault(context, stableId, dependencies, output)
        ScreenId.BATCH_DETAIL -> BatchDetailComponentDefault(context, stableId, dependencies, output)
        ScreenId.PLAN -> PlanComponentDefault(context, stableId, dependencies, output)
        ScreenId.CATALOG -> CatalogComponentDefault(context, stableId, dependencies, output)
        ScreenId.RECIPE -> RecipeComponentDefault(context, stableId, dependencies, output)
        ScreenId.OBSERVATION_EDITOR -> ObservationEditorComponentDefault(context, stableId, dependencies, output)
        ScreenId.CLOSE_BATCH -> CloseBatchComponentDefault(context, stableId, dependencies, output)
        ScreenId.SETTINGS -> SettingsComponentDefault(context, stableId, dependencies, output)
        ScreenId.ZONES -> ZonesComponentDefault(context, stableId, dependencies, output)
        ScreenId.TRAY_SIZES -> TraySizesComponentDefault(context, stableId, dependencies, output)
        ScreenId.BACKUP_RESTORE -> BackupRestoreComponentDefault(context, stableId, dependencies, output)
        ScreenId.ADVERTISING_PRIVACY -> AdvertisingPrivacyComponentDefault(context, stableId, dependencies, output)
        ScreenId.NOTIFICATIONS -> NotificationsComponentDefault(context, stableId, dependencies, output)
    }
}
