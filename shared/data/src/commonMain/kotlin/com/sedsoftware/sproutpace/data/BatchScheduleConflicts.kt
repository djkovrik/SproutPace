package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleEngine
import com.sedsoftware.sproutpace.domain.model.GrowBatch

/** Conflicts are derived from persisted reality, so they survive save/restart without a second state owner. */
internal fun GrowBatch.scheduleConflictKeys(): List<TextKey> {
    val actual = events.lastOrNull() ?: return emptyList()
    val conflicts = ActionScheduleEngine.rebuild(this, ActionScheduleCorrection(actual.to, actual.occurredAt)).conflicts
    return if (conflicts.isEmpty()) emptyList() else listOf(TextKey("schedule_fixed_date_conflict"))
}
