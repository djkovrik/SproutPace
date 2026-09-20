package com.sedsoftware.sproutpace.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import kotlin.coroutines.CoroutineContext

data class AppRootDependencies(
    val releaseStoreFactory: StoreFactory,
    val debugStoreFactory: StoreFactory,
    val isDebug: Boolean,
    val screenDataManager: ScreenDataManager,
    val mainContext: CoroutineContext,
)

/** Manual composition root. Logging/time travel may only be wrapped into debugStoreFactory. */
class AppRootFactory(private val dependencies: AppRootDependencies) {
    fun create(componentContext: ComponentContext): AppRootComponent {
        val selectedStoreFactory = if (dependencies.isDebug) dependencies.debugStoreFactory else dependencies.releaseStoreFactory
        val screenFactory = ScreenComponentFactory(
            ScreenComponentDependencies(selectedStoreFactory, dependencies.screenDataManager, dependencies.mainContext),
        )
        return AppRootComponentDefault(componentContext, screenFactory)
    }
}
