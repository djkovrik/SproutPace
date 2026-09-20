package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.BackupManifest
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe

object SnapshotValidation {
    const val SCHEMA_VERSION: Int = 1
    const val ARCHIVE_VERSION: Int = 1

    fun validate(snapshot: ProductSnapshot): RestoreValidation {
        val reasons = buildList {
            if (snapshot.manifest.schemaVersion != SCHEMA_VERSION) add("unsupported_schema_version")
            if (snapshot.manifest.archiveVersion != ARCHIVE_VERSION) add("unsupported_archive_version")
            duplicates("zones", snapshot.zones.map { it.id.value })?.let(::add)
            duplicates("tray_presets", snapshot.trayPresets.map { it.id.value })?.let(::add)
            duplicates("recipes", snapshot.recipes.map { it.id.value })?.let(::add)
            duplicates("batches", snapshot.batches.map { it.id.value })?.let(::add)
            duplicates("plans", snapshot.plans.map { it.id.value })?.let(::add)
            duplicates("observations", snapshot.observations.map { it.id.value })?.let(::add)
            val batchIds = snapshot.batches.mapTo(hashSetOf()) { it.id }
            if (snapshot.observations.any { it.batchId !in batchIds }) add("orphan_observation")
            val photoIds = snapshot.observations.mapNotNullTo(hashSetOf()) { it.photoId }
            if (snapshot.manifest.photoIntegrity.keys != photoIds) add("photo_manifest_mismatch")
            val expected = recordCounts(snapshot)
            if (snapshot.manifest.recordCounts != expected) add("record_count_mismatch")
            if (snapshot.batches.any { batch ->
                    batch.recipe.builtInCultureId?.let { it !in SproutPaceCatalog.cultureIds } == true
                }) add("unknown_built_in_culture")
        }
        return if (reasons.isEmpty()) RestoreValidation.Valid(snapshot) else RestoreValidation.Invalid(reasons)
    }

    fun manifest(
        zones: List<GrowZone>, trays: List<TraySizePreset>, recipes: List<UserGrowRecipe>,
        batches: List<GrowBatch>, plans: List<HarvestPlan>, observations: List<Observation>,
        photoIntegrity: Map<PhotoId, String>,
    ): BackupManifest {
        val snapshot = ProductSnapshot(
            BackupManifest(SCHEMA_VERSION, ARCHIVE_VERSION, emptyMap(), photoIntegrity),
            zones, trays, recipes, batches, plans, observations,
        )
        return BackupManifest(SCHEMA_VERSION, ARCHIVE_VERSION, recordCounts(snapshot), photoIntegrity)
    }

    private fun recordCounts(snapshot: ProductSnapshot) = linkedMapOf(
        "zones" to snapshot.zones.size,
        "trayPresets" to snapshot.trayPresets.size,
        "recipes" to snapshot.recipes.size,
        "batches" to snapshot.batches.size,
        "plans" to snapshot.plans.size,
        "observations" to snapshot.observations.size,
    )

    private fun duplicates(kind: String, ids: List<String>): String? =
        if (ids.size == ids.distinct().size) null else "duplicate_$kind"
}
