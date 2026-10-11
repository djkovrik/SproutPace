package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DraftIdentityIntegrationTest {
    @Test
    fun sameClockAndCultureHaveIndependentDraftsAndRefreshKeepsIdentity() = runTest {
        val factory = TestSqlDriverFactory()
        val database = LocalDatabase(factory, Dispatchers.Unconfined)
        try {
            val settings = MultiplatformProductSettings(MapSettings())
            var session = 0
            val manager = ProductionScreenDataManager(database, settings,
                now = { Instant.parse("2026-10-10T09:00:00Z") },
                today = { LocalDate.parse("2026-10-10") }, draftIdentity = { "session-${session++}" })
            val active = manager.watch(ScreenId.BATCH_EDITOR, "pea").produceIn(backgroundScope)
            val first = active.receive().field("id")
            assertNotEquals("pea", first)
            manager.execute(ScreenId.NOTIFICATIONS, ScreenCommand("update-notifications",
                arguments = mapOf("dailySummaryEnabled" to "true", "summaryTime" to "10:00",
                    "soakRemindersEnabled" to "true"))).getOrThrow()
            assertEquals(first, active.receive().field("id"))
            assertNotEquals(first, manager.watch(ScreenId.BATCH_EDITOR, "pea").first().field("id"))
            active.cancel()
            assertTrue(database.snapshot().batches.isEmpty())
        } finally {
            database.close()
            factory.close()
        }
    }

    @Test
    fun successfulCopyAdvancesIdentityWhileRefreshPreservesUnusedCopy() = runTest {
        val factory = TestSqlDriverFactory()
        val database = LocalDatabase(factory, Dispatchers.Unconfined)
        try {
            val manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
                draftIdentity = { "fixed-session" })
            val active = manager.watch(ScreenId.RECIPE, "pea").produceIn(backgroundScope)
            val firstId = active.receive().field("newId")
            manager.execute(ScreenId.RECIPE, ScreenCommand("duplicate-recipe", "pea", mapOf(
                "newId" to firstId, "name" to "Personal peas", "substrate" to "Mat",
            ))).getOrThrow()
            val nextId = active.receive().field("newId")
            assertNotEquals(firstId, nextId)
            assertEquals(firstId, database.snapshot().recipes.single().id.value)
            active.cancel()
        } finally {
            database.close()
            factory.close()
        }
    }

    private fun ScreenSnapshot.field(name: String): String = fields.single { it.id.value == name }.value
}
