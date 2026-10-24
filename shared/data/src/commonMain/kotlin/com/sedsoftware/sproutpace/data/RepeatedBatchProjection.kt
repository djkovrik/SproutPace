package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.Placement

/** Repeat retains the source growing inputs and starts a separate planned batch. */
internal fun GrowBatch.repeatedAs(newId: BatchId): GrowBatch = copy(
    id = newId, state = BatchState.PLANNED, placement = Placement.None,
    events = emptyList(), actions = emptyList(), outcome = null,
    closedPlacementSnapshot = Placement.None,
)
