package com.sedsoftware.sproutpace.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ExplicitInteractionSnapshotTest {
    @Test
    fun loadedExplicitEmptyInteractionDoesNotReintroduceRegistryFieldsOrActions() = runTest {
        val manager = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = flowOf(ScreenSnapshot(hasInteractionContract = true))
            override suspend fun execute(screen: ScreenId, command: ScreenCommand) = Result.success(ScreenSnapshot())
        }
        val component = ScreenComponentFactory(ScreenComponentDependencies(DefaultStoreFactory(), manager,
            UnconfinedTestDispatcher(testScheduler))).create(ScreenId.RECIPE, DefaultComponentContext(LifecycleRegistry())) { }
        assertTrue(component.model.value.fields.isEmpty())
        assertTrue(component.model.value.actions.isEmpty())
        assertTrue(component.model.value.facts.isEmpty())
    }
}
