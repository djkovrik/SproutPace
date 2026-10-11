package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceRecoveryGateTest {
    @Test fun gatedOperationsPreserveCoroutineCancellation() = runTest {
        val delegate = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flow<ScreenSnapshot> { }
            override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
                throw kotlinx.coroutines.CancellationException("cancelled")
            override suspend fun previewStage(command: ScreenCommand): Result<com.sedsoftware.sproutpace.component.StageChangeProjection> =
                throw kotlinx.coroutines.CancellationException("cancelled")
        }
        val manager = RecoveryGatedScreenDataManager(delegate, WorkspaceRecoveryGate(backgroundScope) { })
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            manager.execute(ScreenId.TODAY, ScreenCommand("skip-action"))
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> { manager.previewStage(ScreenCommand("confirm-stage")) }
    }

    @Test fun screenCannotObserveOrMutateWorkspaceUntilRecoveryCompletes() = runTest {
        val recovered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val gate = WorkspaceRecoveryGate(backgroundScope) { events += "recover"; recovered.await() }
        val manager = RecoveryGatedScreenDataManager(recordingManager(events), gate)
        val watch = async { manager.watch(ScreenId.TODAY, null).first() }
        val edit = async { manager.execute(ScreenId.TODAY, ScreenCommand("skip-action")).getOrThrow() }
        runCurrent()
        assertEquals(listOf("recover"), events)
        assertFalse(watch.isCompleted)
        assertFalse(edit.isCompleted)
        recovered.complete(Unit)
        watch.await()
        edit.await()
        assertTrue(gate.ready.value)
        assertEquals(setOf("recover", "watch", "execute"), events.toSet())
    }

    @Test fun failedRecoveryExposesNoDataAndRetryCanComplete() = runTest {
        var attempts = 0
        val events = mutableListOf<String>()
        val gate = WorkspaceRecoveryGate(backgroundScope) {
            attempts++
            if (attempts == 1) error("restore_failed")
        }
        val manager = RecoveryGatedScreenDataManager(recordingManager(events), gate)
        assertFailsWith<IllegalStateException> { manager.watch(ScreenId.TODAY, null).first() }
        assertTrue(events.isEmpty())
        assertFalse(gate.ready.value)
        manager.watch(ScreenId.TODAY, null).first()
        assertEquals(2, attempts)
        assertEquals(listOf("watch"), events)
    }

    private fun recordingManager(events: MutableList<String>) = object : ScreenDataManager {
        override fun watch(screen: ScreenId, stableId: String?) = flow {
            events += "watch"
            emit(ScreenSnapshot())
        }
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            events += "execute"
            return Result.success(ScreenSnapshot())
        }
    }
}
