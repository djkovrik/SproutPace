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
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DraftStateTest {
    @Test
    fun restoredCreationIdentitySurvivesLaterReactiveRefreshes() = runTest {
        val manager = Manager(snapshot("id" to "new-subscription-id", "name" to ""))
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.ZONES, null,
            UnconfinedTestDispatcher(testScheduler),
            com.sedsoftware.sproutpace.component.store.SavedScreenDraft(
                mapOf("id" to "restored-id", "name" to "My zone"), setOf("name"),
            )).provide()
        manager.snapshots.value = snapshot("id" to "new-subscription-id", "name" to "Fresh baseline")
        assertEquals("restored-id", store.state.draftValues[ScreenFieldId("id")])
        assertEquals("My zone", store.state.draftValues[ScreenFieldId("name")])
        store.dispose()
    }

    @Test
    fun restoringOriginalFieldValueClearsDirtyState() = runTest {
        val manager = Manager(snapshot("name" to "Original"))
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.ZONES, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("name"), "Changed"))
        assertTrue(store.state.dirtyFields.isNotEmpty())
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("name"), "Original"))
        assertTrue(store.state.dirtyFields.isEmpty())
        store.dispose()
    }

    @Test
    fun externalPickerFailurePreservesPhotoNotesAndDirtyStateWithoutWriting() = runTest {
        val manager = Manager(snapshot("photoId" to "existing-photo", "notes" to ""))
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.OBSERVATION_EDITOR, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("notes"), "Keep these notes"))
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("photoId"), "pending-photo:picked.png"))
        val before = store.state
        val error = TextKey("error_photo_import_failed")
        store.accept(ScreenStore.Intent.ReportFailure(error))
        assertEquals(before.copy(loading = false, errorKey = error), store.state)
        assertEquals(0, manager.executions)
        store.dispose()
    }

    @Test
    fun transientCategorySurvivesRefreshWithoutTriggeringUnsavedChanges() = runTest {
        val manager = Manager(snapshot("category" to "active"))
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.TRAYS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        assertEquals("active", store.state.draftValues[ScreenFieldId("category")])
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("category"), "history"))
        manager.snapshots.value = snapshot("category" to "planned")
        assertEquals("history", store.state.draftValues[ScreenFieldId("category")])
        assertTrue(store.state.dirtyFields.isEmpty())
        store.dispose()
    }

    @Test
    fun failedNotificationSaveRevertsAttemptedValueAndPreservesNewerEdits() = runTest {
        val result = CompletableDeferred<Result<ScreenSnapshot>>()
        val manager = Manager(snapshot("dailySummaryEnabled" to "false", "summaryTime" to "09:00"), result)
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.NOTIFICATIONS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("dailySummaryEnabled"), "true"))
        store.accept(ScreenStore.Intent.Execute(ScreenCommand("update-notifications", arguments = mapOf(
            "dailySummaryEnabled" to "true", "summaryTime" to "09:00",
        ))))
        store.accept(ScreenStore.Intent.EditField(ScreenFieldId("summaryTime"), "10:30"))
        result.complete(Result.failure(IllegalStateException("write failed")))
        assertEquals("false", store.state.draftValues[ScreenFieldId("dailySummaryEnabled")])
        assertEquals("10:30", store.state.draftValues[ScreenFieldId("summaryTime")])
        assertFalse(ScreenFieldId("dailySummaryEnabled") in store.state.dirtyFields)
        assertTrue(ScreenFieldId("summaryTime") in store.state.dirtyFields)
        assertEquals(TextKey("error_operation_failed"), store.state.errorKey)
        store.dispose()
    }

    @Test
    fun notificationToggleCanReturnToAPreviouslySavedValue() = runTest {
        val manager = Manager(snapshot("dailySummaryEnabled" to "false"))
        val store = ScreenStoreProvider(DefaultStoreFactory(), manager, ScreenId.NOTIFICATIONS, null,
            UnconfinedTestDispatcher(testScheduler)).provide()
        listOf("true", "false", "true").forEach { value ->
            store.accept(ScreenStore.Intent.Execute(ScreenCommand("update-notifications",
                arguments = mapOf("dailySummaryEnabled" to value), deduplicationKey = "toggle:$value")))
        }
        assertEquals(3, manager.executions)
        store.dispose()
    }

    private fun snapshot(vararg values: Pair<String, String>) = ScreenSnapshot(fields = values.map { (id, value) ->
        ScreenField(ScreenFieldId(id), TextKey("field_name"), ScreenFieldKind.TEXT, value)
    })

    private class Manager(initial: ScreenSnapshot, val result: CompletableDeferred<Result<ScreenSnapshot>>? = null) :
        ScreenDataManager {
        val snapshots = MutableStateFlow(initial)
        var executions = 0
        override fun watch(screen: ScreenId, stableId: String?) = snapshots
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            executions += 1
            return result?.await() ?: Result.success(snapshots.value)
        }
    }
}
