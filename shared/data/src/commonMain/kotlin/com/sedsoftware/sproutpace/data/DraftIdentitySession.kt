package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import kotlin.uuid.Uuid

/** One subscription owns stable unused draft IDs; persisted copies advance its next identity. */
internal class DraftIdentitySession(private val token: String = Uuid.random().toString()) {
    private val sequences = mutableMapOf<String, Int>()

    fun apply(screen: ScreenId, values: Map<ScreenFieldId, String>, workspace: Workspace): Map<ScreenFieldId, String> {
        val prefix = when (screen) {
            ScreenId.BATCH_EDITOR, ScreenId.BATCH_DETAIL, ScreenId.TRAYS -> "batch"
            ScreenId.RECIPE -> "recipe"
            ScreenId.PLAN -> "plan"
            ScreenId.ZONES -> "zone"
            ScreenId.TRAY_SIZES -> "tray"
            ScreenId.OBSERVATION_EDITOR -> "observation"
            else -> return values
        }
        val existing = when (prefix) {
            "batch" -> workspace.batches.map { it.id.value }
            "recipe" -> workspace.recipes.map { it.id.value }
            "plan" -> workspace.plans.map { it.id.value }
            "zone" -> workspace.zones.map { it.id.value }
            "tray" -> workspace.trays.map { it.id.value }
            else -> workspace.observations.map { it.id.value }
        }.toSet()
        return values.mapValues { (field, value) ->
            when {
                field.value == "newId" -> unused("$prefix-copy", existing)
                field.value == "id" && value !in existing -> unused(prefix, existing)
                else -> value
            }
        }
    }

    private fun unused(prefix: String, existing: Set<String>): String {
        var sequence = sequences[prefix] ?: 0
        while ("$prefix-$token-$sequence" in existing) sequence += 1
        sequences[prefix] = sequence
        return "$prefix-$token-$sequence"
    }
}
