package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.sedsoftware.sproutpace.component.LayoutMode
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.integration.FirstRunComponentPreview
import com.sedsoftware.sproutpace.component.integration.TodayComponentPreview

/** Component-owned root-shell preview. It is never installed by production factories. */
class AppRootComponentPreview : AppRootComponent {
    override val stack: MutableValue<ChildStack<AppRootComponent.Config, ScreenComponent>> = MutableValue(
        ChildStack(AppRootComponent.Config(ScreenId.TODAY), TodayComponentPreview()),
    )
    override val confirmation: Value<ChildSlot<AppRootComponent.ConfirmationConfig, ConfirmationComponent>> = MutableValue(ChildSlot())
    override fun select(destination: PrimaryDestination) = Unit
    override fun open(screen: ScreenId, stableId: String?) = Unit
    override fun openDeepLink(link: DeepLink) = true
    override fun back() = Unit
    override fun dismissConfirmation() = Unit
    override fun resetToFirstRun() {
        stack.value = ChildStack(AppRootComponent.Config(ScreenId.FIRST_RUN), FirstRunComponentPreview())
    }
    override fun presentation(layoutMode: LayoutMode) = when (layoutMode) {
        LayoutMode.COMPACT -> NavigationPresentation(NavigationChrome.BOTTOM_BAR, SINGLE_PANE, true)
        LayoutMode.MEDIUM -> NavigationPresentation(NavigationChrome.COLLAPSED_RAIL, SINGLE_PANE, true)
        LayoutMode.EXPANDED -> NavigationPresentation(NavigationChrome.COLLAPSED_RAIL, DUAL_PANE, true)
        LayoutMode.LARGE -> NavigationPresentation(NavigationChrome.EXPANDED_RAIL, DUAL_PANE, true)
        LayoutMode.EXTRA_LARGE -> NavigationPresentation(NavigationChrome.EXPANDED_RAIL, TRIPLE_PANE, true)
    }

    private companion object {
        const val SINGLE_PANE = 1
        const val DUAL_PANE = 2
        const val TRIPLE_PANE = 3
    }
}
