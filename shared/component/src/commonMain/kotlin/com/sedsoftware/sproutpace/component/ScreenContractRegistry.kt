package com.sedsoftware.sproutpace.component

enum class ScreenVariant {
    CONTENT, EMPTY, VALIDATION_ERROR, LOCAL_ERROR, OFFLINE, PERMISSION_DENIED,
    CAPACITY_CONFLICT, TEMPORARILY_UNASSIGNED, DESTRUCTIVE_CONFIRMATION,
    PROGRESS, RESTORE_PREVIEW, PRIVACY_DECISION,
}

data class ScreenContract(
    val id: ScreenId,
    val variants: Set<ScreenVariant>,
    val primaryOperation: String?,
    val acceptsStableId: Boolean = false,
    val fields: List<ScreenField> = emptyList(),
    val facts: List<ScreenFact> = emptyList(),
    val actions: List<ScreenActionDescriptor> = emptyList(),
    val operations: Map<String, ScreenOperationContract> = emptyMap(),
) {
    init {
        require(fields.map { it.id }.distinct().size == fields.size)
        require(actions.map { it.id }.distinct().size == actions.size)
        require(operations.keys == operations.values.mapTo(mutableSetOf(), ScreenOperationContract::operation))
        require(primaryOperation == null || primaryOperation in operations)
    }
}

/** Normative locale-neutral interaction inventory for SCREEN-001 through SCREEN-016. */
object ScreenContractRegistry {
    private val contractsList = listOf(
        contract(
            ScreenId.FIRST_RUN,
            primary = "continue-setup",
            variants = variants(ScreenVariant.VALIDATION_ERROR, ScreenVariant.PERMISSION_DENIED),
            fields = listOf(
                field("zoneName", required = true),
                field(
                    "type", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("zone_type", "natural_light", "grow_light"),
                ),
                field("capacity", ScreenFieldKind.POSITIVE_INTEGER, required = true),
                field("trayName", required = true),
                field(
                    "shape", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("tray_shape", "rectangle", "circle"),
                ),
                field("widthCm", ScreenFieldKind.POSITIVE_DECIMAL),
                field("lengthCm", ScreenFieldKind.POSITIVE_DECIMAL),
                field("diameterCm", ScreenFieldKind.POSITIVE_DECIMAL),
                field("cadenceDays", ScreenFieldKind.POSITIVE_INTEGER, required = true),
                field("cultureIds", ScreenFieldKind.MULTI_CHOICE, required = true),
                field(
                    "mode", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("plan_mode", "cadence", "continuous_queue"),
                ),
            ),
            operations = listOf(
                operation(
                    "continue-setup", CommandSubjectRequirement.NONE,
                    argument("zoneName"),
                    argument("type", ScreenFieldKind.SINGLE_CHOICE),
                    argument("capacity", ScreenFieldKind.POSITIVE_INTEGER),
                    argument("trayName"),
                    argument("shape", ScreenFieldKind.SINGLE_CHOICE),
                    conditionalArgument("widthCm", ScreenFieldKind.POSITIVE_DECIMAL, "shape", "rectangle"),
                    conditionalArgument("lengthCm", ScreenFieldKind.POSITIVE_DECIMAL, "shape", "rectangle"),
                    conditionalArgument("diameterCm", ScreenFieldKind.POSITIVE_DECIMAL, "shape", "circle"),
                    argument("cadenceDays", ScreenFieldKind.POSITIVE_INTEGER),
                    argument("cultureIds", ScreenFieldKind.MULTI_CHOICE),
                    argument("mode", ScreenFieldKind.SINGLE_CHOICE),
                ),
            ),
        ),
        contract(
            ScreenId.TODAY,
            primary = "complete-action",
            variants = variants(
                ScreenVariant.EMPTY, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE,
                ScreenVariant.PERMISSION_DENIED, ScreenVariant.CAPACITY_CONFLICT,
            ),
            fields = listOf(
                field(
                    "dueKind", ScreenFieldKind.SINGLE_CHOICE,
                    options = choices("due_kind", "elapsed", "civil"),
                ),
                field("dueAt", ScreenFieldKind.INSTANT), field("localDateTime", ScreenFieldKind.LOCAL_DATE_TIME),
                field("timeZoneId"),
            ),
            operations = listOf(
                itemSubjectOperation("complete-action"),
                subjectOperation(
                    "snooze", argument("dueKind", ScreenFieldKind.SINGLE_CHOICE),
                    conditionalArgument("dueAt", ScreenFieldKind.INSTANT, "dueKind", "elapsed"),
                    conditionalArgument("localDateTime", ScreenFieldKind.LOCAL_DATE_TIME, "dueKind", "civil"),
                    conditionalArgument("timeZoneId", ScreenFieldKind.TEXT, "dueKind", "civil"),
                ),
                itemSubjectOperation("skip-action"),
                operation("time-zone-change", CommandSubjectRequirement.NONE, argument("timeZoneId")),
            ),
        ),
        contract(
            ScreenId.TRAYS,
            primary = null,
            variants = variants(ScreenVariant.EMPTY, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE),
            fields = listOf(
                field(
                    "category", ScreenFieldKind.SINGLE_CHOICE, required = true, labelKey = "field_tray_category",
                    options = choices("tray_category", "planned", "active", "history"),
                ),
            ),
            operations = listOf(subjectOperation("delete-batch")),
            actions = listOf(navigationAction("repeat-batch")),
        ),
        contract(
            ScreenId.BATCH_EDITOR,
            primary = "save-batch",
            acceptsStableId = true,
            variants = variants(ScreenVariant.VALIDATION_ERROR, ScreenVariant.LOCAL_ERROR, ScreenVariant.CAPACITY_CONFLICT),
            fields = listOf(
                field("id", labelKey = "field_batch_id"),
                field("recipeId", ScreenFieldKind.SINGLE_CHOICE),
                field("cultureId", ScreenFieldKind.SINGLE_CHOICE),
                field("trayId", ScreenFieldKind.SINGLE_CHOICE, required = true),
                field("substrate", required = true), field("actualSeedMassGrams", ScreenFieldKind.POSITIVE_DECIMAL),
                field("supplier"), field("cultivar"), field("seedLot"),
                field("germinationPercentage", ScreenFieldKind.POSITIVE_INTEGER),
                field("plannedSowDate", ScreenFieldKind.LOCAL_DATE, required = true),
                field("fixedSoakingDate", ScreenFieldKind.LOCAL_DATE),
                field("fixedSownDate", ScreenFieldKind.LOCAL_DATE),
                field("fixedBlackoutDate", ScreenFieldKind.LOCAL_DATE),
                field("fixedLightDate", ScreenFieldKind.LOCAL_DATE),
                field("fixedHarvestDate", ScreenFieldKind.LOCAL_DATE),
                field("timeZoneId"),
                field("zoneId", ScreenFieldKind.SINGLE_CHOICE),
                field(
                    "anchorState", ScreenFieldKind.SINGLE_CHOICE,
                    options = batchStateChoices(),
                ),
                field("anchorAt", ScreenFieldKind.INSTANT),
                field("actualSoakingAt", ScreenFieldKind.INSTANT),
                field("actualSownAt", ScreenFieldKind.INSTANT),
                field("actualBlackoutAt", ScreenFieldKind.INSTANT),
                field("actualLightAt", ScreenFieldKind.INSTANT),
                field("actualHarvestReadyAt", ScreenFieldKind.INSTANT),
            ),
            operations = listOf(
                operation(
                    "save-batch", CommandSubjectRequirement.REQUIRED,
                    argument("recipeId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    argument("cultureId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    argument("trayId", ScreenFieldKind.SINGLE_CHOICE), argument("substrate"),
                    argument("actualSeedMassGrams", ScreenFieldKind.POSITIVE_DECIMAL, required = false),
                    argument("supplier", required = false), argument("cultivar", required = false),
                    argument("seedLot", required = false),
                    argument("germinationPercentage", ScreenFieldKind.POSITIVE_INTEGER, required = false),
                    argument("plannedSowDate", ScreenFieldKind.LOCAL_DATE),
                    argument("fixedSoakingDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedSownDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedBlackoutDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedLightDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedHarvestDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("timeZoneId", required = false),
                    argument("zoneId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    oneOf = listOf(setOf("recipeId", "cultureId")),
                ),
                operation(
                    "correct-batch", CommandSubjectRequirement.REQUIRED,
                    argument("cultureId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    argument("trayId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    argument("substrate", required = false),
                    argument("actualSeedMassGrams", ScreenFieldKind.POSITIVE_DECIMAL, required = false),
                    argument("supplier", required = false), argument("cultivar", required = false),
                    argument("seedLot", required = false),
                    argument("germinationPercentage", ScreenFieldKind.POSITIVE_INTEGER, required = false),
                    argument("plannedSowDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedSoakingDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedSownDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedBlackoutDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedLightDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("fixedHarvestDate", ScreenFieldKind.LOCAL_DATE, required = false),
                    argument("timeZoneId", required = false),
                    argument("zoneId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    argument("anchorState", ScreenFieldKind.SINGLE_CHOICE, required = false),
                    argument("anchorAt", ScreenFieldKind.INSTANT, required = false),
                    argument("actualSoakingAt", ScreenFieldKind.INSTANT, required = false),
                    argument("actualSownAt", ScreenFieldKind.INSTANT, required = false),
                    argument("actualBlackoutAt", ScreenFieldKind.INSTANT, required = false),
                    argument("actualLightAt", ScreenFieldKind.INSTANT, required = false),
                    argument("actualHarvestReadyAt", ScreenFieldKind.INSTANT, required = false),
                ),
            ),
        ),
        contract(
            ScreenId.BATCH_DETAIL,
            primary = "confirm-stage",
            acceptsStableId = true,
            variants = variants(
                ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE, ScreenVariant.TEMPORARILY_UNASSIGNED,
                ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(
                field("eventId"), field("occurredAt", ScreenFieldKind.INSTANT),
                field("nextState", ScreenFieldKind.SINGLE_CHOICE, options = batchStateChoices()),
                field("zoneId", ScreenFieldKind.SINGLE_CHOICE),
                field("unassigned", ScreenFieldKind.BOOLEAN),
                field("skippedState", ScreenFieldKind.SINGLE_CHOICE, options = batchStateChoices()),
                field("reason", labelKey = "field_skip_reason"),
                field("resultingState", ScreenFieldKind.SINGLE_CHOICE, options = batchStateChoices()),
                field("dueAt", ScreenFieldKind.INSTANT), field("actionId"),
                field("actualMassGrams", ScreenFieldKind.POSITIVE_DECIMAL), field("timeZoneId"),
            ),
            operations = listOf(
                eventOperation("start-soaking"),
                eventOperation(
                    "confirm-sowing",
                    extra = listOf(
                        argument("zoneId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                        argument("unassigned", ScreenFieldKind.BOOLEAN, required = false),
                    ),
                    oneOf = listOf(setOf("zoneId", "unassigned")),
                ),
                eventOperation(
                    "confirm-stage",
                    argument("nextState", ScreenFieldKind.SINGLE_CHOICE),
                    argument("zoneId", ScreenFieldKind.SINGLE_CHOICE, required = false),
                ),
                subjectOperation("assign-overflow"), subjectOperation("undo-stage"),
                eventOperation(
                    "skip-stage",
                    argument("skippedState", ScreenFieldKind.SINGLE_CHOICE),
                    argument("reason"),
                    argument("resultingState", ScreenFieldKind.SINGLE_CHOICE),
                ),
                subjectOperation(
                    "schedule-soak-target", argument("dueAt", ScreenFieldKind.INSTANT),
                    argument("actionId", required = false),
                ),
                subjectOperation(
                    "record-actual-mass", argument("actualMassGrams", ScreenFieldKind.POSITIVE_DECIMAL, required = false),
                ),
                itemSubjectOperation("complete-action"),
                itemSubjectOperation("skip-action"),
                operation("time-zone-change", CommandSubjectRequirement.NONE, argument("timeZoneId")),
                subjectOperation("delete-batch"),
            ),
            actions = listOf(navigationAction("add-observation"), navigationAction("close-batch"),
                navigationAction("edit-batch"), navigationAction("repeat-batch")),
        ),
        contract(
            ScreenId.PLAN,
            primary = "save-plan",
            acceptsStableId = true,
            variants = variants(ScreenVariant.EMPTY, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE, ScreenVariant.CAPACITY_CONFLICT),
            fields = listOf(
                field("id", labelKey = "field_plan_id"), field("anchorDate", ScreenFieldKind.LOCAL_DATE),
                field("cadenceDays", ScreenFieldKind.POSITIVE_INTEGER, required = true),
                field("cultureIds", ScreenFieldKind.MULTI_CHOICE, required = true),
                field(
                    "mode", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("plan_mode", "cadence", "continuous_queue"),
                ),
                field("paused", ScreenFieldKind.BOOLEAN),
            ),
            operations = listOf(
                planOperation("save-plan"), planOperation("update-plan"), subjectOperation("pause-plan"),
                subjectOperation("resume-plan"), subjectOperation("confirm-suggestion"), subjectOperation("delete-plan"),
            ),
        ),
        contract(
            ScreenId.CATALOG,
            primary = null,
            variants = variants(ScreenVariant.EMPTY, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE),
            fields = listOf(field("query")),
            actions = listOf(navigationAction("open-recipe"), navigationAction("create-recipe")),
        ),
        contract(
            ScreenId.RECIPE,
            primary = "save-recipe",
            acceptsStableId = true,
            variants = variants(
                ScreenVariant.VALIDATION_ERROR, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE,
                ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(
                field("id", labelKey = "field_recipe_id"),
                field("name", required = true, labelKey = "field_recipe_name"),
                field("cultureId", ScreenFieldKind.SINGLE_CHOICE, required = true),
                field("substrate", required = true), field("supplier"), field("cultivar"), field("seedLot"),
                field("germinationPercentage", ScreenFieldKind.POSITIVE_INTEGER),
                field("stage1", ScreenFieldKind.SINGLE_CHOICE, required = true, options = stageChoices()),
                field("stage1MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = true),
                field("stage1MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = true),
                field("stage1Guidance"),
                field("stage2", ScreenFieldKind.SINGLE_CHOICE, options = stageChoices(includeNone = true)),
                field("stage2MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG),
                field("stage2MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG), field("stage2Guidance"),
                field("stage3", ScreenFieldKind.SINGLE_CHOICE, options = stageChoices(includeNone = true)),
                field("stage3MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG),
                field("stage3MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG), field("stage3Guidance"),
                field("stage4", ScreenFieldKind.SINGLE_CHOICE, options = stageChoices(includeNone = true)),
                field("stage4MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG),
                field("stage4MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG), field("stage4Guidance"),
                field("stage5", ScreenFieldKind.SINGLE_CHOICE, options = stageChoices(includeNone = true)),
                field("stage5MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG),
                field("stage5MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG), field("stage5Guidance"),
                field("newId"),
            ),
            operations = listOf(
                recipeOperation("save-recipe"), recipeOperation("update-recipe"),
                subjectOperation("duplicate-recipe", argument("newId"), argument("name", required = false),
                    argument("substrate", required = false)),
                subjectOperation("delete-recipe"),
            ),
            actions = listOf(navigationAction("use-recipe")),
        ),
        contract(
            ScreenId.OBSERVATION_EDITOR,
            primary = "save-observation",
            acceptsStableId = true,
            variants = variants(
                ScreenVariant.VALIDATION_ERROR, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE,
                ScreenVariant.PERMISSION_DENIED, ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(
                field("id", labelKey = "field_observation_id"),
                field("batchId", ScreenFieldKind.SINGLE_CHOICE, required = true),
                field("occurredAt", ScreenFieldKind.INSTANT, required = true), field("text"), field("photoId"),
            ),
            operations = listOf(
                operation(
                    "save-observation", CommandSubjectRequirement.REQUIRED,
                    argument("batchId", ScreenFieldKind.SINGLE_CHOICE),
                    argument("occurredAt", ScreenFieldKind.INSTANT),
                    argument("text", required = false), argument("photoId", required = false),
                ),
                subjectOperation("delete-observation"),
            ),
        ),
        contract(
            ScreenId.CLOSE_BATCH,
            primary = "save-outcome",
            acceptsStableId = true,
            variants = variants(ScreenVariant.VALIDATION_ERROR, ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE),
            fields = listOf(
                field("occurredAt", ScreenFieldKind.INSTANT, required = true),
                field(
                    "outcome", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("outcome", "harvested", "failed", "abandoned"),
                ),
                field("actualMassGrams", ScreenFieldKind.POSITIVE_DECIMAL),
                field(
                    "assessment", ScreenFieldKind.SINGLE_CHOICE,
                    options = choices("outcome_assessment", "successful", "mixed", "unsuccessful"),
                ),
                field("reason", labelKey = "field_outcome_reason"), field("note"),
            ),
            operations = listOf(
                closeOperation("close-harvest"), closeOperation("close-failed"), closeOperation("close-abandoned"),
                closeOperation("save-outcome", argument("outcome", ScreenFieldKind.SINGLE_CHOICE)),
                subjectOperation(
                    "record-actual-mass", argument("actualMassGrams", ScreenFieldKind.POSITIVE_DECIMAL, required = false),
                ),
            ),
        ),
        contract(
            ScreenId.SETTINGS,
            primary = "set-theme",
            variants = variants(
                ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE, ScreenVariant.PERMISSION_DENIED,
                ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(
                field(
                    "theme", ScreenFieldKind.SINGLE_CHOICE,
                    options = choices("theme", "system", "light", "dark"),
                ),
                field("enabled", ScreenFieldKind.BOOLEAN, labelKey = "field_crash_reporting_enabled"),
            ),
            operations = listOf(
                operation(
                    "set-theme", CommandSubjectRequirement.NONE,
                    argument("theme", ScreenFieldKind.SINGLE_CHOICE),
                ),
                operation("set-crash-reporting", CommandSubjectRequirement.NONE, argument("enabled", ScreenFieldKind.BOOLEAN)),
                operation("erase-all", CommandSubjectRequirement.NONE), operation("delete-all-data", CommandSubjectRequirement.NONE),
            ),
            actions = listOf(
                navigationAction("open-zones"), navigationAction("open-tray-sizes"),
                navigationAction("open-notifications"), navigationAction("open-backup-restore"),
                navigationAction("open-advertising-privacy"),
            ),
        ),
        contract(
            ScreenId.ZONES,
            primary = "save-zone",
            acceptsStableId = true,
            variants = variants(
                ScreenVariant.EMPTY, ScreenVariant.VALIDATION_ERROR, ScreenVariant.LOCAL_ERROR,
                ScreenVariant.OFFLINE, ScreenVariant.CAPACITY_CONFLICT, ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(
                field("id", labelKey = "field_zone_id"),
                field("name", required = true, labelKey = "field_zone_name"),
                field(
                    "type", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("zone_type", "natural_light", "grow_light", "dark"),
                ),
                field("capacity", ScreenFieldKind.POSITIVE_INTEGER, required = true),
            ),
            operations = listOf(zoneOperation("save-zone"), zoneOperation("update-zone"), subjectOperation("delete-zone")),
        ),
        contract(
            ScreenId.TRAY_SIZES,
            primary = "save-tray-size",
            acceptsStableId = true,
            variants = variants(
                ScreenVariant.EMPTY, ScreenVariant.VALIDATION_ERROR, ScreenVariant.LOCAL_ERROR,
                ScreenVariant.OFFLINE, ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(
                field("id", labelKey = "field_tray_id"),
                field("name", required = true, labelKey = "field_tray_name"),
                field(
                    "shape", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("tray_shape", "rectangle", "circle"),
                ),
                field("widthCm", ScreenFieldKind.POSITIVE_DECIMAL), field("lengthCm", ScreenFieldKind.POSITIVE_DECIMAL),
                field("diameterCm", ScreenFieldKind.POSITIVE_DECIMAL),
            ),
            operations = listOf(
                trayOperation("save-tray-size"), trayOperation("update-tray-size"), subjectOperation("delete-tray-size"),
            ),
        ),
        contract(
            ScreenId.BACKUP_RESTORE,
            primary = "create-backup",
            variants = variants(
                ScreenVariant.LOCAL_ERROR, ScreenVariant.PROGRESS, ScreenVariant.RESTORE_PREVIEW,
                ScreenVariant.DESTRUCTIVE_CONFIRMATION,
            ),
            fields = listOf(field("documentId", ScreenFieldKind.DOCUMENT)),
            operations = listOf(
                documentOperation("create-backup"), documentOperation("preview-restore"),
                operation("cancel-restore-preview", CommandSubjectRequirement.NONE),
                documentOperation("restore-backup"), documentOperation("export-csv"),
                operation("erase-all", CommandSubjectRequirement.NONE), operation("delete-all-data", CommandSubjectRequirement.NONE),
            ),
        ),
        contract(
            ScreenId.ADVERTISING_PRIVACY,
            primary = "record-consent",
            variants = variants(ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE, ScreenVariant.PRIVACY_DECISION),
            fields = listOf(
                field(
                    "choice", ScreenFieldKind.SINGLE_CHOICE, required = true,
                    options = choices("advertising_choice", "accepted", "declined"),
                ),
                field("expiresAtEpochMillis", ScreenFieldKind.POSITIVE_LONG, required = true),
                field("policyVersion", required = true),
            ),
            operations = listOf(
                operation(
                    "record-consent", CommandSubjectRequirement.NONE,
                    argument("choice", ScreenFieldKind.SINGLE_CHOICE),
                    argument("expiresAtEpochMillis", ScreenFieldKind.POSITIVE_LONG), argument("policyVersion"),
                ),
            ),
            actions = listOf(navigationAction("open-privacy-policy")),
        ),
        contract(
            ScreenId.NOTIFICATIONS,
            primary = "update-notifications",
            variants = variants(ScreenVariant.LOCAL_ERROR, ScreenVariant.OFFLINE, ScreenVariant.PERMISSION_DENIED),
            fields = listOf(
                field(
                    "dailySummaryEnabled", ScreenFieldKind.BOOLEAN, required = true,
                    labelKey = "field_daily_summary_enabled",
                ),
                field("summaryTime", required = true, labelKey = "field_summary_time"),
                field(
                    "soakRemindersEnabled", ScreenFieldKind.BOOLEAN, required = true,
                    labelKey = "field_soak_reminders_enabled",
                ),
            ),
            operations = listOf(
                operation(
                    "update-notifications", CommandSubjectRequirement.NONE,
                    argument("dailySummaryEnabled", ScreenFieldKind.BOOLEAN),
                    argument("summaryTime"),
                    argument("soakRemindersEnabled", ScreenFieldKind.BOOLEAN),
                ),
            ),
            actions = listOf(navigationAction("request-notification-permission"), navigationAction("open-system-settings")),
        ),
    )

    val contracts: Map<ScreenId, ScreenContract> = contractsList.associateBy(ScreenContract::id)

    init { require(contracts.keys == ScreenId.entries.toSet()) }

    fun prepareCommand(
        screen: ScreenId,
        actionId: ScreenActionId,
        currentSubjectId: String?,
        draft: Map<ScreenFieldId, String>,
    ): CommandPreparation {
        val contract = contracts.getValue(screen)
        val descriptor = contract.actions.firstOrNull { it.id == actionId && it.enabled }
        val operationName = descriptor?.operation ?: actionId.value.takeIf { operation ->
            contract.operations[operation]?.itemTargeted == true
        } ?: return CommandPreparation.Invalid(mapOf(ScreenFieldId("id") to TextKey("state_error_body")))
        val operation = contract.operations.getValue(operationName)
        val optionErrors = contract.fields.filter { field ->
            operation.arguments.any { it.name == field.id.value && it.isApplicable(draft) }
        }.mapNotNull { field ->
            val value = draft[field.id].orEmpty()
            val optionIds = field.options.mapTo(mutableSetOf(), ScreenOption::id)
            val valid = when {
                value.isBlank() || optionIds.isEmpty() -> true
                field.kind == ScreenFieldKind.MULTI_CHOICE -> value.split(',').map(String::trim).all { it in optionIds }
                field.kind == ScreenFieldKind.SINGLE_CHOICE -> value in optionIds
                else -> true
            }
            if (valid) null else field.id to TextKey("validation_invalid_selection")
        }.toMap()
        if (optionErrors.isNotEmpty()) return CommandPreparation.Invalid(optionErrors)
        val draftSubject = draft[ScreenFieldId("id")]
        val creating = operationName in setOf(
            "save-batch", "save-observation", "save-recipe", "save-plan", "save-zone", "save-tray-size",
        )
        val subject = if (creating) {
            draftSubject ?: currentSubjectId
        } else currentSubjectId ?: draftSubject
        return operation.prepare(subject, draft)
    }
}

private fun contract(
        id: ScreenId,
        primary: String?,
        variants: Set<ScreenVariant>,
        acceptsStableId: Boolean = false,
        fields: List<ScreenField> = emptyList(),
        operations: List<ScreenOperationContract> = emptyList(),
        actions: List<ScreenActionDescriptor> = emptyList(),
    ): ScreenContract {
        val commandActions = operations.filterNot(ScreenOperationContract::itemTargeted).map { action(it.operation) }
        return ScreenContract(
            id = id,
            variants = variants,
            primaryOperation = primary,
            acceptsStableId = acceptsStableId,
            fields = fields,
            facts = listOf(fact(id)),
            actions = (commandActions + actions).distinctBy { it.id },
            operations = operations.associateBy(ScreenOperationContract::operation),
        )
    }

private fun variants(vararg extra: ScreenVariant) = setOf(ScreenVariant.CONTENT, *extra)
private fun choices(group: String, vararg ids: String) = ids.map { id ->
        option(id, "option_${group}_$id")
    }

private fun batchStateChoices() = choices(
        "batch_state",
        "planned", "soaking", "sown", "blackout_or_weight", "light_growth",
        "ready_for_harvest", "harvested", "failed", "abandoned",
    )

private fun stageChoices(includeNone: Boolean = false): List<ScreenOption> = buildList {
        if (includeNone) add(option("none", "option_recipe_stage_none"))
        add(option("soaking", "stage_soaking"))
        add(option("sown", "stage_sown"))
        add(option("blackout_or_weight", "stage_blackout"))
        add(option("light_growth", "stage_light_growth"))
        add(option("ready_for_harvest", "stage_harvest"))
    }

private fun argument(
        name: String,
        kind: ScreenFieldKind = ScreenFieldKind.TEXT,
        required: Boolean = true,
    ) = CommandArgument(name, kind, required)

private fun conditionalArgument(
        name: String,
        kind: ScreenFieldKind,
        conditionArgument: String,
        conditionValue: String,
    ) = CommandArgument(name, kind, required = true, requiredWhen = ArgumentCondition(conditionArgument, conditionValue))

private fun operation(
        name: String,
        subject: CommandSubjectRequirement,
        vararg arguments: CommandArgument,
        oneOf: List<Set<String>> = emptyList(),
    ) = ScreenOperationContract(name, subject, arguments.toList(), oneOf)

private fun subjectOperation(name: String, vararg arguments: CommandArgument) =
        operation(name, CommandSubjectRequirement.REQUIRED, *arguments)

private fun itemSubjectOperation(name: String) = ScreenOperationContract(
        operation = name,
        subjectRequirement = CommandSubjectRequirement.REQUIRED,
        itemTargeted = true,
    )

private fun eventOperation(
        name: String,
        vararg arguments: CommandArgument,
        extra: List<CommandArgument> = emptyList(),
        oneOf: List<Set<String>> = emptyList(),
    ) = operation(
        name,
        CommandSubjectRequirement.REQUIRED,
        *(listOf(argument("eventId"), argument("occurredAt", ScreenFieldKind.INSTANT)) + arguments + extra).toTypedArray(),
        oneOf = oneOf,
    )

private fun planOperation(name: String) = operation(
        name, CommandSubjectRequirement.REQUIRED,
        argument("anchorDate", ScreenFieldKind.LOCAL_DATE, required = false),
        argument("cadenceDays", ScreenFieldKind.POSITIVE_INTEGER),
        argument("cultureIds", ScreenFieldKind.MULTI_CHOICE),
        argument("mode", ScreenFieldKind.SINGLE_CHOICE),
        argument("paused", ScreenFieldKind.BOOLEAN, required = false),
    )

private fun recipeOperation(name: String) = operation(
        name, CommandSubjectRequirement.REQUIRED,
        argument("name"), argument("cultureId", ScreenFieldKind.SINGLE_CHOICE), argument("substrate"),
        argument("supplier", required = false), argument("cultivar", required = false),
        argument("seedLot", required = false),
        argument("germinationPercentage", ScreenFieldKind.POSITIVE_INTEGER, required = false),
        argument("stage1", ScreenFieldKind.SINGLE_CHOICE),
        argument("stage1MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG),
        argument("stage1MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG), argument("stage1Guidance", required = false),
        argument("stage2", ScreenFieldKind.SINGLE_CHOICE, required = false),
        argument("stage2MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage2MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage2Guidance", required = false),
        argument("stage3", ScreenFieldKind.SINGLE_CHOICE, required = false),
        argument("stage3MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage3MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage3Guidance", required = false),
        argument("stage4", ScreenFieldKind.SINGLE_CHOICE, required = false),
        argument("stage4MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage4MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage4Guidance", required = false),
        argument("stage5", ScreenFieldKind.SINGLE_CHOICE, required = false),
        argument("stage5MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage5MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        argument("stage5Guidance", required = false),
    )

private fun closeOperation(name: String, vararg extra: CommandArgument) = operation(
        name, CommandSubjectRequirement.REQUIRED,
        argument("occurredAt", ScreenFieldKind.INSTANT),
        argument("actualMassGrams", ScreenFieldKind.POSITIVE_DECIMAL, required = false),
        argument("assessment", ScreenFieldKind.SINGLE_CHOICE, required = false),
        argument("reason", required = false),
        argument("note", required = false), *extra,
    )

private fun zoneOperation(name: String) = operation(
        name, CommandSubjectRequirement.REQUIRED,
        argument("name"), argument("type", ScreenFieldKind.SINGLE_CHOICE),
        argument("capacity", ScreenFieldKind.POSITIVE_INTEGER),
    )

private fun trayOperation(name: String) = operation(
        name, CommandSubjectRequirement.REQUIRED,
        argument("name"), argument("shape", ScreenFieldKind.SINGLE_CHOICE),
        conditionalArgument("widthCm", ScreenFieldKind.POSITIVE_DECIMAL, "shape", "rectangle"),
        conditionalArgument("lengthCm", ScreenFieldKind.POSITIVE_DECIMAL, "shape", "rectangle"),
        conditionalArgument("diameterCm", ScreenFieldKind.POSITIVE_DECIMAL, "shape", "circle"),
    )

private fun documentOperation(name: String) = operation(
        name, CommandSubjectRequirement.NONE, argument("documentId", ScreenFieldKind.DOCUMENT),
    )

private fun navigationAction(name: String) = ScreenActionDescriptor(
        ScreenActionId(name), actionLabelKey(name), operation = null,
    )

/** Exact-operation classification; deliberately avoids prefixes and caller-provided booleans. */
object DestructiveCommandClassifier {
    private val operations = mapOf(
        "correct-batch" to DestructiveOperation.SAVE_BATCH_CORRECTION,
        "save-batch-correction" to DestructiveOperation.SAVE_BATCH_CORRECTION,
        "undo-stage" to DestructiveOperation.UNDO_LAST_TRANSITION,
        "undo-last-transition" to DestructiveOperation.UNDO_LAST_TRANSITION,
        "delete-zone" to DestructiveOperation.DELETE_ZONE,
        "delete-tray-size" to DestructiveOperation.DELETE_TRAY_PRESET,
        "delete-tray-preset" to DestructiveOperation.DELETE_TRAY_PRESET,
        "delete-recipe" to DestructiveOperation.DELETE_RECIPE,
        "delete-batch" to DestructiveOperation.DELETE_BATCH,
        "delete-planned-batch" to DestructiveOperation.DELETE_BATCH,
        "permanently-delete-batch" to DestructiveOperation.DELETE_BATCH,
        "delete-plan" to DestructiveOperation.DELETE_PLAN,
        "delete-observation" to DestructiveOperation.DELETE_OBSERVATION,
        "restore-backup" to DestructiveOperation.REPLACE_RESTORE,
        "replace-restore" to DestructiveOperation.REPLACE_RESTORE,
        "erase-all" to DestructiveOperation.DELETE_ALL_DATA,
        "delete-all-data" to DestructiveOperation.DELETE_ALL_DATA,
        "discard-changes" to DestructiveOperation.DISCARD_CHANGES,
        "discard-batch-changes" to DestructiveOperation.DISCARD_CHANGES,
        "discard-recipe-changes" to DestructiveOperation.DISCARD_CHANGES,
        "discard-observation-changes" to DestructiveOperation.DISCARD_CHANGES,
        "discard-zone-changes" to DestructiveOperation.DISCARD_CHANGES,
        "discard-tray-preset-changes" to DestructiveOperation.DISCARD_CHANGES,
    )

    fun classify(operation: String): DestructiveOperation? = operations[operation]
}
