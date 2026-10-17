package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.model.PhotoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AndroidPrivateFileStoreTest {
    @Test fun rejectsUnsafeIdsAcrossAllOperationsWithoutChangingFiles() = runTest {
        val directory = Files.createTempDirectory("photo-containment-").toFile()
        try {
            val root = File(directory, "photos")
            val files = AndroidPrivateFileStore(root)
            val original = photo("valid-photo.jpg", byteArrayOf(1, 2))
            files.write(original.id, original.bytes)
            val stage = File(directory, "photos.stage").apply { mkdir() }
            File(stage, "keep").writeText("untouched")
            val unsafe = listOf("../outside", "..\\outside", "/absolute", "C:\\absolute", ".", "..", "bad\u0000id")
            unsafe.forEach { value ->
                val id = PhotoId(value)
                assertFailsWith<IllegalArgumentException> { files.read(id) }
                assertFailsWith<IllegalArgumentException> { files.write(id, byteArrayOf(9)) }
                assertFailsWith<IllegalArgumentException> { files.delete(id) }
                assertFailsWith<IllegalArgumentException> {
                    files.replaceAll(listOf(photo("safe-first", byteArrayOf(7)), photo(value, byteArrayOf(9))))
                }
                assertEquals("untouched", File(stage, "keep").readText())
                assertEquals(listOf(original), files.exportAll())
                assertEquals(listOf("valid-photo.jpg"), root.list()!!.toList())
            }
            assertEquals(setOf("photos", "photos.stage"), directory.list()!!.toSet())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun invalidHashesAndDuplicateIdsAreRejectedBeforeStageMutation() = runTest {
        val directory = Files.createTempDirectory("photo-validation-").toFile()
        try {
            val files = AndroidPrivateFileStore(File(directory, "photos"))
            val original = photo("original", byteArrayOf(1))
            files.write(original.id, original.bytes)
            val stage = File(directory, "photos.stage").apply { mkdir() }
            val marker = File(stage, "keep").apply { writeText("untouched") }
            val valid = photo("next", byteArrayOf(2))
            val invalidHash = photo("bad-hash", byteArrayOf(3)).copy(sha256 = "invalid")
            listOf(listOf(valid, invalidHash), listOf(valid, valid)).forEach { set ->
                assertFailsWith<IllegalArgumentException> { files.replaceAll(set) }
                assertEquals("untouched", marker.readText())
                assertEquals(listOf(original), files.exportAll())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun canceledReplacementPreservesLivePhotosAndLeavesNoStage() = runTest {
        val directory = Files.createTempDirectory("photo-cancel-").toFile()
        try {
            val files = AndroidPrivateFileStore(File(directory, "photos"))
            files.write(PhotoId("original"), byteArrayOf(1))
            val replacement = async {
                coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                files.replaceAll(listOf(photo("replacement", byteArrayOf(2))))
            }
            assertFailsWith<CancellationException> { replacement.await() }
            assertContentEquals(byteArrayOf(1), files.read(PhotoId("original")))
            assertFalse(File(directory, "photos.stage").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun nativeWriteFailureCleansPartialStageAndPreservesLivePhotos() = runTest {
        val directory = Files.createTempDirectory("photo-stage-failure-").toFile()
        try {
            val files = AndroidPrivateFileStore(File(directory, "photos"))
            val original = photo("original", byteArrayOf(1))
            files.write(original.id, original.bytes)
            // Every ID/hash passes validation, but the filesystem rejects a >255-character basename.
            val oversizedBasename = "a".repeat(300)
            assertFailsWith<IOException> {
                files.replaceAll(listOf(photo("first-staged", byteArrayOf(2)), photo(oversizedBasename, byteArrayOf(3))))
            }
            assertEquals(listOf(original), files.exportAll())
            assertFalse(File(directory, "photos.stage").exists())
            assertFalse(File(directory, "photos.previous").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun clearRemovesSwapRemnantsButPreservesDurableRecoveryJournal() = runTest {
        val directory = Files.createTempDirectory("photo-cleanup-").toFile()
        try {
            val files = AndroidPrivateFileStore(File(directory, "photos"))
            files.write(PhotoId("valid-id_1.jpg"), byteArrayOf(1))
            files.writeRecoveryArchive(byteArrayOf(5))
            listOf("photos.stage", "photos.previous").forEach { name ->
                File(directory, name).apply { mkdir() }.resolve("stale").writeText("stale")
            }
            File(directory, "photos.recovery.tmp").writeText("partial")
            files.clear()
            assertEquals(emptyList(), files.exportAll())
            assertNotNull(files.readRecoveryArchive())
            assertEquals(setOf("photos", "photos.recovery"), directory.list()!!.toSet())
            val next = photo("valid-id_2.jpg", byteArrayOf(8))
            files.replaceAll(listOf(next))
            assertEquals(listOf(next), files.exportAll())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun photo(id: String, bytes: ByteArray) = PrivatePhoto(PhotoId(id), bytes, Sha256.digest(bytes))
}
