package com.sedsoftware.sproutpace.component

import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Presentation data retains meaning independently of translated labels. */
enum class TaskDayGroup { OVERDUE, TODAY, UPCOMING }

data class BatchIdentity(
    val batchId: String,
    val trayName: String,
    val sowDate: LocalDate?,
    val zoneName: String?,
    val state: BatchState,
    val closedAt: Instant? = null,
)

data class TaskProjection(
    val actionId: String,
    val dueAt: Instant,
    val group: TaskDayGroup,
    val fixed: Boolean,
    val pending: Boolean = true,
) {
    val overdue: Boolean get() = pending && group == TaskDayGroup.OVERDUE
}

data class ZoneOccupancy(
    val zoneId: String,
    val zoneName: String,
    val type: GrowZoneType,
    val occupied: Int,
    val capacity: Int,
    val futureConflict: ZoneFutureConflict? = null,
) {
    val available: Int get() = (capacity - occupied).coerceAtLeast(0)
    val overflow: Int get() = (occupied - capacity).coerceAtLeast(0)
}

data class ZoneFutureConflict(val date: LocalDate, val projectedOccupied: Int, val capacity: Int)

enum class PlanEntryStatus { CONFIRMED, SUGGESTED, NEEDS_RESOLUTION }

data class PlanSummaryProjection(val id: String, val cadenceDays: Int, val cultureIds: List<String>, val paused: Boolean)

data class PlanEntryProjection(
    val cultureId: String?,
    val sowDate: LocalDate?,
    val harvestDate: LocalDate?,
    val status: PlanEntryStatus,
    val zone: ZoneOccupancy?,
    val planId: String? = null,
)
