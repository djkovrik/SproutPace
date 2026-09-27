package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.ComponentContext
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
        handleBackButton = true,
        childFactory = { config, context ->
            screenFactory.create(config.screen, context, config.stableId) { output -> onChildOutput(config, output) }
        },
    )

    override val confirmation: Value<ChildSlot<AppRootComponent.ConfirmationConfig, ConfirmationComponent>> = childSlot(
        source = confirmationNavigation,
        serializer = AppRootComponent.ConfirmationConfig.serializer(),
        handleBackButton = true,
        childFactory = { config, _ ->
            ConfirmationComponentDefault(
                config = config,
                onConfirm = { confirmed ->
                    confirmationNavigation.dismiss { dismissed ->
                        if (dismissed) dispatchConfirmed(confirmed)
                    }
                },
                onCancel = { confirmationNavigation.dismiss() },
            )
        },
    )

    override fun select(destination: PrimaryDestination) {
        navigation.replaceAll(AppRootComponent.Config(destination.screen))
    }

    override fun open(screen: ScreenId, stableId: String?) {
        navigation.pushNew(AppRootComponent.Config(screen, stableId))
    }

    override fun openDeepLink(link: DeepLink): Boolean {
        if (link.screen in STABLE_ID_SCREENS && link.stableId.isNullOrBlank()) return false
        val primary = PrimaryDestination.entries.firstOrNull { it.screen == link.screen }
        if (primary != null) select(primary) else open(link.screen, link.stableId)
        return true
    }

    override fun back() {
        confirmationNavigation.dismiss { dismissed -> if (!dismissed) navigation.pop() }
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
        when {
            source.screen == ScreenId.FIRST_RUN && output.operation == "continue-setup" -> {
                select(PrimaryDestination.PLAN)
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
            active.instance.onAction(ScreenAction.ExecuteConfirmed(config.command))
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
