package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AppRootComponentTest {
    @Test
    fun ac001_emptyWorkspaceCanStartAtFirstRunWithoutAddingBackHistory() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
            override suspend fun execute(screen: ScreenId, command: ScreenCommand) = Result.success(ScreenSnapshot())
        }
        val root = AppRootComponentDefault(
            DefaultComponentContext(LifecycleRegistry()),
            ScreenComponentFactory(
                ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher),
            ),
            initialScreen = ScreenId.FIRST_RUN,
        )

        assertEquals(ScreenId.FIRST_RUN, root.stack.value.active.configuration.screen)
        assertEquals(1, root.stack.value.items.size)
        root.back()
        assertEquals(ScreenId.FIRST_RUN, root.stack.value.active.configuration.screen)
    }

    @Test
    fun ac031_batchDetailKeepsStableIdAndBackReturnsToToday() = runTest {
        val root = root(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))

        root.open(ScreenId.BATCH_DETAIL, "batch-1")
        assertEquals("batch-1", root.stack.value.active.configuration.stableId)
        root.back()

        assertEquals(ScreenId.TODAY, root.stack.value.active.configuration.screen)
        assertEquals(1, root.stack.value.items.size)
    }

    @Test
    fun ac055_capacityResolutionCanNavigateToZonesAndReturnToPlan() = runTest {
        val root = root(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))

        root.select(PrimaryDestination.PLAN)
        root.open(ScreenId.ZONES)
        assertEquals(ScreenId.ZONES, root.stack.value.active.configuration.screen)
        root.back()

        assertEquals(ScreenId.PLAN, root.stack.value.active.configuration.screen)
    }

    @Test
    fun primaryDestinationsAreStableAndDuplicateDetailClicksAreSafe() = runTest {
        val root = root(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
        root.select(PrimaryDestination.TRAYS)
        assertEquals(ScreenId.TRAYS, root.stack.value.active.configuration.screen)

        root.open(ScreenId.BATCH_DETAIL, "batch-1")
        root.open(ScreenId.BATCH_DETAIL, "batch-1")
        assertEquals(2, root.stack.value.items.size)
        assertEquals("batch-1", root.stack.value.active.configuration.stableId)
    }

    @Test
    fun serializableStackRestoresSelectedDetail() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val stateKeeper = StateKeeperDispatcher()
        val first = root(stateKeeper, dispatcher)
        first.open(ScreenId.RECIPE, "recipe-1")
        val saved = stateKeeper.save()

        val restored = root(StateKeeperDispatcher(saved), dispatcher)
        assertEquals(ScreenId.RECIPE, restored.stack.value.active.configuration.screen)
        assertEquals("recipe-1", restored.stack.value.active.configuration.stableId)
    }

    @Test
    fun deepLinksValidateRequiredIdsAndBackReturnsToPrimary() = runTest {
        val root = root(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
        assertFalse(root.openDeepLink(DeepLink(ScreenId.BATCH_DETAIL)))
        assertTrue(root.openDeepLink(DeepLink(ScreenId.BATCH_DETAIL, "batch-2")))
        root.back()
        assertEquals(ScreenId.TODAY, root.stack.value.active.configuration.screen)
    }

    @Test
    fun adaptivePresentationUsesBottomBarRailAndBoundedPanes() {
        val root = AppRootComponentPreview()
        assertEquals(NavigationChrome.BOTTOM_BAR, root.presentation(LayoutMode.COMPACT).chrome)
        assertEquals(2, root.presentation(LayoutMode.EXPANDED).paneCount)
        assertEquals(3, root.presentation(LayoutMode.EXTRA_LARGE).paneCount)
    }

    @Test
    fun previewResetKeepsContractUsableAcrossAdaptivePresentation() {
        val root = AppRootComponentPreview()

        root.resetToFirstRun()

        assertEquals(ScreenId.FIRST_RUN, root.stack.value.active.configuration.screen)
        assertEquals(1, root.stack.value.items.size)
        assertEquals(NavigationChrome.BOTTOM_BAR, root.presentation(LayoutMode.COMPACT).chrome)
        assertEquals(NavigationChrome.EXPANDED_RAIL, root.presentation(LayoutMode.EXTRA_LARGE).chrome)
    }

    @Test
    fun ac095_resetDismissesConfirmationAndBackCannotRevealPreEraseScreens() = runTest {
        val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
        fixture.root.select(PrimaryDestination.TRAYS)
        fixture.root.open(ScreenId.BATCH_DETAIL, "batch-1")
        fixture.root.open(ScreenId.SETTINGS)
        fixture.root.stack.value.active.instance.onAction(
            ScreenAction.Execute(ScreenCommand("delete-all-data")),
        )
        assertNotNull(fixture.root.confirmation.value.child)

        fixture.root.resetToFirstRun()

        assertNull(fixture.root.confirmation.value.child)
        assertEquals(ScreenId.FIRST_RUN, fixture.root.stack.value.active.configuration.screen)
        assertEquals(1, fixture.root.stack.value.items.size)
        assertTrue(fixture.manager.commands.isEmpty())

        fixture.root.back()

        assertEquals(ScreenId.FIRST_RUN, fixture.root.stack.value.active.configuration.screen)
        assertEquals(1, fixture.root.stack.value.items.size)
    }

    @Test
    fun ac095_firstRunResetRestoresAsTheOnlyDestination() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val stateKeeper = StateKeeperDispatcher()
        val first = root(stateKeeper, dispatcher)
        first.select(PrimaryDestination.PLAN)
        first.open(ScreenId.RECIPE, "recipe-1")

        first.resetToFirstRun()
        val restored = root(StateKeeperDispatcher(stateKeeper.save()), dispatcher)

        assertEquals(ScreenId.FIRST_RUN, restored.stack.value.active.configuration.screen)
        assertEquals(1, restored.stack.value.items.size)
        restored.back()
        assertEquals(ScreenId.FIRST_RUN, restored.stack.value.active.configuration.screen)
    }

    @Test
    fun successfulContinueSetupMutatesBeforeReplacingOnboardingWithRestorablePlan() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val stateKeeper = StateKeeperDispatcher()
        lateinit var appRoot: AppRootComponent
        var screenDuringMutation: ScreenId? = null
        var executions = 0
        val manager = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
            override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
                executions += 1
                screenDuringMutation = appRoot.stack.value.active.configuration.screen
                return Result.success(ScreenSnapshot())
            }
        }
        appRoot = AppRootComponentDefault(
            DefaultComponentContext(LifecycleRegistry(), stateKeeper = stateKeeper),
            ScreenComponentFactory(
                ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher),
            ),
        )
        appRoot.open(ScreenId.FIRST_RUN)

        appRoot.stack.value.active.instance.onAction(
            ScreenAction.Execute(ScreenCommand("continue-setup")),
        )

        assertEquals(1, executions)
        assertEquals(ScreenId.FIRST_RUN, screenDuringMutation)
        assertEquals(ScreenId.PLAN, appRoot.stack.value.active.configuration.screen)
        assertEquals(1, appRoot.stack.value.items.size)
        appRoot.back()
        assertEquals(ScreenId.PLAN, appRoot.stack.value.active.configuration.screen)

        val restored = root(StateKeeperDispatcher(stateKeeper.save()), dispatcher)
        assertEquals(ScreenId.PLAN, restored.stack.value.active.configuration.screen)
        assertEquals(1, restored.stack.value.items.size)
        restored.back()
        assertEquals(ScreenId.PLAN, restored.stack.value.active.configuration.screen)
    }

    @Test
    fun ac030_createAndUpdateEditorCompletionsPopExactlyOneEntry() = runTest {
        val cases = listOf(
            Triple(ScreenId.BATCH_EDITOR, "save-batch", "batch-new"),
            Triple(ScreenId.BATCH_EDITOR, "correct-batch", "batch-1"),
            Triple(ScreenId.RECIPE, "save-recipe", "recipe-new"),
            Triple(ScreenId.RECIPE, "update-recipe", "recipe-1"),
            Triple(ScreenId.RECIPE, "duplicate-recipe", "recipe-1"),
        )

        cases.forEach { (screen, operation, stableId) ->
            val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
            fixture.root.select(PrimaryDestination.TRAYS)
            fixture.root.open(screen, stableId)
            assertEquals(2, fixture.root.stack.value.items.size)

            completeActive(fixture, operation, stableId)

            assertEquals(ScreenId.TRAYS, fixture.root.stack.value.active.configuration.screen)
            assertEquals(1, fixture.root.stack.value.items.size)
        }
    }

    @Test
    fun ac080_closeAndObservationCompletionsReturnToExistingBatchDetail() = runTest {
        val cases = listOf(
            ScreenId.OBSERVATION_EDITOR to "save-observation",
            ScreenId.OBSERVATION_EDITOR to "delete-observation",
            ScreenId.CLOSE_BATCH to "close-harvest",
            ScreenId.CLOSE_BATCH to "close-failed",
            ScreenId.CLOSE_BATCH to "close-abandoned",
            ScreenId.CLOSE_BATCH to "save-outcome",
            ScreenId.CLOSE_BATCH to "record-actual-mass",
        )

        cases.forEach { (screen, operation) ->
            val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
            fixture.root.select(PrimaryDestination.TRAYS)
            fixture.root.open(ScreenId.BATCH_DETAIL, "batch-1")
            fixture.root.open(screen, if (screen == ScreenId.CLOSE_BATCH) "batch-1" else "observation-1")
            assertEquals(3, fixture.root.stack.value.items.size)

            completeActive(fixture, operation, fixture.root.stack.value.active.configuration.stableId)

            assertEquals(ScreenId.BATCH_DETAIL, fixture.root.stack.value.active.configuration.screen)
            assertEquals("batch-1", fixture.root.stack.value.active.configuration.stableId)
            assertEquals(2, fixture.root.stack.value.items.size)
        }
    }

    @Test
    fun destructiveBatchAndRecipeCompletionReplaceHistoryWithSafeCollections() = runTest {
        val cases = listOf(
            Triple(ScreenId.BATCH_DETAIL, "delete-batch", PrimaryDestination.TRAYS),
            Triple(ScreenId.RECIPE, "delete-recipe", PrimaryDestination.CATALOG),
        )

        cases.forEach { (screen, operation, destination) ->
            val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
            fixture.root.select(PrimaryDestination.PLAN)
            fixture.root.open(ScreenId.SETTINGS)
            fixture.root.open(screen, "deleted-1")
            assertEquals(3, fixture.root.stack.value.items.size)

            completeActive(fixture, operation, "deleted-1")

            assertEquals(destination.screen, fixture.root.stack.value.active.configuration.screen)
            assertEquals(1, fixture.root.stack.value.items.size)
            fixture.root.back()
            assertEquals(destination.screen, fixture.root.stack.value.active.configuration.screen)
            assertEquals(1, fixture.root.stack.value.items.size)
        }
    }

    @Test
    fun completionRoutingRequiresTheExactActiveScreenAndOperationPair() = runTest {
        val cases = listOf(
            ScreenId.BATCH_EDITOR to "save-recipe",
            ScreenId.RECIPE to "save-batch",
            ScreenId.BATCH_DETAIL to "save-outcome",
            ScreenId.CLOSE_BATCH to "delete-batch",
            ScreenId.OBSERVATION_EDITOR to "unknown-operation",
            ScreenId.PLAN to "save-plan",
        )

        cases.forEach { (screen, operation) ->
            val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
            fixture.root.select(PrimaryDestination.TODAY)
            fixture.root.open(screen, "stable-1")
            val before = fixture.root.stack.value.items.map { it.configuration }

            completeActive(fixture, operation, "stable-1")

            assertEquals(before, fixture.root.stack.value.items.map { it.configuration })
        }
    }

    @Test
    fun lateCompletionFromAnInactiveConfigCannotNavigateTheCurrentEditor() = runTest {
        val completion = CompletableDeferred<Unit>()
        val manager = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())
            override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
                completion.await()
                return Result.success(ScreenSnapshot())
            }
        }
        val appRoot = AppRootComponentDefault(
            DefaultComponentContext(LifecycleRegistry(), stateKeeper = StateKeeperDispatcher()),
            ScreenComponentFactory(
                ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)),
            ),
        )
        appRoot.open(ScreenId.BATCH_EDITOR, "batch-old")
        appRoot.stack.value.active.instance.onAction(
            ScreenAction.Execute(ScreenCommand("save-batch", "batch-old")),
        )
        appRoot.open(ScreenId.BATCH_EDITOR, "batch-current")

        completion.complete(Unit)

        assertEquals(ScreenId.BATCH_EDITOR, appRoot.stack.value.active.configuration.screen)
        assertEquals("batch-current", appRoot.stack.value.active.configuration.stableId)
        assertEquals(3, appRoot.stack.value.items.size)
    }

    @Test
    fun destructiveCommandMutatesOnlyAfterOneExplicitConfirmation() = runTest {
        val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
        val command = ScreenCommand(
            "correct-batch", "batch-1",
            arguments = mapOf("actualSeedMassGrams" to "15.5"),
            deduplicationKey = "correct-batch:batch-1:15.5",
        )
        fixture.root.open(ScreenId.BATCH_EDITOR, "batch-1")

        fixture.root.stack.value.active.instance.onAction(ScreenAction.Execute(command))

        assertTrue(fixture.manager.commands.isEmpty())
        val confirmation = assertNotNull(fixture.root.confirmation.value.child).instance
        assertEquals(command, confirmation.command)
        assertEquals(DestructiveOperation.SAVE_BATCH_CORRECTION, confirmation.destructiveOperation)

        confirmation.confirm()
        confirmation.confirm()

        assertNull(fixture.root.confirmation.value.child)
        assertEquals(listOf(ScreenId.BATCH_EDITOR to command), fixture.manager.commands)
    }

    @Test
    fun cancelBackAndDismissNeverDispatchPendingCommand() = runTest {
        val actions: List<(AppRootComponent, ConfirmationComponent) -> Unit> = listOf(
            { _, confirmation -> confirmation.cancel() },
            { root, _ -> root.back() },
            { root, _ -> root.dismissConfirmation() },
        )

        actions.forEach { resolve ->
            val fixture = rootFixture(StateKeeperDispatcher(), UnconfinedTestDispatcher(testScheduler))
            val command = ScreenCommand("undo-stage", "batch-1")
            fixture.root.open(ScreenId.BATCH_DETAIL, "batch-1")
            fixture.root.stack.value.active.instance.onAction(ScreenAction.Execute(command))
            val confirmation = assertNotNull(fixture.root.confirmation.value.child).instance

            resolve(fixture.root, confirmation)

            assertNull(fixture.root.confirmation.value.child)
            assertTrue(fixture.manager.commands.isEmpty())
            assertEquals(ScreenId.BATCH_DETAIL, fixture.root.stack.value.active.configuration.screen)
        }
    }

    @Test
    fun pendingTypedConfirmationRestoresAndDispatchesExactCommand() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val stateKeeper = StateKeeperDispatcher()
        val initial = rootFixture(stateKeeper, dispatcher)
        val command = ScreenCommand(
            "undo-stage", "batch-1",
            arguments = mapOf("eventId" to "event-stage-3"),
            deduplicationKey = "undo-stage:batch-1:event-stage-3",
        )
        initial.root.open(ScreenId.BATCH_DETAIL, "batch-1")
        initial.root.stack.value.active.instance.onAction(ScreenAction.Execute(command))
        assertTrue(initial.manager.commands.isEmpty())

        val restored = rootFixture(StateKeeperDispatcher(stateKeeper.save()), dispatcher)
        val confirmation = assertNotNull(restored.root.confirmation.value.child).instance
        assertEquals(command, confirmation.command)
        assertEquals(DestructiveOperation.UNDO_LAST_TRANSITION, confirmation.destructiveOperation)

        confirmation.confirm()

        assertEquals(listOf(ScreenId.BATCH_DETAIL to command), restored.manager.commands)
        assertNull(restored.root.confirmation.value.child)
    }

    private fun root(stateKeeper: StateKeeperDispatcher, dispatcher: kotlin.coroutines.CoroutineContext): AppRootComponent {
        return rootFixture(stateKeeper, dispatcher).root
    }

    private fun completeActive(fixture: RootFixture, operation: String, stableId: String?) {
        fixture.root.stack.value.active.instance.onAction(
            ScreenAction.Execute(ScreenCommand(operation, stableId)),
        )
        fixture.root.confirmation.value.child?.instance?.confirm()
    }

    private fun rootFixture(
        stateKeeper: StateKeeperDispatcher,
        dispatcher: kotlin.coroutines.CoroutineContext,
    ): RootFixture {
        val manager = RecordingManager()
        val screenFactory = ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher),
        )
        val root = AppRootComponentDefault(
            DefaultComponentContext(LifecycleRegistry(), stateKeeper = stateKeeper),
            screenFactory,
        )
        return RootFixture(root, manager)
    }

    private data class RootFixture(
        val root: AppRootComponent,
        val manager: RecordingManager,
    )

    private class RecordingManager : ScreenDataManager {
        val commands = mutableListOf<Pair<ScreenId, ScreenCommand>>()

        override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot())

        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            commands += screen to command
            return Result.success(ScreenSnapshot())
        }
    }
}
