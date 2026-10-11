package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.RecipeGuideProjection
import com.sedsoftware.sproutpace.component.RecipeSourceProjection
import com.sedsoftware.sproutpace.component.RecipeStageProjection
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.BuiltInCulture
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.SourceMetadata
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe

internal fun BuiltInCulture.guideProjection() = RecipeGuideProjection(
    readOnly = true,
    cultureId = id.value,
    scientificNameKey = TextKey(scientificNameKey.value),
    stages = stages.map { stage ->
        RecipeStageProjection(stage.stage.recipeStageKey(), stage.duration.minimum.inWholeHours,
            stage.duration.maximum.inWholeHours, TextKey(stage.instructionKey.value))
    },
    gramsPerSquareCentimetre = seedDensity.gramsPerSquareCentimetre,
    version = seedDensity.version,
    reviewedOn = sources.minOf { it.reviewedOn }.toString(),
    sources = sources.map(SourceMetadata::guideProjection),
    substrateKey = TextKey(substrateNoteKey.value),
    lightingKey = TextKey(lightingNoteKey.value),
    safetyKeys = safetyNoteKeys.map { TextKey(it.value) },
)

internal fun UserGrowRecipe.guideProjection() = RecipeGuideProjection(
    readOnly = false,
    cultureId = cultureId?.value,
    stages = stages.map { stage ->
        RecipeStageProjection(stage.stage.recipeStageKey(), stage.duration.minimum.inWholeHours,
            stage.duration.maximum.inWholeHours, stage.guidanceKey?.let { TextKey(it.value) }, stage.userGuidance)
    },
    gramsPerSquareCentimetre = density.gramsPerSquareCentimetre,
    version = density.version,
    reviewedOn = density.source.reviewedOn.toString(),
    sources = listOf(density.source.guideProjection()),
    reviewOutdated = density.source.reviewedOn < SproutPaceCatalog.reviewedOn,
    authoredSubstrate = substrate,
)

private fun SourceMetadata.guideProjection() = RecipeSourceProjection(TextKey(titleKey.value), url, reviewedOn.toString())

private fun GrowStage.recipeStageKey() = TextKey(when (this) {
    GrowStage.SOAKING -> "stage_soaking"
    GrowStage.SOWN -> "stage_sown"
    GrowStage.BLACKOUT_OR_WEIGHT -> "stage_blackout"
    GrowStage.LIGHT_GROWTH -> "stage_light_growth"
    GrowStage.READY_FOR_HARVEST -> "stage_harvest"
})
