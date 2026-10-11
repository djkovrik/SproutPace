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

class BackupPreviewRepeatTest {
    @Test
    fun cancelledPreviewCanReadSameDocumentAgainAndRestoreReadiness() = runTest {
        val manager = PreviewManager()
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.BACKUP_RESTORE, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        val preview = ScreenCommand("preview-restore", arguments = mapOf("documentId" to "backup"))
        repeat(2) {
            store.accept(ScreenStore.Intent.Execute(preview))
            assertTrue(store.state.snapshot.actions.any { it.id.value == "restore-backup" })
            store.accept(ScreenStore.Intent.Execute(ScreenCommand("cancel-restore-preview")))
            assertFalse(store.state.snapshot.actions.any { it.id.value == "restore-backup" })
        }
        assertEquals(listOf("preview-restore", "cancel-restore-preview", "preview-restore", "cancel-restore-preview"),
            manager.operations)
        store.dispose()
    }

    @Test
    fun simultaneousRequestsForSamePreviewStillReadOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val manager = PreviewManager(gate)
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.BACKUP_RESTORE, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        val preview = ScreenCommand("preview-restore", arguments = mapOf("documentId" to "backup"))
        repeat(2) { store.accept(ScreenStore.Intent.Execute(preview)) }
        assertEquals(listOf("preview-restore"), manager.operations)
        gate.complete(Unit)
        assertTrue(store.state.snapshot.actions.any { it.id.value == "restore-backup" })
        store.dispose()
    }

    private class PreviewManager(val gate: CompletableDeferred<Unit>? = null) : ScreenDataManager {
        val operations = mutableListOf<String>()
        private val snapshots = MutableStateFlow(ScreenSnapshot(hasInteractionContract = true))
        override fun watch(screen: ScreenId, stableId: String?) = snapshots
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            operations += command.operation
            gate?.await()
            val actions = if (command.operation == "preview-restore") listOf(
                ScreenActionDescriptor(ScreenActionId("restore-backup"), TextKey("action_restore_backup")),
            ) else emptyList()
            return Result.success(ScreenSnapshot(actions = actions, hasInteractionContract = true))
        }
    }
}
