package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenActionDescriptor
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.WorkspaceSetupState

internal fun Workspace.setupState(): WorkspaceSetupState = when {
    zones.isEmpty() || trays.isEmpty() -> WorkspaceSetupState.NOT_CONFIGURED
    plans.isEmpty() -> WorkspaceSetupState.NO_PLAN
    batches.isEmpty() -> WorkspaceSetupState.NO_BATCHES
    else -> WorkspaceSetupState.ALL_CLEAR
}

internal fun ScreenSnapshot.withSetupReadiness(screen: ScreenId, workspace: Workspace): ScreenSnapshot {
    if (screen != ScreenId.TODAY && screen != ScreenId.PLAN) return this
    val readiness = workspace.setupState()
    val setupAction = when {
        readiness == WorkspaceSetupState.NOT_CONFIGURED -> "resume-setup"
        readiness == WorkspaceSetupState.NO_PLAN && screen == ScreenId.TODAY -> "open-plan"
        else -> null
    }
    return if (setupAction != null) copy(
        setupState = readiness,
        primaryActionKey = TextKey("action_${setupAction.replace('-', '_')}"),
        fields = emptyList(),
        actions = listOf(ScreenActionDescriptor(ScreenActionId(setupAction), TextKey("action_${setupAction.replace('-', '_')}"))),
    ) else copy(
        setupState = readiness,
        primaryActionKey = if (screen == ScreenId.PLAN && readiness == WorkspaceSetupState.NO_PLAN) {
            TextKey("action_save_plan")
        } else primaryActionKey,
    )
}
