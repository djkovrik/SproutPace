package com.sedsoftware.sproutpace.domain.model

import kotlinx.datetime.Instant

data class Observation(
    val id: ObservationId,
    val batchId: BatchId,
    val occurredAt: Instant,
    val text: String,
    val photoId: PhotoId? = null,
)

data class BackupManifest(
    val schemaVersion: Int,
    val archiveVersion: Int,
    val recordCounts: Map<String, Int>,
    val photoIntegrity: Map<PhotoId, String>,
) {
    init {
        require(schemaVersion > 0 && archiveVersion > 0)
        require(recordCounts.values.all { it >= 0 })
        require(photoIntegrity.values.all { it.matches(Regex("[a-f0-9]{64}")) })
    }
}

data class ProductSnapshot(
    val manifest: BackupManifest,
    val zones: List<GrowZone>,
    val trayPresets: List<TraySizePreset>,
    val recipes: List<UserGrowRecipe>,
    val batches: List<GrowBatch>,
    val plans: List<HarvestPlan>,
    val observations: List<Observation>,
)

sealed interface RestoreValidation {
    data class Valid(val snapshot: ProductSnapshot) : RestoreValidation
    data class Invalid(val reasons: List<String>) : RestoreValidation {
        init { require(reasons.isNotEmpty()) }
    }
}
