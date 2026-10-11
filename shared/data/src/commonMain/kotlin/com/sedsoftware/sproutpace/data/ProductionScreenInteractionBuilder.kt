package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.BatchRepeatDraft
import com.sedsoftware.sproutpace.component.ScreenActionDescriptor
import com.sedsoftware.sproutpace.component.ScreenFact
import com.sedsoftware.sproutpace.component.ScreenField
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenOption
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal class ProductionScreenInteractionBuilder(
    private val now: () -> Instant,
    private val today: () -> LocalDate,
    private val backupDocumentId: () -> String?,
    private val backupPreview: () -> BackupPreview.Valid?,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun apply(
        base: ScreenSnapshot,
        screen: ScreenId,
        stableId: String?,
        workspace: Workspace,
        preferences: SettingsSnapshot,
        draftIdentities: DraftIdentitySession = DraftIdentitySession(),
    ): ScreenSnapshot = base.withInteractionContract(screen, stableId, workspace, preferences, draftIdentities)

private fun ScreenSnapshot.withInteractionContract(
    screen: ScreenId,
    stableId: String?,
    workspace: Workspace,
    preferences: SettingsSnapshot,
    draftIdentities: DraftIdentitySession,
): ScreenSnapshot {
    val existingEditorRecord = workspace.batches.any { it.id.value == stableId } ||
        workspace.recipes.any { it.id.value == stableId }
    val suggestionRoute = BatchRepeatDraft.sourceId(stableId) == null && stableId?.contains(':') == true
    if (screen == ScreenId.BATCH_EDITOR && suggestionRoute && !existingEditorRecord &&
        workspace.suggestion(stableId, today(), timeZone()) == null) {
        return copy(primaryActionKey = null, fields = emptyList(), actions = emptyList(), hasInteractionContract = true,
            facts = listOf(ScreenFact("suggestion-unavailable", key("suggestion_unavailable_title"),
                valueKey = key("suggestion_unavailable_body"))))
    }
    val contract = ScreenContractRegistry.contracts.getValue(screen)
    val values = draftIdentities.apply(screen, fieldValues(screen, stableId, workspace, preferences), workspace)
    return copy(
        hasInteractionContract = true,
        fields = contract.fields.filterNot { screen == ScreenId.TODAY }.map { field ->
            val options = if (
                field.kind == com.sedsoftware.sproutpace.component.ScreenFieldKind.SINGLE_CHOICE ||
                field.kind == com.sedsoftware.sproutpace.component.ScreenFieldKind.MULTI_CHOICE
            ) {
                dynamicOptions(field, workspace).ifEmpty { field.options }
            } else {
                field.options
            }
            field.copy(
                value = values[field.id].orEmpty(),
                options = repeatSnapshotOptions(screen, stableId, field.id, workspace) + options,
            )
        },
        facts = screenFacts(screen, stableId, workspace, preferences)
            .distinctBy(ScreenFact::id),
        actions = productionActions(screen, stableId, workspace, contract.actions),
    ).withSetupReadiness(screen, workspace)
}


private fun fieldValues(
    screen: ScreenId,
    stableId: String?,
    workspace: Workspace,
    preferences: SettingsSnapshot,
): Map<ScreenFieldId, String> {
    val selected = selection(stableId, workspace)
    val action = stableId?.let { id ->
        workspace.batches.flatMap(GrowBatch::actions).firstOrNull { it.id.value == id }
    } ?: workspace.batches.flatMap(GrowBatch::actions)
        .firstOrNull { it.status == ScheduledActionStatus.PENDING }
    val values = if (screen in PRIMARY_SCREENS) {
        primaryFieldValues(screen, stableId, workspace, preferences, selected, action?.due)
    } else {
        secondaryFieldValues(screen, stableId, workspace, preferences, selected)
    }
    return values.mapKeys { ScreenFieldId(it.key) }
}

private fun primaryFieldValues(
    screen: ScreenId,
    stableId: String?,
    workspace: Workspace,
    preferences: SettingsSnapshot,
    selected: ScreenSelection,
    due: DueMoment?,
): Map<String, String> = when (screen) {
        ScreenId.FIRST_RUN -> mapOf(
            "zoneName" to "", "type" to "grow_light", "capacity" to "1",
            "trayName" to "", "shape" to "rectangle",
            "widthCm" to "25.4", "lengthCm" to "50.8", "cadenceDays" to "7",
            "cultureIds" to SproutPaceCatalog.cultures.first().id.value, "mode" to "continuous_queue",
        )
        ScreenId.TODAY -> todayFieldValues(due)
        ScreenId.TRAYS -> mapOf(
            "category" to "planned",
        )
        ScreenId.BATCH_EDITOR -> batchEditorFieldValues(stableId, selected.batch, workspace)
        ScreenId.BATCH_DETAIL -> batchDetailFieldValues(stableId, selected.batch, workspace)
        ScreenId.PLAN -> buildMap {
            put("id", selected.plan?.id?.value ?: "")
            put("anchorDate", (selected.plan?.anchorDate ?: today()).toString())
            put("cadenceDays", (selected.plan?.cadence?.value ?: DEFAULT_CADENCE_DAYS).toString())
            put("cultureIds", (selected.plan?.cultureRotation?.joinToString(",") { it.value }
                ?: SproutPaceCatalog.cultures.first().id.value))
            put("mode", (selected.plan?.mode ?: HarvestPlanMode.CADENCE).name.lowercase())
            put("paused", (selected.plan?.paused ?: false).toString())
        }
        ScreenId.CATALOG -> mapOf("query" to "")
        ScreenId.RECIPE -> recipeFieldValues(stableId, selected.recipe)
        else -> secondaryFieldValues(screen, stableId, workspace, preferences, selected)
    }

private fun secondaryFieldValues(
    screen: ScreenId,
    stableId: String?,
    workspace: Workspace,
    preferences: SettingsSnapshot,
    selected: ScreenSelection,
): Map<String, String> = when (screen) {
        ScreenId.OBSERVATION_EDITOR -> observationFieldValues(stableId, selected.observation, workspace)
        ScreenId.CLOSE_BATCH -> closeBatchFieldValues(selected.batch)
        ScreenId.SETTINGS -> mapOf(
            "theme" to preferences.theme.name.lowercase(),
            "enabled" to preferences.crashReporting.toString(),
        )
        ScreenId.ZONES -> mapOf(
            "id" to (selected.zone?.id?.value ?: ""),
            "name" to selected.zone?.name.orEmpty(),
            "type" to (selected.zone?.type ?: GrowZoneType.GROW_LIGHT).name.lowercase(),
            "capacity" to (selected.zone?.capacity ?: 1).toString(),
        )
        ScreenId.TRAY_SIZES -> trayFieldValues(selected.tray)
        ScreenId.BACKUP_RESTORE -> mapOf("documentId" to backupDocumentId().orEmpty())
        ScreenId.ADVERTISING_PRIVACY -> mapOf(
            "choice" to preferences.privacy?.choice?.takeIf {
                it == ConsentChoice.ACCEPTED || it == ConsentChoice.DECLINED
            }?.name?.lowercase().orEmpty().ifBlank { "declined" },
            "expiresAtEpochMillis" to (preferences.privacy?.expiresAtEpochMillis
                ?: now().toEpochMilliseconds() + PRIVACY_VALIDITY_MILLIS).toString(),
            "policyVersion" to preferences.privacy?.policyVersion.orEmpty().ifBlank { DEFAULT_POLICY_VERSION },
        )
        ScreenId.NOTIFICATIONS -> mapOf(
            "dailySummaryEnabled" to preferences.dailySummaryEnabled.toString(),
            "summaryTime" to preferences.summaryTime.toString(),
            "soakRemindersEnabled" to preferences.soakRemindersEnabled.toString(),
        )
        else -> primaryFieldValues(screen, stableId, workspace, preferences, selected, due = null)
    }

private fun todayFieldValues(due: DueMoment?): Map<String, String> = buildMap {
    put("dueKind", if (due is DueMoment.CivilTime) "civil" else "elapsed")
    when (due) {
        is DueMoment.ElapsedTarget -> put("dueAt", due.instant.toString())
        is DueMoment.CivilTime -> {
            put("localDateTime", due.localDateTime.toString())
            put("timeZoneId", due.timeZoneId)
        }
        null -> put("dueAt", now().toString())
    }
}

private fun repeatSnapshotOptions(
    screen: ScreenId, stableId: String?, field: ScreenFieldId, workspace: Workspace,
): List<ScreenOption> {
    if (screen != ScreenId.BATCH_EDITOR) return emptyList()
    val sourceId = BatchRepeatDraft.sourceId(stableId) ?: return emptyList()
    val source = workspace.batch(sourceId)
    val choice = BatchRepeatDraft.snapshotChoice(sourceId)
    return when (field.value) {
        "recipeId" -> listOf(ScreenOption(choice,
            labelKey = source.recipe.builtInCultureId?.let { key("culture_${it.value}_name") },
            authoredLabel = source.recipe.nameSnapshot))
        "trayId" -> listOf(ScreenOption(choice, authoredLabel = source.tray.nameSnapshot))
        else -> emptyList()
    }
}

private fun batchEditorFieldValues(
    stableId: String?,
    batch: GrowBatch?,
    workspace: Workspace,
): Map<String, String> {
    BatchRepeatDraft.sourceId(stableId)?.let { sourceId ->
        val source = workspace.batch(sourceId)
        require(source.state.isTerminal) { "Only terminal batches can be repeated" }
        return batchEditorSnapshotFields(null, source, null, BatchRepeatDraft.snapshotChoice(sourceId),
            source.cultureIdSnapshot?.value ?: source.recipe.builtInCultureId?.value.orEmpty(), null) +
            batchEditorScheduleFields(null) + mapOf("trayId" to BatchRepeatDraft.snapshotChoice(sourceId))
    }
    val suggestion = workspace.suggestion(stableId, today(), timeZone())
    val sourceRecipe = batch?.recipe?.sourceRecipeId?.value
        ?: workspace.recipes.firstOrNull { it.id.value == stableId }?.id?.value
    val fallbackRecipe: String? = null
    val recipe = workspace.recipes.firstOrNull { it.id.value == (sourceRecipe ?: fallbackRecipe) }
    val culture = suggestion?.cultureId?.value
        ?: stableId?.takeIf { id -> SproutPaceCatalog.cultures.any { it.id.value == id } }
        ?: batch?.cultureIdSnapshot?.value
        ?: batch?.recipe?.builtInCultureId?.value
        ?: workspace.recipes.firstOrNull { it.id.value == (sourceRecipe ?: fallbackRecipe) }?.cultureId?.value
        ?: SproutPaceCatalog.cultures.first().id.value
    val sourceTray = batch?.let { current ->
        workspace.trays.firstOrNull { it.name == current.tray.nameSnapshot && it.shape == current.tray.shape }
    }
    return batchEditorSnapshotFields(
        stableId = batch?.id?.value,
        batch = batch,
        recipe = recipe,
        recipeId = sourceRecipe ?: fallbackRecipe,
        cultureId = culture,
        tray = sourceTray ?: workspace.trays.firstOrNull(),
    ) + batchEditorScheduleFields(batch) + batchEditorCorrectionFields(batch) +
        if (suggestion != null) mapOf(
            "plannedSowDate" to suggestion.sowDate(timeZone().id).toString(),
            "fixedHarvestDate" to suggestion.targetHarvestDate.toString(),
            "timeZoneId" to timeZone().id,
            "zoneId" to workspace.planningForecast(today(), timeZone()).firstOrNull {
                it.suggestion == suggestion
            }?.route?.firstOrNull()?.zoneId?.value.orEmpty(),
        ) else emptyMap()
}

private fun batchEditorSnapshotFields(
    stableId: String?,
    batch: GrowBatch?,
    recipe: UserGrowRecipe?,
    recipeId: String?,
    cultureId: String,
    tray: TraySizePreset?,
): Map<String, String> = buildMap {
        put("id", stableId ?: "")
        recipeId?.let { put("recipeId", it) }
        put("cultureId", cultureId)
        tray?.let { put("trayId", it.id.value) }
        put(
            "substrate",
            batch?.substrateSnapshot
                ?: recipe?.substrate.orEmpty(),
        )
        batch?.actualSeedMass?.value?.let { put("actualSeedMassGrams", it.toString()) }
        (batch?.supplier ?: recipe?.supplier)?.let { put("supplier", it) }
        (batch?.cultivar ?: recipe?.cultivar)?.let { put("cultivar", it) }
        (batch?.seedLot ?: recipe?.seedLot)?.let { put("seedLot", it) }
        (batch?.germinationPercentage ?: recipe?.germinationPercentage)?.value?.let {
            put("germinationPercentage", it.toString())
        }
}

private fun batchEditorScheduleFields(batch: GrowBatch?): Map<String, String> = buildMap {
    put("plannedSowDate", (batch?.plannedSowDate ?: today()).toString())
    val civilActions = batch?.actions.orEmpty().mapNotNull { action ->
        (action.due as? DueMoment.CivilTime)?.let { action to it }
    }
    put("timeZoneId", civilActions.firstOrNull()?.second?.timeZoneId ?: timeZone().id)
    civilActions.filter { it.first.userFixed }.forEach { (action, due) ->
        fixedDateField(action.stage)?.let { put(it, due.localDateTime.date.toString()) }
    }
}

private fun batchEditorCorrectionFields(batch: GrowBatch?): Map<String, String> = buildMap {
    (batch?.placement as? Placement.Assigned)?.zoneId?.value?.let { put("zoneId", it) }
    batch?.let { current ->
        put("anchorState", current.state.name.lowercase())
        put("anchorAt", (current.events.lastOrNull()?.occurredAt ?: now()).toString())
        current.events.forEach { event ->
            actualDateField(event.to)?.let { field -> put(field, event.occurredAt.toString()) }
        }
    }
}

private fun batchDetailFieldValues(
    stableId: String?,
    batch: GrowBatch?,
    workspace: Workspace,
): Map<String, String> = buildMap {
    val subject = batch?.id?.value ?: stableId.orEmpty()
    put("eventId", "event-$subject-${(batch?.events?.size ?: 0) + 1}")
    put("occurredAt", now().toString())
    nextBatchState(batch)?.let { put("nextState", it.name.lowercase()) }
    val currentZone = (batch?.placement as? Placement.Assigned)?.zoneId
    val zone = workspace.zones.firstOrNull { it.id == currentZone }
        ?: workspace.zones.firstOrNull { it.isLightCapable }
    zone?.let { put("zoneId", it.id.value) }
    put("unassigned", (zone == null || batch?.placement is Placement.Unassigned).toString())
    nextBatchState(batch)?.let { skipped ->
        put("skippedState", skipped.name.lowercase())
        put("resultingState", stateAfter(batch, skipped).name.lowercase())
    }
    batch?.actions?.firstOrNull { it.status == ScheduledActionStatus.PENDING }?.let {
        put("actionId", it.id.value)
        put("dueAt", SchedulingEngine.resolvedInstant(it).toString())
    } ?: put("dueAt", now().toString())
    ((batch?.outcome as? BatchOutcome.Harvested)?.actualMass ?: batch?.actualSeedMass)?.let {
        put("actualMassGrams", it.value.toString())
    }
    put("timeZoneId", "UTC")
}

private fun recipeFieldValues(stableId: String?, recipe: UserGrowRecipe?): Map<String, String> = buildMap {
    put("id", stableId ?: "")
    put("name", recipe?.name.orEmpty())
    put(
        "cultureId",
        recipe?.cultureId?.value
            ?: stableId?.takeIf { it in SproutPaceCatalog.cultures.map { culture -> culture.id.value } }
            ?: SproutPaceCatalog.cultures.first().id.value,
    )
    put("substrate", recipe?.substrate.orEmpty())
    recipe?.supplier?.let { put("supplier", it) }
    recipe?.cultivar?.let { put("cultivar", it) }
    recipe?.seedLot?.let { put("seedLot", it) }
    recipe?.germinationPercentage?.value?.let { put("germinationPercentage", it.toString()) }
    val defaultCulture = SproutPaceCatalog.cultures.firstOrNull { culture ->
        culture.id.value == (recipe?.cultureId?.value ?: stableId)
    } ?: SproutPaceCatalog.cultures.first()
    val stages = recipe?.stages ?: defaultCulture.stages.map { stage ->
        com.sedsoftware.sproutpace.domain.model.RecipeStage(stage.stage, stage.duration, stage.instructionKey)
    }
    stages.take(MAX_RECIPE_STAGES).forEachIndexed { index, stage ->
        val slot = index + 1
        put("stage$slot", stage.stage.name.lowercase())
        put("stage${slot}MinimumHours", stage.duration.minimum.inWholeHours.toString())
        put("stage${slot}MaximumHours", stage.duration.maximum.inWholeHours.toString())
        stage.userGuidance?.trim()?.takeIf(String::isNotEmpty)?.let { put("stage${slot}Guidance", it) }
    }
    repeat((MAX_RECIPE_STAGES - stages.size).coerceAtLeast(0)) { offset ->
        put("stage${stages.size + offset + 1}", "none")
    }
    put("newId", "")
}

private fun fixedDateField(stage: GrowStage?): String? = when (stage) {
    GrowStage.SOAKING -> "fixedSoakingDate"
    GrowStage.SOWN -> "fixedSownDate"
    GrowStage.BLACKOUT_OR_WEIGHT -> "fixedBlackoutDate"
    GrowStage.LIGHT_GROWTH -> "fixedLightDate"
    GrowStage.READY_FOR_HARVEST -> "fixedHarvestDate"
    null -> null
}

private fun actualDateField(state: BatchState): String? = when (state) {
    BatchState.SOAKING -> "actualSoakingAt"
    BatchState.SOWN -> "actualSownAt"
    BatchState.BLACKOUT_OR_WEIGHT -> "actualBlackoutAt"
    BatchState.LIGHT_GROWTH -> "actualLightAt"
    BatchState.READY_FOR_HARVEST -> "actualHarvestReadyAt"
    else -> null
}

private fun observationFieldValues(
    stableId: String?,
    observation: Observation?,
    workspace: Workspace,
): Map<String, String> = buildMap {
    put("id", observation?.id?.value ?: "")
    put(
        "batchId",
        observation?.batchId?.value
            ?: stableId?.takeIf { id -> workspace.batches.any { it.id.value == id } }
            ?: workspace.batches.firstOrNull()?.id?.value.orEmpty(),
    )
    put("occurredAt", (observation?.occurredAt ?: now()).toString())
    put("text", observation?.text.orEmpty())
    observation?.photoId?.value?.let { put("photoId", it) }
}

private fun closeBatchFieldValues(batch: GrowBatch?): Map<String, String> = buildMap {
    val outcome = batch?.outcome
    put("occurredAt", (outcome?.occurredAt ?: now()).toString())
    put(
        "outcome",
        when (outcome) {
            is BatchOutcome.Failed -> "failed"
            is BatchOutcome.Abandoned -> "abandoned"
            else -> "harvested"
        },
    )
    (outcome as? BatchOutcome.Harvested)?.actualMass?.let { put("actualMassGrams", it.value.toString()) }
    (outcome as? BatchOutcome.Harvested)?.assessment?.let { put("assessment", it.name.lowercase()) }
    when (outcome) {
        is BatchOutcome.Failed -> outcome.reason?.let { put("reason", it) }
        is BatchOutcome.Abandoned -> outcome.reason?.let { put("reason", it) }
        else -> Unit
    }
    outcome?.note?.let { put("note", it) }
}

private fun trayFieldValues(tray: TraySizePreset?): Map<String, String> = buildMap {
    put("id", tray?.id?.value ?: "")
    put("name", tray?.name.orEmpty())
    when (val shape = tray?.shape) {
        is TrayShape.Rectangle -> {
            put("shape", "rectangle")
            put("widthCm", shape.widthCentimetres.toString())
            put("lengthCm", shape.lengthCentimetres.toString())
        }
        is TrayShape.Circle -> {
            put("shape", "circle")
            put("diameterCm", shape.diameterCentimetres.toString())
        }
        null -> {
            put("shape", "rectangle")
            put("widthCm", "25.4")
            put("lengthCm", "50.8")
        }
    }
}

private fun dynamicOptions(field: ScreenField, workspace: Workspace): List<ScreenOption> = when (field.id.value) {
    "cultureId", "cultureIds" -> cultureOptions()
    "recipeId" -> workspace.recipes.sortedBy { it.id.value }.map { authoredOption(it.id.value, it.name) }
    "trayId" -> workspace.trays.sortedBy { it.id.value }.map { authoredOption(it.id.value, it.name) }
    "zoneId" -> workspace.zones.sortedBy { it.id.value }.map { authoredOption(it.id.value, it.name) }
    "batchId" -> workspace.batches.sortedBy { it.id.value }.map { batch ->
        val titleKey = batch.cultureIdSnapshot?.let { key("culture_${it.value}_name") }
        ScreenOption(batch.id.value, titleKey, batch.recipe.nameSnapshot ?: batch.id.value.takeIf { titleKey == null },
            batchIdentity = batch.identity())
    }
    "anchorState", "nextState", "skippedState", "resultingState" -> BatchState.entries.map { state ->
        ScreenOption(state.name.lowercase(), batchStateKey(state))
    }
    else -> emptyList()
}

private fun screenFacts(
    screen: ScreenId,
    stableId: String?,
    workspace: Workspace,
    preferences: SettingsSnapshot,
): List<ScreenFact> {
    val selected = selection(stableId, workspace)
    return if (screen in PRIMARY_SCREENS) {
        primaryScreenFacts(screen, workspace, preferences, selected)
    } else {
        secondaryScreenFacts(screen, workspace, preferences, selected)
    }
}

private fun primaryScreenFacts(
    screen: ScreenId,
    workspace: Workspace,
    preferences: SettingsSnapshot,
    selected: ScreenSelection,
): List<ScreenFact> = when (screen) {
        ScreenId.FIRST_RUN -> listOf(
            valueFact("zone-count", "screen_zones_title", workspace.zones.size),
            valueFact("tray-count", "screen_tray_sizes_title", workspace.trays.size),
        )
        ScreenId.TODAY -> listOf(valueFact(
            "pending-count", "screen_today_title",
            workspace.batches.sumOf { candidate -> candidate.actions.count { it.status == ScheduledActionStatus.PENDING } },
        ))
        ScreenId.TRAYS -> listOf(valueFact("batch-count", "screen_trays_title", workspace.batches.size))
        ScreenId.BATCH_EDITOR -> selected.batch?.let {
            listOf(authoredFact("recommended-seed-mass", "field_harvest_mass", it.recommendedSeedMass.value.toString()))
        }.orEmpty()
        ScreenId.BATCH_DETAIL -> batchDetailFacts(selected.batch, workspace)
        ScreenId.PLAN -> planFacts(selected.plan, workspace)
        ScreenId.CATALOG -> listOf(
            valueFact("catalog-version", "screen_catalog_title", SproutPaceCatalog.VERSION),
            authoredFact("catalog-reviewed-on", "field_date", SproutPaceCatalog.reviewedOn.toString()),
        )
        ScreenId.RECIPE -> selected.recipe?.let {
            listOf(authoredFact("recipe-name", "field_recipe", it.name))
        }.orEmpty()
        else -> secondaryScreenFacts(screen, workspace, preferences, selected)
    }

private fun secondaryScreenFacts(
    screen: ScreenId,
    workspace: Workspace,
    preferences: SettingsSnapshot,
    selected: ScreenSelection,
): List<ScreenFact> = when (screen) {
        ScreenId.OBSERVATION_EDITOR -> selected.observation?.let {
            listOf(authoredFact("observation-date", "field_date", it.occurredAt.toString()))
        }.orEmpty()
        ScreenId.CLOSE_BATCH -> selected.batch?.let {
            listOf(keyedFact("current-state", "field_outcome_date", batchStateKey(it.state)))
        }.orEmpty()
        ScreenId.SETTINGS -> listOf(
            keyedFact("theme", "settings_theme", key("settings_theme_${preferences.theme.name.lowercase()}")),
            keyedFact("crash-reporting", "settings_crash_reporting", booleanKey(preferences.crashReporting)),
        )
        ScreenId.ZONES -> selected.zone?.let {
            listOf(authoredFact("zone-capacity", "field_capacity", it.capacity.toString()))
        } ?: listOf(valueFact("zone-count", "screen_zones_title", workspace.zones.size))
        ScreenId.TRAY_SIZES -> selected.tray?.let {
            listOf(authoredFact("tray-area", "field_tray_size", it.shape.areaSquareCentimetres.toString()))
        } ?: listOf(valueFact("tray-count", "screen_tray_sizes_title", workspace.trays.size))
        ScreenId.BACKUP_RESTORE -> backupFacts()
        ScreenId.ADVERTISING_PRIVACY -> privacyFacts(preferences)
        ScreenId.NOTIFICATIONS -> notificationFacts(preferences)
        else -> primaryScreenFacts(screen, workspace, preferences, selected)
    }

private fun batchDetailFacts(batch: GrowBatch?, workspace: Workspace): List<ScreenFact> = batch?.let {
    listOf(
        keyedFact("batch-state", "screen_batch_detail_title", batchStateKey(it.state)),
        authoredFact("substrate", "field_substrate", it.substrateSnapshot),
        authoredFact("tray-snapshot", "field_tray_size", it.tray.nameSnapshot),
        authoredFact("seed-mass-snapshot", "field_actual_seed_mass", it.actualSeedMass.value.toString()),
        if (it.recipe.nameSnapshot != null) authoredFact("recipe-snapshot", "batch_snapshot_title", it.recipe.nameSnapshot!!)
        else keyedFact("recipe-snapshot", "batch_snapshot_title", key("culture_${it.recipe.builtInCultureId?.value}_name")),
        valueFact(
            "observation-count",
            "screen_observation_editor_title",
            workspace.observations.count { item -> item.batchId == it.id },
        ),
    )
}.orEmpty()

private fun planFacts(plan: HarvestPlan?, workspace: Workspace): List<ScreenFact> = plan?.let {
    listOf(valueFact("cadence-days", "plan_frequency", it.cadence.value))
} ?: listOf(valueFact("plan-count", "screen_plan_title", workspace.plans.size))

private fun backupFacts(): List<ScreenFact> = buildList {
    backupPreview()?.archive?.let { archive ->
        backupDocumentId()?.let { SelectedBackupDocument.decode(it).displayName }?.takeIf(String::isNotBlank)?.let {
            add(authoredFact("restore-file-name", "restore_archive_filename", it))
        }
        add(authoredFact("restore-created-at", "restore_archive_date", archive.createdAt.toString()))
        add(valueFact("restore-format-version", "backup_format_version", archive.formatVersion))
        archive.snapshot.manifest.recordCounts.entries.sortedBy { it.key }.forEach { (name, count) ->
            val label = when (name) {
                "zones" -> "backup_count_zones"
                "trayPresets" -> "backup_count_trays"
                "recipes" -> "backup_count_recipes"
                "batches" -> "backup_count_batches"
                "plans" -> "backup_count_plans"
                "observations" -> "backup_count_observations"
                else -> "backup_count_records"
            }
            add(authoredFact("restore-record-count-$name", label, count.toString()))
        }
        add(valueFact("restore-photo-count", "backup_count_photos", archive.photos.size))
    }
}

private fun privacyFacts(preferences: SettingsSnapshot): List<ScreenFact> = listOf(
    keyedFact(
        "privacy-choice",
        "privacy_heading",
        key("privacy_${preferences.privacy?.choice?.name?.lowercase() ?: "undecided"}"),
    ),
    preferences.privacy?.let { authoredFact("policy-version", "privacy_heading", it.policyVersion) }
        ?: authoredFact("policy-version", "privacy_heading", DEFAULT_POLICY_VERSION),
)

private fun notificationFacts(preferences: SettingsSnapshot): List<ScreenFact> = listOf(
    keyedFact(
        "daily-summary-enabled",
        "field_daily_summary_enabled",
        booleanKey(preferences.dailySummaryEnabled),
    ),
    authoredFact("summary-time", "field_summary_time", preferences.summaryTime.toString()),
    keyedFact(
        "soak-reminders-enabled",
        "field_soak_reminders_enabled",
        booleanKey(preferences.soakRemindersEnabled),
    ),
)

private fun selection(stableId: String?, workspace: Workspace): ScreenSelection = ScreenSelection(
    batch = stableId?.let { id -> workspace.batches.firstOrNull { it.id.value == id } },
    plan = stableId?.let { id -> workspace.plans.firstOrNull { it.id.value == id } } ?: workspace.plans.firstOrNull(),
    recipe = stableId?.let { id -> workspace.recipes.firstOrNull { it.id.value == id } },
    observation = stableId?.let { id -> workspace.observations.firstOrNull { it.id.value == id } },
    zone = stableId?.let { id -> workspace.zones.firstOrNull { it.id.value == id } },
    tray = stableId?.let { id -> workspace.trays.firstOrNull { it.id.value == id } },
)

private data class ScreenSelection(
    val batch: GrowBatch?,
    val plan: HarvestPlan?,
    val recipe: UserGrowRecipe?,
    val observation: Observation?,
    val zone: com.sedsoftware.sproutpace.domain.model.GrowZone?,
    val tray: TraySizePreset?,
)

private fun cultureOptions() = SproutPaceCatalog.cultures.map { culture ->
    ScreenOption(culture.id.value, key(culture.nameKey.value))
}

private fun authoredOption(id: String, value: String) = ScreenOption(id, authoredLabel = value)
private fun authoredFact(id: String, label: String, value: String) =
    ScreenFact(id, key(label), authoredValue = value)
private fun valueFact(id: String, label: String, value: Any) = authoredFact(id, label, value.toString())
private fun keyedFact(id: String, label: String, value: TextKey) = ScreenFact(id, key(label), valueKey = value)

private fun nextBatchState(batch: GrowBatch?): BatchState? = when (batch?.state) {
    BatchState.PLANNED -> if (batch.recipe.stages.any { it.stage == GrowStage.SOAKING }) BatchState.SOAKING else BatchState.SOWN
    BatchState.SOAKING -> BatchState.SOWN
    BatchState.SOWN -> if (batch.recipe.stages.any { it.stage == GrowStage.BLACKOUT_OR_WEIGHT }) {
        BatchState.BLACKOUT_OR_WEIGHT
    } else BatchState.LIGHT_GROWTH
    BatchState.BLACKOUT_OR_WEIGHT -> BatchState.LIGHT_GROWTH
    BatchState.LIGHT_GROWTH -> BatchState.READY_FOR_HARVEST
    else -> null
}

private fun stateAfter(batch: GrowBatch?, skipped: BatchState): BatchState = when (skipped) {
    BatchState.SOAKING -> BatchState.SOWN
    BatchState.SOWN -> if (batch?.recipe?.stages?.any { it.stage == GrowStage.BLACKOUT_OR_WEIGHT } == true) {
        BatchState.BLACKOUT_OR_WEIGHT
    } else BatchState.LIGHT_GROWTH
    BatchState.BLACKOUT_OR_WEIGHT -> BatchState.LIGHT_GROWTH
    BatchState.LIGHT_GROWTH -> BatchState.READY_FOR_HARVEST
    else -> BatchState.READY_FOR_HARVEST
}


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

    private fun booleanKey(value: Boolean) = key(if (value) "state_enabled" else "state_disabled")
    private fun key(value: String) = TextKey(value)

    private companion object {
        const val PRIVACY_VALIDITY_MILLIS = 72L * 60L * 60L * 1_000L
        const val DEFAULT_POLICY_VERSION = "v1"
        const val DEFAULT_CADENCE_DAYS = 7
        const val MAX_RECIPE_STAGES = 5
        val PRIMARY_SCREENS = setOf(
            ScreenId.FIRST_RUN,
            ScreenId.TODAY,
            ScreenId.TRAYS,
            ScreenId.BATCH_EDITOR,
            ScreenId.BATCH_DETAIL,
            ScreenId.PLAN,
            ScreenId.CATALOG,
            ScreenId.RECIPE,
        )
    }
}
