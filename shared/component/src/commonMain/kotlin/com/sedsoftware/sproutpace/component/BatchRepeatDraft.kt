package com.sedsoftware.sproutpace.component

/** A navigation origin and immutable snapshot choice, never a persisted provisional batch. */
object BatchRepeatDraft {
    private const val ROUTE_PREFIX = "repeat-batch:"
    private const val SNAPSHOT_PREFIX = "batch-snapshot:"

    fun route(sourceId: String): String = ROUTE_PREFIX + sourceId
    fun sourceId(route: String?): String? = route?.takeIf { it.startsWith(ROUTE_PREFIX) }
        ?.removePrefix(ROUTE_PREFIX)?.takeIf(String::isNotBlank)
    fun snapshotChoice(sourceId: String): String = SNAPSHOT_PREFIX + sourceId
    fun snapshotSource(choice: String?): String? = choice?.takeIf { it.startsWith(SNAPSHOT_PREFIX) }
        ?.removePrefix(SNAPSHOT_PREFIX)?.takeIf(String::isNotBlank)
}
