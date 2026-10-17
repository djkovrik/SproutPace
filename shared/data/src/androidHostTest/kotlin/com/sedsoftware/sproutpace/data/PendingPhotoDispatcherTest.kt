package com.sedsoftware.sproutpace.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingPhotoDispatcherTest {
    @Test fun nativePendingPreviewReadAndCleanupCrossInjectedWorkerBoundary() = runTest {
        val directory = Files.createTempDirectory("pending-photo-worker-").toFile()
        try {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
                val dispatcher = RecordingDispatcher(worker)
                val pending = AndroidPendingPhotoImportStore(directory, dispatcher)
                val selected = directory.resolve("selected.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                assertContentEquals(byteArrayOf(1, 2, 3), pending.read("selected.jpg"))
                pending.delete("selected.jpg")
                assertFalse(selected.exists())
                directory.resolve("abandoned.jpg").writeBytes(byteArrayOf(4, 5))
                pending.clear()
                assertTrue(directory.listFiles()!!.isEmpty())
                assertTrue(dispatcher.entries.get() >= 3)
            }
        } finally { directory.deleteRecursively() }
    }

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val entries = AtomicInteger()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            entries.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }
}
