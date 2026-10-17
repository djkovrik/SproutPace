package com.sedsoftware.sproutpace

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.EmptyScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.root.AppRootComponentDefault
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Only IME window state is supplied; the production callback, dispatcher, root and dirty Store are real. */
class RootBackDispatcherTest {
    @Test
    fun legacyBackHidesVisibleImeThenNextBackUsesRealDiscardGuardAndCancelPreservesDraft() {
        Fixture().use { fixture ->
            fixture.imeVisible = true
            fixture.dispatcher.onBackPressed()
            assertEquals(1, fixture.hideCount)
            assertFalse(fixture.imeVisible)
            fixture.assertDraftWithoutConfirmation()

            fixture.dispatcher.onBackPressed()
            fixture.assertDiscardConfirmation()
            fixture.root.confirmation.value.child!!.instance.cancel()
            fixture.assertDraftWithoutConfirmation()
            assertEquals(1, fixture.hideCount)
        }
    }

    @Test
    fun predictiveGestureConsumesImeEvenWhenAnimationHidesInsetsBeforeCommit() {
        Fixture().use { fixture ->
            fixture.imeVisible = true
            fixture.dispatcher.dispatchOnBackStarted(backEvent(0f))
            fixture.dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
            fixture.imeVisible = false
            fixture.dispatcher.dispatchOnBackProgressed(backEvent(0.9f))
            fixture.dispatcher.onBackPressed()
            assertEquals(1, fixture.hideCount)
            fixture.assertDraftWithoutConfirmation()

            fixture.dispatcher.onBackPressed()
            fixture.assertDiscardConfirmation()
            assertEquals(1, fixture.hideCount)
        }
    }

    @Test
    fun cancellingPredictiveGestureDoesNotHideImeOrSuppressTheNextClosedImeBack() {
        Fixture().use { fixture ->
            fixture.imeVisible = true
            fixture.dispatcher.dispatchOnBackStarted(backEvent(0f))
            fixture.dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
            fixture.dispatcher.dispatchOnBackCancelled()
            fixture.assertDraftWithoutConfirmation()
            assertEquals(0, fixture.hideCount)
            assertTrue(fixture.imeVisible)

            fixture.imeVisible = false
            fixture.dispatcher.onBackPressed()
            fixture.assertDiscardConfirmation()
            assertEquals(0, fixture.hideCount)
        }
    }

    @Test
    fun imeShownDuringGestureStillTakesPriorityAndToolbarBackKeepsItsExistingBehavior() {
        Fixture().use { fixture ->
            fixture.dispatcher.dispatchOnBackStarted(backEvent(0f))
            fixture.imeVisible = true
            fixture.dispatcher.dispatchOnBackProgressed(backEvent(0.8f))
            fixture.dispatcher.onBackPressed()
            fixture.assertDraftWithoutConfirmation()
            assertEquals(1, fixture.hideCount)

            fixture.imeVisible = true
            fixture.root.back()
            fixture.assertDiscardConfirmation()
            assertEquals("Toolbar navigation does not pass through the platform IME callback", 1, fixture.hideCount)
            fixture.root.confirmation.value.child!!.instance.cancel()
            fixture.assertDraftWithoutConfirmation()
        }
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 200f, progress, BackEventCompat.EDGE_LEFT)

    private class Fixture : AutoCloseable {
        private val lifecycle = LifecycleRegistry().apply { resume() }
        val root = AppRootComponentDefault(
            DefaultComponentContext(lifecycle),
            ScreenComponentFactory(
                ScreenComponentDependencies(DefaultStoreFactory(), EmptyScreenDataManager, Dispatchers.Unconfined),
            ),
            ScreenId.FIRST_RUN,
        )
        var imeVisible = false
        var hideCount = 0
        private val callback = RootBackPressedCallback(
            root::back,
            { imeVisible },
            { hideCount += 1; imeVisible = false },
        )
        val dispatcher = OnBackPressedDispatcher()

        init {
            root.stack.value.active.instance.onAction(ScreenAction.EditField(ScreenFieldId("zoneName"), AUTHORED_DRAFT))
            assertTrue(root.stack.value.active.instance.model.value.hasUnsavedChanges)
            callback.isEnabled = true
            dispatcher.addCallback(callback)
        }

        fun assertDraftWithoutConfirmation() {
            assertNull(root.confirmation.value.child)
            assertDraft()
        }

        fun assertDiscardConfirmation() {
            assertEquals(
                DestructiveOperation.DISCARD_CHANGES,
                root.confirmation.value.child?.configuration?.destructiveOperation,
            )
            assertDraft()
        }

        private fun assertDraft() {
            assertEquals(ScreenId.FIRST_RUN, root.stack.value.active.configuration.screen)
            val model = root.stack.value.active.instance.model.value
            assertTrue(model.hasUnsavedChanges)
            assertEquals(AUTHORED_DRAFT, model.fields.single { it.id.value == "zoneName" }.value)
        }

        override fun close() { callback.remove(); lifecycle.destroy() }
    }

    private companion object { const val AUTHORED_DRAFT = "Unsaved shelf Ж" }
}
