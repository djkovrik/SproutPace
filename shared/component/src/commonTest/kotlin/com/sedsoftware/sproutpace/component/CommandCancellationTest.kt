package com.sedsoftware.sproutpace.component

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.store.ScreenStore
import com.sedsoftware.sproutpace.component.store.ScreenStoreProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CommandCancellationTest {
    @Test
    fun cancellationIsNotFailureAndSameInvocationCanRetryAfterCleanup() = runTest {
        listOf(false, true).forEach { wrapped ->
            listOf("set-theme", "erase-all").forEach { operation ->
                val manager = CancellingManager(wrapped)
                val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.SETTINGS, null,
                    UnconfinedTestDispatcher(testScheduler)).provide()
                val command = ScreenCommand(operation, arguments = if (operation == "set-theme") {
                    mapOf("theme" to "dark")
                } else emptyMap()).newInvocation()
                store.accept(ScreenStore.Intent.Execute(command))
                assertFalse(store.state.busy)
                assertNull(store.state.errorKey)
                assertEquals(0L, store.state.completionRevision)
                manager.cancel = false
                store.accept(ScreenStore.Intent.Execute(command))
                assertFalse(store.state.busy)
                assertEquals(2, manager.calls)
                assertEquals(1L, store.state.completionRevision)
                store.dispose()
            }
        }
    }

    private class CancellingManager(private val wrapped: Boolean) : ScreenDataManager {
        var cancel = true
        var calls = 0
        override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            calls += 1
            if (cancel) {
                val exception = CancellationException("Canceled at persistence boundary")
                if (wrapped) return Result.failure(exception)
                throw exception
            }
            return Result.success(ScreenSnapshot())
        }
    }
}
