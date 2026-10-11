package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.root.AppRootComponentDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RootPreferenceProductionTest {
    @Test
    fun retainedRouterPersistsThemeAndCrashReturningToEarlierValues() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SproutPaceDatabase.Schema.create(driver)
        val database = LocalDatabase(object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }, Dispatchers.Unconfined)
        val settings = MultiplatformProductSettings(MapSettings())
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        try {
            val manager = ProductionScreenDataManager(database, settings)
            val root = AppRootComponentDefault(DefaultComponentContext(lifecycle), ScreenComponentFactory(
                ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)),
            ), ScreenId.SETTINGS)
            val component = root.stack.value.active.instance
            val router = RootActionRouter(root, component) { null }
            listOf("light", "dark", "light").forEach { value ->
                router.dispatch(UiAction.ChooseOption("theme", value))
                advanceUntilIdle()
                assertEquals(value, settings.snapshot().theme.name.lowercase())
                assertEquals(value, component.model.value.fields.single { it.id == ScreenFieldId("theme") }.value)
                assertFalse(component.model.value.busy)
            }
            listOf(false, true, false).forEach { value ->
                router.dispatch(UiAction.EditField("enabled", value.toString()))
                advanceUntilIdle()
                assertEquals(value, settings.snapshot().crashReportingEnabled)
                assertEquals(value.toString(), component.model.value.fields.single { it.id == ScreenFieldId("enabled") }.value)
                assertFalse(component.model.value.busy)
            }
        } finally {
            lifecycle.destroy()
            database.close()
        }
    }
}
