package com.sedsoftware.sproutpace.component.store

import kotlinx.serialization.Serializable

@Serializable
internal data class SavedScreenDraft(
    val values: Map<String, String>,
    val dirtyFields: Set<String>,
    val selectedItemId: String? = null,
)
