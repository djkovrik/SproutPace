package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.AndroidPrivateFileStore
import com.sedsoftware.sproutpace.data.LocalWorkspaceRepository
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.root.AppRootComponentDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Public router -> retained Store -> production manager -> actual file-backed SQLite. */
class ProductionLifecycleFlowTest {
    @Test
    fun abandonRepeatAndActualStorageReopenPreserveTerminalHistory() = runTest {
        val directory = Files.createTempDirectory("sproutpace-terminal-").toFile()
        val url = "jdbc:sqlite:${directory.resolve("history.db").absolutePath}"
        try {
            val (original, repeated, observation) = Fixture(url, directory, initialize = true).use { fixture ->
                fixture.seedActiveBatch()
                fixture.root.open(ScreenId.BATCH_DETAIL, "original")
                fixture.root.open(ScreenId.OBSERVATION_EDITOR, "original")
                advanceUntilIdle()
                val editor = fixture.root.stack.value.active.instance
                assertEquals(ScreenId.OBSERVATION_EDITOR, editor.model.value.screen)
                assertEquals("original", editor.model.value.fields.single { it.id.value == "batchId" }.value)
                assertTrue(editor.model.value.fields.single { it.id.value == "id" }.value.isNotBlank())
                fixture.dispatch(UiAction.EditField("text", "Original cycle note"))
                fixture.dispatch(UiAction.Submit("save-observation"))
                advanceUntilIdle()
                assertEquals(emptyMap(), editor.model.value.validationErrors)
                assertEquals(null, editor.model.value.errorKey)
                val observation = fixture.database.snapshot().observations.single()
                assertEquals("Original cycle note", observation.text)
                assertEquals(ScreenId.BATCH_DETAIL, fixture.root.stack.value.active.configuration.screen)

                fixture.root.open(ScreenId.CLOSE_BATCH, "original")
                advanceUntilIdle()
                fixture.dispatch(UiAction.ChooseOption("outcome", "abandoned"))
                fixture.dispatch(UiAction.EditField("reason", "Travel"))
                fixture.dispatch(UiAction.Submit("save-outcome"))
                fixture.root.confirmation.value.child?.instance?.confirm()
                advanceUntilIdle()
                val original = fixture.database.snapshot().batches.single()
                assertEquals(BatchState.ABANDONED, original.state)
                assertEquals("Travel", (original.outcome as BatchOutcome.Abandoned).reason)
                assertEquals(Placement.None, original.placement)
                assertTrue(original.actions.none { it.status == ScheduledActionStatus.PENDING })
                assertTrue(original.events.isNotEmpty())
                assertEquals(ScreenId.BATCH_DETAIL, fixture.root.stack.value.active.configuration.screen)
                assertEquals(BatchState.ABANDONED, fixture.batchState())

                fixture.dispatch(UiAction.Submit("repeat-batch", original.id.value))
                advanceUntilIdle()
                assertEquals(ScreenId.BATCH_EDITOR, fixture.root.stack.value.active.configuration.screen)
                val cancelledId = fixture.field("id")
                assertNotEquals(original.id.value, cancelledId)
                assertEquals(original.substrateSnapshot, fixture.field("substrate"))
                assertEquals("", fixture.field("anchorState"))
                assertEquals(listOf(original), fixture.database.snapshot().batches)
                fixture.dispatch(UiAction.EditField("substrate", "Discard this draft"))
                fixture.root.back()
                assertNotNull(fixture.root.confirmation.value.child).instance.confirm()
                advanceUntilIdle()
                assertEquals(ScreenId.BATCH_DETAIL, fixture.root.stack.value.active.configuration.screen)
                assertEquals(listOf(original), fixture.database.snapshot().batches)
                fixture.dispatch(UiAction.Submit("repeat-batch", original.id.value))
                advanceUntilIdle()
                assertNotEquals(cancelledId, fixture.field("id"))
                fixture.dispatch(UiAction.EditField("seedLot", "LOT-2"))
                fixture.dispatch(UiAction.Submit("save-batch"))
                advanceUntilIdle()
                val batches = fixture.database.snapshot().batches
                assertEquals(2, batches.size)
                assertEquals(original, batches.single { it.id == original.id })
                val repeated = batches.single { it.id != original.id }
                assertNotEquals(original.id, repeated.id)
                assertEquals(BatchState.PLANNED, repeated.state)
                assertEquals(original.recipe, repeated.recipe)
                assertEquals(original.tray, repeated.tray)
                assertEquals(original.substrateSnapshot, repeated.substrateSnapshot)
                assertEquals(original.actualSeedMass, repeated.actualSeedMass)
                assertEquals(original.supplier, repeated.supplier)
                assertEquals(original.cultivar, repeated.cultivar)
                assertEquals("LOT-2", repeated.seedLot)
                assertTrue(repeated.events.isEmpty())
                assertEquals(null, repeated.outcome)
                assertEquals(listOf(observation), fixture.database.snapshot().observations)
                Triple(original, repeated, observation)
            }

            Fixture(url, directory, initialize = false).use { reopened ->
                val snapshot = reopened.database.snapshot()
                assertEquals(original, snapshot.batches.single { it.id == original.id })
                assertEquals(repeated, snapshot.batches.single { it.id == repeated.id })
                assertEquals(listOf(observation), snapshot.observations)
                reopened.root.open(ScreenId.BATCH_DETAIL, original.id.value)
                advanceUntilIdle()
                assertEquals(BatchState.ABANDONED, reopened.batchState())
                val model = reopened.root.stack.value.active.instance.model.value
                assertFalse(model.busy)
                assertTrue(model.items.any { it.id.startsWith("event:") })
                assertEquals("Original cycle note", model.items.single {
                    it.id == "observation:${observation.id.value}"
                }.authoredTitle)
                assertTrue(model.actions.any { it.id.value == "repeat-batch" })
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private class Fixture(url: String, directory: java.io.File, initialize: Boolean) : AutoCloseable {
        private val driver = JdbcSqliteDriver(url).also { if (initialize) SproutPaceDatabase.Schema.create(it) }
        val database = LocalDatabase(object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }, Dispatchers.Unconfined)
        private val files = AndroidPrivateFileStore(directory.resolve("observation_photos"))
        private val manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            workspaceRepository = LocalWorkspaceRepository(database, files, Dispatchers.Unconfined),
            now = { Instant.parse("2026-10-10T10:00:00Z") }, today = { LocalDate.parse("2026-10-10") })
        private val lifecycle = LifecycleRegistry().apply { resume() }
        val root = AppRootComponentDefault(DefaultComponentContext(lifecycle), ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), manager, Dispatchers.Unconfined),
        ), ScreenId.TRAYS)

        fun dispatch(action: UiAction) {
            val active = root.stack.value.active.instance
            RootActionRouter(root, active) { active.model.value.selectedItemId }.dispatch(action)
        }

        fun batchState(): BatchState = assertNotNull(root.stack.value.active.instance.model.value.items
            .first { it.batch != null && it.task == null }.batch).state

        fun field(id: String): String = root.stack.value.active.instance.model.value.fields.single { it.id.value == id }.value

        suspend fun seedActiveBatch() {
            execute(ScreenId.ZONES, "save-zone", "zone", mapOf("name" to "Shelf", "type" to "grow_light", "capacity" to "2"))
            execute(ScreenId.TRAY_SIZES, "save-tray-size", "tray", mapOf(
                "name" to "Tray", "shape" to "rectangle", "widthCm" to "25.4", "lengthCm" to "50.8",
            ))
            execute(ScreenId.BATCH_EDITOR, "save-batch", "original", mapOf(
                "cultureId" to "radish", "trayId" to "tray", "substrate" to "Coco", "actualSeedMassGrams" to "12.5",
                "plannedSowDate" to "2026-10-10", "timeZoneId" to "UTC", "supplier" to "Seed shop",
                "cultivar" to "Ruby", "seedLot" to "LOT-1",
            ))
            execute(ScreenId.BATCH_DETAIL, "confirm-sowing", "original", mapOf(
                "eventId" to "sown", "occurredAt" to "2026-10-10T09:00:00Z", "zoneId" to "zone",
            ))
        }

        private suspend fun execute(screen: ScreenId, operation: String, id: String, args: Map<String, String>) {
            manager.execute(screen, ScreenCommand(operation, id, args).newInvocation()).getOrThrow()
        }

        override fun close() {
            lifecycle.destroy()
            database.close()
            files.close()
        }
    }
}
