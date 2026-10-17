package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenActionDescriptor
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement

internal fun productionActions(
    screen: ScreenId,
    stableId: String?,
    workspace: Workspace,
    actions: List<ScreenActionDescriptor>,
): List<ScreenActionDescriptor> = when (screen) {
    ScreenId.TODAY -> emptyList()
    ScreenId.PLAN -> {
        val plan = workspace.plans.firstOrNull { it.id.value == stableId } ?: workspace.plans.firstOrNull()
        val allowed = if (plan == null) setOf("save-plan") else setOf(
            "update-plan", "delete-plan", if (plan.paused) "resume-plan" else "pause-plan",
        )
        actions.filter { it.id.value in allowed }
    }
    ScreenId.BATCH_EDITOR -> {
        val commitAction = if (workspace.batches.any { it.id.value == stableId }) "correct-batch" else "save-batch"
        (actions.filter { it.id.value == commitAction } + listOf(
            ScreenActionDescriptor(ScreenActionId("open-zones"), TextKey("action_add_zone")),
            ScreenActionDescriptor(ScreenActionId("open-tray-sizes"), TextKey("action_add_preset")),
        )).distinctBy { it.id }
    }
    ScreenId.RECIPE -> {
        val allowed = when {
            SproutPaceCatalog.cultures.any { it.id.value == stableId } -> setOf("use-recipe", "duplicate-recipe")
            workspace.recipes.any { it.id.value == stableId } ->
                setOf("use-recipe", "update-recipe", "duplicate-recipe", "delete-recipe")
            else -> setOf("save-recipe")
        }
        actions.filter { it.id.value in allowed }
    }
    ScreenId.NOTIFICATIONS -> actions.filterNot { it.operation == "update-notifications" }
    ScreenId.BATCH_DETAIL -> {
        val allowed = workspace.batches.firstOrNull { it.id.value == stableId }?.let(::batchDetailActionIds).orEmpty()
        actions.filter { it.id.value in allowed }
    }
    else -> actions
}

private fun batchDetailActionIds(batch: GrowBatch): Set<String> {
    val configuredStates = batch.recipe.stages.map { stage ->
        when (stage.stage) {
            GrowStage.SOAKING -> BatchState.SOAKING
            GrowStage.SOWN -> BatchState.SOWN
            GrowStage.BLACKOUT_OR_WEIGHT -> BatchState.BLACKOUT_OR_WEIGHT
            GrowStage.LIGHT_GROWTH -> BatchState.LIGHT_GROWTH
            GrowStage.READY_FOR_HARVEST -> BatchState.READY_FOR_HARVEST
        }
    }
    val stateIndex = configuredStates.indexOf(batch.state)
    val hasNextStage = stateIndex >= 0 && stateIndex + 1 < configuredStates.size
    val canSkipStage = stateIndex >= 0 && stateIndex + 2 < configuredStates.size
    return buildSet {
        add("add-observation")
        add("delete-batch")
        if (!batch.state.isTerminal) add("edit-batch")
        when (batch.state) {
            BatchState.PLANNED -> {
                add("confirm-sowing")
                if (BatchState.SOAKING in configuredStates) add("start-soaking")
            }
            BatchState.SOAKING -> {
                add("confirm-sowing")
                add("schedule-soak-target")
            }
            BatchState.HARVESTED -> {
                add("record-actual-mass")
                add("repeat-batch")
            }
            BatchState.FAILED, BatchState.ABANDONED -> add("repeat-batch")
            else -> Unit
        }
        if (!batch.state.isTerminal) add("close-batch")
        if (!batch.state.isTerminal && batch.state != BatchState.SOAKING && hasNextStage) add("confirm-stage")
        if (!batch.state.isTerminal && canSkipStage) add("skip-stage")
        if (!batch.state.isTerminal && batch.events.isNotEmpty()) add("undo-stage")
        if (batch.state.occupiesPlace && batch.placement is Placement.Unassigned) add("assign-overflow")
    }
}
