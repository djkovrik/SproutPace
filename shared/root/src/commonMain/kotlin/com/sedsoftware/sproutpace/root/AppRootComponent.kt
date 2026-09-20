package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.sedsoftware.sproutpace.component.LayoutMode
import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenId
import kotlinx.serialization.Serializable

interface AppRootComponent {
    val stack: Value<ChildStack<Config, ScreenComponent>>
    val confirmation: Value<ChildSlot<ConfirmationConfig, ConfirmationComponent>>

    fun select(destination: PrimaryDestination)
    fun open(screen: ScreenId, stableId: String? = null)
    fun openDeepLink(link: DeepLink): Boolean
    fun back()
    fun dismissConfirmation()
    /** Production roots replace all navigation history; lightweight consumers may ignore the signal. */
    fun resetToFirstRun() = Unit
    fun presentation(layoutMode: LayoutMode): NavigationPresentation

    @Serializable
    data class Config(val screen: ScreenId, val stableId: String? = null)

    @Serializable
    data class ConfirmationConfig(
        val source: Config,
        val command: ScreenCommand,
        val destructiveOperation: DestructiveOperation,
    ) {
        init {
            require(command.destructiveOperation == destructiveOperation) {
                "Confirmation classification must match the serialized command"
            }
        }
    }
}

enum class PrimaryDestination(val screen: ScreenId) {
    TODAY(ScreenId.TODAY), TRAYS(ScreenId.TRAYS), PLAN(ScreenId.PLAN), CATALOG(ScreenId.CATALOG),
}

data class DeepLink(val screen: ScreenId, val stableId: String? = null)

enum class NavigationChrome { BOTTOM_BAR, COLLAPSED_RAIL, EXPANDED_RAIL }

data class NavigationPresentation(
    val chrome: NavigationChrome,
    val paneCount: Int,
    val keepsSelectionVisible: Boolean,
)

interface ConfirmationComponent {
    val command: ScreenCommand
    val destructiveOperation: DestructiveOperation
    fun confirm()
    fun cancel()
}
