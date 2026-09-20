package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

object SchedulingEngine {
    fun snooze(action: ScheduledAction, newDue: DueMoment): ScheduledAction {
        require(action.status == ScheduledActionStatus.PENDING)
        return action.copy(due = newDue)
    }

    fun afterTimeZoneChange(action: ScheduledAction, newTimeZoneId: String): ScheduledAction = when (val due = action.due) {
        is DueMoment.ElapsedTarget -> action
        is DueMoment.CivilTime -> action.copy(due = DueMoment.CivilTime(due.localDateTime, newTimeZoneId))
    }

    fun resolvedInstant(action: ScheduledAction) = when (val due = action.due) {
        is DueMoment.ElapsedTarget -> due.instant
        is DueMoment.CivilTime -> due.localDateTime.toInstant(TimeZone.of(due.timeZoneId))
    }
}
