package com.sedsoftware.sproutpace.component

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.store.ScreenStore
import com.sedsoftware.sproutpace.component.store.ScreenStoreProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StagePreviewStoreTest {
    @Test fun previewRequiresExplicitConfirmationAndAnyEditInvalidatesIt() = runTest {
        val manager = PreviewManager()
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.BATCH_DETAIL, "batch",
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.accept(ScreenStore.Intent.Submit(ScreenActionId("confirm-stage")))
        assertNotNull(store.state.stagePreview)
        assertTrue(manager.executed.isEmpty())
        assertEquals(0L, store.state.completionRevision)
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("occurredAt"), "2026-10-11T12:00:00Z"))
        assertNull(store.state.stagePreview)
        store.accept(ScreenStore.Intent.ConfirmStagePreview)
        assertTrue(manager.executed.isEmpty())
        store.accept(ScreenStore.Intent.Submit(ScreenActionId("confirm-stage")))
        val reviewed = assertNotNull(store.state.stagePreview).command
        store.accept(ScreenStore.Intent.ConfirmStagePreview)
        assertEquals(listOf(reviewed), manager.executed)
        assertNull(store.state.stagePreview)
        assertEquals(1L, store.state.completionRevision)
        store.accept(ScreenStore.Intent.Execute(reviewed))
        assertEquals(1L, store.state.completionRevision, "A duplicate callback must not signal another completion")
        store.dispose()
    }

    @Test fun failedCommitRetainsReviewAndDoesNotSignalEditorCompletion() = runTest {
        val store = ScreenStoreProvider(DefaultStoreFactory(), PreviewManager(fail = true), ScreenId.BATCH_DETAIL, "batch",
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.accept(ScreenStore.Intent.Submit(ScreenActionId("confirm-stage")))
        store.accept(ScreenStore.Intent.ConfirmStagePreview)
        assertNotNull(store.state.stagePreview)
        assertNotNull(store.state.errorKey)
        assertEquals(0L, store.state.completionRevision)
        store.dispose()
    }

    private class PreviewManager(private val fail: Boolean = false) : ScreenDataManager {
        val executed = mutableListOf<ScreenCommand>()
        private val snapshot = ScreenSnapshot(selectedItemId = "batch", fields =
            ScreenContractRegistry.contracts.getValue(ScreenId.BATCH_DETAIL).fields.map { field ->
                field.copy(value = when (field.id.value) {
                    "eventId" -> "event"
                    "occurredAt" -> "2026-10-10T12:00:00Z"
                    "nextState" -> "light_growth"
                    else -> ""
                })
            })
        override fun watch(screen: ScreenId, stableId: String?) = flowOf(snapshot)
        override suspend fun previewStage(command: ScreenCommand) = Result.success(StageChangeProjection(command, emptyList()))
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            executed += command
            if (fail) return Result.failure(IllegalStateException("Cannot save"))
            return Result.success(snapshot)
        }
    }
}
