package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenItemCommand
import com.sedsoftware.sproutpace.component.ScreenPhoto
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.CapacityEngine
import com.sedsoftware.sproutpace.domain.engine.Occupancy
import com.sedsoftware.sproutpace.domain.engine.PlannerEngine
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CapacityResolution
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

internal class ProductionScreenSnapshotRenderer(
    private val database: LocalDatabase,
    private val workspaceRepository: LocalWorkspaceRepository?,
    private val now: () -> Instant,
    private val today: () -> LocalDate,
) {
    suspend fun snapshot(
        screen: ScreenId,
        stableId: String?,
        workspace: Workspace,
        preferences: SettingsSnapshot,
    ): ScreenSnapshot = when (screen) {
        ScreenId.FIRST_RUN -> firstRunSnapshot(workspace)
        ScreenId.TODAY -> todaySnapshot(workspace)
        ScreenId.TRAYS -> traysSnapshot(workspace)
        ScreenId.BATCH_EDITOR -> batchEditorSnapshot(workspace, stableId)
        ScreenId.BATCH_DETAIL -> batchDetailSnapshot(workspace, stableId)
        ScreenId.PLAN -> planSnapshot(workspace, stableId)
        ScreenId.CATALOG -> catalogSnapshot(workspace)
        ScreenId.RECIPE -> recipeSnapshot(workspace, stableId)
        ScreenId.OBSERVATION_EDITOR -> observationSnapshot(workspace, stableId)
        ScreenId.CLOSE_BATCH -> closeBatchSnapshot(workspace, stableId)
        ScreenId.SETTINGS -> settingsSnapshot(preferences)
        ScreenId.ZONES -> zonesSnapshot(workspace, stableId)
        ScreenId.TRAY_SIZES -> traySizesSnapshot(workspace, stableId)
        ScreenId.BACKUP_RESTORE -> backupSnapshot()
        ScreenId.ADVERTISING_PRIVACY -> privacySnapshot(preferences)
        ScreenId.NOTIFICATIONS -> notificationsSnapshot(preferences)
    }

private fun firstRunSnapshot(workspace: Workspace): ScreenSnapshot {
    val configured = workspace.zones.any { it.isLightCapable && it.capacity > 0 } &&
        workspace.trays.isNotEmpty() && workspace.plans.isNotEmpty()
    return ScreenSnapshot(
        items = listOf(
            ScreenItem("catalog", titleKey = key("action_browse_catalog")),
            ScreenItem("setup", titleKey = key(if (configured) "first_run_preview_title" else "first_run_step")),
        ),
        primaryActionKey = key("action_continue"),
    )
}

private fun todaySnapshot(workspace: Workspace): ScreenSnapshot {
    val current = now()
    val pending = workspace.batches.flatMap { batch ->
        batch.actions.filter { it.status == ScheduledActionStatus.PENDING }.map { batch to it }
    }.sortedWith(compareBy<Pair<GrowBatch, ScheduledAction>>(
        { SchedulingEngine.resolvedInstant(it.second) },
        { it.second.id.value },
    ))
    val items = buildList {
        pending.firstOrNull()?.let { (batch, action) -> add(actionItem("next", batch, action)) }
        pending.filter { SchedulingEngine.resolvedInstant(it.second) < current }
            .forEach { (batch, action) -> add(actionItem("overdue", batch, action)) }
        pending.filter { SchedulingEngine.resolvedInstant(it.second) >= current }
            .forEach { (batch, action) -> add(actionItem("today", batch, action)) }
        continuousSuggestion(workspace)?.let { add(suggestionItem(it)) }
    }
    return ScreenSnapshot(items = items.distinctBy(ScreenItem::id), primaryActionKey = key("action_new_batch"))
}

private fun traysSnapshot(workspace: Workspace) = ScreenSnapshot(
    items = workspace.batches.sortedBy { it.id.value }.map(::batchItem),
    primaryActionKey = key("action_new_batch"),
)

private fun batchEditorSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val items = buildList {
        workspace.recipes.sortedBy { it.id.value }.forEach { add(recipeItem(it)) }
        SproutPaceCatalog.cultures.forEach { add(cultureItem(it.id.value)) }
        workspace.trays.sortedBy { it.id.value }.forEach { tray ->
            add(ScreenItem("tray:${tray.id.value}", authoredTitle = tray.name, supportingKey = key("field_tray_size")))
        }
        seedRecommendationItem(workspace, stableId)?.let(::add)
    }
    return ScreenSnapshot(items = items, selectedItemId = stableId, primaryActionKey = key("action_save_batch"))
}

private suspend fun batchDetailSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val batch = stableId?.let { id -> workspace.batches.firstOrNull { it.id.value == id } }
        ?: return ScreenSnapshot(primaryActionKey = key("action_new_batch"))
    val items = buildList {
        add(batchItem(batch))
        batch.events.forEach { event ->
            add(ScreenItem("event:${event.id.value}", titleKey = batchStateKey(event.to), supportingKey = key("stage_actual")))
        }
        batch.actions.sortedBy { SchedulingEngine.resolvedInstant(it) }.forEach { action ->
            add(actionItem("action", batch, action))
        }
        workspace.observations.filter { it.batchId == batch.id }.sortedBy { it.occurredAt }.forEach { observation ->
            add(observationItem(observation, "observation:${observation.id.value}"))
        }
    }
    return ScreenSnapshot(
        items = items,
        selectedItemId = batch.id.value,
        primaryActionKey = if (batch.state.isTerminal) null else key("action_confirm_stage"),
        conflictResolutionKeys = if (batch.placement is Placement.Unassigned) {
            listOf(key("choose_another_zone"), key("change_capacity"), key("leave_unassigned"))
        } else emptyList(),
    )
}

private fun planSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val occupancies = occupancies(workspace.batches)
    val suggestions = workspace.plans.flatMap { plan ->
        PlannerEngine.suggestions(plan, DEFAULT_SUGGESTION_COUNT) { date, _ ->
            capacityDecision(workspace.zones, occupancies, date)
        }
    }
    val conflicts = suggestions.flatMap { suggestion ->
        val status = suggestion.status
        if (status is com.sedsoftware.sproutpace.domain.model.SuggestionStatus.NeedsResolution) {
            status.conflict.resolutions
        } else emptySet()
    }.map(::resolutionKey).distinct()
    val items = workspace.plans.sortedBy { it.id.value }.map(::planItem) + suggestions.map(::suggestionItem)
    return ScreenSnapshot(
        items = items,
        selectedItemId = stableId,
        primaryActionKey = key(if (items.isEmpty()) "action_new_batch" else "action_confirm_suggestion"),
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
    return ScreenSnapshot(items = items, selectedItemId = stableId, primaryActionKey = key("action_use_recipe"), offline = true)
}

private suspend fun observationSnapshot(workspace: Workspace, stableId: String?): ScreenSnapshot {
    val selected = stableId?.let { id -> workspace.observations.firstOrNull { it.id.value == id } }
    val batchId = selected?.batchId?.value ?: stableId?.takeIf { id -> workspace.batches.any { it.id.value == id } }
    return ScreenSnapshot(
        items = workspace.observations.filter { batchId == null || it.batchId.value == batchId }
            .sortedWith(compareBy<Observation> { it.occurredAt }.thenBy { it.id.value })
            .map { observationItem(it, it.id.value) },
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

private fun zonesSnapshot(workspace: Workspace, stableId: String?) = ScreenSnapshot(
    items = workspace.zones.sortedBy { it.id.value }.map { zone ->
        val used = workspace.batches.count { it.state.occupiesPlace && (it.placement as? Placement.Assigned)?.zoneId == zone.id }
        ScreenItem(
            zone.id.value,
            authoredTitle = zone.name,
            supportingKey = key(if (zone.isLightCapable) "zone_light_capacity" else "zone_dark_capacity"),
            stateKey = if (used > zone.capacity) key("capacity_conflict_title") else null,
        )
    },
    selectedItemId = stableId,
    primaryActionKey = key("action_add_zone"),
    conflictResolutionKeys = workspace.zones.filter { zone ->
        workspace.batches.count { it.state.occupiesPlace && (it.placement as? Placement.Assigned)?.zoneId == zone.id } > zone.capacity
    }.flatMap { listOf(key("choose_another_zone"), key("change_capacity"), key("leave_unassigned")) }.distinct(),
)

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
    primaryActionKey = key("action_request_permission"),
)


private suspend fun observationItem(observation: Observation, itemId: String): ScreenItem {
    val photoId = observation.photoId
    val photo = photoId?.let { id ->
        runCatching { workspaceRepository?.readPhoto(id) }.getOrNull()
            ?.takeIf(ByteArray::isNotEmpty)
            ?.let { bytes -> ScreenPhoto(id.value, bytes, key("observation_photo_content_description")) }
    }
    return ScreenItem(
        id = itemId,
        authoredTitle = observation.text.ifBlank { observation.occurredAt.toString() },
        stateKey = key("observation_photo_unavailable").takeIf { photoId != null && photo == null },
        photo = photo,
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

private fun continuousSuggestion(workspace: Workspace) = workspace.plans.firstNotNullOfOrNull { plan ->
    PlannerEngine.nextContinuousSuggestion(plan, today()) { date, _ ->
        capacityDecision(workspace.zones, occupancies(workspace.batches), date)
    }
}

private fun capacityDecision(zones: List<GrowZone>, occupancies: List<Occupancy>, date: LocalDate): CapacityDecision {
    val zone = zones.firstOrNull { it.isLightCapable } ?: return CapacityDecision.NeedsResolution(
        com.sedsoftware.sproutpace.domain.model.CapacityConflict(
            null, date, 1, 0,
            setOf(CapacityResolution.CHOOSE_ANOTHER_DATE, CapacityResolution.CHOOSE_ANOTHER_ZONE, CapacityResolution.CHANGE_CAPACITY),
        ),
    )
    return CapacityEngine.validate(zone, occupancies, date = date)
}

private fun occupancies(batches: List<GrowBatch>) = batches.filter { it.state.occupiesPlace }.mapNotNull { batch ->
    (batch.placement as? Placement.Assigned)?.let { Occupancy(it.zoneId, batch.id) }
}

private fun actionItem(prefix: String, batch: GrowBatch, action: ScheduledAction) = ScreenItem(
    "$prefix:${action.id.value}", titleKey = action.stage?.let(::stageKey) ?: key("today_next_action"),
    supportingKey = batch.cultureIdSnapshot?.let { key("culture_${it.value}_name") },
    stateKey = actionStatusKey(action.status),
    commands = if (action.status == ScheduledActionStatus.PENDING) {
        listOf(
            ScreenItemCommand("complete-action", action.id.value),
            ScreenItemCommand("skip-action", action.id.value),
        )
    } else {
        emptyList()
    },
)

private fun batchItem(batch: GrowBatch) = ScreenItem(
    batch.id.value,
    titleKey = batch.cultureIdSnapshot?.let { key("culture_${it.value}_name") },
    authoredTitle = batch.recipe.nameSnapshot ?: if (batch.cultureIdSnapshot == null) batch.id.value else null,
    stateKey = batchStateKey(batch.state),
    actionId = "open-batch",
)

private fun recipeItem(recipe: UserGrowRecipe) = ScreenItem(recipe.id.value, authoredTitle = recipe.name, actionId = "open-recipe")
private fun cultureItem(id: String) = ScreenItem(id, titleKey = key("culture_${id}_name"), actionId = "open-recipe")
private fun planItem(plan: HarvestPlan) = ScreenItem(
    plan.id.value,
    authoredTitle = plan.id.value,
    stateKey = key(if (plan.paused) "plan_paused" else "plan_active"),
)
private fun suggestionItem(suggestion: com.sedsoftware.sproutpace.domain.model.BatchSuggestion) = ScreenItem(
    suggestion.id,
    titleKey = key("culture_${suggestion.cultureId.value}_name"),
    supportingKey = key("plan_suggested_broccoli"),
    actionId = "confirm-suggestion",
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
        const val DEFAULT_SUGGESTION_COUNT = 3
        val PRIVACY_POLICY_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}
