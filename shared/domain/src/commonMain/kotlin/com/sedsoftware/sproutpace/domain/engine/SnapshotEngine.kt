package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RestoreValidation

object SnapshotEngine {
    fun orderedObservations(observations: List<Observation>): List<Observation> =
        observations.sortedWith(compareBy<Observation> { it.occurredAt }.thenBy { it.id.value })

    fun validate(snapshot: ProductSnapshot): RestoreValidation {
        val reasons = buildList {
            val batchIds = snapshot.batches.mapTo(mutableSetOf()) { it.id }
            if (snapshot.observations.any { it.batchId !in batchIds }) add("observation_without_batch")
            val expected = mapOf(
                "zones" to snapshot.zones.size,
                "trayPresets" to snapshot.trayPresets.size,
                "recipes" to snapshot.recipes.size,
                "batches" to snapshot.batches.size,
                "plans" to snapshot.plans.size,
                "observations" to snapshot.observations.size,
            )
            if (expected.any { (key, count) -> snapshot.manifest.recordCounts[key] != count }) add("record_count_mismatch")
            val photoIds = snapshot.observations.mapNotNullTo(mutableSetOf()) { it.photoId }
            if (snapshot.manifest.photoIntegrity.keys != photoIds) add("photo_integrity_mismatch")
        }
        return if (reasons.isEmpty()) RestoreValidation.Valid(snapshot) else RestoreValidation.Invalid(reasons)
    }
}
