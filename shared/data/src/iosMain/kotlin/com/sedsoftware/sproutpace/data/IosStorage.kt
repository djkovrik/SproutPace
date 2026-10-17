@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sedsoftware.sproutpace.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.PhotoId
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

class IosSqlDriverFactory(private val name: String = "sproutpace.db") : SqlDriverFactory {
    private var driver: NativeSqliteDriver? = null
    override fun create() = checkNotNull(driver ?: NativeSqliteDriver(
        schema = SproutPaceDatabase.Schema,
        name = name,
        onConfiguration = { configuration ->
            configuration.copy(
                extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true),
            )
        },
    ).also { driver = it })
    override fun close() { driver?.close(); driver = null }
}

class IosPrivateFileStore : PrivateFileStore {
    private val manager = NSFileManager.defaultManager
    private val root: String = (manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL)
        .URLByAppendingPathComponent("observation_photos", true)!!.path!!

    init {
        if (!manager.fileExistsAtPath(root) && manager.fileExistsAtPath("$root.previous")) {
            check(manager.moveItemAtPath("$root.previous", root, null))
        }
        check(manager.createDirectoryAtPath(root, true, null, null))
    }

    override suspend fun readRecoveryArchive(): ByteArray? = NSData.dataWithContentsOfFile("$root.recovery")?.let {
        it.bytes?.readBytes(it.length.toInt())
    }
    override suspend fun writeRecoveryArchive(bytes: ByteArray?) {
        val journal = "$root.recovery"
        if (bytes == null) {
            if (manager.fileExistsAtPath(journal)) check(manager.removeItemAtPath(journal, null))
        } else bytes.usePinned { pinned ->
            val data = NSData.create(bytes = if (bytes.isEmpty()) null else pinned.addressOf(0), length = bytes.size.toULong())
            check(data.writeToFile(journal, true))
        }
    }

    override suspend fun read(id: PhotoId): ByteArray? = NSData.dataWithContentsOfFile(path(id))?.let { data ->
        data.bytes?.readBytes(data.length.toInt())
    }
    override suspend fun write(id: PhotoId, bytes: ByteArray) {
        val target = path(id)
        currentCoroutineContext().ensureActive()
        bytes.usePinned { pinned ->
            val data = NSData.create(bytes = if (bytes.isEmpty()) null else pinned.addressOf(0), length = bytes.size.toULong())
            check(data.writeToFile(target, true))
        }
    }
    override suspend fun delete(id: PhotoId) { if (manager.fileExistsAtPath(path(id))) check(manager.removeItemAtPath(path(id), null)) }
    override suspend fun exportAll(): List<PrivatePhoto> = manager.contentsOfDirectoryAtPath(root, null).orEmpty()
        .filterIsInstance<String>().sorted().map { name ->
            val context = currentCoroutineContext()
            val bytes = requireNotNull(read(PhotoId(name)))
            PrivatePhoto(PhotoId(name), bytes, Sha256.digest(bytes, context::ensureActive))
        }
    override suspend fun replaceAll(photos: List<PrivatePhoto>) {
        validatePhotos(photos)
        val stage = "$root.stage"; val previous = "$root.previous"
        try {
            currentCoroutineContext().ensureActive()
            removeIfPresent(stage)
            check(manager.createDirectoryAtPath(stage, true, null, null))
            photos.forEach { photo ->
                currentCoroutineContext().ensureActive()
                val target = containedPhotoPath(stage, photo.id)
                photo.bytes.usePinned { pinned ->
                    val data = NSData.create(
                        bytes = if (photo.bytes.isEmpty()) null else pinned.addressOf(0),
                        length = photo.bytes.size.toULong(),
                    )
                    check(data.writeToFile(target, true))
                }
            }
            currentCoroutineContext().ensureActive()
            removeIfPresent(previous)
            check(manager.moveItemAtPath(root, previous, null))
            commitStage(stage, previous)
        } finally {
            removeIfPresent(stage)
        }
    }
    private fun commitStage(stage: String, previous: String) {
        try {
            check(manager.moveItemAtPath(stage, root, null))
        } catch (expectedFailure: Throwable) {
            if (!manager.moveItemAtPath(previous, root, null)) {
                expectedFailure.addSuppressed(IllegalStateException("Unable to restore private photo directory"))
            }
            throw expectedFailure
        }
        removeIfPresent(previous)
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
        manager.contentsOfDirectoryAtPath(root, null).orEmpty().filterIsInstance<String>().forEach { name ->
            removeIfPresent("$root/$name")
        }
        listOf("stage", "previous", "recovery.tmp").forEach { removeIfPresent("$root.$it") }
    }
    private fun removeIfPresent(path: String) {
        if (manager.fileExistsAtPath(path)) check(manager.removeItemAtPath(path, null))
    }
    override fun close() = Unit
    private fun path(id: PhotoId): String = containedPhotoPath(root, id)
    private fun containedPhotoPath(directory: String, id: PhotoId): String {
        val parent = NSURL.fileURLWithPath(directory).URLByResolvingSymlinksInPath!!
        val candidate = parent.URLByAppendingPathComponent(requireSafePhotoId(id))!!
            .URLByResolvingSymlinksInPath!!
        require(candidate.URLByDeletingLastPathComponent?.path == parent.path) {
            "Private photo must remain app-private"
        }
        return requireNotNull(candidate.path)
    }
}

class IosPendingPhotoImportStore(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PendingPhotoImportStore {
    private val manager = NSFileManager.defaultManager
    private val root: String = (manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL)
        .URLByAppendingPathComponent("SproutPace", true)!!
        .URLByAppendingPathComponent("PendingPhotos", true)!!.path!!

    init { manager.createDirectoryAtPath(root, true, null, null) }

    override suspend fun read(token: String): ByteArray? = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val bytes = NSData.dataWithContentsOfFile(path(token))?.let { data -> data.bytes?.readBytes(data.length.toInt()) }
        currentCoroutineContext().ensureActive()
        bytes
    }
    override suspend fun delete(token: String) = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val target = path(token)
        if (manager.fileExistsAtPath(target)) check(manager.removeItemAtPath(target, null))
    }
    override suspend fun clear() = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        manager.contentsOfDirectoryAtPath(root, null).orEmpty().filterIsInstance<String>().forEach { delete(it) }
    }
    override fun close() = Unit
    private fun path(token: String): String = "$root/${requireSafePendingPhotoToken(token)}"
}
