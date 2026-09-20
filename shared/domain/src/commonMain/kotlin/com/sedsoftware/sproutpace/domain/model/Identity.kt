package com.sedsoftware.sproutpace.domain.model

import kotlin.jvm.JvmInline

private const val MAX_PERCENTAGE = 100

@JvmInline value class GrowZoneId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class TraySizePresetId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class CultureId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class RecipeId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class BatchId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class StageEventId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class ScheduledActionId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class HarvestPlanId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class ObservationId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class PhotoId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class LocalizationKey(val value: String) { init { require(value.matches(Regex("[a-z][a-z0-9_]*"))) } }

data class PositiveGrams(val value: Double) {
    init { require(value.isFinite() && value > 0.0) }
}

data class Percentage(val value: Int) {
    init { require(value in 0..MAX_PERCENTAGE) }
}
