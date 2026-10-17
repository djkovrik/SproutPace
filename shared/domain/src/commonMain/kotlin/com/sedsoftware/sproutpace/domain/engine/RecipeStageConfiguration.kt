package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.RecipeStage

/** Authored recipes follow the cultivation lifecycle; historical snapshots stay readable. */
object RecipeStageConfiguration {
    fun isValid(stages: List<RecipeStage>): Boolean {
        val configured = stages.map { it.stage }
        return configured.containsAll(required) &&
            configured.zipWithNext().all { (previous, next) -> previous.ordinal < next.ordinal }
    }

    private val required = setOf(GrowStage.SOWN, GrowStage.LIGHT_GROWTH, GrowStage.READY_FOR_HARVEST)
}
