package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.engine.RecipeStageConfiguration
import com.sedsoftware.sproutpace.domain.model.DurationRange
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class RecipeStageConfigurationTest {
    @Test fun onlyOptionalStagesCanBeOmittedAndReorderingOrDuplicationIsRejected() {
        // All 32 subsets exercise required/optional stage boundaries.
        repeat(1 shl GrowStage.entries.size) { mask ->
            val stages = GrowStage.entries.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val expected = stages.containsAll(listOf(GrowStage.SOWN, GrowStage.LIGHT_GROWTH, GrowStage.READY_FOR_HARVEST))
            val configured = stages.map { RecipeStage(it, DurationRange(1.hours, 2.hours), userGuidance = "Guidance") }
            if (expected) assertTrue(RecipeStageConfiguration.isValid(configured))
            else assertFalse(RecipeStageConfiguration.isValid(configured))
            if (expected) {
                assertFalse(RecipeStageConfiguration.isValid(configured.reversed()))
                assertFalse(RecipeStageConfiguration.isValid(configured + configured.last()))
            }
        }
    }
}
