package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImmediatePreferencesIntegrationTest {
    @Test
    fun sameManagerPersistsReturningThemeAndCrashPreferencePayloads() = runTest {
        val factory = TestSqlDriverFactory()
        val database = LocalDatabase(factory, Dispatchers.Unconfined)
        try {
            val settings = MultiplatformProductSettings(MapSettings())
            val manager = ProductionScreenDataManager(database, settings)
            listOf("dark", "light", "dark").forEach { value ->
                manager.execute(ScreenId.SETTINGS, ScreenCommand("set-theme",
                    arguments = mapOf("theme" to value), deduplicationKey = "theme:$value")).getOrThrow()
                assertEquals(value, settings.snapshot().theme.name.lowercase())
            }
            listOf(false, true, false).forEach { value ->
                manager.execute(ScreenId.SETTINGS, ScreenCommand("set-crash-reporting",
                    arguments = mapOf("enabled" to value.toString()), deduplicationKey = "crash:$value")).getOrThrow()
                assertEquals(value, settings.snapshot().crashReportingEnabled)
            }
        } finally {
            database.close()
            factory.close()
        }
    }
}
