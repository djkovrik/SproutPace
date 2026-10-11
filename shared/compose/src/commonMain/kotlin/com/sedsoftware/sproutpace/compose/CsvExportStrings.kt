package com.sedsoftware.sproutpace.compose

import org.jetbrains.compose.resources.getString
import sproutpace.shared.compose.generated.resources.Res
import sproutpace.shared.compose.generated.resources.csv_actual_mass
import sproutpace.shared.compose.generated.resources.csv_batch_id
import sproutpace.shared.compose.generated.resources.csv_culture_id
import sproutpace.shared.compose.generated.resources.csv_note
import sproutpace.shared.compose.generated.resources.csv_outcome_at
import sproutpace.shared.compose.generated.resources.csv_recipe_name
import sproutpace.shared.compose.generated.resources.csv_state

internal suspend fun localizedCsvHeaders(): List<String> = listOf(
    Res.string.csv_batch_id, Res.string.csv_culture_id, Res.string.csv_recipe_name,
    Res.string.csv_state, Res.string.csv_outcome_at, Res.string.csv_actual_mass, Res.string.csv_note,
).map { getString(it) }
