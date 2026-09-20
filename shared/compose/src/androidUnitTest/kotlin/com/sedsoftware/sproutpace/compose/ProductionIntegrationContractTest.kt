package com.sedsoftware.sproutpace.compose

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductionIntegrationContractTest {
    @Test
    fun androidDocumentPortTreatsContentUriAsOpaqueAndTransfersBytes() = runTest {
        val expected = byteArrayOf(1, 2, 3)
        val visited = mutableListOf<String>()
        val port = AndroidArchiveDocumentPort(
            readDocument = { uri -> visited += uri.toString(); expected },
            writeDocument = { uri, bytes ->
                visited += uri.toString()
                assertContentEquals(expected, bytes)
            },
        )

        assertContentEquals(expected, port.read("content://documents/tree/archive"))
        port.write("content://documents/tree/archive", expected)

        assertEquals(
            listOf("content://documents/tree/archive", "content://documents/tree/archive"),
            visited,
        )
    }

    @Test
    fun androidDocumentPortRejectsFilesystemPaths() = runTest {
        val port = AndroidArchiveDocumentPort(
            readDocument = { null },
            writeDocument = { _, _ -> Unit },
        )

        assertFailsWith<IllegalArgumentException> { port.read("/shared/archive.sproutpace") }
        assertFailsWith<IllegalArgumentException> {
            port.write("file:///shared/archive.sproutpace", byteArrayOf(1))
        }
    }
}
