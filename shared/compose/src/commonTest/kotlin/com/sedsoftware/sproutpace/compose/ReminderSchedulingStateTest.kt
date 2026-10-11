package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import com.sedsoftware.sproutpace.platform.PlatformFailure
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.platform.ReminderReconciliationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSchedulingStateTest {
    @Test
    fun partialOsFailureIsVisibleAndSuccessfulRetryClearsWarning() = runTest {
        val monitor = ReminderSchedulingMonitor()
        monitor.run {
            assertEquals(ReminderSchedulingState.RUNNING, monitor.state.value)
            result(mapOf(PhysicalReminderId("soak-1") to PlatformFailure.Io("schedule_failed")))
        }
        val failed = ScreenSnapshot().withReminderScheduling(monitor.state.value)
        assertEquals("notifications_schedule_failed", failed.facts.single().valueKey?.value)
        monitor.run { result() }
        assertEquals(ReminderSchedulingState.IDLE, monitor.state.value)
        assertTrue(failed.withReminderScheduling(monitor.state.value).facts.isEmpty())
    }

    @Test
    fun driverExceptionIsReportedWithoutEscapingToSavedPreferenceMutation() = runTest {
        val monitor = ReminderSchedulingMonitor()
        monitor.run { error("OS service unavailable") }
        assertEquals(ReminderSchedulingState.FAILED, monitor.state.value)
    }

    private fun result(failed: Map<PhysicalReminderId, PlatformFailure> = emptyMap()) = ReminderReconciliationResult(
        ReconciliationReason.PRODUCT_DATA_CHANGED, emptySet(), emptySet(), failed,
    )
}
