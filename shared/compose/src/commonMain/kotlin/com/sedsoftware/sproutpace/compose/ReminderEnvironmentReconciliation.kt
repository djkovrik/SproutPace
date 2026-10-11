package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.platform.ReminderLifecycleCoordinator
import com.sedsoftware.sproutpace.platform.requireSuccessful
import kotlinx.datetime.TimeZone

/** Persist civil schedule changes before deriving physical registrations from the database. */
internal suspend fun reconcileReminderEnvironment(
    reason: ReconciliationReason,
    manager: ScreenDataManager,
    coordinator: ReminderLifecycleCoordinator,
    timeZone: TimeZone,
) {
    coordinator.reconcile(reason) {
        if (reason == ReconciliationReason.TIME_ZONE_CHANGED) {
            manager.execute(ScreenId.TODAY, ScreenCommand(
                operation = "time-zone-change",
                arguments = mapOf("timeZoneId" to timeZone.id),
            ).newInvocation()).getOrThrow()
        }
    }.requireSuccessful()
}
