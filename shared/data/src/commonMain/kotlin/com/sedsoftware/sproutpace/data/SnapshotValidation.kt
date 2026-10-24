package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.BackupManifest
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import com.sedsoftware.sproutpace.domain.model.DueMoment
import kotlinx.datetime.TimeZone

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
            val expected = recordCounts(snapshot)
            if (snapshot.manifest.recordCounts != expected) add("record_count_mismatch")
            addAll(referenceErrors(snapshot))
            addAll(batchErrors(snapshot))
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

    private fun referenceErrors(snapshot: ProductSnapshot): List<String> = buildList {
        val batchIds = snapshot.batches.mapTo(hashSetOf()) { it.id }
        if (snapshot.observations.any { it.batchId !in batchIds }) add("orphan_observation")
        val photoIds = snapshot.observations.mapNotNullTo(hashSetOf()) { it.photoId }
        if ((photoIds + snapshot.manifest.photoIntegrity.keys).any { !isSafePhotoId(it) }) add("unsafe_photo_id")
        if (snapshot.manifest.photoIntegrity.keys != photoIds) add("photo_manifest_mismatch")
        val cultures = snapshot.plans.flatMap { it.cultureRotation } + snapshot.recipes.mapNotNull { it.cultureId } +
            snapshot.batches.mapNotNull { it.cultureIdSnapshot } + snapshot.batches.mapNotNull { it.recipe.builtInCultureId }
        if (cultures.any { it !in SproutPaceCatalog.cultureIds }) add("unknown_culture_reference")
        val zoneIds = snapshot.zones.mapTo(hashSetOf()) { it.id }
        if (snapshot.batches.any { batch ->
                !batch.state.isTerminal && (batch.placement as? Placement.Assigned)?.zoneId?.let { it !in zoneIds } == true
            }) add("orphan_active_placement")
    }

    private fun batchErrors(snapshot: ProductSnapshot): List<String> = buildList {
        if (snapshot.batches.any { batch -> batch.actions.any { it.batchId != batch.id } }) add("action_batch_mismatch")
        if (snapshot.batches.any { batch -> batch.actions.any { action ->
                (action.due as? DueMoment.CivilTime)?.let { runCatching { TimeZone.of(it.timeZoneId) }.isFailure } == true
            } }) add("unknown_action_time_zone")
        val actionIds = snapshot.batches.flatMap { batch -> batch.actions.map { it.id } }
        if (actionIds.distinct().size != actionIds.size) add("duplicate_action")
        if (snapshot.batches.any { batch -> batch.events.map { it.id }.distinct().size != batch.events.size }) add("duplicate_stage_event")
        if (snapshot.batches.any(::invalidOutcome)) add("outcome_state_mismatch")
    }

    private fun invalidOutcome(batch: GrowBatch): Boolean = when (batch.outcome) {
        is BatchOutcome.Harvested -> batch.state != BatchState.HARVESTED
        is BatchOutcome.Failed -> batch.state != BatchState.FAILED
        is BatchOutcome.Abandoned -> batch.state != BatchState.ABANDONED
        null -> batch.state.isTerminal
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
