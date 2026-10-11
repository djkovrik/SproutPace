package com.sedsoftware.sproutpace.component

import kotlinx.datetime.Instant

data class StageChangeProjection(val command: ScreenCommand, val dates: List<StageDateChange>)

data class StageDateChange(
    val actionId: String,
    val stageKey: TextKey,
    val previous: Instant?,
    val resulting: Instant,
    val fixed: Boolean,
    val proposed: Instant? = null,
)

val previewedStageOperations: Set<String> = setOf("confirm-stage", "confirm-sowing", "skip-stage")
