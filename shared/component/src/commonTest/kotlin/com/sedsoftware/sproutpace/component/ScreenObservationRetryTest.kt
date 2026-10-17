package com.sedsoftware.sproutpace.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** The only supplied boundary is a cold read flow; public components own all failure/retry/draft transitions. */
class ScreenObservationRetryTest {
    @Test
    fun firstReadConstructionFailureCanRetryWithoutLosingAuthoredDraftOrGeneratedIdentity() = runTest {
        val manager = ReadManager()
        val read = manager.route(ScreenId.ZONES).apply {
            constructionFailure = ReadFailure()
            baseline = fields("id" to "generated-zone", "name" to "Stored shelf", "capacity" to "2")
        }
        Fixture(ScreenId.ZONES, manager, UnconfinedTestDispatcher(testScheduler)).use { fixture ->
            val component = fixture.component
            assertEquals(LoadState.ERROR, component.model.value.loadState)
            assertEquals(READ_ERROR, component.model.value.errorKey)
            component.onAction(ScreenAction.EditField(ScreenFieldId("name"), " My shelf Ж "))
            component.onAction(ScreenAction.Retry)
            assertEquals(LoadState.CONTENT, component.model.value.loadState)
            assertEquals("generated-zone", component.field("id"))
            assertEquals(" My shelf Ж ", component.field("name"))
            assertTrue(component.model.value.hasUnsavedChanges)
            assertEquals(2, read.attempts)
            assertEquals(1, read.active)
        }
        assertEquals(0, read.active)
    }

    @Test
    fun failedRefreshKeepsSnapshotAndDirtyFieldsAndRetriesWithOneCollector() = runTest {
        val manager = ReadManager()
        val read = manager.route(ScreenId.ZONES).apply {
            baseline = fields("id" to "original-zone", "name" to "Stored shelf", "capacity" to "2")
        }
        Fixture(ScreenId.ZONES, manager, UnconfinedTestDispatcher(testScheduler)).use { fixture ->
            val component = fixture.component
            component.onAction(ScreenAction.EditField(ScreenFieldId("name"), "Keep my unsaved shelf"))
            read.events.send(Result.failure(ReadFailure()))
            assertEquals(LoadState.ERROR, component.model.value.loadState)
            assertEquals("original-zone", component.field("id"))
            assertEquals("Keep my unsaved shelf", component.field("name"))
            read.baseline = fields("id" to "fresh-unrelated-draft-id", "name" to "External shelf", "capacity" to "3")
            component.onAction(ScreenAction.Retry)
            component.onAction(ScreenAction.Retry)
            assertNull(component.model.value.errorKey)
            assertEquals("original-zone", component.field("id"))
            assertEquals("Keep my unsaved shelf", component.field("name"))
            assertEquals("3", component.field("capacity"))
            assertEquals(3, read.attempts)
            assertEquals(1, read.active)
            assertEquals(1, read.maximumActive)
        }
        assertEquals(0, read.active)
    }

    @Test
    fun detailFailureDoesNotHideCollectionOrDisappearOnCollectionRefreshAndRetriesOnlyDetail() = runTest {
        val manager = ReadManager()
        val collection = manager.route(ScreenId.TRAYS).apply { baseline = collection() }
        val detail = manager.route(ScreenId.BATCH_DETAIL, "second").apply { baseline = detail("second", "Last good detail") }
        Fixture(ScreenId.TRAYS, manager, UnconfinedTestDispatcher(testScheduler)).use { fixture ->
            val component = fixture.component
            component.onAction(ScreenAction.Select("second"))
            detail.events.send(Result.failure(ReadFailure()))
            assertEquals(LoadState.CONTENT, component.model.value.loadState)
            assertEquals(LoadState.ERROR, component.model.value.detail?.loadState)
            assertEquals("Last good detail", component.model.value.detail?.items?.single()?.authoredTitle)
            collection.events.send(Result.success(collection().copy(offline = false)))
            assertEquals(READ_ERROR, component.model.value.detail?.errorKey)
            assertEquals("second", component.model.value.selectedItemId)
            detail.baseline = detail("second", "Recovered exact detail")
            component.onAction(ScreenAction.Retry)
            assertEquals(1, collection.attempts)
            assertEquals(2, detail.attempts)
            assertEquals(1, detail.maximumActive)
            assertEquals(LoadState.CONTENT, component.model.value.detail?.loadState)
            assertEquals("Recovered exact detail", component.model.value.detail?.items?.single()?.authoredTitle)
        }
        assertEquals(0, collection.active)
        assertEquals(0, detail.active)
    }

    @Test
    fun failureBeforeFirstDetailEmissionStillHasExactSelectedSubjectAndPublicRetry() = runTest {
        val manager = ReadManager()
        manager.route(ScreenId.TRAYS).baseline = collection()
        val detail = manager.route(ScreenId.BATCH_DETAIL, "second").apply {
            constructionFailure = IllegalStateException("Local read unavailable")
            baseline = detail("second", "Fresh detail")
        }
        Fixture(ScreenId.TRAYS, manager, UnconfinedTestDispatcher(testScheduler)).use { fixture ->
            val component = fixture.component
            component.onAction(ScreenAction.Select("second"))
            assertEquals(LoadState.CONTENT, component.model.value.loadState)
            assertEquals(LoadState.ERROR, component.model.value.detail?.loadState)
            assertEquals(TextKey("state_error_body"), component.model.value.detail?.errorKey)
            assertEquals("second", component.model.value.detail?.selectedItemId)
            component.onAction(ScreenAction.Retry)
            assertEquals("Fresh detail", component.model.value.detail?.items?.single()?.authoredTitle)
            assertEquals(2, detail.attempts)
        }
    }

    @Test
    fun simultaneousPrimaryAndDetailFailuresRecoverIndependentlyThroughOneRetry() = runTest {
        val manager = ReadManager()
        val collection = manager.route(ScreenId.TRAYS).apply { baseline = collection() }
        val detail = manager.route(ScreenId.BATCH_DETAIL, "second").apply { baseline = detail("second", "Exact detail") }
        Fixture(ScreenId.TRAYS, manager, UnconfinedTestDispatcher(testScheduler)).use { fixture ->
            val component = fixture.component
            component.onAction(ScreenAction.Select("second"))
            collection.events.send(Result.failure(ReadFailure()))
            detail.events.send(Result.failure(ReadFailure()))
            assertEquals(LoadState.ERROR, component.model.value.loadState)
            assertEquals(LoadState.ERROR, component.model.value.detail?.loadState)
            component.onAction(ScreenAction.Retry)
            assertNull(component.model.value.errorKey)
            assertNull(component.model.value.detail?.errorKey)
            assertEquals("second", component.model.value.selectedItemId)
            assertEquals(2, collection.attempts)
            assertEquals(2, detail.attempts)
        }
    }

    @Test
    fun selectionChangeCancelsOldDetailAndLateOldFailureCannotAffectNewSelection() = runTest {
        val manager = ReadManager()
        manager.route(ScreenId.TRAYS).baseline = collection()
        val first = manager.route(ScreenId.BATCH_DETAIL, "first").apply { baseline = detail("first", "First detail") }
        val second = manager.route(ScreenId.BATCH_DETAIL, "second").apply { baseline = detail("second", "Second detail") }
        Fixture(ScreenId.TRAYS, manager, UnconfinedTestDispatcher(testScheduler)).use { fixture ->
            val component = fixture.component
            component.onAction(ScreenAction.Select("first"))
            component.onAction(ScreenAction.Select("second"))
            first.events.send(Result.failure(ReadFailure()))
            assertEquals(0, first.active)
            assertEquals(1, second.active)
            assertEquals("second", component.model.value.detail?.selectedItemId)
            assertNull(component.model.value.detail?.errorKey)
        }
    }

    @Test
    fun cancellationAndDisposalNeverBecomeVisibleFailuresOrMutateDisposedDraft() = runTest {
        val manager = ReadManager()
        val read = manager.route(ScreenId.ZONES).apply { baseline = fields("id" to "zone", "name" to "Stored") }
        val fixture = Fixture(ScreenId.ZONES, manager, UnconfinedTestDispatcher(testScheduler))
        val component = fixture.component
        component.onAction(ScreenAction.EditField(ScreenFieldId("name"), "Unsaved"))
        read.events.send(Result.failure(CancellationException("Canceled native read")))
        assertNull(component.model.value.errorKey)
        component.onAction(ScreenAction.Retry)
        val before = component.model.value
        fixture.close()
        read.events.send(Result.success(fields("id" to "late", "name" to "Must not apply")))
        assertEquals(before, component.model.value)
        assertEquals(0, read.active)
    }

    private class Fixture(screen: ScreenId, manager: ReadManager, context: kotlin.coroutines.CoroutineContext) : AutoCloseable {
        private val lifecycle = LifecycleRegistry().apply { resume() }
        val component = ScreenComponentFactory(ScreenComponentDependencies(DefaultStoreFactory(), manager, context))
            .create(screen, DefaultComponentContext(lifecycle)) { }
        override fun close() = lifecycle.destroy()
    }

    private class ReadManager : ScreenDataManager {
        private val routes = mutableMapOf<Pair<ScreenId, String?>, Read>()
        fun route(screen: ScreenId, id: String? = null) = routes.getOrPut(screen to id, ::Read)
        override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> {
            val read = route(screen, stableId)
            read.attempts += 1
            read.constructionFailure?.let { read.constructionFailure = null; throw it }
            return flow {
                read.active += 1
                read.maximumActive = maxOf(read.maximumActive, read.active)
                try {
                    emit(read.baseline)
                    for (result in read.events) emit(result.getOrThrow())
                } finally { read.active -= 1 }
            }
        }
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
            error("Observation recovery must not execute writes")
    }

    private class Read {
        var baseline = ScreenSnapshot()
        var constructionFailure: Throwable? = null
        val events = Channel<Result<ScreenSnapshot>>(Channel.UNLIMITED)
        var attempts = 0
        var active = 0
        var maximumActive = 0
    }

    private class ReadFailure : IllegalStateException("Transient local read failure"), ScreenFailure {
        override val errorKey = READ_ERROR
    }
    private fun ScreenComponent.field(id: String) = model.value.fields.single { it.id.value == id }.value
    private fun fields(vararg values: Pair<String, String>) = ScreenSnapshot(hasInteractionContract = true,
        items = listOf(ScreenItem("stored-zone", authoredTitle = "Stored shelf")),
        fields = values.map { (id, value) -> ScreenField(ScreenFieldId(id), TextKey("field_name"), ScreenFieldKind.TEXT, value) })
    private fun collection() = ScreenSnapshot(hasInteractionContract = true, items = listOf(
        ScreenItem("first", authoredTitle = "First"), ScreenItem("second", authoredTitle = "Second")))
    private fun detail(id: String, title: String) = ScreenSnapshot(hasInteractionContract = true,
        selectedItemId = id, items = listOf(ScreenItem(id, authoredTitle = title)))
    private companion object { val READ_ERROR = TextKey("error_operation_failed") }
}
