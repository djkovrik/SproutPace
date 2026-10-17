package com.sedsoftware.sproutpace.component

import kotlinx.datetime.Instant

/** An actual historical timestamp, with user-authored reasons preserved verbatim. */
data class BatchHistoryProjection(
    val occurredAt: Instant,
    val skipped: Boolean = false,
    val reason: String? = null,
    val priorOccurredAt: List<Instant> = emptyList(),
    val actualHarvestMassGrams: Double? = null,
)
