package com.sedsoftware.sproutpace.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrimaryDetailSelectionTest {
    @Test
    fun selectedDetailUsesItsExactStableIdAndSurvivesCollectionRefresh() = runTest {
        val manager = Manager()
        val component = component(manager, UnconfinedTestDispatcher(testScheduler))
        component.onAction(ScreenAction.Select("second"))
        assertEquals("second", component.model.value.detail?.selectedItemId)
        assertEquals("Second batch details", component.model.value.detail?.items?.single()?.authoredTitle)
        manager.collection.value = manager.collection.value.copy(offline = false)
        assertEquals("second", component.model.value.selectedItemId)
        assertEquals("Second batch details", component.model.value.detail?.items?.single()?.authoredTitle)
        component.onAction(ScreenAction.Select("first"))
        manager.second.value = manager.second.value.copy(items = listOf(ScreenItem("late", authoredTitle = "Late second data")))
        assertEquals("first", component.model.value.detail?.selectedItemId)
    }

    @Test
    fun removingSelectedRecordClearsVisibleDetail() = runTest {
        val manager = Manager()
        val component = component(manager, UnconfinedTestDispatcher(testScheduler))
        component.onAction(ScreenAction.Select("second"))
        manager.collection.value = manager.collection.value.copy(items = listOf(ScreenItem("first", authoredTitle = "First")))
        assertNull(component.model.value.selectedItemId)
        assertNull(component.model.value.detail)
    }

    @Test
    fun selectionRestoresAndLoadsFreshDetailAfterRecreation() = runTest {
        val manager = Manager()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val keeper = StateKeeperDispatcher()
        val first = component(manager, dispatcher, keeper)
        first.onAction(ScreenAction.Select("second"))
        val restored = component(manager, dispatcher, StateKeeperDispatcher(keeper.save()))
        assertEquals("second", restored.model.value.selectedItemId)
        assertEquals("Second batch details", restored.model.value.detail?.items?.single()?.authoredTitle)
    }

    private fun component(manager: Manager, dispatcher: CoroutineContext,
        keeper: StateKeeperDispatcher = StateKeeperDispatcher()): ScreenComponent =
        ScreenComponentFactory(ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher)).create(
            ScreenId.TRAYS, DefaultComponentContext(LifecycleRegistry(), stateKeeper = keeper),
        ) { }

    private class Manager : ScreenDataManager {
        val collection = MutableStateFlow(ScreenSnapshot(items = listOf(
            ScreenItem("first", authoredTitle = "First"), ScreenItem("second", authoredTitle = "Second"),
        ), hasInteractionContract = true))
        val first = MutableStateFlow(ScreenSnapshot(selectedItemId = "first",
            items = listOf(ScreenItem("first", authoredTitle = "First batch details")), hasInteractionContract = true))
        val second = MutableStateFlow(ScreenSnapshot(selectedItemId = "second",
            items = listOf(ScreenItem("second", authoredTitle = "Second batch details")), hasInteractionContract = true))
        override fun watch(screen: ScreenId, stableId: String?) = when {
            screen == ScreenId.TRAYS -> collection
            stableId == "first" -> first
            else -> second
        }
        override suspend fun execute(screen: ScreenId, command: ScreenCommand) = Result.success(collection.value)
    }
}
