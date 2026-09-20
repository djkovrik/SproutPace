package com.sedsoftware.sproutpace.component.store

import com.arkivanov.mvikotlin.core.store.Store
import com.sedsoftware.sproutpace.component.LayoutMode
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey

internal interface ScreenStore : Store<ScreenStore.Intent, ScreenStore.State, ScreenStore.Label> {
    sealed interface Intent {
        data object Retry : Intent
        data class Select(val id: String) : Intent
        data class EditField(val fieldId: ScreenFieldId, val value: String) : Intent
        data class Submit(val actionId: ScreenActionId) : Intent
        data object CancelDraft : Intent
        data class Execute(val command: ScreenCommand) : Intent
        data class SetLayout(val mode: LayoutMode) : Intent
    }

    data class State(
        val screen: ScreenId,
        val snapshot: ScreenSnapshot = ScreenSnapshot(),
        val loading: Boolean = true,
        val busy: Boolean = false,
        val errorKey: TextKey? = null,
        val layoutMode: LayoutMode = LayoutMode.COMPACT,
        val stableId: String? = null,
        val draftValues: Map<ScreenFieldId, String> = emptyMap(),
        val dirtyFields: Set<ScreenFieldId> = emptySet(),
        val validationErrors: Map<ScreenFieldId, TextKey> = emptyMap(),
    )

    sealed interface Label {
        data class Completed(val command: ScreenCommand) : Label
        data class ConfirmationRequested(val command: ScreenCommand) : Label
    }
}
