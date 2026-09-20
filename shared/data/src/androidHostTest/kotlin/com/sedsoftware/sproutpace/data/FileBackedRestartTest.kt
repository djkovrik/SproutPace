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

class FileBackedRestartTest {
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

private class TestFilePrivateStore(private val root: File) : PrivateFileStore {
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
