package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.DestructiveOperation
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirtyNavigationTest {
    @Test
    fun cleanBackLeavesImmediatelyAndDirtyAppBackPreservesDraftUntilConfirmed() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler))
        root.open(ScreenId.BATCH_EDITOR)
        root.back()
        assertEquals(ScreenId.TODAY, root.stack.value.active.configuration.screen)
        assertNull(root.confirmation.value.child)

        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        root.stack.value.active.instance.onAction(ScreenAction.Back)
        val dialog = assertNotNull(root.confirmation.value.child).instance
        assertEquals(DestructiveOperation.DISCARD_CHANGES, dialog.destructiveOperation)
        dialog.cancel()
        assertDraft(root)
        root.back()
        val confirmed = assertNotNull(root.confirmation.value.child).instance
        confirmed.confirm()
        confirmed.confirm()
        assertEquals(ScreenId.TODAY, root.stack.value.active.configuration.screen)
        assertEquals(1, root.stack.value.items.size)
    }

    @Test
    fun systemBackUsesTheSameGuardAndDismissesItWithoutLosingDraft() = runTest {
        val back = BackDispatcher()
        val root = fixture(UnconfinedTestDispatcher(testScheduler), back = back)
        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        back.back()
        assertNotNull(root.confirmation.value.child)
        back.back()
        assertNull(root.confirmation.value.child)
        assertDraft(root)
        back.back()
        assertNotNull(root.confirmation.value.child).instance.confirm()
        assertEquals(ScreenId.TODAY, root.stack.value.active.configuration.screen)
        assertFalse(back.isEnabled)
    }

    @Test
    fun selectingDestinationWaitsForDiscardAndKeepsTheFirstRequestedDestination() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler))
        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        root.select(PrimaryDestination.CATALOG)
        root.select(PrimaryDestination.PLAN)
        assertDraft(root)
        assertNotNull(root.confirmation.value.child).instance.confirm()
        assertEquals(ScreenId.CATALOG, root.stack.value.active.configuration.screen)
        assertEquals(1, root.stack.value.items.size)
    }

    @Test
    fun openingScreenDiscardsBeforePushSoReturningCannotRevealOldDraft() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler))
        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        root.open(ScreenId.RECIPE, "recipe-42")
        assertDraft(root)
        assertNotNull(root.confirmation.value.child).instance.confirm()
        assertEquals("recipe-42", root.stack.value.active.configuration.stableId)
        root.back()
        assertEquals(ScreenId.BATCH_EDITOR, root.stack.value.active.configuration.screen)
        assertFalse(root.stack.value.active.instance.model.value.hasUnsavedChanges)
    }

    @Test
    fun pendingDestinationSurvivesSavedNavigationState() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val stateKeeper = StateKeeperDispatcher()
        val root = fixture(dispatcher, stateKeeper)
        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        root.open(ScreenId.RECIPE, "recipe-restored")

        val restored = fixture(dispatcher, StateKeeperDispatcher(stateKeeper.save()))
        assertDraft(restored)
        assertNotNull(restored.confirmation.value.child).instance.confirm()
        assertEquals(ScreenId.RECIPE, restored.stack.value.active.configuration.screen)
        assertEquals("recipe-restored", restored.stack.value.active.configuration.stableId)
        assertEquals(3, restored.stack.value.items.size)
    }

    @Test
    fun dismissedDialogCannotConfirmANewerNavigationRequest() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler))
        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        root.select(PrimaryDestination.PLAN)
        val stale = assertNotNull(root.confirmation.value.child).instance
        root.dismissConfirmation()
        root.select(PrimaryDestination.CATALOG)
        stale.confirm()
        assertNotNull(root.confirmation.value.child).instance.confirm()
        assertEquals(ScreenId.CATALOG, root.stack.value.active.configuration.screen)
    }

    @Test
    fun staleCallbackCannotResolveAReopenedIdenticalRequest() = runTest {
        val root = fixture(UnconfinedTestDispatcher(testScheduler))
        root.open(ScreenId.BATCH_EDITOR)
        edit(root)
        root.back()
        val stale = assertNotNull(root.confirmation.value.child).instance
        root.dismissConfirmation()
        root.back()
        stale.confirm()
        assertDraft(root)
        assertNotNull(root.confirmation.value.child).instance.cancel()
        assertDraft(root)
    }

    private fun edit(root: AppRootComponent) {
        val editor = root.stack.value.active.instance
        editor.onAction(ScreenAction.EditField(ScreenFieldId("plannedSowDate"), "2026-11-01"))
    }

    private fun assertDraft(root: AppRootComponent) {
        assertEquals(ScreenId.BATCH_EDITOR, root.stack.value.active.configuration.screen)
        assertTrue(root.stack.value.active.instance.model.value.hasUnsavedChanges)
        assertEquals("2026-11-01", root.stack.value.active.instance.model.value.fields
            .first { it.id == ScreenFieldId("plannedSowDate") }.value)
    }

    private fun fixture(
        dispatcher: CoroutineContext,
        stateKeeper: StateKeeperDispatcher = StateKeeperDispatcher(),
        back: BackDispatcher = BackDispatcher(),
    ): AppRootComponent {
        val manager = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
            override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
                error("Navigation must not execute a persistence command")
        }
        return AppRootComponentDefault(
            DefaultComponentContext(LifecycleRegistry(), stateKeeper = stateKeeper, backHandler = back),
            ScreenComponentFactory(ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher)),
        )
    }
}
