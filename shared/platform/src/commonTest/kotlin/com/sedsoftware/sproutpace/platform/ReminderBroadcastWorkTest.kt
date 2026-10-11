package com.sedsoftware.sproutpace.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReminderBroadcastWorkTest {
    @Test fun driverResultCancellationEscapesScheduleAndCancelReconciliation() = runTest {
        val reminder = PhysicalReminder(PhysicalReminderId("daily"), ReminderKind.DAILY_SUMMARY, 2_000,
            NativeTextKey.DAILY_SUMMARY_TITLE, NativeTextKey.DAILY_SUMMARY_BODY)
        listOf(false, true).forEach { cancelling ->
            val cancellation = kotlinx.coroutines.CancellationException("driver cancelled")
            val driver = object : ReminderDriver {
                override suspend fun registeredIds() = if (cancelling) setOf(reminder.id) else emptySet()
                override suspend fun schedule(reminder: PhysicalReminder): Result<Unit> = Result.failure(cancellation)
                override suspend fun cancel(id: PhysicalReminderId): Result<Unit> = Result.failure(cancellation)
            }
            val plan = ReminderPlan(if (cancelling) 0 else 1, if (cancelling) null else reminder, emptyList())
            val failure = assertFailsWith<kotlinx.coroutines.CancellationException> {
                ReminderReconciler(driver).reconcile(plan, 1_000, PermissionState.Granted, ReconciliationReason.APP_START)
            }
            kotlin.test.assertSame(cancellation, failure)
        }
    }

    @Test fun platformBudgetKeepsNearestDailyAndSoakAndReportsOmittedRequests() = runTest {
        fun reminder(id: String, at: Long, kind: ReminderKind) = PhysicalReminder(PhysicalReminderId(id), kind, at,
            NativeTextKey.DAILY_SUMMARY_TITLE, NativeTextKey.DAILY_SUMMARY_BODY)
        val daily = reminder("daily", 3_000, ReminderKind.DAILY_SUMMARY)
        val soak = reminder("soak", 2_000, ReminderKind.SOAK)
        val later = reminder("later", 4_000, ReminderKind.DAILY_SUMMARY)
        val delegate = FakeReminderDriver(listOf(later))
        val driver = object : ReminderDriver by delegate {
            override suspend fun pendingCapacity() = remainingReminderCapacity(64, 62)
        }
        val result = ReminderReconciler(driver).reconcile(ReminderPlan(0, null, listOf(soak),
            dailySummaries = listOf(later, daily)), 1_000, PermissionState.Granted, ReconciliationReason.APP_START)
        assertEquals(setOf(daily.id, soak.id), delegate.registeredIds())
        assertEquals(setOf(later.id), result.cancelled)
        assertEquals(mapOf(later.id to PlatformFailure.Rejected("reminder_capacity_exceeded")), result.failed)
        assertEquals(0, remainingReminderCapacity(64, 70))
    }

    @Test fun receiverFinishesAndKeepsRetrySignalOnFailure() = runTest {
        val events = mutableListOf<String>()
        runReminderBroadcast(work = { events += "evaluate"; error("database unavailable") },
            onFailure = { events += "retry" }, finish = { events += "finish" })
        assertEquals(listOf("evaluate", "retry", "finish"), events)
    }

    @Test fun finishIsCalledEvenWhenRetryRecordingFails() = runTest {
        var finishes = 0
        assertFailsWith<IllegalStateException> {
            runReminderBroadcast(work = { error("failure") }, onFailure = { error("storage failure") },
                finish = { finishes++ })
        }
        assertEquals(1, finishes)
    }

    @Test fun duplicateBroadcastRearmsButDoesNotPostTwiceAndNextDayCanPost() = runTest {
        var claim: String? = null
        var posted = 0
        var evaluations = 0
        suspend fun deliver(token: String, useful: Boolean) = deliverReminderOnce(token,
            evaluateAndRearm = { evaluations++; useful }, readClaim = { claim }, writeClaim = { claim = it },
            post = { posted++ })
        deliver("2026-10-10", true)
        deliver("2026-10-10", true)
        deliver("2026-10-11", false)
        assertEquals(1, posted)
        assertEquals("2026-10-10", claim)
        deliver("2026-10-12", true)
        assertEquals(2, posted)
        assertEquals(4, evaluations)
    }

    @Test fun postFailureReleasesClaimForRetry() = runTest {
        var claim: String? = null
        assertFailsWith<IllegalStateException> {
            deliverReminderOnce("day", { true }, { claim }, { claim = it }, { error("post failed") })
        }
        assertEquals(null, claim)
    }
}
