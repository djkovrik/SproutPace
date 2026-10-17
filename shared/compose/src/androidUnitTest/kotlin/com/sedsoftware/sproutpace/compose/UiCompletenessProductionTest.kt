package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.data.*
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.*
import com.sedsoftware.sproutpace.root.AppRootComponentDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.*

/** Regressions exercise public field edits and commands against actual SQLite, including storage reopen. */
class UiCompletenessProductionTest {
    @Test
    fun authoredTextSurvivesPublicEditorsStorageReopenAndBackup() = runTest {
        storage { url ->
            val original = Fixture(url, true).use { fixture ->
                fixture.save(ScreenId.ZONES, null, "save-zone", mapOf("name" to AUTHORED, "type" to "grow_light", "capacity" to " 2 "))
                val zone = fixture.database.snapshot().zones.single()
                assertEquals(AUTHORED, zone.name)
                fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf("name" to AUTHORED, "shape" to "rectangle",
                    "widthCm" to " 20 ", "lengthCm" to "30"))
                val tray = fixture.database.snapshot().trayPresets.single()
                fixture.save(ScreenId.RECIPE, null, "save-recipe", mapOf("name" to AUTHORED, "substrate" to AUTHORED,
                    "stage1" to "sown", "stage1MinimumHours" to "0", "stage1MaximumHours" to "0", "stage1Guidance" to AUTHORED,
                    "stage2" to "light_growth", "stage2MinimumHours" to "24", "stage2MaximumHours" to "48", "stage2Guidance" to AUTHORED,
                    "stage3" to "ready_for_harvest", "stage3MinimumHours" to "0", "stage3MaximumHours" to "0", "stage3Guidance" to AUTHORED,
                    "stage4" to "none", "stage5" to "none"))
                val recipe = fixture.database.snapshot().recipes.single()
                assertEquals(AUTHORED, recipe.name)
                assertTrue(recipe.stages.all { it.userGuidance == AUTHORED })
                fixture.save(ScreenId.BATCH_EDITOR, null, "save-batch", mapOf("recipeId" to recipe.id.value,
                    "trayId" to tray.id.value, "substrate" to AUTHORED, "actualSeedMassGrams" to "12.5",
                    "plannedSowDate" to "2026-10-10", "supplier" to AUTHORED, "cultivar" to AUTHORED, "seedLot" to AUTHORED))
                val batch = fixture.database.snapshot().batches.single()
                fixture.save(ScreenId.OBSERVATION_EDITOR, batch.id.value, "save-observation", mapOf("text" to AUTHORED))
                fixture.save(ScreenId.CLOSE_BATCH, batch.id.value, "save-outcome", mapOf("outcome" to "failed",
                    "reason" to AUTHORED, "note" to " \t\n "))
                val snapshot = fixture.database.snapshot()
                val saved = snapshot.batches.single()
                assertEquals(AUTHORED, saved.substrateSnapshot)
                assertEquals(AUTHORED, saved.supplier)
                assertEquals(AUTHORED, saved.cultivar)
                assertEquals(AUTHORED, saved.seedLot)
                assertEquals(AUTHORED, (saved.outcome as BatchOutcome.Failed).reason)
                assertEquals(" \t\n ", saved.outcome?.note)
                assertEquals(AUTHORED, snapshot.observations.single().text)
                val codec = LocalBackupArchiveCodec()
                assertEquals(snapshot, codec.decode(codec.encode(LocalBackupArchive(snapshot, emptyList()))).snapshot)
                snapshot
            }
            Fixture(url, false).use { reopened ->
                assertEquals(original, reopened.database.snapshot())
                reopened.root.open(ScreenId.RECIPE, original.recipes.single().id.value)
                assertEquals(AUTHORED, reopened.field("name"))
                assertEquals(AUTHORED, reopened.field("stage1Guidance"))
                reopened.root.open(ScreenId.OBSERVATION_EDITOR, original.observations.single().id.value)
                assertEquals(AUTHORED, reopened.field("text"))
            }
        }
    }

    @Test
    fun actualHarvestMassIsVisibleAfterReopenAndMissingMeasurementStaysEmpty() = runTest {
        suspend fun assertMeasurement(fixture: Fixture, expected: Double?) {
            fixture.root.open(ScreenId.BATCH_DETAIL, "batch")
            assertEquals(expected?.toString().orEmpty(), fixture.field("actualMassGrams"))
            assertEquals(expected, fixture.model().toUiState().items.single {
                it.id == "event:outcome"
            }.history?.actualHarvestMassGrams)
            val persisted = fixture.database.snapshot().batches.single()
            assertEquals(expected, (persisted.outcome as BatchOutcome.Harvested).actualMass?.value)
            assertEquals(12.5, persisted.actualSeedMass.value)
            fixture.dispatch(UiAction.Navigate(UiScreen.TRAYS))
            fixture.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY))
            assertEquals(expected, fixture.model().toUiState().items.single {
                it.id == "batch"
            }.batch?.actualHarvestMassGrams)
        }
        storage { url ->
            Fixture(url, true).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.CLOSE_BATCH, "batch", "save-outcome", mapOf("outcome" to "harvested"))
                assertMeasurement(fixture, null)
            }
            Fixture(url, false).use { fixture ->
                assertMeasurement(fixture, null)
                fixture.save(ScreenId.BATCH_DETAIL, "batch", "record-actual-mass", mapOf("actualMassGrams" to "42.5"))
                assertMeasurement(fixture, 42.5)
            }
            Fixture(url, false).use { fixture ->
                assertMeasurement(fixture, 42.5)
                fixture.save(ScreenId.BATCH_DETAIL, "batch", "record-actual-mass", mapOf("actualMassGrams" to "43.5"))
                assertMeasurement(fixture, 43.5)
            }
            Fixture(url, false).use { fixture ->
                assertMeasurement(fixture, 43.5)
                fixture.save(ScreenId.BATCH_DETAIL, "batch", "record-actual-mass", mapOf("actualMassGrams" to ""))
                assertMeasurement(fixture, null)
            }
            Fixture(url, false).use { fixture -> assertMeasurement(fixture, null) }
        }
    }

    @Test
    fun detailTaskCommandsTargetExactActionAndSkipRequiresConfirmation() = runTest {
        Fixture(JdbcSqliteDriver.IN_MEMORY, true).use { fixture ->
            fixture.seed()
            fixture.execute(ScreenId.BATCH_DETAIL, "schedule-soak-target", "batch", mapOf("dueAt" to "2026-10-10T10:30:00Z"))
            fixture.execute(ScreenId.BATCH_DETAIL, "schedule-soak-target", "batch", mapOf("dueAt" to "2026-10-10T11:30:00Z", "actionId" to "second-task"))
            fixture.root.open(ScreenId.BATCH_DETAIL, "batch")
            val completed = fixture.model().items.single { it.task?.actionId == "second-task" }.commands.single { it.operation == "complete-action" }
            fixture.dispatch(UiAction.SubmitItem(completed.operation, completed.subjectId))
            advanceUntilIdle()
            assertNull(fixture.root.confirmation.value.child)
            assertEquals(ScheduledActionStatus.COMPLETED, fixture.database.snapshot().batches.single().actions.single { it.id.value == "second-task" }.status)
            val task = fixture.model().items.single { it.commands.any { command -> command.operation == "skip-action" } }
            val skip = task.commands.single { it.operation == "skip-action" }
            val before = fixture.database.snapshot().batches.single()
            fixture.dispatch(UiAction.SubmitItem(skip.operation, skip.subjectId))
            assertEquals(before, fixture.database.snapshot().batches.single())
            assertEquals(DestructiveOperation.SKIP_ACTION, fixture.root.confirmation.value.child?.configuration?.destructiveOperation)
            fixture.root.dismissConfirmation()
            assertEquals(before, fixture.database.snapshot().batches.single())
            fixture.dispatch(UiAction.SubmitItem(skip.operation, skip.subjectId))
            val confirmation = assertNotNull(fixture.root.confirmation.value.child).instance
            confirmation.confirm()
            confirmation.confirm()
            advanceUntilIdle()
            val after = fixture.database.snapshot().batches.single()
            assertEquals(before.state, after.state)
            assertEquals(before.events, after.events)
            assertEquals(ScheduledActionStatus.SKIPPED, after.actions.single { it.id.value == skip.subjectId }.status)
            assertTrue(fixture.model().items.none { it.commands.any { command -> command.subjectId == skip.subjectId } })
        }
    }

    @Test
    fun managerCreatedCompletableSoakReminderIsScheduledAndStopsDeliveringAfterCompletion() = runTest {
        Fixture(JdbcSqliteDriver.IN_MEMORY, true).use { fixture ->
            fixture.seed()
            fixture.settings.setNotificationsEnabled(false)
            fixture.execute(ScreenId.BATCH_DETAIL, "start-soaking", "batch", mapOf(
                "eventId" to "soaking", "occurredAt" to "2026-10-10T09:00:00Z"))
            val initialActions = fixture.database.snapshot().batches.single().actions
            val lifecycleActions = initialActions.filter { it.stage != null }
            assertNull(initialActions.single { it.id.value == "soak:batch" }.stage)
            assertEquals(ScheduledActionKind.SOAK_TARGET, initialActions.single { it.id.value == "soak:batch" }.kind)
            val codec = DomainJsonCodec()
            val original = fixture.database.snapshot().batches.single()
            val legacyJson = codec.encodeBatch(original).replace(",\"kind\":\"GENERAL\"", "")
            assertEquals(original, codec.decodeBatch(legacyJson))
            fixture.save(ScreenId.BATCH_DETAIL, "batch", "schedule-soak-target", mapOf("dueAt" to "2026-10-10T10:30:00Z"))
            val actions = fixture.database.snapshot().batches.single().actions
            assertTrue(actions.containsAll(lifecycleActions))
            assertEquals(initialActions.size, actions.size)
            assertEquals(DueMoment.ElapsedTarget(Instant.parse("2026-10-10T10:30:00Z")),
                actions.single { it.id.value == "soak:batch" }.due)
            var now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilliseconds()
            val source = SnapshotReminderPlanSource({ fixture.database.snapshot() }, fixture.settings, { now }, { kotlinx.datetime.TimeZone.UTC })
            val reminder = source.currentPlan().soakReminders.single()
            assertEquals("sproutpace:soak:batch:soak:batch", reminder.id.value)
            assertNull(fixture.database.snapshot().batches.single().actions.single { it.id.value == "soak:batch" }.stage)
            now = reminder.fireAtEpochMillis
            assertTrue(source.shouldDeliver(reminder.id, reminder.fireAtEpochMillis))
            fixture.root.open(ScreenId.BATCH_DETAIL, "batch")
            fixture.dispatch(UiAction.SubmitItem("complete-action", "soak:batch"))
            advanceUntilIdle()
            assertFalse(source.shouldDeliver(reminder.id, reminder.fireAtEpochMillis))
            assertTrue(source.currentPlan().soakReminders.isEmpty())
        }
    }

    @Test
    fun customSoakTargetRetainsItsPurposeAfterStorageAndBackupAndOtherTasksAreNotSoakReminders() = runTest {
        val preferences = MapSettings()
        storage { url ->
            Fixture(url, true, preferences).use { fixture ->
                fixture.seed()
                fixture.settings.setNotificationsEnabled(false)
                fixture.execute(ScreenId.BATCH_DETAIL, "schedule-soak-target", "batch", mapOf(
                    "actionId" to "custom-reminder", "dueAt" to "2026-10-10T10:30:00Z"))
                val batch = fixture.database.snapshot().batches.single()
                fixture.database.saveBatch(batch.copy(actions = batch.actions + ScheduledAction(
                    ScheduledActionId("ordinary-task"), batch.id, null,
                    DueMoment.ElapsedTarget(Instant.parse("2026-10-10T10:30:00Z")),
                )))
                val snapshot = fixture.database.snapshot()
                assertEquals(ScheduledActionKind.SOAK_TARGET, snapshot.batches.single().actions.single {
                    it.id.value == "custom-reminder"
                }.kind)
                val codec = LocalBackupArchiveCodec()
                assertEquals(snapshot, codec.decode(codec.encode(LocalBackupArchive(snapshot, emptyList()))).snapshot)
            }
            Fixture(url, false, preferences).use { reopened ->
                var now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilliseconds()
                val source = SnapshotReminderPlanSource({ reopened.database.snapshot() }, reopened.settings,
                    { now }, { kotlinx.datetime.TimeZone.UTC })
                val reminder = source.currentPlan().soakReminders.single()
                assertEquals("sproutpace:soak:batch:custom-reminder", reminder.id.value)
                now = reminder.fireAtEpochMillis
                assertTrue(source.shouldDeliver(reminder.id, reminder.fireAtEpochMillis))
                reopened.root.open(ScreenId.BATCH_DETAIL, "batch")
                assertEquals("custom-reminder", reopened.field("actionId"))
                assertEquals("2026-10-10T10:30:00Z", reopened.field("dueAt"))
                reopened.dispatch(UiAction.SubmitItem("complete-action", "custom-reminder"))
                advanceUntilIdle()
                assertFalse(source.shouldDeliver(reminder.id, reminder.fireAtEpochMillis))
                assertTrue(source.currentPlan().soakReminders.isEmpty())
                assertEquals(ScheduledActionStatus.PENDING, reopened.database.snapshot().batches.single().actions.single {
                    it.id.value == "ordinary-task"
                }.status)
            }
        }
    }

    @Test
    fun publicRestorePreviewCancelAndEraseClearsSelectionsAndReopensEmptyFirstRun() = runTest {
        storage { url ->
            Fixture(url, true).use { fixture ->
                fixture.seed()
                fixture.documents.bytes["imported"] = fixture.backup.createArchiveBytes()
                fixture.root.open(ScreenId.BACKUP_RESTORE)
                fixture.dispatch(UiAction.Submit("preview-restore"))
                advanceUntilIdle()
                assertTrue(fixture.model().facts.any { it.id == "restore-format-version" })
                val beforeCancel = fixture.database.snapshot()
                fixture.dispatch(UiAction.Submit("cancel-restore-preview"))
                advanceUntilIdle()
                assertEquals(beforeCancel, fixture.database.snapshot())
                assertTrue(fixture.model().facts.none { it.id.startsWith("restore-") })
                fixture.dispatch(UiAction.Submit("preview-restore"))
                advanceUntilIdle()
                fixture.dispatch(UiAction.Submit("erase-all"))
                assertEquals(beforeCancel, fixture.database.snapshot())
                assertNotNull(fixture.root.confirmation.value.child).instance.confirm()
                advanceUntilIdle()
                assertEquals(ScreenId.FIRST_RUN, fixture.model().screen)
                assertTrue(fixture.database.snapshot().batches.isEmpty())
                assertTrue(fixture.documents.bytes.isEmpty())
                assertEquals(1, fixture.pendingCleanupCount)
                fixture.root.open(ScreenId.BACKUP_RESTORE)
                assertTrue(fixture.model().facts.none { it.id.startsWith("restore-") })
                assertEquals("", fixture.field("documentId"))
            }
            Fixture(url, false).use { reopened ->
                assertTrue(reopened.database.snapshot().zones.isEmpty())
                assertTrue(reopened.database.snapshot().batches.isEmpty())
                reopened.root.open(ScreenId.FIRST_RUN)
                assertEquals("", reopened.field("zoneName"))
                assertEquals("", reopened.field("trayName"))
            }
        }
    }

    @Test
    fun assignedCapacityConflictAppearsOnTraysAndResolvesWithoutMovingOrRewritingBatch() = runTest {
        Fixture(JdbcSqliteDriver.IN_MEMORY, true).use { fixture ->
            fixture.seed()
            fixture.execute(ScreenId.BATCH_DETAIL, "confirm-sowing", "batch", mapOf("eventId" to "sown", "occurredAt" to "2026-10-10T09:00:00Z", "zoneId" to "zone"))
            val before = fixture.database.snapshot().batches.single()
            fixture.execute(ScreenId.ZONES, "update-zone", "zone", mapOf("name" to "Shelf", "type" to "grow_light", "capacity" to "0"))
            val row = fixture.manager.watch(ScreenId.TRAYS).first().items.single()
            assertEquals(1, row.occupancy?.overflow)
            assertEquals("capacity_conflict_title", row.supportingKey?.value)
            fixture.root.open(ScreenId.TRAYS)
            val command = row.commands.single()
            fixture.dispatch(UiAction.SubmitItem(command.operation, command.subjectId))
            assertEquals(ScreenId.ZONES, fixture.model().screen)
            assertEquals("zone", fixture.model().selectedItemId)
            fixture.dispatch(UiAction.EditField("capacity", "2"))
            fixture.dispatch(UiAction.Submit("update-zone"))
            advanceUntilIdle()
            assertNull(fixture.manager.watch(ScreenId.TRAYS).first().items.single().occupancy)
            assertEquals(before, fixture.database.snapshot().batches.single())
        }
    }

    @Test
    fun firstRunOptionalDarkZoneAndLocalTimeValidateAndPersistWithoutCreatingBatch() = runTest {
        val preferences = MapSettings()
        storage { url ->
            Fixture(url, true, preferences).use { fixture ->
                fixture.root.open(ScreenId.FIRST_RUN)
                mapOf("zoneName" to AUTHORED, "trayName" to AUTHORED, "darkZoneEnabled" to "true",
                    "darkZoneName" to AUTHORED, "darkZoneCapacity" to "0", "dailySummaryEnabled" to "true", "summaryTime" to "bad")
                    .forEach { (id, value) -> fixture.dispatch(UiAction.EditField(id, value)) }
                fixture.dispatch(UiAction.Submit("continue-setup"))
                assertTrue(fixture.model().validationErrors.keys.map { it.value }.containsAll(listOf("darkZoneCapacity", "summaryTime")))
                assertTrue(fixture.database.snapshot().zones.isEmpty())
                fixture.dispatch(UiAction.EditField("darkZoneCapacity", "2"))
                fixture.dispatch(UiAction.EditField("summaryTime", "07:35"))
                fixture.dispatch(UiAction.Submit("continue-setup"))
                advanceUntilIdle()
                assertEquals(2, fixture.database.snapshot().zones.size)
                assertEquals(GrowZoneType.DARK, fixture.database.snapshot().zones.single { it.id.value == "first-run-dark-zone" }.type)
                assertTrue(fixture.database.snapshot().batches.isEmpty())
            }
            Fixture(url, false, preferences).use { reopened ->
                assertEquals(SummaryLocalTime(7, 35), reopened.settings.summaryTime.value)
                assertTrue(reopened.settings.notificationsEnabled.value)
                assertEquals(2, reopened.database.snapshot().zones.size)
                assertTrue(reopened.database.snapshot().batches.isEmpty())
            }
        }
    }

    private suspend fun storage(block: suspend (String) -> Unit) {
        val path = Files.createTempFile("sproutpace-ui-completeness-", ".db")
        try { block("jdbc:sqlite:${path.toAbsolutePath()}") } finally { Files.deleteIfExists(path) }
    }

    private class Fixture(url: String, initialize: Boolean, preferences: MapSettings = MapSettings()) : AutoCloseable {
        private val driver = JdbcSqliteDriver(url).also { if (initialize) SproutPaceDatabase.Schema.create(it) }
        val database = LocalDatabase(object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }, Dispatchers.Unconfined)
        val settings = MultiplatformProductSettings(preferences)
        private val files = InMemoryPrivateFileStore()
        val documents = Documents()
        var pendingCleanupCount = 0
        val backup: LocalBackupService = LocalBackupService(database, files, settings, Dispatchers.Unconfined, documents = documents,
            output = LocalDataIntegrationOutput { root.resetToFirstRun() }, clearPendingPhotos = { pendingCleanupCount += 1 })
        val manager = ProductionScreenDataManager(database, settings,
            workspaceRepository = LocalWorkspaceRepository(database, files, Dispatchers.Unconfined),
            backupService = backup,
            now = { Instant.parse("2026-10-10T10:00:00Z") }, today = { LocalDate.parse("2026-10-10") })
        private val lifecycle = LifecycleRegistry().apply { resume() }
        val root: AppRootComponentDefault = AppRootComponentDefault(DefaultComponentContext(lifecycle), ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), manager, Dispatchers.Unconfined)), ScreenId.TRAYS)
        fun model() = root.stack.value.active.instance.model.value
        fun field(id: String) = model().fields.single { it.id.value == id }.value
        fun dispatch(action: UiAction) {
            val active = root.stack.value.active.instance
            RootActionRouter(root, active, PlatformActionHandler { request, result ->
                if (request.kind == PlatformActionKind.CHOOSE_DOCUMENT) result("imported")
            }) { active.model.value.selectedItemId }.dispatch(action)
        }
        fun save(screen: ScreenId, stableId: String?, operation: String, fields: Map<String, String>) {
            root.open(screen, stableId)
            fields.forEach { (id, value) -> dispatch(UiAction.EditField(id, value)) }
            dispatch(UiAction.Submit(operation))
            root.confirmation.value.child?.instance?.confirm()
        }
        suspend fun execute(screen: ScreenId, operation: String, id: String, args: Map<String, String>) {
            manager.execute(screen, ScreenCommand(operation, id, args).newInvocation()).getOrThrow()
        }
        suspend fun seed() {
            execute(ScreenId.ZONES, "save-zone", "zone", mapOf("name" to "Shelf", "type" to "grow_light", "capacity" to "2"))
            execute(ScreenId.TRAY_SIZES, "save-tray-size", "tray", mapOf("name" to "Tray", "shape" to "rectangle", "widthCm" to "20", "lengthCm" to "30"))
            execute(ScreenId.BATCH_EDITOR, "save-batch", "batch", mapOf("cultureId" to "pea", "trayId" to "tray", "substrate" to "Coco",
                "actualSeedMassGrams" to "12.5", "plannedSowDate" to "2026-10-10"))
        }
        override fun close() { lifecycle.destroy(); database.close(); files.close() }
    }

    private class Documents : ArchiveDocumentPort {
        val bytes = mutableMapOf<String, ByteArray>()
        override suspend fun read(documentId: String) = bytes[documentId]
        override suspend fun write(documentId: String, bytes: ByteArray) { this.bytes[documentId] = bytes }
        override suspend fun clearImportedArchives() { bytes.clear() }
    }

    companion object { private const val AUTHORED = " \tСвой текст\nsecond line  " }
}
