package com.sedsoftware.sproutpace.component.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
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
) {
    fun provide(): ScreenStore = object : ScreenStore,
        com.arkivanov.mvikotlin.core.store.Store<ScreenStore.Intent, ScreenStore.State, ScreenStore.Label> by
        storeFactory.create(
            name = "${screen.name}Store",
            initialState = ScreenStore.State(
                screen = screen,
                stableId = stableId,
                draftValues = ScreenContractRegistry.contracts.getValue(screen).fields.associate { it.id to it.value },
            ),
            bootstrapper = SimpleBootstrapper(Action.Observe),
            executorFactory = { ExecutorImpl() },
            reducer = ReducerImpl,
        ) {}

    private sealed interface Action { data object Observe : Action }
    private sealed interface Msg {
        data class Loaded(val snapshot: ScreenSnapshot) : Msg
        data class Committed(val snapshot: ScreenSnapshot) : Msg
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
        private val inFlight = mutableSetOf<String>()
        private val completedKeys = mutableSetOf<String>()

        override fun executeAction(action: Action) { observe() }

        override fun executeIntent(intent: ScreenStore.Intent) {
            when (intent) {
                ScreenStore.Intent.Retry -> observe()
                is ScreenStore.Intent.Select -> dispatch(Msg.Selected(intent.id))
                is ScreenStore.Intent.EditField -> dispatch(Msg.FieldEdited(intent.fieldId, intent.value))
                is ScreenStore.Intent.Submit -> submit(intent.actionId)
                ScreenStore.Intent.CancelDraft -> dispatch(Msg.DraftCancelled)
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
                    if (preparation.command.destructiveOperation == null) {
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

        private fun observe() {
            observation?.cancel()
            observation = scope.launch {
                manager.watch(screen, stableId).collectLatest { dispatch(Msg.Loaded(it)) }
            }
        }

        private fun execute(command: ScreenCommand) {
            if (command.deduplicationKey in completedKeys || !inFlight.add(command.deduplicationKey)) return
            scope.launch {
                dispatch(Msg.Busy(true))
                manager.execute(screen, command).unwrap(
                    onSuccess = {
                        dispatch(Msg.Committed(it))
                        completedKeys += command.deduplicationKey
                        publish(ScreenStore.Label.Completed(command))
                    },
                    onFailure = { dispatch(Msg.Failed(TextKey("error_operation_failed"))) },
                )
                inFlight -= command.deduplicationKey
                dispatch(Msg.Busy(false))
            }
        }
    }

    private object ReducerImpl : Reducer<ScreenStore.State, Msg> {
        override fun ScreenStore.State.reduce(msg: Msg): ScreenStore.State = when (msg) {
            is Msg.Loaded -> {
                val availableFields = if (msg.snapshot.fields.isEmpty()) {
                    ScreenContractRegistry.contracts.getValue(screen).fields
                } else {
                    msg.snapshot.fields
                }
                val loadedValues = availableFields.associate { it.id to it.value }
                copy(
                    snapshot = msg.snapshot,
                    loading = false,
                    errorKey = null,
                    draftValues = draftValues + loadedValues.filterKeys { it !in dirtyFields },
                )
            }
            is Msg.Committed -> {
                val fields = if (msg.snapshot.fields.isEmpty()) {
                    ScreenContractRegistry.contracts.getValue(screen).fields
                } else {
                    msg.snapshot.fields
                }
                copy(
                    snapshot = msg.snapshot,
                    loading = false,
                    errorKey = null,
                    draftValues = fields.associate { it.id to it.value },
                    dirtyFields = emptySet(),
                    validationErrors = emptyMap(),
                )
            }
            is Msg.Selected -> copy(snapshot = snapshot.copy(selectedItemId = msg.id))
            is Msg.Busy -> copy(busy = msg.value)
            is Msg.Failed -> copy(loading = false, errorKey = msg.key)
            is Msg.Layout -> copy(layoutMode = msg.mode)
            is Msg.FieldEdited -> copy(
                draftValues = draftValues + (msg.fieldId to msg.value),
                dirtyFields = dirtyFields + msg.fieldId,
                validationErrors = validationErrors - msg.fieldId,
            )
            is Msg.ValidationFailed -> copy(validationErrors = msg.errors)
            Msg.DraftCancelled -> {
                val fields = if (snapshot.fields.isEmpty()) {
                    ScreenContractRegistry.contracts.getValue(screen).fields
                } else {
                    snapshot.fields
                }
                copy(
                    draftValues = fields.associate { it.id to it.value },
                    dirtyFields = emptySet(),
                    validationErrors = emptyMap(),
                )
            }
        }
    }
}

internal inline fun <T> Result<T>.unwrap(onSuccess: (T) -> Unit, onFailure: (Throwable) -> Unit) {
    fold(onSuccess = onSuccess, onFailure = {
        if (it is CancellationException) throw it
        onFailure(it)
    })
}
