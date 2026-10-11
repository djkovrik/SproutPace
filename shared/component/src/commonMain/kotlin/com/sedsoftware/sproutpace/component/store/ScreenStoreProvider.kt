package com.sedsoftware.sproutpace.component.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.sedsoftware.sproutpace.component.isImmediatePreference
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class ScreenStoreProvider(
    private val storeFactory: StoreFactory,
    private val manager: ScreenDataManager,
    private val screen: ScreenId,
    private val stableId: String?,
    private val mainContext: CoroutineContext,
    private val restoredDraft: SavedScreenDraft? = null,
) {
    fun provide(): ScreenStore = object : ScreenStore,
        com.arkivanov.mvikotlin.core.store.Store<ScreenStore.Intent, ScreenStore.State, ScreenStore.Label> by
        storeFactory.create(
            name = "${screen.name}Store",
            initialState = ScreenStore.State(
                screen = screen,
                stableId = stableId,
                snapshot = ScreenSnapshot(selectedItemId = restoredDraft?.selectedItemId),
                draftValues = ScreenContractRegistry.contracts.getValue(screen).fields.associate { it.id to it.value } +
                    restoredDraft?.values.orEmpty().mapKeys { ScreenFieldId(it.key) },
                dirtyFields = restoredDraft?.dirtyFields.orEmpty().mapTo(mutableSetOf(), ::ScreenFieldId),
                restoredFields = restoredDraft?.values.orEmpty().keys.mapTo(mutableSetOf(), ::ScreenFieldId),
            ),
            bootstrapper = SimpleBootstrapper(Action.Observe),
            executorFactory = { ExecutorImpl() },
            reducer = ReducerImpl,
        ) {}

    private sealed interface Action { data object Observe : Action }
    private sealed interface Msg {
        data class Loaded(val snapshot: ScreenSnapshot, val protectedFields: Set<ScreenFieldId> = emptySet()) : Msg
        data class DetailLoaded(val subjectId: String, val snapshot: ScreenSnapshot) : Msg
        data class Committed(val snapshot: ScreenSnapshot) : Msg
        data class PreferenceFinished(
            val snapshot: ScreenSnapshot?, val fields: Set<ScreenFieldId>, val preserved: Set<ScreenFieldId>,
        ) : Msg
        data class StagePreviewed(val preview: com.sedsoftware.sproutpace.component.StageChangeProjection) : Msg
        data class Selected(val id: String) : Msg
        data class Busy(val value: Boolean) : Msg
        data class Failed(val key: TextKey) : Msg
        data class Layout(val mode: com.sedsoftware.sproutpace.component.LayoutMode) : Msg
        data class FieldEdited(val fieldId: ScreenFieldId, val value: String) : Msg
        data class ValidationFailed(val errors: Map<ScreenFieldId, TextKey>) : Msg
        data object DraftCancelled : Msg
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<ScreenStore.Intent, Action, ScreenStore.State, Msg, ScreenStore.Label>(mainContext) {
        private var observation: Job? = null
        private var detailObservation: Job? = null
        private var detailSubject: String? = null
        private val inFlight = mutableSetOf<String>()
        private val inFlightPayloads = mutableSetOf<String>()
        private val completedKeys = mutableSetOf<String>()
        private val fieldRevisions = mutableMapOf<ScreenFieldId, Long>()
        private val preferences = ArrayDeque<PreferenceRequest>()
        private var drainingPreferences = false
        private var activePreviews = 0

        private inner class PreferenceRequest(val command: ScreenCommand) {
            val invocationKeys = mutableSetOf<String>().apply { command.invocationId?.let(::add) }
            val revisions = command.arguments.keys.associate { name ->
                val id = ScreenFieldId(name)
                id to (fieldRevisions[id] ?: 0L)
            }
        }

        override fun executeAction(action: Action) { observe() }

        override fun executeIntent(intent: ScreenStore.Intent) {
            when (intent) {
                ScreenStore.Intent.Retry -> observe()
                is ScreenStore.Intent.Select -> {
                    dispatch(Msg.Selected(intent.id))
                    observeDetail(intent.id)
                }
                is ScreenStore.Intent.EditField -> {
                    fieldRevisions[intent.fieldId] = (fieldRevisions[intent.fieldId] ?: 0L) + 1
                    dispatch(Msg.FieldEdited(intent.fieldId, intent.value))
                }
                is ScreenStore.Intent.Submit -> submit(intent.actionId)
                ScreenStore.Intent.CancelDraft -> dispatch(Msg.DraftCancelled)
                is ScreenStore.Intent.ReportFailure -> dispatch(Msg.Failed(intent.key))
                ScreenStore.Intent.ConfirmStagePreview -> state().stagePreview?.let { executeValidated(it.command) }
                is ScreenStore.Intent.SetLayout -> dispatch(Msg.Layout(intent.mode))
                is ScreenStore.Intent.Execute -> executeValidated(intent.command)
            }
        }

        private fun submit(actionId: com.sedsoftware.sproutpace.component.ScreenActionId) {
            when (
                val preparation = ScreenContractRegistry.prepareCommand(
                    screen = screen,
                    actionId = actionId,
                    currentSubjectId = state().stableId ?: state().snapshot.selectedItemId,
                    draft = state().draftValues,
                )
            ) {
                is CommandPreparation.Invalid -> dispatch(Msg.ValidationFailed(preparation.errors))
                is CommandPreparation.Ready -> {
                    if (screen == ScreenId.BATCH_DETAIL && preparation.command.operation in
                        com.sedsoftware.sproutpace.component.previewedStageOperations) {
                        previewStage(preparation.command)
                    } else if (preparation.command.destructiveOperation == null) {
                        executeValidated(preparation.command)
                    } else {
                        publish(ScreenStore.Label.ConfirmationRequested(preparation.command))
                    }
                }
            }
        }

        private fun executeValidated(command: ScreenCommand) {
            val operation = ScreenContractRegistry.contracts.getValue(screen).operations[command.operation]
            if (operation == null) {
                dispatch(Msg.ValidationFailed(mapOf(ScreenFieldId("id") to TextKey("state_error_body"))))
                return
            }
            if (!operation.itemTargeted) {
                execute(command)
                return
            }
            val draft = command.arguments.mapKeys { (name, _) -> ScreenFieldId(name) }
            when (
                val preparation = ScreenContractRegistry.prepareCommand(
                    screen = screen,
                    actionId = com.sedsoftware.sproutpace.component.ScreenActionId(command.operation),
                    currentSubjectId = command.subjectId,
                    draft = draft,
                )
            ) {
                is CommandPreparation.Invalid -> dispatch(Msg.ValidationFailed(preparation.errors))
                is CommandPreparation.Ready -> {
                    val exactTarget = state().snapshot.items.any { item ->
                        item.commands.any { target ->
                            target.operation == command.operation && target.subjectId == command.subjectId
                        }
                    }
                    if (operation.itemTargeted && !exactTarget) {
                        dispatch(Msg.ValidationFailed(mapOf(ScreenFieldId("id") to TextKey("state_error_body"))))
                    } else if (
                        preparation.command.operation != command.operation ||
                        preparation.command.subjectId != command.subjectId ||
                        preparation.command.arguments != command.arguments
                    ) {
                        dispatch(Msg.ValidationFailed(mapOf(ScreenFieldId("id") to TextKey("state_error_body"))))
                    } else {
                        execute(command)
                    }
                }
            }
        }

        private fun previewStage(command: ScreenCommand) {
            val draft = state().draftValues
            scope.launch {
                activePreviews += 1
                dispatch(Msg.Busy(true))
                try {
                    manager.previewStage(command).unwrap(
                        onSuccess = { if (state().draftValues == draft) dispatch(Msg.StagePreviewed(it)) },
                        onFailure = { dispatch(Msg.Failed(TextKey("error_operation_failed"))) },
                    )
                } finally {
                    activePreviews -= 1
                    dispatchBusy()
                }
            }
        }

        private fun observe() {
            observation?.cancel()
            observation = scope.launch {
                manager.watch(screen, stableId).collectLatest {
                    dispatch(Msg.Loaded(it, preferences.flatMap { request -> request.revisions.keys }.toSet()))
                    observeDetail(state().snapshot.selectedItemId)
                }
            }
        }

        private fun observeDetail(subjectId: String?) {
            val detailScreen = when (screen) {
                ScreenId.TRAYS -> ScreenId.BATCH_DETAIL
                ScreenId.CATALOG -> ScreenId.RECIPE
                else -> return
            }
            if (detailSubject == subjectId) return
            detailSubject = subjectId
            detailObservation?.cancel()
            if (subjectId == null) return
            detailObservation = scope.launch {
                manager.watch(detailScreen, subjectId).collectLatest { dispatch(Msg.DetailLoaded(subjectId, it)) }
            }
        }

        private fun executePreference(command: ScreenCommand) {
            if (command.invocationId != null && command.executionKey in completedKeys) return
            preferences.lastOrNull()?.takeIf { it.command.deduplicationKey == command.deduplicationKey }?.let {
                command.invocationId?.let(it.invocationKeys::add)
                return
            }
            preferences.addLast(PreferenceRequest(command))
            if (drainingPreferences) return
            drainingPreferences = true
            scope.launch {
                dispatch(Msg.Busy(true))
                try {
                    while (preferences.isNotEmpty()) {
                        val request = preferences.first()
                        val result = manager.execute(screen, request.command)
                        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        val preserved = state().draftValues.keys.filterTo(mutableSetOf()) { id ->
                            id !in request.revisions || (fieldRevisions[id] ?: 0L) != request.revisions[id]
                        }
                        preferences.removeFirst()
                        dispatch(Msg.PreferenceFinished(result.getOrNull(), request.revisions.keys, preserved))
                        if (result.isSuccess) {
                            completedKeys += request.invocationKeys
                            publish(ScreenStore.Label.Completed(request.command))
                        }
                    }
                } finally {
                    preferences.clear()
                    drainingPreferences = false
                    dispatchBusy()
                }
            }
        }

        private fun execute(command: ScreenCommand) {
            if (command.isImmediatePreference) {
                executePreference(command)
                return
            }
            val repeatableScreen = screen == ScreenId.NOTIFICATIONS || screen == ScreenId.BACKUP_RESTORE
            if ((!repeatableScreen && command.executionKey in completedKeys) ||
                command.executionKey in inFlight || command.deduplicationKey in inFlightPayloads) return
            inFlight += command.executionKey
            inFlightPayloads += command.deduplicationKey
            scope.launch {
                dispatch(Msg.Busy(true))
                try {
                    manager.execute(screen, command).unwrap(
                        onSuccess = {
                            dispatch(Msg.Committed(it))
                            completedKeys += command.executionKey
                            publish(ScreenStore.Label.Completed(command))
                        },
                        onFailure = {
                            val key = (it as? com.sedsoftware.sproutpace.component.ScreenFailure)?.errorKey
                                ?: TextKey("error_operation_failed")
                            dispatch(Msg.Failed(key))
                        },
                    )
                } finally {
                    inFlight -= command.executionKey
                    inFlightPayloads -= command.deduplicationKey
                    dispatchBusy()
                }
            }
        }

        private fun dispatchBusy() {
            dispatch(Msg.Busy(inFlight.isNotEmpty() || drainingPreferences || activePreviews > 0))
        }
    }

    private object ReducerImpl : Reducer<ScreenStore.State, Msg> {
        override fun ScreenStore.State.reduce(msg: Msg): ScreenStore.State = when (msg) {
            is Msg.Loaded -> loaded(msg)
            is Msg.DetailLoaded -> if (snapshot.selectedItemId == msg.subjectId) copy(detailSnapshot = msg.snapshot) else this
            is Msg.Committed -> {
                val fields = if (!msg.snapshot.hasInteractionContract && msg.snapshot.fields.isEmpty()) {
                    ScreenContractRegistry.contracts.getValue(screen).fields
                } else {
                    msg.snapshot.fields
                }
                copy(
                    snapshot = msg.snapshot,
                    loading = false,
                    errorKey = null,
                    stagePreview = null,
                    completionRevision = completionRevision + 1,
                    draftValues = fields.associate { it.id to it.value } + draftValues.filterKeys { isTransientFilter(it) },
                    dirtyFields = emptySet(),
                    validationErrors = emptyMap(),
                )
            }
            is Msg.PreferenceFinished -> preferenceFinished(msg)
            is Msg.StagePreviewed -> copy(stagePreview = msg.preview, errorKey = null)
            is Msg.Selected -> copy(snapshot = snapshot.copy(selectedItemId = msg.id),
                detailSnapshot = detailSnapshot.takeIf { snapshot.selectedItemId == msg.id })
            is Msg.Busy -> copy(busy = msg.value)
            is Msg.Failed -> copy(loading = false, errorKey = msg.key)
            is Msg.Layout -> copy(layoutMode = msg.mode)
            is Msg.FieldEdited -> fieldEdited(msg).copy(stagePreview = null)
            is Msg.ValidationFailed -> copy(validationErrors = msg.errors)
            Msg.DraftCancelled -> {
                val fields = if (!snapshot.hasInteractionContract && snapshot.fields.isEmpty()) {
                    ScreenContractRegistry.contracts.getValue(screen).fields
                } else {
                    snapshot.fields
                }
                copy(
                    draftValues = fields.associate { it.id to it.value },
                    stagePreview = null,
                    dirtyFields = emptySet(),
                    validationErrors = emptyMap(),
                )
            }
        }

        private fun ScreenStore.State.loaded(msg: Msg.Loaded): ScreenStore.State {
            val availableFields = if (!msg.snapshot.hasInteractionContract && msg.snapshot.fields.isEmpty()) {
                ScreenContractRegistry.contracts.getValue(screen).fields
            } else {
                msg.snapshot.fields
            }
            val loadedValues = availableFields.associate { it.id to it.value }
            return copy(
                snapshot = msg.snapshot.copy(selectedItemId = if (screen in setOf(ScreenId.TRAYS, ScreenId.CATALOG)) {
                    snapshot.selectedItemId?.takeIf { selected -> msg.snapshot.items.any { it.id == selected } }
                } else msg.snapshot.selectedItemId),
                loading = false,
                errorKey = null,
                draftValues = draftValues + loadedValues.filterKeys {
                    it !in dirtyFields && it !in restoredFields && it !in msg.protectedFields &&
                        (loading || (!isTransientFilter(it) && it.value != "id"))
                },
                restoredFields = emptySet(),
            )
        }

        private fun ScreenStore.State.preferenceFinished(msg: Msg.PreferenceFinished): ScreenStore.State {
            val baseline = (msg.snapshot ?: snapshot).fields.associate { it.id to it.value }
            val settled = msg.fields - msg.preserved
            val values = draftValues + settled.associateWith { baseline[it].orEmpty() }
            return copy(
                snapshot = msg.snapshot ?: snapshot,
                draftValues = values,
                dirtyFields = values.keys.filterTo(mutableSetOf()) { id ->
                    !isTransientFilter(id) && values[id] != baseline[id]
                },
                loading = false,
                validationErrors = validationErrors - settled,
                errorKey = if (msg.snapshot == null) TextKey("error_operation_failed") else null,
                completionRevision = completionRevision + if (msg.snapshot != null) 1 else 0,
            )
        }

        private fun ScreenStore.State.fieldEdited(msg: Msg.FieldEdited): ScreenStore.State {
            val fields = if (snapshot.hasInteractionContract) snapshot.fields else
                snapshot.fields.ifEmpty { ScreenContractRegistry.contracts.getValue(screen).fields }
            val baseline = fields.firstOrNull { it.id == msg.fieldId }?.value.orEmpty()
            return copy(
                draftValues = draftValues + (msg.fieldId to msg.value),
                dirtyFields = if (msg.value == baseline || isTransientFilter(msg.fieldId)) {
                    dirtyFields - msg.fieldId
                } else dirtyFields + msg.fieldId,
                validationErrors = validationErrors - msg.fieldId,
            )
        }

        private fun ScreenStore.State.isTransientFilter(id: ScreenFieldId): Boolean =
            (screen == ScreenId.CATALOG && id.value == "query") ||
                (screen == ScreenId.TRAYS && id.value == "category")
    }
}

internal inline fun <T> Result<T>.unwrap(onSuccess: (T) -> Unit, onFailure: (Throwable) -> Unit) {
    fold(onSuccess = onSuccess, onFailure = {
        if (it is CancellationException) throw it
        onFailure(it)
    })
}
