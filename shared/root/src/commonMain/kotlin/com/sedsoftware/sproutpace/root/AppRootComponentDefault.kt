package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.Cancellation
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.sedsoftware.sproutpace.component.LayoutMode
import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenOutput
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory

class AppRootComponentDefault(
    componentContext: ComponentContext,
    private val screenFactory: ScreenComponentFactory,
    initialScreen: ScreenId = ScreenId.TODAY,
) : AppRootComponent, ComponentContext by componentContext {
    private val navigation = StackNavigation<AppRootComponent.Config>()
    private val confirmationNavigation = SlotNavigation<AppRootComponent.ConfirmationConfig>()

    override val stack = childStack(
        source = navigation,
        serializer = AppRootComponent.Config.serializer(),
        initialConfiguration = AppRootComponent.Config(initialScreen),
        handleBackButton = false,
        childFactory = { config, context ->
            screenFactory.create(config.screen, context, config.stableId) { output -> onChildOutput(config, output) }
        },
    )

    override val confirmation: Value<ChildSlot<AppRootComponent.ConfirmationConfig, ConfirmationComponent>> = childSlot(
        source = confirmationNavigation,
        serializer = AppRootComponent.ConfirmationConfig.serializer(),
        handleBackButton = false,
        childFactory = { config, _ ->
            lateinit var component: ConfirmationComponent
            component = ConfirmationComponentDefault(
                config = config,
                onConfirm = { confirmed ->
                    if (isCurrentConfirmation(component)) {
                        confirmationNavigation.dismiss { dismissed ->
                            if (dismissed) dispatchConfirmed(confirmed)
                        }
                    }
                },
                onCancel = {
                    if (isCurrentConfirmation(component)) confirmationNavigation.dismiss()
                },
            )
            component
        },
    )

    init {
        val callback = BackCallback(isEnabled = false, onBack = ::back)
        backHandler.register(callback)
        fun updateBackEnabled() {
            callback.isEnabled = confirmation.value.child != null || stack.value.backStack.isNotEmpty() ||
                stack.value.active.instance.model.value.hasUnsavedChanges
        }
        var modelSubscription: Cancellation? = null
        val stackSubscription = stack.subscribe { childStack ->
            modelSubscription?.cancel()
            modelSubscription = childStack.active.instance.model.subscribe { updateBackEnabled() }
            updateBackEnabled()
        }
        val confirmationSubscription = confirmation.subscribe { updateBackEnabled() }
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                modelSubscription?.cancel()
                stackSubscription.cancel()
                confirmationSubscription.cancel()
                backHandler.unregister(callback)
            }
        })
    }

    override fun select(destination: PrimaryDestination) {
        requestNavigation(AppRootComponent.PendingNavigation.Select(AppRootComponent.Config(destination.screen)))
    }

    override fun open(screen: ScreenId, stableId: String?) {
        requestNavigation(AppRootComponent.PendingNavigation.Open(AppRootComponent.Config(screen, stableId)))
    }

    override fun openDependency(screen: ScreenId) {
        val field = when (screen) {
            ScreenId.ZONES -> "zoneId"
            ScreenId.TRAY_SIZES -> "trayId"
            else -> return
        }
        if (stack.value.active.configuration.screen != ScreenId.BATCH_EDITOR || confirmation.value.child != null) return
        val id = "dependency-${screen.name.lowercase()}-${kotlin.random.Random.nextLong().toULong()}"
        navigation.pushNew(AppRootComponent.Config(screen = screen, stableId = id, returnField = field))
    }

    override fun openDeepLink(link: DeepLink): Boolean {
        if (link.screen in STABLE_ID_SCREENS && link.stableId.isNullOrBlank()) return false
        val primary = PrimaryDestination.entries.firstOrNull { it.screen == link.screen }
        if (primary != null) select(primary) else open(link.screen, link.stableId)
        return true
    }

    override fun back() {
        confirmationNavigation.dismiss { dismissed ->
            if (!dismissed) requestNavigation(AppRootComponent.PendingNavigation.Back)
        }
    }

    override fun dismissConfirmation() { confirmationNavigation.dismiss() }

    override fun resetToFirstRun() {
        confirmationNavigation.dismiss {
            navigation.replaceAll(AppRootComponent.Config(ScreenId.FIRST_RUN))
        }
    }

    override fun presentation(layoutMode: LayoutMode): NavigationPresentation = when (layoutMode) {
        LayoutMode.COMPACT -> NavigationPresentation(NavigationChrome.BOTTOM_BAR, SINGLE_PANE, true)
        LayoutMode.MEDIUM -> NavigationPresentation(NavigationChrome.COLLAPSED_RAIL, SINGLE_PANE, true)
        LayoutMode.EXPANDED -> NavigationPresentation(NavigationChrome.COLLAPSED_RAIL, DUAL_PANE, true)
        LayoutMode.LARGE -> NavigationPresentation(NavigationChrome.EXPANDED_RAIL, DUAL_PANE, true)
        LayoutMode.EXTRA_LARGE -> NavigationPresentation(NavigationChrome.EXPANDED_RAIL, TRIPLE_PANE, true)
    }

    private fun onChildOutput(source: AppRootComponent.Config, output: ScreenOutput) {
        when (output) {
            ScreenOutput.Back -> back()
            is ScreenOutput.Open -> open(output.screen, output.stableId)
            is ScreenOutput.Completed -> onCompleted(source, output)
            is ScreenOutput.ConfirmationRequested -> {
                val active = stack.value.active.configuration
                if (active == source && active.screen == output.sourceScreen) {
                    confirmationNavigation.activate(
                        AppRootComponent.ConfirmationConfig(
                            source = active,
                            command = output.command,
                            destructiveOperation = output.destructiveOperation,
                        ),
                    )
                }
            }
        }
    }

    private fun onCompleted(source: AppRootComponent.Config, output: ScreenOutput.Completed) {
        if (stack.value.active.configuration != source) return
        val dependencyOperation = when (source.screen) {
            ScreenId.ZONES -> "save-zone"
            ScreenId.TRAY_SIZES -> "save-tray-size"
            else -> null
        }
        val parent = stack.value.backStack.lastOrNull()
        val savedId = output.stableId
        if (source.returnField != null && output.operation == dependencyOperation && savedId != null &&
            parent?.configuration?.screen == ScreenId.BATCH_EDITOR
        ) {
            parent.instance.onAction(ScreenAction.ChooseOption(ScreenFieldId(source.returnField), savedId))
            navigation.pop()
            return
        }
        when {
            source.screen in setOf(ScreenId.ZONES, ScreenId.TRAY_SIZES) &&
                output.operation in setOf(
                    "save-zone", "update-zone", "delete-zone",
                    "save-tray-size", "update-tray-size", "delete-tray-size",
                ) -> {
                navigation.pop()
            }
            source.screen == ScreenId.FIRST_RUN && output.operation == "continue-setup" -> {
                navigation.replaceAll(AppRootComponent.Config(ScreenId.PLAN))
            }
            source.screen == ScreenId.BATCH_DETAIL && output.operation == "delete-batch" -> {
                select(PrimaryDestination.TRAYS)
            }
            source.screen == ScreenId.RECIPE && output.operation == "delete-recipe" -> {
                select(PrimaryDestination.CATALOG)
            }
            source.screen == ScreenId.BATCH_EDITOR && output.operation in BATCH_EDITOR_COMPLETIONS -> {
                navigation.pop()
            }
            source.screen == ScreenId.RECIPE && output.operation in RECIPE_EDITOR_COMPLETIONS -> {
                navigation.pop()
            }
            source.screen == ScreenId.OBSERVATION_EDITOR && output.operation in OBSERVATION_COMPLETIONS -> {
                navigation.pop()
            }
            source.screen == ScreenId.CLOSE_BATCH && output.operation in CLOSE_BATCH_COMPLETIONS -> {
                navigation.pop()
            }
        }
    }

    private fun dispatchConfirmed(config: AppRootComponent.ConfirmationConfig) {
        val active = stack.value.active
        if (active.configuration == config.source) {
            val pending = config.pendingNavigation
            if (pending != null) {
                active.instance.onAction(ScreenAction.CancelDraft)
                performNavigation(pending)
            } else {
                active.instance.onAction(ScreenAction.ExecuteConfirmed(config.command))
            }
        }
    }

    private fun isCurrentConfirmation(component: ConfirmationComponent): Boolean =
        confirmation.value.child?.instance === component

    private fun requestNavigation(pending: AppRootComponent.PendingNavigation) {
        if (confirmation.value.child != null) return
        val active = stack.value.active
        if (pending is AppRootComponent.PendingNavigation.Open && pending.destination == active.configuration) return
        if (pending is AppRootComponent.PendingNavigation.Select && pending.destination == active.configuration &&
            stack.value.backStack.isEmpty()
        ) return
        val preservingSetup = active.configuration.screen == ScreenId.FIRST_RUN &&
            pending is AppRootComponent.PendingNavigation.Select
        if (active.instance.model.value.hasUnsavedChanges && !preservingSetup) {
            confirmationNavigation.activate(
                AppRootComponent.ConfirmationConfig(
                    source = active.configuration,
                    command = ScreenCommand("discard-changes", active.configuration.stableId),
                    destructiveOperation = DestructiveOperation.DISCARD_CHANGES,
                    pendingNavigation = pending,
                ),
            )
        } else {
            performNavigation(pending)
        }
    }

    private fun performNavigation(pending: AppRootComponent.PendingNavigation) {
        when (pending) {
            AppRootComponent.PendingNavigation.Back -> navigation.pop()
            is AppRootComponent.PendingNavigation.Open -> {
                if (pending.destination.screen == ScreenId.FIRST_RUN) {
                    navigation.replaceAll(pending.destination)
                } else navigation.pushNew(pending.destination)
            }
            is AppRootComponent.PendingNavigation.Select -> {
                val setup = stack.value.items.firstOrNull { it.configuration.screen == ScreenId.FIRST_RUN }?.configuration
                if (setup == null) navigation.replaceAll(pending.destination)
                else navigation.replaceAll(setup, pending.destination)
            }
        }
    }

    private companion object {
        const val SINGLE_PANE = 1
        const val DUAL_PANE = 2
        const val TRIPLE_PANE = 3
        val STABLE_ID_SCREENS = setOf(ScreenId.BATCH_DETAIL, ScreenId.RECIPE, ScreenId.OBSERVATION_EDITOR)
        val BATCH_EDITOR_COMPLETIONS = setOf("save-batch", "correct-batch")
        val RECIPE_EDITOR_COMPLETIONS = setOf("save-recipe", "update-recipe", "duplicate-recipe")
        val OBSERVATION_COMPLETIONS = setOf("save-observation", "delete-observation")
        val CLOSE_BATCH_COMPLETIONS = setOf(
            "close-harvest", "close-failed", "close-abandoned", "save-outcome", "record-actual-mass",
        )
    }
}

private class ConfirmationComponentDefault(
    private val config: AppRootComponent.ConfirmationConfig,
    private val onConfirm: (AppRootComponent.ConfirmationConfig) -> Unit,
    private val onCancel: () -> Unit,
) : ConfirmationComponent {
    override val command = config.command
    override val destructiveOperation = config.destructiveOperation
    private var resolved = false
    override fun confirm() {
        if (!resolved) {
            resolved = true
            onConfirm(config)
        }
    }
    override fun cancel() {
        if (!resolved) {
            resolved = true
            onCancel()
        }
    }
}
