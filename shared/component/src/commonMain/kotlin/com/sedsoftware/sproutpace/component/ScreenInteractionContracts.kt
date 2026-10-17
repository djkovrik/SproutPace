package com.sedsoftware.sproutpace.component

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

data class ScreenFieldId(val value: String) {
    init { require(value.matches(Regex("[a-z][A-Za-z0-9]*"))) }
}

const val PENDING_PHOTO_PREFIX: String = "pending-photo:"
const val REMOVE_PHOTO_VALUE: String = "remove-photo"

fun pendingPhotoDraftValue(token: String): String {
    require(token.isNotBlank() && '/' !in token && '\\' !in token)
    return PENDING_PHOTO_PREFIX + token
}

fun pendingPhotoToken(value: String?): String? = value
    ?.takeIf { it.startsWith(PENDING_PHOTO_PREFIX) }
    ?.removePrefix(PENDING_PHOTO_PREFIX)
    ?.takeIf(String::isNotBlank)

data class ScreenActionId(val value: String) {
    init { require(value.matches(Regex("[a-z][a-z0-9-]*"))) }
}

enum class ScreenFieldKind {
    TEXT,
    POSITIVE_INTEGER,
    POSITIVE_LONG,
    NON_NEGATIVE_LONG,
    POSITIVE_DECIMAL,
    BOOLEAN,
    LOCAL_DATE,
    LOCAL_DATE_TIME,
    INSTANT,
    SINGLE_CHOICE,
    MULTI_CHOICE,
    DOCUMENT,
}

data class ScreenOption(
    val id: String,
    val labelKey: TextKey? = null,
    val authoredLabel: String? = null,
    val batchIdentity: BatchIdentity? = null,
    val trayShape: com.sedsoftware.sproutpace.domain.model.TrayShape? = null,
    val seedDensity: com.sedsoftware.sproutpace.domain.model.SeedDensity? = null,
) {
    init {
        require(id.isNotBlank())
        require(labelKey != null || !authoredLabel.isNullOrBlank())
    }
}

data class ScreenField(
    val id: ScreenFieldId,
    val labelKey: TextKey,
    val kind: ScreenFieldKind,
    val value: String = "",
    val required: Boolean = false,
    val readOnly: Boolean = false,
    val options: List<ScreenOption> = emptyList(),
) {
    init {
        require(options.map(ScreenOption::id).distinct().size == options.size)
        require(kind == ScreenFieldKind.SINGLE_CHOICE || kind == ScreenFieldKind.MULTI_CHOICE || options.isEmpty())
    }
}

data class ScreenFact(
    val id: String,
    val labelKey: TextKey,
    val valueKey: TextKey? = null,
    val authoredValue: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(valueKey != null || !authoredValue.isNullOrBlank())
    }
}

data class ScreenActionDescriptor(
    val id: ScreenActionId,
    val labelKey: TextKey,
    val operation: String? = null,
    val enabled: Boolean = true,
)

enum class CommandSubjectRequirement { NONE, OPTIONAL, REQUIRED }

data class ArgumentCondition(val argument: String, val equals: String)

data class CommandArgument(
    val name: String,
    val kind: ScreenFieldKind = ScreenFieldKind.TEXT,
    val required: Boolean = true,
    val requiredWhen: ArgumentCondition? = null,
) {
    init { require(name.matches(Regex("[a-z][A-Za-z0-9]*"))) }
}

data class ScreenOperationContract(
    val operation: String,
    val subjectRequirement: CommandSubjectRequirement,
    val arguments: List<CommandArgument> = emptyList(),
    val oneOfArguments: List<Set<String>> = emptyList(),
    val itemTargeted: Boolean = false,
) {
    init {
        require(operation.matches(Regex("[a-z][a-z0-9-]*")))
        require(!itemTargeted || subjectRequirement == CommandSubjectRequirement.REQUIRED)
        require(arguments.map(CommandArgument::name).distinct().size == arguments.size)
        val names = arguments.mapTo(mutableSetOf(), CommandArgument::name)
        require(oneOfArguments.flatten().all { it in names })
    }
}

sealed interface CommandPreparation {
    data class Ready(val command: ScreenCommand) : CommandPreparation
    data class Invalid(val errors: Map<ScreenFieldId, TextKey>) : CommandPreparation
}

internal fun ScreenOperationContract.prepare(
    subjectId: String?,
    draft: Map<ScreenFieldId, String>,
): CommandPreparation {
    val normalizedSubject = subjectId?.trim()?.takeIf(String::isNotEmpty)
    val errors = linkedMapOf<ScreenFieldId, TextKey>()
    if (subjectRequirement == CommandSubjectRequirement.REQUIRED && normalizedSubject == null) {
        errors[ScreenFieldId("id")] = TextKey("validation_required_selection")
    }
    val applicableArguments = arguments.filter { it.isApplicable(draft) }
    applicableArguments.forEach { argument ->
        val fieldId = ScreenFieldId(argument.name)
        val value = argument.normalizedValue(draft[fieldId].orEmpty())
        val stageDuration = Regex("^stage[1-5](MinimumHours|MaximumHours)$").matches(argument.name)
        if ((argument.required || stageDuration) && value.isBlank()) {
            errors[fieldId] = argument.requiredError()
        } else if (value.isNotEmpty() && !argument.kind.accepts(value)) {
            errors[fieldId] = argument.kind.invalidError()
        } else if (argument.name == "summaryTime" && value.isNotEmpty() &&
            runCatching { LocalTime.parse(value) }.isFailure
        ) {
            errors[fieldId] = TextKey("validation_valid_time")
        }
    }
    validateAlternatives(draft, errors)
    applicableArguments.validateRanges(draft, errors)
    if (errors.isNotEmpty()) return CommandPreparation.Invalid(errors)

    val arguments = applicableArguments.mapNotNull { argument ->
        draft[ScreenFieldId(argument.name)]?.let(argument::normalizedValue)?.takeIf(String::isNotEmpty)
            ?.let { argument.name to it }
    }.toMap()
    val deduplicationKey = buildList {
        add(operation)
        normalizedSubject?.let(::add)
        arguments.entries.sortedBy { it.key }.forEach { (key, value) -> add("$key=$value") }
    }.joinToString(":")
    return CommandPreparation.Ready(
        ScreenCommand(operation, normalizedSubject, arguments, deduplicationKey).newInvocation(),
    )
}

/** Authored content is data: whitespace is significant, unlike identifiers and numeric controls. */
private fun CommandArgument.normalizedValue(value: String): String =
    if (name in AUTHORED_ARGUMENTS || name.matches(Regex("stage[1-5]Guidance"))) value else value.trim()

private val AUTHORED_ARGUMENTS = setOf(
    "name", "zoneName", "darkZoneName", "trayName", "substrate", "supplier", "cultivar", "seedLot",
    "text", "reason", "note", "priorZoneName",
)

private fun ScreenOperationContract.validateAlternatives(
    draft: Map<ScreenFieldId, String>,
    errors: MutableMap<ScreenFieldId, TextKey>,
) {
    oneOfArguments.forEach { alternatives ->
        val hasSelection = alternatives.any { name ->
            val argument = arguments.first { it.name == name }
            val value = draft[ScreenFieldId(name)].orEmpty()
            if (argument.kind == ScreenFieldKind.BOOLEAN) value == "true" else value.isNotBlank()
        }
        if (!hasSelection) {
            val key = if (alternatives == setOf("recipeId", "cultureId")) {
                "validation_recipe_or_culture"
            } else {
                "validation_required_selection"
            }
            alternatives.forEach { errors[ScreenFieldId(it)] = TextKey(key) }
        }
    }
}

private fun List<CommandArgument>.validateRanges(
    draft: Map<ScreenFieldId, String>,
    errors: MutableMap<ScreenFieldId, TextKey>,
) {
    filter { it.name.endsWith("MaximumHours") }.forEach { maximum ->
        val minimumId = ScreenFieldId(maximum.name.replace("MaximumHours", "MinimumHours"))
        val maximumId = ScreenFieldId(maximum.name)
        val minimumValue = draft[minimumId]?.toLongOrNull()
        val maximumValue = draft[maximumId]?.toLongOrNull()
        if (minimumId in errors || maximumId in errors) return@forEach
        if (minimumValue != null && maximumValue != null && maximumValue < minimumValue) {
            errors[maximumId] = TextKey("validation_maximum_at_least_minimum")
        }
    }
}

internal fun CommandArgument.isApplicable(draft: Map<ScreenFieldId, String>): Boolean {
    val outcome = draft[ScreenFieldId("outcome")]
    if (name in setOf("actualMassGrams", "assessment") && outcome in setOf("failed", "abandoned")) return false
    if (name == "reason" && outcome == "harvested") return false
    val conditionMet = requiredWhen?.let { condition ->
        draft[ScreenFieldId(condition.argument)].orEmpty().equals(condition.equals, ignoreCase = true)
    } ?: true
    val stage = Regex("^(stage[1-5])(MinimumHours|MaximumHours|Guidance)$").matchEntire(name)?.groupValues?.get(1)
    val stageSelected = stage == null || draft[ScreenFieldId(stage)].orEmpty().let { it.isNotBlank() && it != "none" }
    return conditionMet && stageSelected
}

private fun CommandArgument.requiredError(): TextKey = TextKey(
    when {
        name in setOf("name", "zoneName", "trayName") -> "validation_required_name"
        kind in setOf(ScreenFieldKind.SINGLE_CHOICE, ScreenFieldKind.MULTI_CHOICE) -> "validation_required_selection"
        else -> "validation_required_value"
    },
)

private fun ScreenFieldKind.invalidError(): TextKey = TextKey(
    when (this) {
        ScreenFieldKind.POSITIVE_INTEGER, ScreenFieldKind.POSITIVE_LONG -> "validation_positive_integer"
        ScreenFieldKind.NON_NEGATIVE_LONG -> "validation_non_negative_integer"
        ScreenFieldKind.POSITIVE_DECIMAL -> "validation_positive_number"
        ScreenFieldKind.LOCAL_DATE -> "validation_valid_date"
        ScreenFieldKind.LOCAL_DATE_TIME, ScreenFieldKind.INSTANT -> "validation_valid_date_time"
        ScreenFieldKind.BOOLEAN, ScreenFieldKind.SINGLE_CHOICE, ScreenFieldKind.MULTI_CHOICE -> "validation_invalid_selection"
        else -> "validation_required_value"
    },
)

private fun ScreenFieldKind.accepts(value: String): Boolean = when (this) {
    ScreenFieldKind.TEXT -> true
    ScreenFieldKind.POSITIVE_INTEGER -> value.toIntOrNull()?.let { it > 0 } == true
    ScreenFieldKind.POSITIVE_LONG -> value.toLongOrNull()?.let { it > 0L } == true
    ScreenFieldKind.NON_NEGATIVE_LONG -> value.toLongOrNull()?.let { it >= 0L } == true
    ScreenFieldKind.POSITIVE_DECIMAL -> value.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
    ScreenFieldKind.BOOLEAN -> value.toBooleanStrictOrNull() != null
    ScreenFieldKind.LOCAL_DATE -> runCatching { LocalDate.parse(value) }.isSuccess
    ScreenFieldKind.LOCAL_DATE_TIME -> runCatching { LocalDateTime.parse(value) }.isSuccess
    ScreenFieldKind.INSTANT -> runCatching { Instant.parse(value) }.isSuccess
    else -> value.isNotBlank()
}

internal fun field(
    id: String,
    kind: ScreenFieldKind = ScreenFieldKind.TEXT,
    required: Boolean = false,
    options: List<ScreenOption> = emptyList(),
    labelKey: String? = null,
) = ScreenField(
    ScreenFieldId(id),
    TextKey(labelKey ?: fieldLabelKey(id)),
    kind,
    required = required,
    options = options,
)

internal fun option(id: String, labelKey: String) = ScreenOption(id, TextKey(labelKey))

internal fun fact(screen: ScreenId) = ScreenFact(
    id = "${screen.name.lowercase()}-summary",
    labelKey = TextKey("screen_${screen.name.lowercase()}_title"),
    valueKey = TextKey("screen_${screen.name.lowercase()}_title"),
)

internal fun action(operation: String) = ScreenActionDescriptor(
    id = ScreenActionId(operation),
    labelKey = actionLabelKey(operation),
    operation = operation,
)

private fun fieldLabelKey(id: String): String =
    FIELD_LABEL_KEYS[id] ?: error("No semantic TextKey registered for field '$id'")

private val FIELD_LABEL_KEYS = mapOf(
    "id" to "field_record_id",
    "zoneName" to "field_zone_name",
    "type" to "field_zone_type",
    "capacity" to "field_zone_capacity",
    "trayName" to "field_tray_name",
    "shape" to "field_tray_shape",
    "cadenceDays" to "field_plan_cadence_days",
    "cultureIds" to "field_plan_culture_ids",
    "mode" to "field_plan_mode",
    "dueKind" to "field_due_kind",
    "dueAt" to "field_due_at",
    "localDateTime" to "field_local_date_time",
    "timeZoneId" to "field_time_zone",
    "newId" to "field_new_id",
    "recipeId" to "field_recipe_selector",
    "cultureId" to "field_culture_selector",
    "trayId" to "field_tray_selector",
    "substrate" to "field_substrate",
    "actualSeedMassGrams" to "field_actual_seed_mass",
    "zoneId" to "field_zone_selector",
    "anchorDate" to "field_plan_anchor_date",
    "anchorState" to "field_anchor_state",
    "anchorAt" to "field_anchor_at",
    "plannedSowDate" to "field_planned_sow_date",
    "fixedSoakingDate" to "field_fixed_soaking_date",
    "fixedSownDate" to "field_fixed_sown_date",
    "fixedBlackoutDate" to "field_fixed_blackout_date",
    "fixedLightDate" to "field_fixed_light_date",
    "fixedHarvestDate" to "field_fixed_harvest_date",
    "actualSoakingAt" to "field_actual_soaking_at",
    "actualSownAt" to "field_actual_sown_at",
    "actualBlackoutAt" to "field_actual_blackout_at",
    "actualLightAt" to "field_actual_light_at",
    "actualHarvestReadyAt" to "field_actual_harvest_ready_at",
    "eventId" to "field_event_id",
    "occurredAt" to "field_occurred_at",
    "nextState" to "field_next_state",
    "unassigned" to "field_leave_unassigned",
    "skippedState" to "field_skipped_state",
    "reason" to "field_reason",
    "resultingState" to "field_resulting_state",
    "actionId" to "field_scheduled_action_selector",
    "actualMassGrams" to "field_actual_harvest_mass",
    "query" to "field_catalog_query",
    "name" to "field_name",
    "supplier" to "field_seed_supplier",
    "cultivar" to "field_cultivar",
    "seedLot" to "field_seed_lot",
    "germinationPercentage" to "field_germination_percentage",
    "stage1" to "field_recipe_stage_1",
    "stage2" to "field_recipe_stage_2",
    "stage3" to "field_recipe_stage_3",
    "stage4" to "field_recipe_stage_4",
    "stage5" to "field_recipe_stage_5",
    "stage1MinimumHours" to "field_stage_1_minimum_hours",
    "stage1MaximumHours" to "field_stage_1_maximum_hours",
    "stage1Guidance" to "field_stage_1_guidance",
    "stage2MinimumHours" to "field_stage_2_minimum_hours",
    "stage2MaximumHours" to "field_stage_2_maximum_hours",
    "stage2Guidance" to "field_stage_2_guidance",
    "stage3MinimumHours" to "field_stage_3_minimum_hours",
    "stage3MaximumHours" to "field_stage_3_maximum_hours",
    "stage3Guidance" to "field_stage_3_guidance",
    "stage4MinimumHours" to "field_stage_4_minimum_hours",
    "stage4MaximumHours" to "field_stage_4_maximum_hours",
    "stage4Guidance" to "field_stage_4_guidance",
    "stage5MinimumHours" to "field_stage_5_minimum_hours",
    "stage5MaximumHours" to "field_stage_5_maximum_hours",
    "stage5Guidance" to "field_stage_5_guidance",
    "batchId" to "field_batch_selector",
    "text" to "field_observation_text",
    "photoId" to "field_observation_photo",
    "outcome" to "field_outcome",
    "assessment" to "field_outcome_assessment",
    "note" to "field_outcome_note",
    "theme" to "field_theme",
    "enabled" to "field_enabled",
    "paused" to "field_plan_paused",
    "widthCm" to "field_tray_width_cm",
    "lengthCm" to "field_tray_length_cm",
    "diameterCm" to "field_tray_diameter_cm",
    "documentId" to "field_document",
    "choice" to "field_advertising_choice",
    "expiresAtEpochMillis" to "field_consent_expiry",
    "policyVersion" to "field_policy_version",
)

internal fun actionLabelKey(operation: String): TextKey = TextKey(
    "action_${operation.replace('-', '_')}",
)
