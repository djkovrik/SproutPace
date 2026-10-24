package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class LocalDataAcceptanceTest {
    @Test fun archiveCsvAndPreviewRunOnWorkerAndCancelledReadNeverReplacesMultiPhotoWorkspace() = runTest {
        seedCompleteFixture(fixture())
        val before = backup.createArchive()
        val archiveBytes = LocalBackupArchiveCodec().encode(before)
        val caller = Thread.currentThread()
        val workerThread = java.util.concurrent.atomic.AtomicReference<Thread>()
        java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "archive-worker").also(workerThread::set)
        }
            .asCoroutineDispatcher().use { worker ->
                var cancelRead = false
                val operations = mutableListOf<String>()
                fun workerBoundary(operation: String) {
                    assertNotEquals(caller, Thread.currentThread(), operation)
                    assertSame(workerThread.get(), Thread.currentThread(), operation)
                    operations += operation
                }
                val documents = object : ArchiveDocumentPort {
                    override suspend fun read(documentId: String): ByteArray {
                        workerBoundary("read")
                        if (cancelRead) currentCoroutineContext().cancel()
                        return archiveBytes
                    }
                    override suspend fun write(documentId: String, bytes: ByteArray) { workerBoundary("write") }
                }
                val service = LocalBackupService(database, files, settings, worker, documents = documents,
                    now = { workerBoundary("snapshot"); before.createdAt },
                    csvHeaders = { workerBoundary("csv"); BatchHistoryCsv.HEADER.split(',') })
                service.writeArchive("backup")
                service.writeCsv("csv")
                assertIs<BackupPreview.Valid>(service.readPreview("backup"))
                assertTrue(operations.containsAll(listOf("snapshot", "write", "csv", "read")))
                cancelRead = true
                val job = async { service.replaceFromDocument("backup") }
                assertFailsWith<CancellationException> { job.await() }
                assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
                assertNull(files.readRecoveryArchive())
            }
    }

    @Test fun invalidPhotoPathsAreRejectedBeforeAnyWorkspaceOrPhotoWrite() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        val paths = listOf("../outside-photos.txt", "..\\outside.txt", "/tmp/outside", "C:\\outside", "a/b", "a\\b", ".", "..")
        for (path in paths) {
            val id = PhotoId(path)
            val oldId = before.photos.first().id
            val photos = before.photos.map { if (it.id == oldId) it.copy(id = id) else it }
            val observations = before.snapshot.observations.map { if (it.photoId == oldId) it.copy(photoId = id) else it }
            val snapshot = before.snapshot.copy(observations = observations, manifest = before.snapshot.manifest.copy(
                photoIntegrity = photos.associate { it.id to it.sha256 }))
            val hostile = before.copy(snapshot = snapshot, photos = photos)
            assertIs<BackupPreview.Invalid>(backup.preview(hostile), path)
            assertFailsWith<LocalDataException>(path) { backup.replaceArchive(hostile) }
            assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt), path)
            assertNull(files.readRecoveryArchive(), path)
        }
        assertIs<BackupPreview.Valid>(backup.preview(before))
    }

    @Test fun unknownPlanCultureAndBrokenRequiredLinksFailPreviewAndPreserveWorkspace() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        val current = before.snapshot
        val variants = listOf(
            current.copy(plans = current.plans.map { it.copy(cultureRotation = listOf(CultureId("unknown-culture"))) }),
            current.copy(batches = current.batches.map { batch -> batch.copy(actions = batch.actions.map {
                it.copy(batchId = BatchId("missing-batch")) }) }),
            current.copy(batches = current.batches.map { batch -> batch.copy(actions = batch.actions.map {
                it.copy(due = DueMoment.CivilTime(LocalDateTime(2026, 10, 17, 9, 0), "Invalid/TimeZone")) }) }),
            current.copy(batches = current.batches.map { batch -> if (batch.state.isTerminal) batch else batch.copy(
                placement = Placement.Assigned(GrowZoneId("missing-zone"), "Deleted")) }),
        )
        for (snapshot in variants) {
            val invalid = before.copy(snapshot = snapshot)
            assertIs<BackupPreview.Invalid>(backup.preview(invalid))
            assertFailsWith<LocalDataException> { backup.replaceArchive(invalid) }
            assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        }
        backup.replaceArchive(before)
        val manager = ProductionScreenDataManager(database, settings)
        listOf(com.sedsoftware.sproutpace.component.ScreenId.TODAY,
            com.sedsoftware.sproutpace.component.ScreenId.PLAN,
            com.sedsoftware.sproutpace.component.ScreenId.ZONES).forEach { manager.watch(it).first() }
    }

    @Test fun resetCleansImportedArchivesAndPendingPhotosAndReportsCleanupFailure() = runTest {
        seedCompleteFixture(fixture())
        var imports = 1
        var pendingPhotos = 2
        var failCleanup = true
        val documents = object : ArchiveDocumentPort {
            override suspend fun read(documentId: String): ByteArray? = null
            override suspend fun write(documentId: String, bytes: ByteArray) = Unit
            override suspend fun clearImportedArchives() {
                if (failCleanup) error("import cleanup failed")
                imports = 0
            }
        }
        val service = LocalBackupService(database, files, settings, Dispatchers.Unconfined,
            documents = documents, clearPendingPhotos = { pendingPhotos = 0 })
        val before = backup.createArchive()
        assertEquals(LocalDataErrorKey.ERASE_FAILED, assertFailsWith<LocalDataException> { service.eraseAll() }.key)
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        assertEquals(1, imports)
        failCleanup = false
        service.eraseAll()
        assertEquals(0, imports)
        assertEquals(0, pendingPhotos)
        assertTrue(database.snapshot().batches.isEmpty())
        assertTrue(files.exportAll().isEmpty())
        assertNull(files.readRecoveryArchive())
    }

    @Test fun codecCancellationDuringMultiPhotoEncodeAndDecodeIsNeverWrappedAsCorruption() = runTest {
        seedCompleteFixture(fixture())
        val archive = backup.createArchive()
        val codec = LocalBackupArchiveCodec()
        val bytes = codec.encode(archive)
        var checkpoints = 0
        assertFailsWith<CancellationException> { codec.encode(archive) {
            if (++checkpoints == 13) throw CancellationException("encode cancelled during second photo")
        } }
        checkpoints = 0
        assertFailsWith<CancellationException> { codec.decode(bytes) {
            if (++checkpoints == 13) throw CancellationException("decode cancelled during second photo")
        } }
        assertEquals(archive, backup.createArchive().copy(createdAt = archive.createdAt))
    }

    @Test fun cancelledEraseRestoresAllStateBeforePropagatingCancellation() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        var reconciliations = 0
        val interrupted = LocalBackupService(database, files, settings, Dispatchers.Unconfined,
            reminders = ReminderReconciliationPort {
                reconciliations++
                if (reconciliations == 1) {
                    currentCoroutineContext().cancel()
                    awaitCancellation()
                }
                currentCoroutineContext().ensureActive()
            })
        val worker = async { interrupted.eraseAll() }
        assertFailsWith<CancellationException> { worker.await() }
        assertEquals(2, reconciliations)
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        assertNull(files.readRecoveryArchive())
    }

    @Test fun localizedCsvHeadersAreInjectedAndEscapedWithoutChangingAuthoredValues() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val headers = listOf("Партия", "Культура", "Рецепт", "Статус", "Дата результата", "Масса, г", "Заметка")
        val snapshot = database.snapshot()
        val russian = BatchHistoryCsv.encode(snapshot, headers).decodeToString()
        val english = BatchHistoryCsv.encode(snapshot).decodeToString()
        assertTrue(russian.startsWith("Партия,Культура,Рецепт,Статус,Дата результата,\"Масса, г\",Заметка\r\n"))
        assertEquals(english.substringAfter("\r\n"), russian.substringAfter("\r\n"))
        assertTrue(russian.contains("Хороший, свежий"))
        assertFalse(russian.contains("photo-1"))
        val documents = mutableMapOf<String, ByteArray>()
        val service = LocalBackupService(database, files, settings, Dispatchers.Unconfined,
            documents = object : ArchiveDocumentPort {
                override suspend fun read(documentId: String) = documents[documentId]
                override suspend fun write(documentId: String, bytes: ByteArray) { documents[documentId] = bytes }
            }, csvHeaders = { headers })
        service.writeCsv("picked-destination")
        assertContentEquals(russian.encodeToByteArray(), documents.getValue("picked-destination"))
    }
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var files: InMemoryPrivateFileStore
    private lateinit var settings: MultiplatformProductSettings
    private lateinit var backup: LocalBackupService
    private lateinit var workspace: LocalWorkspaceRepository

    @BeforeTest
    fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        files = InMemoryPrivateFileStore()
        settings = MultiplatformProductSettings(MapSettings())
        backup = LocalBackupService(database, files, settings, Dispatchers.Unconfined)
        workspace = LocalWorkspaceRepository(database, files, Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        files.close()
        factory.close()
    }

    @Test fun ac001_startupSetupDetectionRequiresZoneTrayAndPlan() = runTest {
        val fixture = fixture()
        assertFalse(database.hasMinimumGrowingSetup())
        database.saveZone(fixture.zone)
        database.saveTrayPreset(fixture.tray)
        assertFalse(database.hasMinimumGrowingSetup())

        database.savePlan(fixture.plan)

        assertTrue(database.hasMinimumGrowingSetup())
    }

    @Test fun ac013_deleteZoneUnassignsCurrentBatchesAndPreservesHistory() = runTest {
        val fixture = fixture()
        database.saveZone(fixture.zone)
        val planned = fixture.activeBatch.copy(id = BatchId("planned"), state = BatchState.PLANNED,
            events = emptyList(), actions = emptyList())
        val historical = fixture.terminalBatch.copy(placement = fixture.activeBatch.placement)
        val otherZone = fixture.zone.copy(id = GrowZoneId("other-zone"), name = "Other shelf")
        val unrelated = fixture.activeBatch.copy(id = BatchId("other-batch"),
            placement = Placement.Assigned(otherZone.id, otherZone.name), events = emptyList(), actions = emptyList())
        database.saveZone(otherZone)
        listOf(fixture.activeBatch, planned, historical, unrelated).forEach { database.saveBatch(it) }
        val manager = ProductionScreenDataManager(database, settings)

        manager.execute(com.sedsoftware.sproutpace.component.ScreenId.ZONES,
            com.sedsoftware.sproutpace.component.ScreenCommand("delete-zone", fixture.zone.id.value)).getOrThrow()

        val saved = database.snapshot()
        assertEquals(listOf(otherZone), saved.zones)
        val unassigned = Placement.Unassigned(fixture.zone.name)
        assertEquals(fixture.activeBatch.copy(placement = unassigned), saved.batches[0])
        assertEquals(planned.copy(placement = unassigned), saved.batches[1])
        assertEquals(historical, saved.batches[2])
        assertEquals(unrelated, saved.batches[3])
        val trays = manager.watch(com.sedsoftware.sproutpace.component.ScreenId.TRAYS).first()
        assertEquals("state_temporarily_unassigned_title",
            trays.items.first { it.id == fixture.activeBatch.id.value }.supportingKey?.value)
        val detail = manager.watch(com.sedsoftware.sproutpace.component.ScreenId.BATCH_DETAIL,
            fixture.activeBatch.id.value).first()
        assertTrue(detail.conflictResolutionKeys.isNotEmpty())

        manager.execute(com.sedsoftware.sproutpace.component.ScreenId.BATCH_DETAIL,
            com.sedsoftware.sproutpace.component.ScreenCommand("undo-stage", fixture.activeBatch.id.value)).getOrThrow()
        assertEquals(unassigned, database.snapshot().batches.first().placement)
        assertEquals(historical, database.snapshot().batches.first { it.id == historical.id })
    }

    @Test fun savingCurrentBatchWithMissingZoneNormalizesOnlyCurrentPlacement() = runTest {
        val fixture = fixture()
        val expected = fixture.activeBatch.copy(placement = Placement.Unassigned(fixture.zone.name))
        assertEquals(expected, database.saveBatch(fixture.activeBatch))
        assertEquals(expected, database.snapshot().batches.single())
        assertEquals(fixture.activeBatch.events, expected.events)
    }

    @Test fun ac015_savedMetricTrayRestoresItsNameShapeAndDimensions() = runTest {
        val tray = fixture().tray
        database.saveTrayPreset(tray)

        val restored = database.watchTrayPresets().first().single()

        assertEquals(tray.id, restored.id)
        assertEquals(tray.name, restored.name)
        assertEquals(tray.shape, restored.shape)
    }

    @Test fun ac016_updatingTrayChangesFutureSelectionButNotBatchSnapshot() = runTest {
        val fixture = fixture()
        database.saveTrayPreset(fixture.tray)
        database.saveBatch(fixture.activeBatch)
        val updated = fixture.tray.copy(shape = TrayShape.Circle(30.0))

        database.saveTrayPreset(updated)

        assertEquals(updated, database.snapshot().trayPresets.single())
        assertEquals(fixture.activeBatch.tray, database.snapshot().batches.single().tray)
    }

    @Test fun ac017_deletingTrayKeepsHistoricalBatchSnapshotReadable() = runTest {
        val fixture = fixture()
        database.saveTrayPreset(fixture.tray)
        database.saveBatch(fixture.activeBatch)

        database.deleteTrayPreset(fixture.tray.id)

        assertTrue(database.snapshot().trayPresets.isEmpty())
        assertEquals(fixture.activeBatch.tray, database.snapshot().batches.single().tray)
    }

    @Test fun ac022_personalRecipeRestoresEveryAuthoredField() = runTest {
        val recipe = fixture().recipe
        database.saveRecipe(recipe)

        assertEquals(recipe, database.watchRecipes().first().single())
        assertEquals("LOT-42", database.snapshot().recipes.single().seedLot)
        assertEquals(Percentage(91), database.snapshot().recipes.single().germinationPercentage)
    }

    @Test fun ac023_recipeUpdateDoesNotMutateExistingBatchSnapshot() = runTest {
        val fixture = fixture()
        database.saveRecipe(fixture.recipe)
        database.saveBatch(fixture.activeBatch)

        database.saveRecipe(fixture.recipe.copy(name = "Updated recipe", substrate = "hemp"))

        assertEquals("Updated recipe", database.snapshot().recipes.single().name)
        assertEquals(fixture.activeBatch.recipe, database.snapshot().batches.single().recipe)
        assertEquals("coco coir", database.snapshot().batches.single().substrateSnapshot)
    }

    @Test fun ac024_deleteRecipePreservesReferencedHistory() = runTest {
        val fixture = fixture()
        database.saveRecipe(fixture.recipe)
        database.saveBatch(fixture.terminalBatch)

        database.deleteRecipe(fixture.recipe.id)

        assertTrue(database.snapshot().recipes.isEmpty())
        assertEquals(fixture.terminalBatch, database.snapshot().batches.single())
    }

    @Test fun ac025_duplicateRecipePersistsCopiedParametersUnderDistinctIdentity() = runTest {
        val original = fixture().recipe
        val duplicate = original.copy(id = RecipeId("recipe-copy"), name = "Copy of ${original.name}")
        database.saveRecipe(original)

        database.saveRecipe(duplicate)

        val recipes = database.snapshot().recipes
        assertEquals(setOf(original.id, duplicate.id), recipes.mapTo(linkedSetOf()) { it.id })
        assertEquals(original.stages, recipes.first { it.id == duplicate.id }.stages)
        assertEquals(original.density, recipes.first { it.id == duplicate.id }.density)
    }

    @Test fun ac033_deletePlannedBatchLeavesNoHistoryOrTrashRecord() = runTest {
        val batch = fixture().activeBatch.copy(state = BatchState.PLANNED, placement = Placement.None, events = emptyList())
        database.saveBatch(batch)

        database.deleteBatch(batch.id)

        assertTrue(database.snapshot().batches.isEmpty())
        assertTrue(database.snapshot().observations.isEmpty())
    }

    @Test fun ac034_deleteActiveBatchCascadesObservationsAndPrivatePhotos() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        workspace.saveObservation(fixture.observations.first(), fixture.photoOne)

        workspace.deleteBatch(fixture.activeBatch.id)

        assertTrue(database.snapshot().batches.isEmpty())
        assertTrue(database.snapshot().observations.isEmpty())
        assertNull(files.read(PhotoId("photo-1")))
    }

    @Test fun ac053_deletePlanPreservesRelatedBatches() = runTest {
        val fixture = fixture()
        database.saveZone(fixture.zone)
        database.savePlan(fixture.plan)
        database.saveBatch(fixture.activeBatch)

        database.deletePlan(fixture.plan.id)

        assertTrue(database.snapshot().plans.isEmpty())
        assertEquals(fixture.activeBatch, database.snapshot().batches.single())
    }

    @Test fun ac056_planSuggestionDoesNotCreateBatchBeforeExplicitSave() = runTest {
        database.savePlan(fixture().plan)

        assertTrue(database.snapshot().batches.isEmpty())
        assertEquals(1, database.snapshot().plans.size)
    }

    @Test fun ac070_datedObservationPersistsInChronologicalHistory() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        database.saveObservation(fixture.observations.last())
        database.saveObservation(fixture.observations.first())

        assertEquals(
            fixture.observations.map { it.id },
            database.watchObservations().first().map { it.id },
        )
    }

    @Test fun ac071_observationRestoresTimestampTextAndPhotoLink() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        workspace.saveObservation(fixture.observations.first(), fixture.photoOne)

        val restored = database.watchObservations().first().single()

        assertEquals(fixture.observations.first(), restored)
        assertContentEquals(fixture.photoOne, files.read(requireNotNull(restored.photoId)))
    }

    @Test fun ac072_observationUpdateKeepsIdentityAndOtherTimelineItems() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        fixture.observations.forEach { database.saveObservation(it) }
        val updated = fixture.observations.first().copy(
            occurredAt = Instant.parse("2026-09-20T11:00:00Z"),
            text = "Корни и ростки",
        )

        database.saveObservation(updated)

        val observations = database.snapshot().observations
        assertEquals(setOf(ObservationId("observation-1"), ObservationId("observation-2")), observations.mapTo(linkedSetOf()) { it.id })
        assertEquals(updated, observations.first { it.id == updated.id })
    }

    @Test fun ac073_deleteObservationAlsoDeletesItsPrivatePhoto() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        workspace.saveObservation(fixture.observations.first(), fixture.photoOne)

        workspace.deleteObservation(fixture.observations.first().id)

        assertTrue(database.snapshot().observations.isEmpty())
        assertNull(files.read(PhotoId("photo-1")))
    }

    @Test fun ac074_importedPrivatePhotoRendersFromSameLinkAfterReadback() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)

        val managedId = managedPhotoId(fixture.observations.first().id, fixture.photoOne)
        workspace.saveObservation(
            fixture.observations.first().copy(photoId = null),
            ObservationPhotoChange.Replace(managedId, fixture.photoOne),
        )

        val photoId = requireNotNull(database.snapshot().observations.single().photoId)
        assertEquals(managedId, photoId)
        assertContentEquals(fixture.photoOne, files.read(photoId))
        assertEquals(Sha256.digest(fixture.photoOne), files.exportAll().single().sha256)
    }

    @Test fun ac074_replaceAndRemoveDeleteOnlyPreviouslyCommittedManagedBytes() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        val observation = fixture.observations.first().copy(photoId = null)
        val firstId = managedPhotoId(observation.id, fixture.photoOne)
        val secondId = managedPhotoId(observation.id, fixture.photoTwo)

        workspace.saveObservation(observation, ObservationPhotoChange.Replace(firstId, fixture.photoOne))
        workspace.saveObservation(observation, ObservationPhotoChange.Replace(secondId, fixture.photoTwo))
        assertNull(files.read(firstId))
        assertContentEquals(fixture.photoTwo, files.read(secondId))
        assertEquals(secondId, database.snapshot().observations.single().photoId)

        workspace.saveObservation(observation, ObservationPhotoChange.Remove)
        assertNull(files.read(secondId))
        assertNull(database.snapshot().observations.single().photoId)
    }

    @Test fun ac074_pendingTokensAreOpaqueAndTraversalSafe() {
        assertEquals("picker-01.image", requireSafePendingPhotoToken("picker-01.image"))
        listOf("", ".", "..", "../photo", "folder/photo", "folder\\photo", " bad").forEach { token ->
            assertFails { requireSafePendingPhotoToken(token) }
        }
        val first = managedPhotoId(ObservationId("observation-1"), byteArrayOf(1, 2, 3))
        val repeat = managedPhotoId(ObservationId("observation-1"), byteArrayOf(1, 2, 3))
        val other = managedPhotoId(ObservationId("observation-2"), byteArrayOf(1, 2, 3))
        assertEquals(first, repeat)
        assertTrue(first != other)
        assertTrue(first.value.matches(Regex("photo-[0-9a-f]{64}")))
    }

    @Test fun ac074_failedCommitCompensatesReplacementBytesAndRestoresPriorLink() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.activeBatch)
        val original = fixture.observations.first().copy(photoId = null)
        val firstId = managedPhotoId(original.id, fixture.photoOne)
        workspace.saveObservation(original, ObservationPhotoChange.Replace(firstId, fixture.photoOne))
        val secondId = managedPhotoId(original.id, fixture.photoTwo)

        assertFails {
            workspace.saveObservation(
                original.copy(batchId = BatchId("missing-batch")),
                ObservationPhotoChange.Replace(secondId, fixture.photoTwo),
            )
        }

        val restored = database.snapshot().observations.single()
        assertEquals(fixture.activeBatch.id, restored.batchId)
        assertEquals(firstId, restored.photoId)
        assertContentEquals(fixture.photoOne, files.read(firstId))
        assertNull(files.read(secondId))
    }

    @Test fun ac082_abandonedOutcomePersistsDistinctlyAndCancelsFutureActions() = runTest {
        val abandoned = fixture().activeBatch.copy(
            state = BatchState.ABANDONED,
            placement = Placement.None,
            actions = fixture().activeBatch.actions.map { it.copy(status = ScheduledActionStatus.CANCELLED) },
            outcome = BatchOutcome.Abandoned(Instant.parse("2026-09-22T12:00:00Z"), reason = "Travel"),
        )

        database.saveBatch(abandoned)

        val restored = database.snapshot().batches.single()
        assertEquals(BatchState.ABANDONED, restored.state)
        assertEquals("Travel", (restored.outcome as BatchOutcome.Abandoned).reason)
        assertTrue(restored.actions.all { it.status == ScheduledActionStatus.CANCELLED })
    }

    @Test fun ac083_repeatCreatesIndependentPlannedBatchAndPreservesOriginal() = runTest {
        val fixture = fixture()
        val repeated = fixture.terminalBatch.copy(
            id = BatchId("batch-repeat"), state = BatchState.PLANNED, placement = Placement.None,
            events = emptyList(), actions = emptyList(), outcome = null,
        )
        database.saveBatch(fixture.terminalBatch)

        database.saveBatch(repeated)

        val batches = database.snapshot().batches
        assertEquals(setOf(fixture.terminalBatch.id, repeated.id), batches.mapTo(linkedSetOf()) { it.id })
        assertEquals(fixture.terminalBatch, batches.first { it.id == fixture.terminalBatch.id })
        assertEquals(fixture.terminalBatch.recipe, batches.first { it.id == repeated.id }.recipe)
    }

    @Test fun ac084_terminalHistoryRestoresOutcomeSnapshotsObservationsAndMass() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.terminalBatch)
        fixture.observations.forEach { database.saveObservation(it.copy(batchId = fixture.terminalBatch.id, photoId = null)) }

        val snapshot = database.snapshot()

        assertEquals(fixture.terminalBatch.recipe, snapshot.batches.single().recipe)
        assertEquals(fixture.terminalBatch.tray, snapshot.batches.single().tray)
        assertEquals(145.5, (snapshot.batches.single().outcome as BatchOutcome.Harvested).actualMass?.value)
        assertEquals(2, snapshot.observations.size)
    }

    @Test fun ac090_fullBackupContainsEveryRecordPreferenceAndReferencedPhotoWithoutMutatingLiveState() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = database.snapshot()

        val archive = backup.createArchive()

        assertEquals(before.zones, database.snapshot().zones)
        assertEquals(before.batches, database.snapshot().batches)
        assertEquals(fixture.preferences, archive.preferences)
        assertEquals(setOf(PhotoId("photo-1"), PhotoId("photo-2")), archive.photos.mapTo(linkedSetOf()) { it.id })
        assertEquals(archive.photos.associate { it.id to it.sha256 }, archive.snapshot.manifest.photoIntegrity)
    }

    @Test fun ac091_restorePreviewExposesVersionCountsPhotosAndReplacementPayload() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val archive = backup.createArchive()

        val preview = assertIs<BackupPreview.Valid>(backup.preview(archive)).archive

        assertEquals(SnapshotValidation.ARCHIVE_VERSION, preview.snapshot.manifest.archiveVersion)
        assertEquals(1, preview.snapshot.manifest.recordCounts["zones"])
        assertEquals(2, preview.photos.size)
        assertEquals(fixture.preferences, preview.preferences)
    }

    @Test fun ac092_replacementRestoreIsExactAndRepeatRestoreNeverDuplicatesRowsOrFiles() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val archive = backup.createArchive()
        database.erase()
        files.clear()
        settings.clearProductState()

        backup.replaceArchive(archive)
        backup.replaceArchive(archive)
        val restored = backup.createArchive()

        assertEquals(archive, restored.copy(createdAt = archive.createdAt))
        assertEquals(archive.snapshot.manifest.recordCounts, restored.snapshot.manifest.recordCounts)
        assertEquals(archive.photos.map { it.id }, restored.photos.map { it.id })
    }

    @Test fun ac093_invalidArchiveRollbackPreservesDatabasePhotosAndPreferences() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        val corrupt = before.copy(
            photos = before.photos.mapIndexed { index, photo ->
                if (index == 0) photo.copy(bytes = byteArrayOf(99)) else photo
            },
        )

        assertIs<BackupPreview.Invalid>(backup.preview(corrupt))
        assertFailsWith<IllegalArgumentException> { backup.replaceArchive(corrupt) }

        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
    }

    @Test fun ac094_csvIsUtf8EscapedIncludesActualMassAndExcludesPhotoBytes() = runTest {
        val fixture = fixture()
        database.saveBatch(fixture.terminalBatch)

        val bytes = BatchHistoryCsv.encode(database.snapshot())
        val csv = bytes.decodeToString()

        assertContentEquals(csv.encodeToByteArray(), bytes)
        assertTrue(csv.startsWith(BatchHistoryCsv.HEADER))
        assertTrue(csv.contains("145.5"))
        assertTrue(csv.contains("Хороший, свежий"))
        assertFalse(csv.contains(fixture.photoOne.decodeToString()))
    }

    @Test fun ac095_eraseRemovesDatabasePrivatePhotosAndProductSettings() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)

        backup.eraseAll()

        val snapshot = database.snapshot()
        assertTrue(snapshot.zones.isEmpty() && snapshot.trayPresets.isEmpty() && snapshot.recipes.isEmpty())
        assertTrue(snapshot.batches.isEmpty() && snapshot.plans.isEmpty() && snapshot.observations.isEmpty())
        assertTrue(files.exportAll().isEmpty())
        assertEquals(ProductPreferences(), settings.snapshot())
    }

    @Test fun ac103_canonicalAdvertisingPrivacyPayloadBacksUpRestoresAndEraseClearsIt() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val codec = LocalBackupArchiveCodec()
        val archive = backup.createArchive().let { codec.decode(codec.encode(it)) }
        val archivedPrivacy = requireNotNull(archive.preferences.privacyState)
        settings.setPrivacyState(archivedPrivacy.copy(policyVersion = "policy-replaced", choice = ConsentChoice.WITHDRAWN))

        backup.replaceArchive(archive)

        assertEquals(archivedPrivacy, settings.privacyState.value)
        assertEquals(ADVERTISING_PRIVACY_ENDPOINT, settings.privacyState.value?.endpoint)
        assertEquals(ConsentChoice.DECLINED, settings.privacyState.value?.choice)
        backup.eraseAll()
        assertNull(settings.privacyState.value)
    }

    @Test fun qg012_completeArchiveRoundTripsEveryRelationshipStageScheduleUtf8AndMultiplePhotos() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val archive = backup.createArchive()

        assertIs<BackupPreview.Valid>(backup.preview(archive))
        database.saveZone(GrowZone(GrowZoneId("old-zone"), "Old", GrowZoneType.DARK, 0))
        backup.replaceArchive(archive)
        val restored = backup.createArchive()

        assertEquals(archive, restored.copy(createdAt = archive.createdAt))
        assertEquals(fixture.activeBatch.events, restored.snapshot.batches.first { it.id == fixture.activeBatch.id }.events)
        assertEquals(fixture.activeBatch.actions, restored.snapshot.batches.first { it.id == fixture.activeBatch.id }.actions)
        assertEquals(listOf("Рост хороший", "Корни видны"), restored.snapshot.observations.map { it.text })
        assertEquals(2, restored.photos.size)
        assertFalse(restored.snapshot.zones.any { it.id.value == "old-zone" })
    }

    @Test fun qg012_missingCorruptDuplicateAndUnsupportedArchivesAreRejectedBeforeMutation() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val valid = backup.createArchive()
        val before = backup.createArchive()
        val unsupported = valid.copy(snapshot = valid.snapshot.copy(
            manifest = valid.snapshot.manifest.copy(archiveVersion = 99),
        ))
        val missing = valid.copy(photos = valid.photos.dropLast(1))
        val duplicate = valid.copy(photos = valid.photos + valid.photos.first())
        val corrupt = valid.copy(photos = valid.photos.mapIndexed { i, photo -> if (i == 0) photo.copy(bytes = byteArrayOf(0)) else photo })

        listOf(unsupported, missing, duplicate, corrupt).forEach { invalid ->
            assertIs<BackupPreview.Invalid>(backup.preview(invalid))
            assertFailsWith<IllegalArgumentException> { backup.replaceArchive(invalid) }
            assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        }
    }

    @Test fun qg012_cancellingBackupCreationPropagatesAndLeavesLiveStateUntouched() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        val exportStarted = CompletableDeferred<Unit>()
        val service = LocalBackupService(
            database = database,
            files = BlockingExportFileStore(files, exportStarted),
            settings = settings,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val operation = async { service.createArchive() }
        exportStarted.await()

        operation.cancel()

        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
    }

    @Test fun qg012_cancellingArchiveReadAndValidationPropagatesWithoutMutation() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        val readStarted = CompletableDeferred<Unit>()
        val service = LocalBackupService(
            database = database,
            files = files,
            settings = settings,
            ioDispatcher = Dispatchers.Unconfined,
            documents = BlockingArchiveDocuments(readStarted = readStarted),
        )
        val operation = async { service.readPreview("picked-archive") }
        readStarted.await()

        operation.cancel()

        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
    }

    @Test fun qg012_cancellingReplacementCompensatesDatabaseFilesSettingsAndReminders() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val target = backup.createArchive()
        database.saveZone(GrowZone(GrowZoneId("current-zone"), "Current", GrowZoneType.DARK, 0))
        settings.setTheme(ThemePreference.LIGHT)
        files.write(PhotoId("current-photo"), byteArrayOf(7, 8, 9))
        val before = backup.createArchive()
        val reminderStarted = CompletableDeferred<Unit>()
        val reminders = BlockingReminderReconciliation(reminderStarted)
        val service = LocalBackupService(
            database = database,
            files = files,
            settings = settings,
            ioDispatcher = Dispatchers.Unconfined,
            reminders = reminders,
        )
        val operation = async { service.replaceArchive(target) }
        reminderStarted.await()

        operation.cancel()

        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        assertEquals(before.snapshot, reminders.compensatedWith)
    }

    @Test fun qg012_cancellingCsvWritePropagatesAndLeavesLiveStateUntouched() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val before = backup.createArchive()
        val writeStarted = CompletableDeferred<Unit>()
        val documents = BlockingArchiveDocuments(writeStarted = writeStarted)
        val service = LocalBackupService(
            database = database,
            files = files,
            settings = settings,
            ioDispatcher = Dispatchers.Unconfined,
            documents = documents,
        )
        val operation = async { service.writeCsv("picked-csv") }
        writeStarted.await()

        operation.cancel()

        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        assertFalse(documents.writeCompleted)
    }

    @Test fun ac090_ac091_archiveBytesAreDeterministicVersionedPortableAndDocumentBacked() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val documents = MemoryArchiveDocuments()
        val instant = Instant.parse("2026-09-20T12:34:56Z")
        val service = LocalBackupService(
            database, files, settings, Dispatchers.Unconfined,
            documents = documents, now = { instant },
        )

        val first = service.createArchiveBytes()
        val second = service.createArchiveBytes()
        service.writeArchive("picked-document")
        val preview = service.readPreview("picked-document")

        assertContentEquals(first, second)
        assertContentEquals(first, documents.values.getValue("picked-document"))
        assertEquals(instant, assertIs<BackupPreview.Valid>(preview).archive.createdAt)
        assertTrue(first.decodeToString().startsWith("SPROUTPACE-LOCAL-ARCHIVE\t1\t"))
        assertFalse(first.decodeToString().contains("/data/"))
        assertFalse(first.decodeToString().contains("C:\\"))
    }

    @Test fun ac092_ac093_restoreCompensatesEveryStoreWhenReminderReconciliationFails() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val target = backup.createArchive()
        database.saveZone(GrowZone(GrowZoneId("current-zone"), "Current", GrowZoneType.DARK, 0))
        settings.setTheme(ThemePreference.LIGHT)
        files.write(PhotoId("current-photo"), byteArrayOf(7, 8, 9))
        val before = backup.createArchive()
        val reminders = RecordingReminders(failNext = true)
        val service = LocalBackupService(
            database, files, settings, Dispatchers.Unconfined, reminders = reminders,
        )

        val failure = assertFailsWith<LocalDataException> { service.replaceArchive(target) }

        assertEquals(LocalDataErrorKey.RESTORE_FAILED, failure.key)
        assertEquals(before, backup.createArchive().copy(createdAt = before.createdAt))
        assertEquals(before.snapshot, reminders.snapshots.last())
    }

    @Test fun ac095_eraseCancelsRemindersAndEmitsTypedRootOutputOnlyAfterCommit() = runTest {
        val fixture = fixture()
        seedCompleteFixture(fixture)
        val reminders = RecordingReminders()
        val outputs = mutableListOf<LocalDataIntegrationEvent>()
        val service = LocalBackupService(
            database, files, settings, Dispatchers.Unconfined,
            reminders = reminders, output = LocalDataIntegrationOutput { event -> outputs.add(event); Unit },
        )

        service.eraseAll()

        assertTrue(reminders.snapshots.single().batches.isEmpty())
        assertEquals(
            listOf<LocalDataIntegrationEvent>(LocalDataIntegrationEvent.FirstRunNavigationRequired),
            outputs,
        )
    }

    private suspend fun seedCompleteFixture(fixture: Fixture) {
        database.saveZone(fixture.zone)
        database.saveTrayPreset(fixture.tray)
        database.saveRecipe(fixture.recipe)
        database.saveBatch(fixture.activeBatch)
        database.saveBatch(fixture.terminalBatch)
        database.savePlan(fixture.plan)
        workspace.saveObservation(fixture.observations[0], fixture.photoOne)
        workspace.saveObservation(fixture.observations[1], fixture.photoTwo)
        settings.replace(fixture.preferences)
    }

    private fun fixture(): Fixture {
        val culture = SproutPaceCatalog.cultures.first()
        val zone = GrowZone(GrowZoneId("zone-1"), "Grow shelf", GrowZoneType.GROW_LIGHT, 2)
        val tray = TraySizePreset(TraySizePresetId("tray-1"), "10 × 20", TrayShape.Rectangle(25.4, 50.8))
        val recipe = UserGrowRecipe(
            id = RecipeId("recipe-1"), name = "Редис mix", cultureId = culture.id,
            stages = culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) },
            density = culture.seedDensity, substrate = "coco coir", supplier = "Seed Co",
            cultivar = "Ruby", seedLot = "LOT-42", germinationPercentage = Percentage(91),
        )
        val assigned = Placement.Assigned(zone.id, zone.name)
        val event = StageEvent(
            id = StageEventId("event-1"), from = BatchState.SOWN, to = BatchState.LIGHT_GROWTH,
            occurredAt = Instant.parse("2026-09-20T09:00:00Z"), kind = StageEventKind.CONFIRMED,
            placementBefore = assigned, placementAfter = assigned,
        )
        val actions = listOf(
            ScheduledAction(
                ScheduledActionId("action-complete"), BatchId("batch-active"), GrowStage.SOWN,
                DueMoment.ElapsedTarget(Instant.parse("2026-09-20T09:00:00Z")), ScheduledActionStatus.COMPLETED,
            ),
            ScheduledAction(
                ScheduledActionId("action-future"), BatchId("batch-active"), GrowStage.READY_FOR_HARVEST,
                DueMoment.CivilTime(LocalDateTime(2026, 9, 24, 9, 0), "Europe/Moscow"),
            ),
        )
        val recipeSnapshot = RecipeSnapshot(recipe.id, recipe.cultureId, recipe.name, recipe.stages, recipe.density)
        val traySnapshot = TraySnapshot(tray.name, tray.shape)
        val activeBatch = GrowBatch(
            id = BatchId("batch-active"), recipe = recipeSnapshot, tray = traySnapshot,
            substrateSnapshot = recipe.substrate, recommendedSeedMass = PositiveGrams(10.0),
            actualSeedMass = PositiveGrams(11.0), state = BatchState.LIGHT_GROWTH,
            placement = assigned, events = listOf(event), actions = actions,
            supplier = recipe.supplier, cultivar = recipe.cultivar, seedLot = recipe.seedLot,
            germinationPercentage = recipe.germinationPercentage,
        )
        val terminalBatch = activeBatch.copy(
            id = BatchId("batch-terminal"), state = BatchState.HARVESTED, placement = Placement.None,
            actions = actions.map { it.copy(
                id = ScheduledActionId("terminal:${it.id.value}"),
                batchId = BatchId("batch-terminal"), status = ScheduledActionStatus.CANCELLED,
            ) },
            outcome = BatchOutcome.Harvested(
                Instant.parse("2026-09-25T12:00:00Z"), PositiveGrams(145.5),
                OutcomeAssessment.SUCCESSFUL, note = "Хороший, свежий",
            ),
        )
        val observations = listOf(
            Observation(
                ObservationId("observation-1"), activeBatch.id, Instant.parse("2026-09-20T10:00:00Z"),
                "Рост хороший", PhotoId("photo-1"),
            ),
            Observation(
                ObservationId("observation-2"), activeBatch.id, Instant.parse("2026-09-21T10:00:00Z"),
                "Корни видны", PhotoId("photo-2"),
            ),
        )
        return Fixture(
            zone, tray, recipe, activeBatch, terminalBatch,
            HarvestPlan(
                HarvestPlanId("plan-1"), kotlinx.datetime.LocalDate(2026, 9, 20), HarvestCadenceDays(3),
                listOf(CultureId("radish"), CultureId("pea")), HarvestPlanMode.CONTINUOUS_QUEUE,
            ),
            observations, "photo one bytes".encodeToByteArray(), "photo two bytes".encodeToByteArray(),
            ProductPreferences(
                ThemePreference.DARK, crashReportingEnabled = false, notificationsEnabled = false,
                privacyState = AdvertisingPrivacyState(
                    schemaVersion = ADVERTISING_PRIVACY_SCHEMA_VERSION,
                    policyVersion = "policy-1",
                    expiresAtEpochMillis = 1_800_000_000_000,
                    choice = ConsentChoice.DECLINED,
                    payloadVersion = ADVERTISING_PRIVACY_PAYLOAD_VERSION,
                    endpoint = ADVERTISING_PRIVACY_ENDPOINT,
                    consentRequired = true,
                    fetchedAtEpochMillis = 1_799_999_900_000,
                ),
            ),
        )
    }

    private data class Fixture(
        val zone: GrowZone,
        val tray: TraySizePreset,
        val recipe: UserGrowRecipe,
        val activeBatch: GrowBatch,
        val terminalBatch: GrowBatch,
        val plan: HarvestPlan,
        val observations: List<Observation>,
        val photoOne: ByteArray,
        val photoTwo: ByteArray,
        val preferences: ProductPreferences,
    )

    private class MemoryArchiveDocuments : ArchiveDocumentPort {
        val values = mutableMapOf<String, ByteArray>()
        override suspend fun read(documentId: String): ByteArray? = values[documentId]?.copyOf()
        override suspend fun write(documentId: String, bytes: ByteArray) { values[documentId] = bytes.copyOf() }
    }

    private class BlockingArchiveDocuments(
        private val readStarted: CompletableDeferred<Unit>? = null,
        private val writeStarted: CompletableDeferred<Unit>? = null,
    ) : ArchiveDocumentPort {
        var writeCompleted: Boolean = false
            private set

        override suspend fun read(documentId: String): ByteArray? {
            requireNotNull(readStarted).complete(Unit)
            awaitCancellation()
        }

        override suspend fun write(documentId: String, bytes: ByteArray) {
            requireNotNull(writeStarted).complete(Unit)
            awaitCancellation()
        }
    }

    private class BlockingExportFileStore(
        private val delegate: PrivateFileStore,
        private val exportStarted: CompletableDeferred<Unit>,
    ) : PrivateFileStore by delegate {
        override suspend fun exportAll(): List<PrivatePhoto> {
            exportStarted.complete(Unit)
            awaitCancellation()
        }
    }

    private class BlockingReminderReconciliation(
        private val firstCallStarted: CompletableDeferred<Unit>,
    ) : ReminderReconciliationPort {
        private var shouldBlock = true
        var compensatedWith: ProductSnapshot? = null
            private set

        override suspend fun replaceFutureReminders(snapshot: ProductSnapshot) {
            if (shouldBlock) {
                shouldBlock = false
                firstCallStarted.complete(Unit)
                awaitCancellation()
            }
            compensatedWith = snapshot
        }
    }

    private class RecordingReminders(var failNext: Boolean = false) : ReminderReconciliationPort {
        val snapshots = mutableListOf<ProductSnapshot>()
        override suspend fun replaceFutureReminders(snapshot: ProductSnapshot) {
            if (failNext) {
                failNext = false
                throw IllegalStateException("injected reminder failure")
            }
            snapshots += snapshot
        }
    }
}
