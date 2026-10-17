package com.sedsoftware.sproutpace.data

import app.cash.sqldelight.db.SqlDriver
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhotoMutationCancellationTest {
    @Test fun cancellationAfterNativePhotoRemovalRestoresExactRowsAndFiles() = runTest {
        Mutation.entries.forEach { mutation ->
            withPhotoFixture { fixture ->
                val before = fixture.database.snapshot()
                val photos = fixture.files.exportAll()
                val operation = async {
                    val job = requireNotNull(currentCoroutineContext()[Job])
                    fixture.files.afterDelete = { job.cancel(CancellationException("after-native-delete")) }
                    fixture.mutate(mutation)
                }
                assertFailsWith<CancellationException> { operation.await() }
                assertEquals(before, fixture.database.snapshot(), mutation.name)
                assertEquals(photos, fixture.files.exportAll(), mutation.name)
            }
        }
    }

    @Test fun cancellationAfterCommittedSqlRestoresExactRowsAndFiles() = runTest {
        Mutation.entries.forEach { mutation ->
            withPhotoFixture { fixture ->
                val before = fixture.database.snapshot()
                val photos = fixture.files.exportAll()
                var committedChangeObserved = false
                val operation = async {
                    val job = requireNotNull(currentCoroutineContext()[Job])
                    fixture.factory.afterCommit = {
                        // SQLDelight notifies listeners after the actual JDBC transaction commits.
                        committedChangeObserved = fixture.database.snapshotNow() != before
                        job.cancel(CancellationException("after-sql-commit"))
                    }
                    fixture.mutate(mutation)
                }
                assertFailsWith<CancellationException> { operation.await() }
                assertTrue(committedChangeObserved, mutation.name)
                assertEquals(before, fixture.database.snapshot(), mutation.name)
                assertEquals(photos, fixture.files.exportAll(), mutation.name)
            }
        }
    }

    @Test fun rollbackFailureKeepsOriginalCauseAndStillCleansReplacement() = runTest {
        withPhotoFixture { fixture ->
            val original = IllegalStateException("native-delete-failed")
            val rollbackFailure = IOException("old-photo-restore-failed")
            fixture.files.afterDelete = {
                fixture.files.rejectNextWrite = rollbackFailure
                throw original
            }
            val failure = assertFailsWith<IllegalStateException> { fixture.mutate(Mutation.REPLACE) }
            // Coroutine stack-trace recovery may copy the thrown JVM exception at dispatcher boundaries.
            val causes = generateSequence<Throwable>(failure) { it.cause }.toList()
            assertSame(original, causes.last())
            assertTrue(causes.flatMap { it.suppressedExceptions }.any { it === rollbackFailure })
            assertEquals(fixture.observation, fixture.database.snapshot().observations.first { it.id == fixture.observation.id })
            assertNull(fixture.files.read(PhotoId("replacement")))
        }
    }

    private enum class Mutation { REPLACE, REMOVE, DELETE_OBSERVATION, DELETE_BATCH }

    private suspend fun withPhotoFixture(block: suspend (PhotoFixture) -> Unit) {
        val directory = Files.createTempDirectory("photo-compensation-").toFile()
        try {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                val fixture = PhotoFixture(directory.toPath(), dispatcher)
                try { fixture.seed(); block(fixture) } finally { fixture.database.close(); fixture.files.close() }
            }
        } finally { directory.deleteRecursively() }
    }

    private class PhotoFixture(directory: java.nio.file.Path, dispatcher: CoroutineDispatcher) {
        val factory = CommitSignalFactory()
        val database = LocalDatabase(factory, dispatcher)
        val files = MutationSignalFiles(AndroidPrivateFileStore(directory.resolve("photos").toFile()))
        private val repository = LocalWorkspaceRepository(database, files, dispatcher)
        private val culture = SproutPaceCatalog.cultures.first()
        private val batch = GrowBatch(
            BatchId("batch-a"), RecipeSnapshot(null, culture.id, "Recipe",
                culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
            TraySnapshot("Tray", TrayShape.Rectangle(20.0, 30.0)), "Coco", PositiveGrams(1.0), PositiveGrams(1.0),
            BatchState.PLANNED, Placement.None, emptyList(), emptyList(),
        )
        val observation = Observation(ObservationId("observation-a"), batch.id,
            kotlinx.datetime.Instant.parse("2026-10-17T12:00:00Z"), "Original", PhotoId("original-a"))

        suspend fun seed() {
            database.saveBatch(batch)
            database.saveBatch(batch.copy(id = BatchId("batch-z")))
            repository.saveObservation(observation, ObservationPhotoChange.Replace(requireNotNull(observation.photoId), byteArrayOf(1)))
            repository.saveObservation(observation.copy(id = ObservationId("observation-b"), photoId = PhotoId("original-b")),
                ObservationPhotoChange.Replace(PhotoId("original-b"), byteArrayOf(2)))
        }

        suspend fun mutate(mutation: Mutation) {
            when (mutation) {
                Mutation.REPLACE -> repository.saveObservation(observation.copy(text = "Edited"),
                    ObservationPhotoChange.Replace(PhotoId("replacement"), byteArrayOf(3)))
                Mutation.REMOVE -> repository.saveObservation(observation.copy(text = "Edited"), ObservationPhotoChange.Remove)
                Mutation.DELETE_OBSERVATION -> repository.deleteObservation(observation.id)
                Mutation.DELETE_BATCH -> repository.deleteBatch(batch.id)
            }
        }
    }

    private class MutationSignalFiles(private val delegate: PrivateFileStore) : PrivateFileStore by delegate {
        var afterDelete: (() -> Unit)? = null
        var rejectNextWrite: Throwable? = null
        override suspend fun delete(id: PhotoId) {
            delegate.delete(id)
            val signal = afterDelete
            afterDelete = null
            signal?.invoke()
            currentCoroutineContext().ensureActive()
        }
        override suspend fun write(id: PhotoId, bytes: ByteArray) {
            rejectNextWrite?.let { failure -> rejectNextWrite = null; throw failure }
            delegate.write(id, bytes)
        }
    }

    private class CommitSignalFactory : SqlDriverFactory {
        private val delegate = TestSqlDriverFactory()
        var afterCommit: (() -> Unit)? = null
        private val driver = object : SqlDriver by delegate.create() {
            override fun notifyListeners(vararg queryKeys: String) {
                delegate.create().notifyListeners(*queryKeys)
                val signal = afterCommit
                afterCommit = null
                signal?.invoke()
            }
        }
        override fun create(): SqlDriver = driver
        override fun close() = delegate.close()
    }
}
