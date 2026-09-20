package com.sedsoftware.sproutpace.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Duration

enum class GrowStage { SOAKING, SOWN, BLACKOUT_OR_WEIGHT, LIGHT_GROWTH, READY_FOR_HARVEST }

data class DurationRange(val minimum: Duration, val maximum: Duration) {
    init {
        require(!minimum.isNegative() && !maximum.isNegative())
        require(minimum <= maximum)
    }
}

data class StageGuidance(
    val stage: GrowStage,
    val duration: DurationRange,
    val instructionKey: LocalizationKey,
)

data class SourceMetadata(
    val url: String,
    val titleKey: LocalizationKey,
    val reviewedOn: LocalDate,
) {
    init { require(url.startsWith("https://")) }
}

data class SeedDensity(
    val gramsPerSquareCentimetre: Double,
    val version: Int,
    val source: SourceMetadata,
) {
    init {
        require(gramsPerSquareCentimetre.isFinite() && gramsPerSquareCentimetre > 0.0)
        require(version > 0)
    }
}

data class BuiltInCulture(
    val id: CultureId,
    val nameKey: LocalizationKey,
    val scientificNameKey: LocalizationKey,
    val illustrationResourceName: String,
    val stages: List<StageGuidance>,
    val seedDensity: SeedDensity,
    val substrateNoteKey: LocalizationKey,
    val lightingNoteKey: LocalizationKey,
    val safetyNoteKeys: List<LocalizationKey>,
    val sources: List<SourceMetadata>,
) {
    init {
        require(illustrationResourceName.matches(Regex("[a-z][a-z0-9_]*")))
        require(stages.isNotEmpty() && stages.map { it.stage }.distinct().size == stages.size)
        require(safetyNoteKeys.isNotEmpty())
        require(sources.isNotEmpty())
    }
}

data class RecipeStage(
    val stage: GrowStage,
    val duration: DurationRange,
    val guidanceKey: LocalizationKey? = null,
    val userGuidance: String? = null,
) {
    init { require(guidanceKey != null || !userGuidance.isNullOrBlank()) }
}

data class UserGrowRecipe(
    val id: RecipeId,
    val name: String,
    val cultureId: CultureId?,
    val stages: List<RecipeStage>,
    val density: SeedDensity,
    val substrate: String,
    val supplier: String? = null,
    val cultivar: String? = null,
    val seedLot: String? = null,
    val germinationPercentage: Percentage? = null,
) {
    init {
        require(name.isNotBlank())
        require(stages.isNotEmpty())
        require(stages.map { it.stage }.distinct().size == stages.size)
        require(substrate.isNotBlank())
    }
}

data class RecipeSnapshot(
    val sourceRecipeId: RecipeId?,
    val builtInCultureId: CultureId?,
    val nameSnapshot: String?,
    val stages: List<RecipeStage>,
    val density: SeedDensity,
) {
    init {
        require(sourceRecipeId != null || builtInCultureId != null)
        require(stages.isNotEmpty())
    }
}

data class TraySnapshot(val nameSnapshot: String, val shape: TrayShape) {
    init { require(nameSnapshot.isNotBlank()) }
}
