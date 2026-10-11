package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable

@Composable
internal fun SproutPaceUiState.withLocalizedCatalogSearch(): SproutPaceUiState {
    if (screen != UiScreen.CATALOG) return this
    val names = items.associate { item ->
        item.id to listOfNotNull(
            localizedOrAuthored(item.titleKey, item.authoredTitle),
            item.recipe?.scientificNameKey?.let { localizedText(it.value) },
        )
    }
    return filterCatalogSearch(names)
}

internal fun SproutPaceUiState.filterCatalogSearch(names: Map<String, List<String>>): SproutPaceUiState {
    val query = fields.firstOrNull { it.id == "query" }?.value.orEmpty().trim()
    if (screen != UiScreen.CATALOG || query.isEmpty()) return this
    val matches = items.filter { item -> names[item.id].orEmpty().any { it.contains(query, ignoreCase = true) } }
    return copy(items = matches, variant = if (matches.isEmpty()) UiVariant.EMPTY else variant,
        showInlineAd = showInlineAd && matches.size >= CATALOG_SEARCH_AD_MIN_ITEMS)
}

private const val CATALOG_SEARCH_AD_MIN_ITEMS = 5
