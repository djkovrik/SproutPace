package com.sedsoftware.sproutpace.component

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.store.ScreenStore
import com.sedsoftware.sproutpace.component.store.ScreenStoreProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImmediatePreferenceQueueTest {
    @Test
    fun delayedCallbackCannotReapplyAnOlderCompletedPreferenceInvocation() = runTest {
        val manager = Manager()
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.SETTINGS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        val dark = theme("dark").newInvocation()
        val light = theme("light").newInvocation()
        store.accept(ScreenStore.Intent.Execute(dark))
        manager.complete("dark")
        store.accept(ScreenStore.Intent.Execute(light))
        manager.complete("light")
        store.accept(ScreenStore.Intent.Execute(dark))
        assertEquals(2, manager.calls.size)
        assertEquals("light", store.state.draftValues[ScreenFieldId("theme")])
        store.dispose()
    }

    @Test
    fun rapidReturnToEarlierValueKeepsOrderAndCoalescesAdjacentCallback() = runTest {
        val manager = Manager()
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.SETTINGS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.changeTheme("dark")
        store.accept(ScreenStore.Intent.Execute(theme("dark")))
        store.changeTheme("light")
        store.changeTheme("dark")
        assertEquals(listOf("dark"), manager.calls.map { it.arguments["theme"] })
        manager.complete("dark")
        assertEquals("dark", store.state.draftValues[ScreenFieldId("theme")])
        assertTrue(store.state.busy)
        manager.complete("light")
        assertEquals("dark", store.state.draftValues[ScreenFieldId("theme")])
        manager.complete("dark")
        assertEquals(listOf("dark", "light", "dark"), manager.calls.map { it.arguments["theme"] })
        assertEquals("dark", store.state.draftValues[ScreenFieldId("theme")])
        assertTrue(store.state.dirtyFields.isEmpty())
        assertFalse(store.state.busy)
        store.dispose()
    }

    @Test
    fun failedEarlierRequestPreservesNewerEditAndSameValueCanRetry() = runTest {
        val manager = Manager()
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.SETTINGS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.changeTheme("dark")
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("theme"), "light"))
        manager.fail()
        assertEquals("light", store.state.draftValues[ScreenFieldId("theme")])
        assertEquals(TextKey("error_operation_failed"), store.state.errorKey)
        store.changeTheme("dark")
        manager.complete("dark")
        assertEquals(2, manager.calls.size)
        assertEquals("dark", store.state.draftValues[ScreenFieldId("theme")])
        assertEquals(null, store.state.errorKey)
        store.dispose()
    }

    @Test
    fun unrelatedDraftSurvivesPreferenceSuccessAndFailureRollsBackAttemptedField() = runTest {
        val manager = Manager()
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.SETTINGS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("enabled"), "false"))
        store.changeTheme("dark")
        manager.complete("dark")
        assertEquals("false", store.state.draftValues[ScreenFieldId("enabled")])
        store.changeTheme("light")
        manager.fail()
        assertEquals("dark", store.state.draftValues[ScreenFieldId("theme")])
        assertEquals("false", store.state.draftValues[ScreenFieldId("enabled")])
        store.dispose()
    }

    @Test
    fun duplicateSaveEventAndDestructiveCallbacksRemainSuppressed() = runTest {
        listOf(
            ScreenId.BATCH_EDITOR to "save-batch",
            ScreenId.BATCH_DETAIL to "confirm-stage",
            ScreenId.SETTINGS to "erase-all",
        ).forEach { (screen, operation) ->
            val manager = Manager()
            val store = ScreenStoreProvider(DefaultStoreFactory(), manager, screen, "batch-1",
                UnconfinedTestDispatcher(testScheduler)).provide()
            val command = ScreenCommand(operation, "batch-1", deduplicationKey = "one-event")
            store.accept(ScreenStore.Intent.Execute(command))
            store.accept(ScreenStore.Intent.Execute(command))
            assertEquals(1, manager.calls.size)
            manager.complete("system")
            store.accept(ScreenStore.Intent.Execute(command))
            assertEquals(1, manager.calls.size)
            store.dispose()
        }
    }
    @Test
    fun policyExemptsOnlyAbsolutePreferenceSetters() {
        listOf("set-theme", "set-crash-reporting", "update-notifications").forEach {
            assertTrue(ScreenCommand(it).isImmediatePreference)
        }
        listOf("save-batch", "confirm-stage", "erase-all", "delete-all-data", "restore-backup").forEach {
            assertFalse(ScreenCommand(it).isImmediatePreference)
        }
    }

    private fun ScreenStore.changeTheme(value: String) {
        accept(ScreenStore.Intent.EditField(ScreenFieldId("theme"), value))
        accept(ScreenStore.Intent.Execute(theme(value)))
    }

    private fun theme(value: String) = ScreenCommand("set-theme", arguments = mapOf("theme" to value),
        deduplicationKey = "theme:$value")

    private class Manager : ScreenDataManager {
        val snapshots = MutableStateFlow(snapshot("system"))
        val calls = mutableListOf<ScreenCommand>()
        private val pending = ArrayDeque<CompletableDeferred<Result<ScreenSnapshot>>>()
        override fun watch(screen: ScreenId, stableId: String?) = snapshots
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            calls += command
            val result = CompletableDeferred<Result<ScreenSnapshot>>()
            pending.addLast(result)
            return result.await()
        }
        fun complete(value: String) {
            val result = pending.removeFirst()
            snapshots.value = snapshot(value)
            result.complete(Result.success(snapshots.value))
        }
        fun fail() { pending.removeFirst().complete(Result.failure(IllegalStateException("write failed"))) }
    }

    companion object {
        private fun snapshot(theme: String) = ScreenSnapshot(fields = listOf(
            ScreenField(ScreenFieldId("theme"), TextKey("field_theme"), ScreenFieldKind.TEXT, theme),
            ScreenField(ScreenFieldId("enabled"), TextKey("field_crash_reporting_enabled"), ScreenFieldKind.TEXT, "true"),
        ))
    }
}
