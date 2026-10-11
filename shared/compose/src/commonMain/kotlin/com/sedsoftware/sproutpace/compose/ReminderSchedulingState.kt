package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFact
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.platform.ReminderReconciliationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ReminderSchedulingState { IDLE, RUNNING, FAILED }

/** Scheduling failures do not roll back an independently saved notification preference. */
internal class ReminderSchedulingMonitor {
    private val mutableState = MutableStateFlow(ReminderSchedulingState.IDLE)
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()

    suspend fun run(block: suspend () -> ReminderReconciliationResult) = mutex.withLock {
        mutableState.value = ReminderSchedulingState.RUNNING
        try {
            mutableState.value = if (block().failed.isEmpty()) ReminderSchedulingState.IDLE else ReminderSchedulingState.FAILED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = ReminderSchedulingState.FAILED
        }
    }
}

internal fun ScreenSnapshot.withReminderScheduling(state: ReminderSchedulingState): ScreenSnapshot = copy(
    facts = facts.filterNot { it.id == "reminder-scheduling" } + when (state) {
        ReminderSchedulingState.IDLE -> emptyList()
        else -> listOf(ScreenFact(
            "reminder-scheduling", TextKey("notifications_permission"),
            valueKey = TextKey(
                if (state == ReminderSchedulingState.FAILED) "notifications_schedule_failed" else "notifications_scheduling",
            ),
        ))
    },
)
