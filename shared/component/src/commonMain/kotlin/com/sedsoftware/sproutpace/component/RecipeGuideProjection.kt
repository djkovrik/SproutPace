package com.sedsoftware.sproutpace.component

/** Locale-neutral guide metadata. User-authored content remains verbatim. */
data class RecipeGuideProjection(
    val readOnly: Boolean,
    val cultureId: String?,
    val scientificNameKey: TextKey? = null,
    val stages: List<RecipeStageProjection>,
    val gramsPerSquareCentimetre: Double,
    val version: Int,
    val reviewedOn: String,
    val sources: List<RecipeSourceProjection>,
    val reviewOutdated: Boolean = false,
    val substrateKey: TextKey? = null,
    val authoredSubstrate: String? = null,
    val lightingKey: TextKey? = null,
    val safetyKeys: List<TextKey> = emptyList(),
)

data class RecipeStageProjection(
    val titleKey: TextKey,
    val minimumHours: Long,
    val maximumHours: Long,
    val guidanceKey: TextKey? = null,
    val authoredGuidance: String? = null,
)

data class RecipeSourceProjection(val titleKey: TextKey, val url: String, val reviewedOn: String)
