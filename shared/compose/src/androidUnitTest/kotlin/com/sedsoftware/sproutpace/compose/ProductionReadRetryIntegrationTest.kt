package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.data.*
import com.sedsoftware.sproutpace.platform.FakeCrashReportingDriver
import com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService
import com.sedsoftware.sproutpace.platform.FakeReminderDriver
import com.sedsoftware.sproutpace.platform.PermissionState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.io.IOException
import java.util.Properties
import kotlin.test.*

/** A single transient native read fault surrounds real recovery, manager, SQLite and private files. */
class ProductionReadRetryIntegrationTest {
    @Test
    fun publicRetryRecoversActualGraphAfterTransientPrivateRecoveryReadFailureAndCanPersistNewData() = runTest {
        withPublicWorkspace { directory, preferences ->
            val before = PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.PLAN, null, "save-plan", mapOf(
                    "id" to "recovery-plan", "anchorDate" to "2026-10-30", "cadenceDays" to "5",
                    "cultureIds" to "pea", "mode" to "cadence"))
                assertTrue(fixture.database.hasMinimumGrowingSetup())
                fixture.database.snapshot()
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val driver = JdbcSqliteDriver("jdbc:sqlite:${directory.resolve("workspace.db").toAbsolutePath()}",
                Properties().apply { setProperty("foreign_keys", "true") })
            val factory = object : SqlDriverFactory {
                override fun create(): SqlDriver = driver
                override fun close() = Unit
            }
            val diskFiles = AndroidPrivateFileStore(directory.resolve("photos").toFile())
            var reads = 0
            var storageAvailable = false
            val files = object : PrivateFileStore by diskFiles {
                override suspend fun readRecoveryArchive(): ByteArray? {
                    reads += 1
                    if (!storageAvailable) throw IOException("Transient native storage read")
                    return diskFiles.readRecoveryArchive()
                }
            }
            val graph = ProductionAppGraph(factory, preferences, files, InMemoryPendingPhotoImportStore(),
                PublicFileDocuments(directory.resolve("documents")), FakeReminderDriver(),
                FakeNotificationPermissionService(PermissionState.Granted), FakeCrashReportingDriver(),
                dispatcher, dispatcher, false, DefaultStoreFactory(), DefaultStoreFactory(),
                nowEpochMillis = { Instant.parse("2026-10-10T10:00:00Z").toEpochMilliseconds() },
                timeZone = { TimeZone.UTC }, screenTimeTicks = emptyFlow())
            val inspection = LocalDatabase(factory, dispatcher)
            fun dispatch(action: UiAction) {
                val active = graph.root.stack.value.active.instance
                RootActionRouter(graph.root, active, PlatformActionHandler { _, _ -> }) {
                    active.model.value.selectedItemId
                }.dispatch(action)
            }
            try {
                graph.onStart()
                graph.onResume()
                runCurrent()
                assertFalse(graph.workspaceReady.value)
                assertEquals(LoadState.ERROR, graph.root.stack.value.active.instance.model.value.loadState)
                assertTrue(reads >= 1)
                val failedReadAttempts = reads
                storageAvailable = true
                dispatch(UiAction.Retry)
                runCurrent()
                assertTrue(graph.workspaceReady.value)
                assertEquals(failedReadAttempts + 1, reads)
                assertEquals(before, inspection.snapshot())
                assertNull(graph.root.stack.value.active.instance.model.value.errorKey)
                assertEquals(ScreenId.TODAY, graph.root.stack.value.active.configuration.screen)
                graph.root.open(ScreenId.ZONES)
                runCurrent()
                val generatedId = graph.root.stack.value.active.instance.model.value.fields.single { it.id.value == "id" }.value
                assertTrue(generatedId.isNotBlank())
                dispatch(UiAction.EditField("name", "Recovered public shelf Ж"))
                dispatch(UiAction.EditField("type", "grow_light"))
                dispatch(UiAction.EditField("capacity", "3"))
                dispatch(UiAction.Submit("save-zone"))
                runCurrent()
                assertEquals("Recovered public shelf Ж", inspection.snapshot().zones.single { it.id.value == generatedId }.name)
                assertEquals(failedReadAttempts + 1, reads)
            } finally {
                graph.close()
                driver.close()
            }
            runCurrent()
            PublicProductionFixture(directory, preferences).use { reopened ->
                assertTrue(reopened.database.snapshot().zones.any { it.name == "Recovered public shelf Ж" })
                assertEquals(before.batches, reopened.database.snapshot().batches)
                assertEquals(before.trayPresets, reopened.database.snapshot().trayPresets)
                assertEquals(before.plans, reopened.database.snapshot().plans)
            }
        }
    }
}
