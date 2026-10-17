package com.sedsoftware.sproutpace.compose

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchiveDocumentStorageTest {
    @Test fun documentIoAndCleanupUseInjectedDispatcher() = runTest {
        Executors.newSingleThreadExecutor { task -> Thread(task, "archive-test-io") }
            .asCoroutineDispatcher().use { dispatcher ->
                val threads = mutableListOf<String>()
                val bytes = byteArrayOf(1, 2)
                val port = AndroidArchiveDocumentPort(
                    readDocument = { threads += Thread.currentThread().name; bytes },
                    writeDocument = { _, _ -> threads += Thread.currentThread().name },
                    readImportedFile = { threads += Thread.currentThread().name; bytes },
                    isImportedFile = { true },
                    clearImports = { threads += Thread.currentThread().name },
                    ioDispatcher = dispatcher,
                )
                assertContentEquals(bytes, port.read("content://provider/archive"))
                assertContentEquals(bytes, port.read("/app/imports/archive"))
                port.write("content://provider/export", bytes)
                port.clearImportedArchives()
                assertEquals(List(4) { "archive-test-io" }, threads)
            }
    }

    @Test fun resetRemovesOnlyAppOwnedImportCopies() {
        val directory = Files.createTempDirectory("document-cleanup-").toFile()
        try {
            val imports = File(directory, "imports").apply { mkdir() }
            File(imports, "selected.sproutpace").writeText("local copy")
            val backup = File(directory, "export.sproutpace").apply { writeText("user export") }
            val csv = File(directory, "history.csv").apply { writeText("user history") }
            clearAndroidImportedArchives(imports)
            assertTrue(imports.listFiles()!!.isEmpty())
            assertEquals("user export", backup.readText())
            assertEquals("user history", csv.readText())
            clearAndroidImportedArchives(imports)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cleanupFailuresReachCaller() = runTest {
        val port = AndroidArchiveDocumentPort(
            readDocument = { null },
            writeDocument = { _, _ -> },
            clearImports = { error("cleanup unavailable") },
        )
        assertFailsWith<IllegalStateException> { port.clearImportedArchives() }
    }
}
