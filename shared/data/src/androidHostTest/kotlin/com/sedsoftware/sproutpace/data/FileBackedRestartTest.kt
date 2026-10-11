package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FileBackedRestartTest {
    @Test fun interruptedRestoreRecoversCompletePreviousWorkspaceAfterDatabaseReopen() = runTest {
        listOf(false, true).forEach { databaseWasReplaced ->
            val root = Files.createTempDirectory("sproutpace-recovery-").toFile()
            val url = "jdbc:sqlite:${File(root, "workspace.db").absolutePath}"
            val factory = TestSqlDriverFactory(url = url)
            val database = LocalDatabase(factory, Dispatchers.Unconfined)
            val photos = AndroidPrivateFileStore(File(root, "photos"))
            val preferencesFile = File(root, "preferences.archive")
            val settings = FileProductSettings(preferencesFile)
            val fixture = restartFixture()
            database.saveZone(fixture.zone)
            database.saveTrayPreset(fixture.tray)
            database.saveRecipe(fixture.recipe)
            database.saveBatch(fixture.batch)
            database.saveObservation(fixture.observation)
            photos.write(requireNotNull(fixture.observation.photoId), fixture.photo)
            settings.replace(ProductPreferences(theme = ThemePreference.DARK))
            val service = LocalBackupService(database, photos, settings, Dispatchers.Unconfined)
            val previous = service.createArchive()

            // Persist exactly the production rollback record, then stop at either crash window.
            photos.writeRecoveryArchive(LocalBackupArchiveCodec().encode(previous))
            photos.replaceAll(emptyList())
            if (databaseWasReplaced) {
                database.erase()
                settings.clearProductState()
            }
            database.close()
            factory.close()
            photos.close()

            val reopenedFactory = TestSqlDriverFactory(url = url, initializeSchema = false)
            val reopened = LocalDatabase(reopenedFactory, Dispatchers.Unconfined)
            val reopenedPhotos = AndroidPrivateFileStore(File(root, "photos"))
            val reopenedSettings = FileProductSettings(preferencesFile)
            val failedRecovery = LocalBackupService(reopened, reopenedPhotos, reopenedSettings, Dispatchers.Unconfined,
                reminders = ReminderReconciliationPort { error("Physical reminders unavailable") })
            assertFailsWith<LocalDataException> { failedRecovery.recoverPendingReplacement() }
            assertNotNull(reopenedPhotos.readRecoveryArchive())
            val reminderSnapshots = mutableListOf<ProductSnapshot>()
            val recovery = LocalBackupService(reopened, reopenedPhotos, reopenedSettings, Dispatchers.Unconfined,
                reminders = ReminderReconciliationPort { reminderSnapshots += it })
            recovery.recoverPendingReplacement()
            assertEquals(previous, recovery.createArchive().copy(createdAt = previous.createdAt))
            assertEquals(listOf(previous.snapshot), reminderSnapshots)
            assertNull(reopenedPhotos.readRecoveryArchive())
            recovery.recoverPendingReplacement()
            assertEquals(1, reminderSnapshots.size)
            reopened.close()
            reopenedFactory.close()
            reopenedPhotos.close()
            root.deleteRecursively()
        }
    }

    @Test fun nativePhotoDirectoryRecoversInterruptedRenameBeforeJournalRecovery() = runTest {
        val root = Files.createTempDirectory("sproutpace-photo-swap-").toFile()
        val directory = File(root, "photos")
        val files = AndroidPrivateFileStore(directory)
        files.write(PhotoId("original"), byteArrayOf(1, 2))
        assertTrue(directory.renameTo(File(root, "photos.previous")))
        val reopened = AndroidPrivateFileStore(directory)
        assertContentEquals(byteArrayOf(1, 2), reopened.read(PhotoId("original")))
        root.deleteRecursively()
    }

    @Test fun assignedHistoryAndPrivatePhotosSurviveActualCloseAndReopen() = runTest {
        val root = Files.createTempDirectory("sproutpace-reopen-").toFile()
        val databaseFile = File(root, "sproutpace.db")
        val photoRoot = File(root, "photos")
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"
        val initialFactory = TestSqlDriverFactory(url = url)
        val initialDatabase = LocalDatabase(initialFactory, Dispatchers.Unconfined)
        val initialFiles = TestFilePrivateStore(photoRoot)
        val fixture = restartFixture()

        initialDatabase.saveZone(fixture.zone)
        initialDatabase.saveTrayPreset(fixture.tray)
        initialDatabase.saveRecipe(fixture.recipe)
        initialDatabase.saveBatch(fixture.batch)
        initialDatabase.savePlan(fixture.plan)
        val pending = InMemoryPendingPhotoImportStore().apply { stage("picked-reopen.jpg", fixture.photo) }
        val initialManager = ProductionScreenDataManager(
            database = initialDatabase,
            settings = MultiplatformProductSettings(MapSettings()),
            workspaceRepository = LocalWorkspaceRepository(initialDatabase, initialFiles, Dispatchers.Unconfined),
            pendingPhotos = pending,
        )
        initialManager.execute(
            ScreenId.OBSERVATION_EDITOR,
            ScreenCommand(
                operation = "save-observation",
                subjectId = fixture.observation.id.value,
                arguments = mapOf(
                    "batchId" to fixture.batch.id.value,
                    "occurredAt" to fixture.observation.occurredAt.toString(),
                    "text" to fixture.observation.text,
                    "photoId" to pendingPhotoDraftValue("picked-reopen.jpg"),
                ),
            ),
        ).getOrThrow()
        val savedObservation = initialDatabase.snapshot().observations.single()
        assertEquals(null, pending.read("picked-reopen.jpg"))
        initialDatabase.close()
        initialFiles.close()
        initialFactory.close()

        val reopenedFactory = TestSqlDriverFactory(url = url, initializeSchema = false)
        val reopenedDatabase = LocalDatabase(reopenedFactory, Dispatchers.Unconfined)
        val reopenedFiles = TestFilePrivateStore(photoRoot)

        val restored = reopenedDatabase.snapshot()
        assertEquals(listOf(fixture.zone), restored.zones)
        assertEquals(listOf(fixture.tray), restored.trayPresets)
        assertEquals(listOf(fixture.recipe), restored.recipes)
        assertEquals(listOf(fixture.batch), restored.batches)
        assertEquals(listOf(fixture.plan), restored.plans)
        assertEquals(listOf(savedObservation), restored.observations)
        assertContentEquals(fixture.photo, reopenedFiles.read(requireNotNull(savedObservation.photoId)))
        val reopenedManager = ProductionScreenDataManager(
            database = reopenedDatabase,
            settings = MultiplatformProductSettings(MapSettings()),
            workspaceRepository = LocalWorkspaceRepository(reopenedDatabase, reopenedFiles, Dispatchers.Unconfined),
        )
        val rendered = reopenedManager.watch(ScreenId.BATCH_DETAIL, fixture.batch.id.value).first()
            .items.single { it.id == "observation:${fixture.observation.id.value}" }
        assertEquals(savedObservation.photoId?.value, rendered.photo?.id)
        assertContentEquals(fixture.photo, rendered.photo?.bytes)

        reopenedDatabase.close()
        reopenedFiles.close()
        reopenedFactory.close()
        root.deleteRecursively()
    }

    private fun restartFixture(): RestartFixture {
        val culture = SproutPaceCatalog.cultures.first()
        val zone = GrowZone(GrowZoneId("zone-reopen"), "Полка", GrowZoneType.GROW_LIGHT, 2)
        val tray = TraySizePreset(TraySizePresetId("tray-reopen"), "10 × 20", TrayShape.Rectangle(25.4, 50.8))
        val recipe = UserGrowRecipe(
            RecipeId("recipe-reopen"), "Редис", culture.id,
            culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) },
            culture.seedDensity, "coco",
        )
        val batchId = BatchId("batch-reopen")
        val batch = GrowBatch(
            batchId, RecipeSnapshot(recipe.id, recipe.cultureId, recipe.name, recipe.stages, recipe.density),
            TraySnapshot(tray.name, tray.shape), recipe.substrate, PositiveGrams(10.0), PositiveGrams(11.0),
            BatchState.PLANNED, Placement.None, emptyList(), emptyList(),
        )
        val plan = HarvestPlan(
            HarvestPlanId("plan-reopen"), LocalDate(2026, 9, 20), HarvestCadenceDays(4),
            listOf(culture.id), HarvestPlanMode.CADENCE,
        )
        val observation = Observation(
            ObservationId("observation-reopen"), batchId, Instant.parse("2026-09-20T12:00:00Z"),
            "Корни видны", PhotoId("photo-reopen"),
        )
        return RestartFixture(zone, tray, recipe, batch, plan, observation, "private bytes".encodeToByteArray())
    }

    private data class RestartFixture(
        val zone: GrowZone,
        val tray: TraySizePreset,
        val recipe: UserGrowRecipe,
        val batch: GrowBatch,
        val plan: HarvestPlan,
        val observation: Observation,
        val photo: ByteArray,
    )
}

private class FileProductSettings(
    private val file: File,
    private val delegate: ProductSettings = MultiplatformProductSettings(MapSettings()),
) : ProductSettings by delegate {
    init {
        if (file.exists()) delegate.replace(LocalBackupArchiveCodec().decode(file.readBytes()).preferences)
    }
    override fun replace(value: ProductPreferences) {
        delegate.replace(value)
        val empty = ProductSnapshot(BackupManifest(1, 1, emptyMap(), emptyMap()),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        file.writeBytes(LocalBackupArchiveCodec().encode(LocalBackupArchive(empty, emptyList(), value)))
    }
    override fun clearProductState() = replace(ProductPreferences())
}

private class TestFilePrivateStore(private val root: File) : PrivateFileStore {
    private val journal = File(root.parentFile, "${root.name}.recovery")
    override suspend fun readRecoveryArchive(): ByteArray? = journal.takeIf(File::isFile)?.readBytes()
    override suspend fun writeRecoveryArchive(bytes: ByteArray?) {
        if (bytes == null) check(!journal.exists() || journal.delete()) else journal.writeBytes(bytes)
    }
    init { check(root.mkdirs() || root.isDirectory) }
    override suspend fun read(id: PhotoId): ByteArray? = file(id).takeIf(File::isFile)?.readBytes()
    override suspend fun write(id: PhotoId, bytes: ByteArray) { file(id).writeBytes(bytes) }
    override suspend fun delete(id: PhotoId) { check(!file(id).exists() || file(id).delete()) }
    override suspend fun exportAll(): List<PrivatePhoto> = root.listFiles().orEmpty().sortedBy(File::getName).map {
        val bytes = it.readBytes(); PrivatePhoto(PhotoId(it.name), bytes, Sha256.digest(bytes))
    }
    override suspend fun replaceAll(photos: List<PrivatePhoto>) {
        clear()
        photos.forEach { photo -> require(Sha256.digest(photo.bytes) == photo.sha256); write(photo.id, photo.bytes) }
    }
    override suspend fun clear() { root.listFiles().orEmpty().forEach { check(it.delete()) } }
    override fun close() = Unit
    private fun file(id: PhotoId) = File(root, id.value).also { require(it.parentFile == root) }
}
