package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.data.InMemoryPendingPhotoImportStore
import com.sedsoftware.sproutpace.data.InMemoryPrivateFileStore
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.ScheduledActionKind
import com.sedsoftware.sproutpace.platform.FakeCrashReportingDriver
import com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService
import com.sedsoftware.sproutpace.platform.FakeReminderDriver
import com.sedsoftware.sproutpace.platform.PermissionState
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Real production graph and public root commands; native registration is the observed boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeReminderProductionRegressionTest {
    @Test fun publicStartSoakingImmediatelyRegistersOneElapsedReminderWithoutAnotherResume() = runTest {
        withPublicWorkspace { directory, preferences ->
            arrangePlannedBatch(directory, preferences)
            GraphFixture(directory, preferences, this).use { fixture ->
                fixture.start(this)
                assertTrue(fixture.physical.registeredIds().isEmpty())
                fixture.graph.root.open(ScreenId.BATCH_DETAIL, "batch")
                runCurrent()
                fixture.dispatch(UiAction.Submit("start-soaking", "batch"))
                runCurrent()
                val batch = fixture.batch()
                assertEquals(BatchState.SOAKING, batch.state)
                val target = batch.actions.single { it.kind == ScheduledActionKind.SOAK_TARGET }
                val expectedId = PhysicalReminderId("sproutpace:soak:batch:${target.id.value}")
                assertEquals(setOf(expectedId), fixture.physical.registeredIds())
                assertEquals(listOf(expectedId), fixture.physical.scheduledCalls.map { it.id })
                assertEquals((CLOCK + 6.hours).toEpochMilliseconds(), fixture.physical.scheduledCalls.single().fireAtEpochMillis)
                assertTrue(fixture.physical.cancelledCalls.isEmpty())
            }
        }
    }

    @Test fun publicActualSoakingCorrectionImmediatelyReplacesSamePhysicalTargetAndSurvivesReopen() = runTest {
        withPublicWorkspace { directory, preferences ->
            arrangePlannedBatch(directory, preferences)
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                fixture.submit("start-soaking")
            }
            lateinit var corrected: GrowBatch
            GraphFixture(directory, preferences, this).use { fixture ->
                fixture.start(this)
                val original = fixture.physical.scheduledCalls.last()
                fixture.physical.scheduledCalls.clear()
                fixture.physical.cancelledCalls.clear()
                fixture.graph.root.open(ScreenId.BATCH_EDITOR, "batch")
                runCurrent()
                fixture.dispatch(UiAction.EditField("actualSoakingAt", "2026-10-10T11:00:00Z"))
                fixture.dispatch(UiAction.Submit("correct-batch", "batch"))
                runCurrent()
                val confirmation = assertNotNull(fixture.graph.root.confirmation.value.child)
                assertEquals(CLOCK, fixture.batch().events.single().occurredAt)
                confirmation.instance.confirm()
                runCurrent()
                corrected = fixture.batch()
                assertEquals(Instant.parse("2026-10-10T11:00:00Z"), corrected.events.single().occurredAt)
                val updated = fixture.physical.scheduledCalls.single()
                assertEquals(original.id, updated.id)
                assertEquals(original.fireAtEpochMillis + 1.hours.inWholeMilliseconds, updated.fireAtEpochMillis)
                assertEquals(setOf(original.id), fixture.physical.registeredIds())
                assertFalse(fixture.physical.cancelledCalls.contains(original.id))
                assertEquals(listOf(CLOCK), corrected.events.single().priorOccurredAt)
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                assertEquals(corrected, reopened.database.snapshot().batches.single())
            }
        }
    }

    private suspend fun arrangePlannedBatch(directory: Path, preferences: MapSettings) {
        PublicProductionFixture(directory, preferences).use { fixture ->
            fixture.seed()
            fixture.settings.setNotificationsEnabled(false)
            fixture.settings.setSoakRemindersEnabled(true)
        }
    }

    private class GraphFixture(directory: Path, preferences: MapSettings, scope: TestScope) : AutoCloseable {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val driver = JdbcSqliteDriver("jdbc:sqlite:${directory.resolve("workspace.db").toAbsolutePath()}")
        private val factory = object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }
        private val inspection = LocalDatabase(factory, dispatcher)
        val physical = FakeReminderDriver()
        val graph = ProductionAppGraph(factory, preferences, InMemoryPrivateFileStore(), InMemoryPendingPhotoImportStore(),
            PublicFileDocuments(directory.resolve("documents")), physical,
            FakeNotificationPermissionService(PermissionState.Granted), FakeCrashReportingDriver(),
            dispatcher, dispatcher, false, DefaultStoreFactory(), DefaultStoreFactory(),
            nowEpochMillis = { CLOCK.toEpochMilliseconds() }, timeZone = { TimeZone.UTC }, screenTimeTicks = emptyFlow())

        fun start(scope: TestScope) {
            graph.onStart()
            graph.onResume()
            scope.runCurrent()
        }
        fun dispatch(action: UiAction) {
            val component = graph.root.stack.value.active.instance
            RootActionRouter(graph.root, component, PlatformActionHandler { _, _ -> }) {
                component.model.value.selectedItemId
            }.dispatch(action)
        }
        suspend fun batch(): GrowBatch = inspection.snapshot().batches.single()
        override fun close() { graph.close(); driver.close() }
    }

    private companion object {
        val CLOCK: Instant = Instant.parse("2026-10-10T10:00:00Z")
    }
}
