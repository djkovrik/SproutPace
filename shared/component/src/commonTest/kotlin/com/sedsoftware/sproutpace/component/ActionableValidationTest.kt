package com.sedsoftware.sproutpace.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class ActionableValidationTest {
    @Test
    fun missingFieldsExplainWhetherToEnterANameOrSelectAnOption() {
        val result = contract(
            CommandArgument("name"),
            CommandArgument("cultureId", ScreenFieldKind.SINGLE_CHOICE),
        ).prepare(null, emptyMap())
        val errors = assertIs<CommandPreparation.Invalid>(result).errors
        assertEquals(TextKey("validation_required_name"), errors[ScreenFieldId("name")])
        assertEquals(TextKey("validation_required_selection"), errors[ScreenFieldId("cultureId")])
    }

    @Test
    fun malformedNumbersAndDatesExplainTheirExpectedType() {
        val cases = listOf(
            Triple(ScreenFieldKind.POSITIVE_INTEGER, "0", "validation_positive_integer"),
            Triple(ScreenFieldKind.POSITIVE_DECIMAL, "NaN", "validation_positive_number"),
            Triple(ScreenFieldKind.NON_NEGATIVE_LONG, "-1", "validation_non_negative_integer"),
            Triple(ScreenFieldKind.LOCAL_DATE, "2026-02-30", "validation_valid_date"),
            Triple(ScreenFieldKind.LOCAL_DATE_TIME, "2026-01-01T99:00", "validation_valid_date_time"),
        )
        cases.forEach { (kind, value, key) ->
            val result = contract(CommandArgument("value", kind)).prepare(null, draft("value" to value))
            assertEquals(TextKey(key), assertIs<CommandPreparation.Invalid>(result).errors[ScreenFieldId("value")])
        }
    }

    @Test
    fun hiddenShapeDimensionIsNeitherValidatedNorSubmitted() {
        val operation = contract(
            CommandArgument("shape", ScreenFieldKind.SINGLE_CHOICE),
            CommandArgument("widthCm", ScreenFieldKind.POSITIVE_DECIMAL,
                requiredWhen = ArgumentCondition("shape", "rectangle")),
            CommandArgument("diameterCm", ScreenFieldKind.POSITIVE_DECIMAL,
                requiredWhen = ArgumentCondition("shape", "circle")),
        )
        val result = operation.prepare(null, draft("shape" to "circle", "diameterCm" to "12", "widthCm" to "bad"))
        val command = assertIs<CommandPreparation.Ready>(result).command
        assertFalse("widthCm" in command.arguments)
        assertEquals("12", command.arguments["diameterCm"])
    }

    @Test
    fun selectedStageRequiresOrderedRangeButNoneIgnoresHiddenRange() {
        val operation = contract(
            CommandArgument("stage2", ScreenFieldKind.SINGLE_CHOICE, required = false),
            CommandArgument("stage2MinimumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
            CommandArgument("stage2MaximumHours", ScreenFieldKind.NON_NEGATIVE_LONG, required = false),
        )
        val invalid = operation.prepare(null, draft(
            "stage2" to "soaking", "stage2MinimumHours" to "12", "stage2MaximumHours" to "6",
        ))
        assertEquals(TextKey("validation_maximum_at_least_minimum"),
            assertIs<CommandPreparation.Invalid>(invalid).errors[ScreenFieldId("stage2MaximumHours")])
        val hidden = operation.prepare(null, draft(
            "stage2" to "none", "stage2MinimumHours" to "invalid", "stage2MaximumHours" to "invalid",
        ))
        assertEquals(mapOf("stage2" to "none"), assertIs<CommandPreparation.Ready>(hidden).command.arguments)
    }

    @Test
    fun batchRequiresRecipeOrCultureWithAnActionableChoice() {
        val operation = contract(
            CommandArgument("recipeId", required = false), CommandArgument("cultureId", required = false),
        ).copy(oneOfArguments = listOf(setOf("recipeId", "cultureId")))
        val errors = assertIs<CommandPreparation.Invalid>(operation.prepare(null, emptyMap())).errors
        assertEquals(setOf(TextKey("validation_recipe_or_culture")), errors.values.toSet())
    }

    @Test
    fun notificationTimeRejectsInvalidClockTimeBeforePersistence() {
        val result = contract(CommandArgument("summaryTime")).prepare(null, draft("summaryTime" to "25:30"))
        assertEquals(TextKey("validation_valid_time"),
            assertIs<CommandPreparation.Invalid>(result).errors[ScreenFieldId("summaryTime")])
    }

    @Test
    fun failedOutcomeIgnoresHiddenHarvestMassAndAssessment() {
        val operation = contract(CommandArgument("outcome"), CommandArgument("reason"),
            CommandArgument("actualMassGrams", ScreenFieldKind.POSITIVE_DECIMAL, required = false),
            CommandArgument("assessment", required = false))
        val result = operation.prepare(null, draft("outcome" to "failed", "reason" to "Mold",
            "actualMassGrams" to "invalid", "assessment" to "old assessment"))
        assertEquals(mapOf("outcome" to "failed", "reason" to "Mold"),
            assertIs<CommandPreparation.Ready>(result).command.arguments)
    }

    private fun contract(vararg arguments: CommandArgument) =
        ScreenOperationContract("save-test", CommandSubjectRequirement.NONE, arguments.toList())

    private fun draft(vararg values: Pair<String, String>) = values.associate { ScreenFieldId(it.first) to it.second }
}
