package com.sedsoftware.sproutpace.component.integration

import com.sedsoftware.sproutpace.component.ScreenActionDescriptor
import com.sedsoftware.sproutpace.component.ScreenFact
import com.sedsoftware.sproutpace.component.ScreenField
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine

/** Recompute guidance from retained selections; the user's actual-mass field remains their own input. */
fun List<ScreenFact>.withDraftSeedRecommendation(
    screen: ScreenId,
    fields: List<ScreenField>,
    actions: List<ScreenActionDescriptor>,
): List<ScreenFact> {
    if (screen != ScreenId.BATCH_EDITOR || actions.none { it.id.value == "save-batch" }) return this
    val tray = fields.firstOrNull { it.id.value == "trayId" }
    val recipe = fields.firstOrNull { it.id.value == "recipeId" && it.value.isNotBlank() }
        ?: fields.firstOrNull { it.id.value == "cultureId" }
    val shape = tray?.options?.firstOrNull { it.id == tray.value }?.trayShape
    val density = recipe?.options?.firstOrNull { it.id == recipe.value }?.seedDensity
    val recommendation = if (shape != null && density != null) ScreenFact(
        "recommended-seed-mass", TextKey("field_recommended_seed_mass"),
        authoredValue = MeasurementEngine.recommendedSeedMass(shape, density).value.toString(),
    ) else null
    return filterNot { it.id == "recommended-seed-mass" } + listOfNotNull(recommendation)
}
