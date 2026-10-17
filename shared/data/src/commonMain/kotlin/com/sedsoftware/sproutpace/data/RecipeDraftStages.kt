package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.domain.engine.RecipeStageConfiguration
import com.sedsoftware.sproutpace.domain.model.BuiltInCulture
import com.sedsoftware.sproutpace.domain.model.DurationRange
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlin.time.Duration.Companion.hours

/** Preserve locale-neutral inherited guidance when only recipe metadata or durations change. */
internal fun ScreenCommand.recipeDraftStages(existing: UserGrowRecipe?, culture: BuiltInCulture): List<RecipeStage> {
    val stages = (1..RECIPE_STAGE_SLOTS).mapNotNull { slot ->
        val rawStage = optional("stage$slot")?.takeUnless { it == "none" } ?: return@mapNotNull null
        val stage = enumValue<GrowStage>(rawStage)
        val minimum = optional("stage${slot}MinimumHours")?.toLongOrNull()
            ?: error("stage${slot}MinimumHours is required")
        val maximum = optional("stage${slot}MaximumHours")?.toLongOrNull()
            ?: error("stage${slot}MaximumHours is required")
        require(minimum >= 0L && maximum >= minimum) { "Invalid duration for stage $slot" }
        val previous = existing?.stages?.firstOrNull { it.stage == stage }
        val authored = arguments["stage${slot}Guidance"]?.takeIf { it.isNotBlank() } ?: previous?.userGuidance
        val inheritedKey = if (authored == null) {
            previous?.guidanceKey ?: culture.stages.firstOrNull { it.stage == stage }?.instructionKey
        } else null
        RecipeStage(stage, DurationRange(minimum.hours, maximum.hours), inheritedKey, authored)
    }
    if (!RecipeStageConfiguration.isValid(stages)) throw LocalDataException(LocalDataErrorKey.INVALID_RECIPE_STAGES)
    return stages
}

private const val RECIPE_STAGE_SLOTS = 5
