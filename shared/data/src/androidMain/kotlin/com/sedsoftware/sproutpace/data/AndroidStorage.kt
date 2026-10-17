package com.sedsoftware.sproutpace.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.PhotoId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class AndroidSqlDriverFactory(
    private val context: Context,
    private val name: String = "sproutpace.db",
) : SqlDriverFactory {
    private var driver: AndroidSqliteDriver? = null
    override fun create() = checkNotNull(driver ?: AndroidSqliteDriver(
        schema = SproutPaceDatabase.Schema,
        context = context,
        name = name,
        callback = object : AndroidSqliteDriver.Callback(SproutPaceDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    ).also { driver = it })
    override fun close() { driver?.close(); driver = null }
}

class AndroidPrivateFileStore(directory: File) : PrivateFileStore {
    constructor(context: Context) : this(File(context.filesDir, "observation_photos"))
    private val root = directory.canonicalFile
    init {
        val previous = File(root.parentFile, "${root.name}.previous")
        if (!root.exists() && previous.exists()) check(previous.renameTo(root))
        check(root.mkdirs() || root.isDirectory)
    }
    private val recovery = File(root.parentFile, "${root.name}.recovery")
    override suspend fun readRecoveryArchive(): ByteArray? = recovery.takeIf(File::isFile)?.readBytes()
    override suspend fun writeRecoveryArchive(bytes: ByteArray?) {
        if (bytes == null) {
            check(!recovery.exists() || recovery.delete())
        } else {
            val temporary = File(root.parentFile, "${root.name}.recovery.tmp")
            try {
                currentCoroutineContext().ensureActive()
                temporary.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(recovery))
            } finally {
                check(!temporary.exists() || temporary.delete())
            }
        }
    }
    override suspend fun read(id: PhotoId): ByteArray? = file(id).takeIf(File::isFile)?.readBytes()
    override suspend fun write(id: PhotoId, bytes: ByteArray) {
        val target = file(id)
        val temporary = containedFile(root, ".${requireSafePhotoId(id)}.tmp")
        try {
            currentCoroutineContext().ensureActive()
            temporary.writeBytes(bytes)
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(target)) { "Unable to commit private photo" }
        } finally {
            check(!temporary.exists() || temporary.delete())
        }
    }
    override suspend fun delete(id: PhotoId) { check(!file(id).exists() || file(id).delete()) }
    override suspend fun exportAll(): List<PrivatePhoto> = root.listFiles().orEmpty()
        .filter { it.isFile && !it.name.startsWith('.') }
        .sortedBy { it.name }
        .map { entry ->
            val context = currentCoroutineContext()
            val id = PhotoId(entry.name)
            file(id).readBytes().let { PrivatePhoto(id, it, Sha256.digest(it, context::ensureActive)) }
        }
    override suspend fun replaceAll(photos: List<PrivatePhoto>) {
        validatePhotos(photos)
        val parent = checkNotNull(root.parentFile)
        val staged = File(parent, "${root.name}.stage")
        val previous = File(parent, "${root.name}.previous")
        try {
            currentCoroutineContext().ensureActive()
            check(staged.deleteRecursively()); check(staged.mkdir())
            photos.forEach { photo ->
                currentCoroutineContext().ensureActive()
                containedFile(staged, requireSafePhotoId(photo.id)).writeBytes(photo.bytes)
            }
            currentCoroutineContext().ensureActive()
            check(previous.deleteRecursively())
            check(root.renameTo(previous))
            commitStage(staged, previous)
        } finally {
            check(staged.deleteRecursively())
        }
    }
    private fun commitStage(staged: File, previous: File) {
        try {
            check(staged.renameTo(root))
        } catch (expectedFailure: Throwable) {
            if (!previous.renameTo(root)) {
                expectedFailure.addSuppressed(IllegalStateException("Unable to restore private photo directory"))
            }
            throw expectedFailure
        }
        check(previous.deleteRecursively())
    }
    private suspend fun validatePhotos(photos: List<PrivatePhoto>) {
        val context = currentCoroutineContext()
        require(photos.map { requireSafePhotoId(it.id) }.distinct().size == photos.size)
        photos.forEach { photo ->
            context.ensureActive()
            require(Sha256.digest(photo.bytes, context::ensureActive) == photo.sha256)
        }
    }
    override suspend fun clear() {
        root.listFiles().orEmpty().forEach { check(it.deleteRecursively()) }
        listOf("stage", "previous", "recovery.tmp").forEach { suffix ->
            check(File(root.parentFile, "${root.name}.$suffix").deleteRecursively())
        }
    }
    override fun close() = Unit
    private fun file(id: PhotoId): File = containedFile(root, requireSafePhotoId(id))
    private fun containedFile(directory: File, name: String): File = File(directory, name).canonicalFile.also {
        require(it.parentFile == directory.canonicalFile) { "Private photo must remain app-private" }
    }
}

class AndroidPendingPhotoImportStore(
    directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PendingPhotoImportStore {
    constructor(context: Context) : this(File(context.filesDir, "pending_photos"))
    private val root = directory.canonicalFile.also {
        check(it.mkdirs() || it.isDirectory)
    }

    override suspend fun read(token: String): ByteArray? = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        file(token).takeIf(File::isFile)?.readBytes().also { currentCoroutineContext().ensureActive() }
    }
    override suspend fun delete(token: String) = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        file(token).let { check(!it.exists() || it.delete()) }
    }
    override suspend fun clear() = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        root.listFiles().orEmpty().forEach { file -> if (file.isFile) check(file.delete()) }
    }
    override fun close() = Unit

    private fun file(token: String): File = File(root, requireSafePendingPhotoToken(token)).canonicalFile.also {
        require(it.parentFile == root) { "Pending photo must remain app-private" }
    }
}
