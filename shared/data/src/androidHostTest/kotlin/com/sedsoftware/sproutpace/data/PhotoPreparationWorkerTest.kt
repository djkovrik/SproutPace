package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhotoPreparationWorkerTest {
    @Test fun publicManagerHashesOnWorkerAndPreservesIndependentStableIdentity() = runTest {
        withPhotoFixture { fixture ->
            withContext(fixture.caller) { fixture.save() }
            fixture.assertSaved()
            assertSame(fixture.callerOwner.thread, fixture.pending.readThreads.single(), "Pending read caller ownership")
            fixture.assertHashWorker("public manager")
        }
    }

    @Test fun cancellationDuringOrAfterRealHashLeavesRowsFilesAndPendingRetryable() = runTest {
        CancellationPoint.entries.forEach { point ->
            withPhotoFixture { fixture ->
                val before = fixture.database.snapshot()
                val photos = fixture.files.exportAll()
                val operation = async(fixture.caller) {
                    val job = requireNotNull(currentCoroutineContext()[Job])
                    fixture.hash.cancel = { job.cancel(CancellationException("photo-preparation-$point")) }
                    fixture.hash.cancellationPoint = point
                    fixture.save()
                }
                assertFailsWith<CancellationException> { operation.await() }
                fixture.assertHashWorker(point.name)
                assertEquals(before, fixture.database.snapshot(), point.name)
                assertEquals(photos, fixture.files.exportAll(), point.name)
                assertEquals(0, fixture.files.writes.get(), point.name)
                assertEquals(0, fixture.files.deletes.get(), point.name)
                assertContentEquals(fixture.bytes, fixture.pending.value, point.name)

                fixture.hash.cancel = null
                withContext(fixture.caller) { fixture.save() }
                fixture.assertSaved()
            }
        }
    }

    @Test fun preparationOwnsBytesAndLegacyOverloadPreservesSuppliedIdentity() = runTest {
        withPhotoFixture { fixture ->
            val original = fixture.bytes.copyOf()
            val replacement = withContext(fixture.caller) {
                fixture.repository.prepareManagedPhotoReplacement(fixture.observation.id, fixture.bytes)
            }
            assertNotSame(fixture.bytes, replacement.bytes)
            fixture.bytes.fill(0)
            assertContentEquals(original, replacement.bytes)
            assertEquals(independentPhotoId(fixture.observation.id, original), replacement.id)

            val legacyId = PhotoId("legacy-explicit-id")
            val legacy = LocalWorkspaceRepository(fixture.database, fixture.files, fixture.worker)
            withContext(fixture.caller) {
                legacy.saveObservation(fixture.observation.copy(photoId = legacyId), original)
            }
            original.fill(0)
            assertEquals(legacyId, fixture.database.snapshot().observations.first { it.id == fixture.observation.id }.photoId)
            assertContentEquals(replacement.bytes, fixture.files.read(legacyId))
            assertNull(fixture.files.read(requireNotNull(fixture.observation.photoId)))
            assertContentEquals(UNRELATED_BYTES, fixture.files.read(UNRELATED_PHOTO))
        }
    }

    private suspend fun withPhotoFixture(block: suspend (PhotoFixture) -> Unit) {
        val directory = Files.createTempDirectory("photo-preparation-").toFile()
        try {
            val workerOwner = ThreadOwner(WORKER_THREAD)
            val callerOwner = ThreadOwner(CALLER_THREAD)
            Executors.newSingleThreadExecutor(workerOwner).asCoroutineDispatcher().use { worker ->
                Executors.newSingleThreadExecutor(callerOwner).asCoroutineDispatcher().use { caller ->
                    val fixture = PhotoFixture(directory.toPath(), worker, caller, workerOwner, callerOwner)
                    try { fixture.seed(); block(fixture) } finally { fixture.database.close(); fixture.files.close() }
                }
            }
        } finally { directory.deleteRecursively() }
    }

    private class PhotoFixture(
        directory: Path,
        val worker: CoroutineDispatcher,
        val caller: CoroutineDispatcher,
        private val workerOwner: ThreadOwner,
        val callerOwner: ThreadOwner,
    ) {
        val database = LocalDatabase(TestSqlDriverFactory("jdbc:sqlite:${directory.resolve("workspace.db")}"), worker)
        val files = CountingFiles(AndroidPrivateFileStore(directory.resolve("photos").toFile()))
        val hash = HashProbe()
        val repository = LocalWorkspaceRepository(database, files, worker, hash::id)
        val bytes = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val pending = PendingBytes(bytes)
        private val manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            workspaceRepository = repository, pendingPhotos = pending)
        private val culture = SproutPaceCatalog.cultures.first()
        private val batch = GrowBatch(
            BatchId("batch-a"), RecipeSnapshot(null, culture.id, "Recipe",
                culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
            TraySnapshot("Tray", TrayShape.Rectangle(20.0, 30.0)), "Coco", PositiveGrams(1.0), PositiveGrams(1.0),
            BatchState.PLANNED, Placement.None, emptyList(), emptyList(),
        )
        val observation = Observation(ObservationId("observation-ростки"), batch.id,
            Instant.parse("2026-10-17T12:00:00Z"), "Original", PhotoId("original-photo"))
        private val command = ScreenCommand("save-observation", observation.id.value, mapOf(
            "batchId" to batch.id.value, "occurredAt" to observation.occurredAt.toString(),
            "text" to "Edited", "photoId" to pendingPhotoDraftValue(TOKEN),
        ))

        suspend fun seed() {
            database.saveBatch(batch)
            database.saveObservation(observation)
            database.saveObservation(observation.copy(id = ObservationId("unrelated-observation"), photoId = UNRELATED_PHOTO))
            files.write(requireNotNull(observation.photoId), byteArrayOf(1, 2))
            files.write(UNRELATED_PHOTO, UNRELATED_BYTES)
            files.writes.set(0)
        }

        suspend fun save() { manager.execute(ScreenId.OBSERVATION_EDITOR, command).getOrThrow() }

        fun assertHashWorker(context: String) {
            assertTrue(hash.shaThreads.isNotEmpty(), "$context: no real SHA checkpoints observed")
            hash.shaThreads.forEach { actual ->
                assertSame(workerOwner.thread, actual, "$context: expected worker=${workerOwner.thread}, actual=$actual")
                assertNotSame(callerOwner.thread, actual, "$context: SHA executed on caller=$actual")
            }
        }

        suspend fun assertSaved() {
            val saved = database.snapshot().observations.first { it.id == observation.id }
            assertEquals(independentPhotoId(observation.id, bytes), saved.photoId)
            assertEquals("Edited", saved.text)
            assertContentEquals(bytes, files.read(requireNotNull(saved.photoId)))
            assertNull(files.read(requireNotNull(observation.photoId)))
            assertContentEquals(UNRELATED_BYTES, files.read(UNRELATED_PHOTO))
            assertNull(pending.value)
        }
    }

    private enum class CancellationPoint { DURING_HASH, AFTER_HASH }

    private class HashProbe {
        val shaThreads = ConcurrentLinkedQueue<Thread>()
        var cancellationPoint: CancellationPoint? = null
        var cancel: (() -> Unit)? = null
        private var shaCheckpoints = 0

        fun id(observationId: ObservationId, bytes: ByteArray, checkActive: () -> Unit): PhotoId {
            val id = managedPhotoId(observationId, bytes) {
                if (Thread.currentThread().stackTrace.any {
                    it.className == "com.sedsoftware.sproutpace.data.Sha256" && it.methodName == "digest"
                }) {
                    shaThreads.add(Thread.currentThread())
                    shaCheckpoints++
                    if (cancellationPoint == CancellationPoint.DURING_HASH && shaCheckpoints == 8) cancel?.invoke()
                }
                checkActive()
            }
            if (cancellationPoint == CancellationPoint.AFTER_HASH) cancel?.invoke()
            return id
        }
    }

    private class PendingBytes(var value: ByteArray?) : PendingPhotoImportStore {
        val readThreads = ConcurrentLinkedQueue<Thread>()
        override suspend fun read(token: String): ByteArray? {
            require(token == TOKEN)
            readThreads.add(Thread.currentThread())
            return value
        }
        override suspend fun delete(token: String) { require(token == TOKEN); value = null }
        override suspend fun clear() { value = null }
        override fun close() { value = null }
    }

    private class CountingFiles(private val delegate: PrivateFileStore) : PrivateFileStore by delegate {
        val writes = AtomicInteger()
        val deletes = AtomicInteger()
        override suspend fun write(id: PhotoId, bytes: ByteArray) { writes.incrementAndGet(); delegate.write(id, bytes) }
        override suspend fun delete(id: PhotoId) { deletes.incrementAndGet(); delegate.delete(id) }
    }

    private class ThreadOwner(private val name: String) : ThreadFactory {
        private val created = AtomicReference<Thread>()
        val thread: Thread get() = requireNotNull(created.get())
        override fun newThread(runnable: Runnable): Thread = Thread(runnable, name).also { created.set(it) }
    }

    private companion object {
        const val TOKEN = "selected-photo"
        const val CALLER_THREAD = "photo-store-caller"
        const val WORKER_THREAD = "photo-preparation-worker"
        val UNRELATED_PHOTO = PhotoId("unrelated-photo")
        val UNRELATED_BYTES = byteArrayOf(7, 8, 9)

        fun independentPhotoId(observationId: ObservationId, bytes: ByteArray): PhotoId {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(observationId.value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            val hex = digest.digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            return PhotoId("photo-$hex")
        }
    }
}
