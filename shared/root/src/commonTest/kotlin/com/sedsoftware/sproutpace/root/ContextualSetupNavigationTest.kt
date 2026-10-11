package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContextualSetupNavigationTest {
    @Test
    fun standaloneZoneAndTraySavesReturnToTheirList() = runTest {
        listOf(ScreenId.ZONES to "save-zone", ScreenId.TRAY_SIZES to "save-tray-size").forEach { (screen, operation) ->
            val root = fixture(UnconfinedTestDispatcher(testScheduler))
            root.open(screen)
            val list = root.stack.value.active.instance
            root.open(screen, "new-record")
            root.stack.value.active.instance.onAction(ScreenAction.Execute(ScreenCommand(operation, "saved-record")))
            assertSame(list, root.stack.value.active.instance)
            assertNull(root.stack.value.active.configuration.stableId)
        }
    }

    @Test
    fun dependencyCreationReturnsToExactDraftAndSelectsSavedTray() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler))
        root.open(ScreenId.BATCH_EDITOR)
        val editor = root.stack.value.active.instance
        editor.onAction(ScreenAction.EditField(ScreenFieldId("substrate"), "My substrate"))
        root.openDependency(ScreenId.TRAY_SIZES)
        assertNull(root.confirmation.value.child)
        root.stack.value.active.instance.onAction(ScreenAction.Execute(ScreenCommand("save-tray-size", "new-tray")))
        assertSame(editor, root.stack.value.active.instance)
        assertEquals("My substrate", editor.model.value.fields.first { it.id.value == "substrate" }.value)
        assertEquals("new-tray", editor.model.value.fields.first { it.id.value == "trayId" }.value)
        assertTrue(editor.model.value.hasUnsavedChanges)
    }

    @Test
    fun dependencyReturnIntentAndParentDraftSurviveRecreation() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val keeper = StateKeeperDispatcher()
        val root = fixture(dispatcher, keeper)
        root.open(ScreenId.BATCH_EDITOR)
        root.stack.value.active.instance.onAction(ScreenAction.EditField(ScreenFieldId("substrate"), "Restored substrate"))
        root.openDependency(ScreenId.ZONES)
        val restored = fixture(dispatcher, StateKeeperDispatcher(keeper.save()))
        restored.stack.value.active.instance.onAction(ScreenAction.Execute(ScreenCommand("save-zone", "new-zone")))
        val editor = restored.stack.value.active.instance.model.value
        assertEquals(ScreenId.BATCH_EDITOR, editor.screen)
        assertEquals("Restored substrate", editor.fields.first { it.id.value == "substrate" }.value)
        assertEquals("new-zone", editor.fields.first { it.id.value == "zoneId" }.value)
    }

    @Test
    fun browseThenResumeSetupPreservesEnteredValuesAndCompletionRemovesSetupHistory() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler), initial = ScreenId.FIRST_RUN)
        val setup = root.stack.value.active.instance
        setup.onAction(ScreenAction.EditField(ScreenFieldId("zoneName"), "Kitchen shelf"))
        root.select(PrimaryDestination.CATALOG)
        root.select(PrimaryDestination.TODAY)
        root.open(ScreenId.FIRST_RUN)
        assertSame(setup, root.stack.value.active.instance)
        assertEquals("Kitchen shelf", setup.model.value.fields.first { it.id.value == "zoneName" }.value)
        setup.onAction(ScreenAction.Execute(ScreenCommand("continue-setup")))
        assertEquals(ScreenId.PLAN, root.stack.value.active.configuration.screen)
        assertEquals(1, root.stack.value.items.size)
    }

    private fun fixture(
        dispatcher: CoroutineContext,
        keeper: StateKeeperDispatcher = StateKeeperDispatcher(),
        initial: ScreenId = ScreenId.TODAY,
    ): AppRootComponent = AppRootComponentDefault(
        DefaultComponentContext(LifecycleRegistry(), stateKeeper = keeper),
        ScreenComponentFactory(ScreenComponentDependencies(DefaultStoreFactory(), object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
            override suspend fun execute(screen: ScreenId, command: ScreenCommand) = Result.success(ScreenSnapshot())
        }, dispatcher)),
        initialScreen = initial,
    )
}
