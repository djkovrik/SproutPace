package com.sedsoftware.sproutpace.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Metadata travels beside the opaque location; only [location] is passed to file services. */
@Serializable
data class SelectedBackupDocument(val location: String, val displayName: String? = null) {
    init { require(location.isNotBlank()) }

    fun encode(): String = PREFIX + Json.encodeToString(this)

    companion object {
        private const val PREFIX = "sproutpace-document:"

        fun decode(value: String): SelectedBackupDocument = if (value.startsWith(PREFIX)) {
            Json.decodeFromString<SelectedBackupDocument>(value.removePrefix(PREFIX))
        } else SelectedBackupDocument(value)
    }
}
