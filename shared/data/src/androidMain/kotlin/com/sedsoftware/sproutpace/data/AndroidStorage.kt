package com.sedsoftware.sproutpace.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.PhotoId
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

class AndroidPrivateFileStore(private val root: File) : PrivateFileStore {
    constructor(context: Context) : this(File(context.filesDir, "observation_photos"))
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
            temporary.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
            check(temporary.renameTo(recovery))
        }
    }
    override suspend fun read(id: PhotoId): ByteArray? = file(id).takeIf(File::isFile)?.readBytes()
    override suspend fun write(id: PhotoId, bytes: ByteArray) {
        val temporary = File(root, ".${id.value}.tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(file(id))) { "Unable to commit private photo" }
    }
    override suspend fun delete(id: PhotoId) { check(!file(id).exists() || file(id).delete()) }
    override suspend fun exportAll(): List<PrivatePhoto> = root.listFiles().orEmpty()
        .filter { it.isFile && !it.name.startsWith('.') }
        .sortedBy { it.name }
        .map { file -> file.readBytes().let { PrivatePhoto(PhotoId(file.name), it, Sha256.digest(it)) } }
    override suspend fun replaceAll(photos: List<PrivatePhoto>) {
        val parent = checkNotNull(root.parentFile)
        val staged = File(parent, "${root.name}.stage")
        val previous = File(parent, "${root.name}.previous")
        staged.deleteRecursively(); check(staged.mkdir())
        photos.forEach { photo ->
            require(Sha256.digest(photo.bytes) == photo.sha256)
            File(staged, photo.id.value).writeBytes(photo.bytes)
        }
        previous.deleteRecursively()
        check(root.renameTo(previous))
        runCatching {
            check(staged.renameTo(root))
        }.getOrElse { failure ->
            previous.renameTo(root)
            throw failure
        }
        previous.deleteRecursively()
    }
    override suspend fun clear() { root.listFiles().orEmpty().forEach { check(it.delete()) } }
    override fun close() = Unit
    private fun file(id: PhotoId): File = File(root, id.value).also { require(it.parentFile == root) }
}

class AndroidPendingPhotoImportStore(context: Context) : PendingPhotoImportStore {
    private val root = File(context.filesDir, "pending_photos").canonicalFile.also {
        check(it.mkdirs() || it.isDirectory)
    }

    override suspend fun read(token: String): ByteArray? = file(token).takeIf(File::isFile)?.readBytes()
    override suspend fun delete(token: String) { file(token).let { check(!it.exists() || it.delete()) } }
    override suspend fun clear() {
        root.listFiles().orEmpty().forEach { file -> if (file.isFile) check(file.delete()) }
    }
    override fun close() = Unit

    private fun file(token: String): File = File(root, requireSafePendingPhotoToken(token)).canonicalFile.also {
        require(it.parentFile == root) { "Pending photo must remain app-private" }
    }
}
