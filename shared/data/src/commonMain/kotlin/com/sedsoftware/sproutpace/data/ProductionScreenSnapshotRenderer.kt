package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenItemCommand
import com.sedsoftware.sproutpace.component.ScreenPhoto
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.effectiveActions
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CapacityResolution
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.sedsoftware.sproutpace.component.TaskProjection
import com.sedsoftware.sproutpace.component.TaskDayGroup
import com.sedsoftware.sproutpace.component.PlanEntryProjection
import com.sedsoftware.sproutpace.component.PlanEntryStatus

internal class ProductionScreenSnapshotRenderer(
    private val now: () -> Instant,
    private val today: () -> LocalDate,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun snapshot(
        screen: ScreenId,
        stableId: String?,
        workspace: Workspace,
        preferences: SettingsSnapshot,
        photos: Map<PhotoId, ByteArray> = emptyMap(),
    ): ScreenSnapshot = when (screen) {
        ScreenId.FIRST_RUN -> firstRunSnapshot()
        ScreenId.TODAY -> todaySnapshot(workspace)
        ScreenId.TRAYS -> traysSnapshot(workspace)
        ScreenId.BATCH_EDITOR -> batchEditorSnapshot(workspace, stableId)
        ScreenId.BATCH_DETAIL -> batchDetailSnapshot(workspace, stableId, photos)
        ScreenId.PLAN -> planSnapshot(workspace, stableId)
        ScreenId.CATALOG -> catalogSnapshot(workspace)
        ScreenId.RECIPE -> recipeSnapshot(workspace, stableId)
        ScreenId.OBSERVATION_EDITOR -> observationSnapshot(workspace, stableId, photos)
        ScreenId.CLOSE_BATCH -> closeBatchSnapshot(workspace, stableId)
        ScreenId.SETTINGS -> settingsSnapshot(preferences)
        ScreenId.ZONES -> zonesSnapshot(workspace, stableId)
        ScreenId.TRAY_SIZES -> traySizesSnapshot(workspace, stableId)
        ScreenId.BACKUP_RESTORE -> backupSnapshot()
        ScreenId.ADVERTISING_PRIVACY -> privacySnapshot(preferences)
        ScreenId.NOTIFICATIONS -> notificationsSnapshot(preferences)
    }

private fun firstRunSnapshot(): ScreenSnapshot {
    return ScreenSnapshot(
        primaryActionKey = key("action_continue"),
    )
}

private fun todaySnapshot(workspace: Workspace): ScreenSnapshot {
    val pending = workspace.batches.flatMap { batch ->
        batch.effectiveActions().filter { it.status == ScheduledActionStatus.PENDING }.map { batch to it }
    }.sortedWith(compareBy<Pair<GrowBatch, ScheduledAction>>(
        { SchedulingEngine.resolvedInstant(it.second) },
        { it.second.stage?.ordinal ?: Int.MAX_VALUE },
        { it.second.id.value },
    ))
    val items = buildList {
        pending.distinctBy { it.second.id }.forEach { (batch, action) -> add(actionItem("action", batch, action)) }
        continuousSuggestion(workspace)?.let { add(forecastItem(it, workspace)) }
    }
    return ScreenSnapshot(items = items, primaryActionKey = key("action_new_batch"))
}

private fun traysSnapshot(workspace: Workspace) = ScreenSnapshot(
    items = workspace.batches.sortedBy { it.id.value }.map(::batchItem),
    primaryActionKey = key("action_new_batch"),
)

private fun batchEditorSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val forecast = workspace.planningForecast(today(), timeZone()).firstOrNull { it.suggestion.id == stableId }
    val suggestion = forecast?.suggestion
    val conflict = (suggestion?.status as? com.sedsoftware.sproutpace.domain.model.SuggestionStatus.NeedsResolution)?.conflict
    val items = buildList {
        workspace.recipes.sortedBy { it.id.value }.forEach { add(recipeItem(it)) }
        SproutPaceCatalog.cultures.forEach { add(cultureItem(it.id.value)) }
        workspace.trays.sortedBy { it.id.value }.forEach { tray ->
            add(ScreenItem("tray:${tray.id.value}", authoredTitle = tray.name, supportingKey = key("field_tray_size")))
        }
        seedRecommendationItem(workspace, stableId)?.let(::add)
        forecast?.let { add(forecastItem(it, workspace)) }
    }
    return ScreenSnapshot(items = items, selectedItemId = stableId, primaryActionKey = key("action_save_batch"),
        conflictResolutionKeys = conflict?.resolutions?.map(::resolutionKey).orEmpty())
}

private fun batchDetailSnapshot(workspace: Workspace, stableId: String?, photos: Map<PhotoId, ByteArray>): ScreenSnapshot {
    val batch = stableId?.let { id -> workspace.batches.firstOrNull { it.id.value == id } }
        ?: return ScreenSnapshot(primaryActionKey = key("action_new_batch"))
    val items = buildList {
        val zoneId = (batch.placement as? Placement.Assigned)?.zoneId
        add(batchItem(batch).copy(occupancy = workspace.zones.firstOrNull { it.id == zoneId }?.occupancy(workspace.batches)))
        batch.events.forEach { event ->
            add(ScreenItem("event:${event.id.value}", titleKey = batchStateKey(event.to), supportingKey = key("stage_actual"),
                history = com.sedsoftware.sproutpace.component.BatchHistoryProjection(
                    event.occurredAt, event.kind == com.sedsoftware.sproutpace.domain.model.StageEventKind.SKIPPED,
                    event.reason, event.priorOccurredAt,
                )))
        }
        batch.outcome?.let { outcome ->
            val reason = when (outcome) {
                is com.sedsoftware.sproutpace.domain.model.BatchOutcome.Failed -> outcome.reason
                is com.sedsoftware.sproutpace.domain.model.BatchOutcome.Abandoned -> outcome.reason
                is com.sedsoftware.sproutpace.domain.model.BatchOutcome.Harvested -> null
            }
            add(ScreenItem("event:outcome", titleKey = batchStateKey(batch.state),
                history = com.sedsoftware.sproutpace.component.BatchHistoryProjection(
                    outcome.occurredAt, reason = listOfNotNull(reason, outcome.note).joinToString("\n").ifBlank { null },
                )))
        }
        batch.effectiveActions().sortedBy { SchedulingEngine.resolvedInstant(it) }.forEach { action ->
            add(actionItem("action", batch, action))
        }
        workspace.observations.filter { it.batchId == batch.id }.sortedBy { it.occurredAt }.forEach { observation ->
            add(observationItem(observation, "observation:${observation.id.value}", photos))
        }
        batch.recipe.stages.forEach { stage ->
            add(ScreenItem("snapshot:${stage.stage.name}", titleKey = stageKey(stage.stage),
                snapshotStage = com.sedsoftware.sproutpace.component.RecipeStageProjection(
                    stageKey(stage.stage), stage.duration.minimum.inWholeHours, stage.duration.maximum.inWholeHours,
                    stage.guidanceKey?.let { key(it.value) }, stage.userGuidance,
                )))
        }
    }
    return ScreenSnapshot(
        items = items,
        selectedItemId = batch.id.value,
        primaryActionKey = batchDetailPrimaryAction(batch),
        conflictResolutionKeys = if (batch.placement is Placement.Unassigned) {
            listOf(key("choose_another_zone"), key("change_capacity"), key("leave_unassigned"))
        } else emptyList(),
    )
}

private fun batchDetailPrimaryAction(batch: GrowBatch): TextKey? = when (batch.state) {
    BatchState.PLANNED -> if (batch.recipe.stages.any { it.stage == GrowStage.SOAKING }) {
        key("action_start_soaking")
    } else {
        key("action_confirm_sowing")
    }
    BatchState.SOAKING -> key("action_confirm_sowing")
    BatchState.SOWN, BatchState.BLACKOUT_OR_WEIGHT, BatchState.LIGHT_GROWTH -> key("action_confirm_stage")
    BatchState.READY_FOR_HARVEST -> key("action_close_batch")
    BatchState.HARVESTED, BatchState.FAILED, BatchState.ABANDONED -> null
}

private fun planSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val report = workspace.workspaceForecast(today(), timeZone())
    val forecast = report.suggestions
    val conflicts = report.allocations.flatMap {
        (it.decision as? com.sedsoftware.sproutpace.domain.model.CapacityDecision.NeedsResolution)
            ?.conflict?.resolutions.orEmpty()
    }.map(::resolutionKey).distinct()
    val confirmed = workspace.batches.filterNot { it.state.isTerminal }.map { batch ->
        batchItem(batch).copy(planEntry = PlanEntryProjection(
            batch.cultureIdSnapshot?.value,
            batch.plannedSowDate,
            batch.actions.firstOrNull { it.stage == GrowStage.READY_FOR_HARVEST }
                ?.let { SchedulingEngine.resolvedInstant(it).toLocalDateTime(timeZone()).date },
            if (report.allocations.any { it.id == "batch:${batch.id.value}" &&
                it.decision is com.sedsoftware.sproutpace.domain.model.CapacityDecision.NeedsResolution }) {
                PlanEntryStatus.NEEDS_RESOLUTION
            } else PlanEntryStatus.CONFIRMED,
            workspace.zones.firstOrNull { it.id == (batch.placement as? Placement.Assigned)?.zoneId }
                ?.let { it.occupancy(workspace.batches).copy(futureConflict = report.zoneConflict(it)) },
        ))
    }
    val items = (confirmed + forecast.map { forecastItem(it, workspace) }).sortedWith(compareBy({ it.planEntry?.sowDate }, { it.id }))
    return ScreenSnapshot(
        items = items,
        selectedItemId = stableId ?: workspace.plans.firstOrNull()?.id?.value,
        plans = workspace.plans.map {
            com.sedsoftware.sproutpace.component.PlanSummaryProjection(
                it.id.value, it.cadence.value, it.cultureRotation.map { culture -> culture.value }, it.paused,
            )
        },
        primaryActionKey = key(if (workspace.plans.isEmpty()) "action_save_plan" else "action_new_batch"),
        conflictResolutionKeys = conflicts,
    )
}

private fun catalogSnapshot(workspace: Workspace) = ScreenSnapshot(
    items = SproutPaceCatalog.cultures.map { cultureItem(it.id.value) } +
        workspace.recipes.sortedBy { it.id.value }.map(::recipeItem),
    offline = true,
)

private fun recipeSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val builtIn = stableId?.let { id -> SproutPaceCatalog.cultures.firstOrNull { it.id.value == id } }
    val personal = stableId?.let { id -> workspace.recipes.firstOrNull { it.id.value == id } }
    val items = when {
        builtIn != null -> buildList {
            add(cultureItem(builtIn.id.value))
            builtIn.stages.forEach {
                add(
                    ScreenItem(
                        "stage:${it.stage.name.lowercase()}",
                        titleKey = stageKey(it.stage),
                        supportingKey = TextKey(it.instructionKey.value),
                    ),
                )
            }
            builtIn.safetyNoteKeys.forEachIndexed { index, value -> add(ScreenItem("safety:$index", titleKey = TextKey(value.value))) }
            builtIn.sources.forEachIndexed { index, value ->
                add(ScreenItem("source:$index", titleKey = TextKey(value.titleKey.value)))
            }
        }
        personal != null -> listOf(recipeItem(personal)) + personal.stages.map {
            ScreenItem(
                "stage:${it.stage.name.lowercase()}",
                titleKey = stageKey(it.stage),
                supportingKey = it.guidanceKey?.let { value -> TextKey(value.value) },
                authoredTitle = it.userGuidance,
            )
        }
        else -> emptyList()
    }
    return ScreenSnapshot(
        items = items,
        selectedItemId = stableId,
        primaryActionKey = key(if (builtIn != null || personal != null) "action_use_recipe" else "action_save_recipe"),
        offline = true,
    )
}

private fun observationSnapshot(workspace: Workspace, stableId: String?, photos: Map<PhotoId, ByteArray>): ScreenSnapshot {
    val selected = stableId?.let { id -> workspace.observations.firstOrNull { it.id.value == id } }
    val batchId = selected?.batchId?.value ?: stableId?.takeIf { id -> workspace.batches.any { it.id.value == id } }
    return ScreenSnapshot(
        items = workspace.observations.filter { batchId == null || it.batchId.value == batchId }
            .sortedWith(compareBy<Observation> { it.occurredAt }.thenBy { it.id.value })
            .map { observationItem(it, it.id.value, photos) },
        selectedItemId = selected?.id?.value,
        primaryActionKey = key("action_save_observation"),
    )
}

private fun closeBatchSnapshot(workspace: Workspace, stableId: String?) = ScreenSnapshot(
    items = workspace.batches.filter { stableId == null || it.id.value == stableId }.map(::batchItem),
    selectedItemId = stableId,
    primaryActionKey = key("action_save_outcome"),
)

private fun settingsSnapshot(preferences: SettingsSnapshot) = ScreenSnapshot(items = buildList {
    add(ScreenItem("theme", titleKey = key("settings_theme"), stateKey = key("settings_theme_${preferences.theme.name.lowercase()}")))
    add(ScreenItem("crash-reporting", titleKey = key("settings_crash_reporting"), stateKey = booleanKey(preferences.crashReporting)))
    add(
        ScreenItem(
            "notifications",
            titleKey = key("screen_notifications_title"),
            stateKey = booleanKey(preferences.dailySummaryEnabled || preferences.soakRemindersEnabled),
        ),
    )
    add(ScreenItem("backup", titleKey = key("screen_backup_restore_title")))
    if (preferences.privacy.isFreshApplicable(now().toEpochMilliseconds())) {
        add(
            ScreenItem(
                "privacy",
                titleKey = key("screen_advertising_privacy_title"),
                actionId = "open-advertising-privacy",
            ),
        )
    }
})

private fun AdvertisingPrivacyState?.isFreshApplicable(nowEpochMillis: Long): Boolean {
    val value = this
    val lifetime = value?.let { it.expiresAtEpochMillis - it.fetchedAtEpochMillis }
    return value != null &&
        value.consentRequired &&
        value.payloadVersion == ADVERTISING_PRIVACY_PAYLOAD_VERSION &&
        value.endpoint == ADVERTISING_PRIVACY_ENDPOINT &&
        value.schemaVersion == ADVERTISING_PRIVACY_SCHEMA_VERSION &&
        PRIVACY_POLICY_VERSION.matches(value.policyVersion) &&
        value.fetchedAtEpochMillis in 0..nowEpochMillis &&
        value.expiresAtEpochMillis > nowEpochMillis &&
        lifetime != null &&
        lifetime in 1..ADVERTISING_PRIVACY_MAX_AGE_MILLIS
}

private fun zonesSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val report = workspace.workspaceForecast(today(), timeZone())
    return ScreenSnapshot(
    items = workspace.zones.sortedBy { it.id.value }.map { zone ->
        val used = workspace.batches.count { it.state.occupiesPlace && (it.placement as? Placement.Assigned)?.zoneId == zone.id }
        ScreenItem(
            zone.id.value,
            authoredTitle = zone.name,
            occupancy = zone.occupancy(workspace.batches).copy(
                futureConflict = report.zoneConflict(zone),
            ),
            stateKey = if (used > zone.capacity) key("capacity_conflict_title") else null,
        )
    },
    selectedItemId = stableId,
    primaryActionKey = key("action_add_zone"),
    conflictResolutionKeys = workspace.zones.filter { zone ->
        workspace.batches.count { it.state.occupiesPlace && (it.placement as? Placement.Assigned)?.zoneId == zone.id } > zone.capacity
    }.flatMap { listOf(key("choose_another_zone"), key("change_capacity"), key("leave_unassigned")) }.distinct(),
)

}

private fun traySizesSnapshot(workspace: Workspace, stableId: String?) = ScreenSnapshot(
    items = workspace.trays.sortedBy { it.id.value }.map {
        ScreenItem(it.id.value, authoredTitle = it.name, supportingKey = key("field_tray_size"))
    },
    selectedItemId = stableId,
    primaryActionKey = key("action_add_preset"),
)

private fun backupSnapshot() = ScreenSnapshot(items = listOf(
    ScreenItem("create", titleKey = key("action_create_backup"), actionId = "create-backup"),
    ScreenItem("preview", titleKey = key("action_preview_restore"), actionId = "preview-restore"),
    ScreenItem("restore", titleKey = key("action_restore_backup"), actionId = "restore-backup"),
    ScreenItem("csv", titleKey = key("action_export_csv"), actionId = "export-csv"),
    ScreenItem("erase", titleKey = key("action_delete_all_data"), actionId = "erase-all"),
))

private fun privacySnapshot(preferences: SettingsSnapshot) = ScreenSnapshot(
    items = listOf(
        ScreenItem(
            "privacy",
            titleKey = key("privacy_heading"),
            stateKey = key("privacy_${preferences.privacy?.choice?.name?.lowercase() ?: "undecided"}"),
        ),
    ),
    primaryActionKey = key("action_accept"),
)

private fun notificationsSnapshot(preferences: SettingsSnapshot) = ScreenSnapshot(
    items = listOf(
        ScreenItem(
            "daily-summary",
            titleKey = key("notifications_daily_summary"),
            stateKey = booleanKey(preferences.dailySummaryEnabled),
        ),
        ScreenItem("summary-time", authoredTitle = preferences.summaryTime.toString(), titleKey = key("notifications_time")),
        ScreenItem(
            "soak-reminder",
            titleKey = key("notifications_soak_reminder"),
            stateKey = booleanKey(preferences.soakRemindersEnabled),
        ),
    ),
    permissionState = if (preferences.dailySummaryEnabled || preferences.soakRemindersEnabled) {
        PermissionState.NOT_REQUESTED
    } else {
        PermissionState.NOT_REQUIRED
    },
)


private fun observationItem(observation: Observation, itemId: String, photos: Map<PhotoId, ByteArray>): ScreenItem {
    val photoId = observation.photoId
    val photo = photoId?.let { id ->
        photos[id]
            ?.takeIf(ByteArray::isNotEmpty)
            ?.let { bytes -> ScreenPhoto(id.value, bytes, key("observation_photo_content_description")) }
    }
    return ScreenItem(
        id = itemId,
        authoredTitle = observation.text.takeIf { it.isNotBlank() },
        titleKey = key("screen_observation_editor_title").takeIf { observation.text.isBlank() },
        stateKey = key("observation_photo_unavailable").takeIf { photoId != null && photo == null },
        photo = photo,
        history = com.sedsoftware.sproutpace.component.BatchHistoryProjection(observation.occurredAt),
    )
}


private fun seedRecommendationItem(workspace: Workspace, stableId: String?): ScreenItem? {
    val batch = stableId?.let { id -> workspace.batches.firstOrNull { it.id.value == id } }
    return batch?.let {
        ScreenItem(
            "seed-grams",
            authoredTitle = it.recommendedSeedMass.value.toString(),
            supportingKey = key("field_recipe"),
        )
    }
}

private fun continuousSuggestion(workspace: Workspace) = workspace.planningForecast(today(), timeZone()).firstOrNull { entry ->
    workspace.plans.any { it.id == entry.suggestion.planId &&
        it.mode == com.sedsoftware.sproutpace.domain.model.HarvestPlanMode.CONTINUOUS_QUEUE }
}

private fun forecastItem(forecast: PlannedSuggestion, workspace: Workspace): ScreenItem = suggestionItem(forecast.suggestion).copy(
    planEntry = suggestionProjection(forecast.suggestion).copy(
        zone = workspace.zones.firstOrNull { it.id == forecast.route.firstOrNull()?.zoneId }?.occupancy(workspace.batches),
    ),
)

private fun actionItem(prefix: String, batch: GrowBatch, action: ScheduledAction) = ScreenItem(
    "$prefix:${action.id.value}", titleKey = key(when (action.stage) {
        GrowStage.SOAKING -> "task_action_soak"
        GrowStage.SOWN -> "task_action_sow"
        GrowStage.BLACKOUT_OR_WEIGHT -> "task_action_cover"
        GrowStage.LIGHT_GROWTH -> "task_action_light"
        GrowStage.READY_FOR_HARVEST -> "task_action_harvest"
        null -> "today_next_action"
    }),
    supportingKey = batch.cultureIdSnapshot?.let { key("culture_${it.value}_name") },
    stateKey = actionStatusKey(action.status),
    batch = batch.identity(),
    task = TaskProjection(
        action.id.value, SchedulingEngine.resolvedInstant(action),
        when {
            SchedulingEngine.resolvedInstant(action) < now() -> TaskDayGroup.OVERDUE
            SchedulingEngine.resolvedInstant(action).toLocalDateTime(timeZone()).date == today() -> TaskDayGroup.TODAY
            else -> TaskDayGroup.UPCOMING
        },
        action.userFixed,
        pending = action.status == ScheduledActionStatus.PENDING,
    ),
    commands = when {
        action.status != ScheduledActionStatus.PENDING -> emptyList()
        action.stage == GrowStage.READY_FOR_HARVEST -> listOf(
            ScreenItemCommand("close-batch", batch.id.value), ScreenItemCommand("open-batch", batch.id.value),
        )
        action.stage != null -> listOf(ScreenItemCommand("open-batch", batch.id.value))
        else -> listOf(
            ScreenItemCommand("complete-action", action.id.value),
            ScreenItemCommand("skip-action", action.id.value),
        )
    },
)

private fun batchItem(batch: GrowBatch) = ScreenItem(
    batch.id.value,
    titleKey = batch.cultureIdSnapshot?.let { key("culture_${it.value}_name") },
    authoredTitle = batch.recipe.nameSnapshot ?: if (batch.cultureIdSnapshot == null) batch.id.value else null,
    stateKey = batchStateKey(batch.state),
    supportingKey = if (batch.placement is Placement.Unassigned) key("state_temporarily_unassigned_title") else null,
    actionId = "open-batch",
    batch = batch.identity(),
)

private fun recipeItem(recipe: UserGrowRecipe) = ScreenItem(
    recipe.id.value, authoredTitle = recipe.name, actionId = "open-recipe", recipe = recipe.guideProjection(),
)
private fun cultureItem(id: String) = ScreenItem(
    id, titleKey = key("culture_${id}_name"), actionId = "open-recipe",
    recipe = SproutPaceCatalog.cultures.first { it.id.value == id }.guideProjection(),
)
private fun suggestionItem(suggestion: com.sedsoftware.sproutpace.domain.model.BatchSuggestion) = ScreenItem(
    suggestion.id,
    titleKey = key("culture_${suggestion.cultureId.value}_name"),
    planEntry = suggestionProjection(suggestion),
    actionId = "confirm-suggestion",
)

private fun suggestionProjection(suggestion: com.sedsoftware.sproutpace.domain.model.BatchSuggestion) = PlanEntryProjection(
    cultureId = suggestion.cultureId.value,
    sowDate = suggestion.sowDate(timeZone().id),
    harvestDate = suggestion.targetHarvestDate,
    status = if (suggestion.status is com.sedsoftware.sproutpace.domain.model.SuggestionStatus.NeedsResolution) {
        PlanEntryStatus.NEEDS_RESOLUTION
    } else PlanEntryStatus.SUGGESTED,
    zone = null,
    planId = suggestion.planId.value,
)

private fun batchStateKey(state: BatchState) = when (state) {
    BatchState.PLANNED -> key("trays_planned")
    BatchState.SOAKING -> key("stage_soaking")
    BatchState.SOWN -> key("stage_sown")
    BatchState.BLACKOUT_OR_WEIGHT -> key("stage_blackout")
    BatchState.LIGHT_GROWTH -> key("stage_light_growth")
    BatchState.READY_FOR_HARVEST -> key("stage_harvest")
    BatchState.HARVESTED -> key("outcome_harvested")
    BatchState.FAILED -> key("outcome_failed")
    BatchState.ABANDONED -> key("outcome_abandoned")
}

private fun stageKey(stage: GrowStage) = when (stage) {
    GrowStage.SOAKING -> key("stage_soaking")
    GrowStage.SOWN -> key("stage_sown")
    GrowStage.BLACKOUT_OR_WEIGHT -> key("stage_blackout")
    GrowStage.LIGHT_GROWTH -> key("stage_light_growth")
    GrowStage.READY_FOR_HARVEST -> key("stage_harvest")
}

private fun actionStatusKey(status: ScheduledActionStatus) = key("action_status_${status.name.lowercase()}")
private fun resolutionKey(value: CapacityResolution) = key(value.name.lowercase())
private fun booleanKey(value: Boolean) = key(if (value) "state_enabled" else "state_disabled")
private fun key(value: String) = TextKey(value)


    private companion object {
        val PRIVACY_POLICY_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}
