package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sproutpace.shared.compose.generated.resources.*

/** The only presentation-boundary resolver for stable bundled text keys. */
internal fun strictTextResource(key: String): StringResource =
    requireNotNull(Res.allStringResources[key] ?: catalogTextAlias(key)) {
        "Unknown bundled TextKey '$key'"
    }

@Composable
internal fun localizedText(key: String): String = stringResource(strictTextResource(key))

@Composable
internal fun localizedOrAuthored(key: String?, authored: String?): String = when {
    authored != null -> authored
    key != null -> localizedText(key)
    else -> error("Localized or authored text is required")
}

/**
 * Version 2 guidance resolves directly. These aliases preserve version 1 recipe snapshots.
 * This finite table keeps that many-to-one resource mapping explicit and rejects unknown cultures/suffixes.
 */
private fun catalogTextAlias(key: String): StringResource? {
    directCatalogAliases[key]?.let { return it }
    val prefix = catalogPrefixes.firstOrNull(key::startsWith) ?: return null
    return when (key.removePrefix(prefix)) {
        "soaking" -> Res.string.guidance_soaking
        "sowing" -> Res.string.guidance_sowing
        "blackout" -> Res.string.guidance_blackout
        "light_growth" -> Res.string.guidance_light_growth
        "harvest" -> Res.string.guidance_harvest
        "substrate_note" -> Res.string.guidance_substrate
        "lighting_note" -> Res.string.guidance_lighting
        else -> null
    }
}

private val catalogPrefixes = listOf(
    "culture_garden_cress_", "culture_sunflower_", "culture_broccoli_", "culture_mustard_",
    "culture_arugula_", "culture_radish_", "culture_basil_", "culture_pea_",
)

private val directCatalogAliases = mapOf(
    "culture_radish_name" to Res.string.culture_radish,
    "culture_broccoli_name" to Res.string.culture_broccoli,
    "culture_arugula_name" to Res.string.culture_arugula,
    "culture_mustard_name" to Res.string.culture_mustard,
    "culture_pea_name" to Res.string.culture_pea,
    "culture_sunflower_name" to Res.string.culture_sunflower,
    "culture_basil_name" to Res.string.culture_basil,
    "culture_garden_cress_name" to Res.string.culture_cress,
    "culture_radish_scientific_name" to Res.string.scientific_radish,
    "culture_broccoli_scientific_name" to Res.string.scientific_broccoli,
    "culture_arugula_scientific_name" to Res.string.scientific_arugula,
    "culture_mustard_scientific_name" to Res.string.scientific_mustard,
    "culture_pea_scientific_name" to Res.string.scientific_pea,
    "culture_sunflower_scientific_name" to Res.string.scientific_sunflower,
    "culture_basil_scientific_name" to Res.string.scientific_basil,
    "culture_garden_cress_scientific_name" to Res.string.scientific_cress,
)
