@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sedsoftware.sproutpace.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.PhotoId
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
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
            check(NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(journal, true))
        }
    }

    override suspend fun read(id: PhotoId): ByteArray? = NSData.dataWithContentsOfFile(path(id))?.let { data ->
        data.bytes?.readBytes(data.length.toInt())
    }
    override suspend fun write(id: PhotoId, bytes: ByteArray) {
        bytes.usePinned { pinned ->
            val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            check(data.writeToFile(path(id), true))
        }
    }
    override suspend fun delete(id: PhotoId) { if (manager.fileExistsAtPath(path(id))) check(manager.removeItemAtPath(path(id), null)) }
    override suspend fun exportAll(): List<PrivatePhoto> = manager.contentsOfDirectoryAtPath(root, null).orEmpty()
        .filterIsInstance<String>().sorted().map { name ->
            val bytes = requireNotNull(read(PhotoId(name)))
            PrivatePhoto(PhotoId(name), bytes, Sha256.digest(bytes))
        }
    override suspend fun replaceAll(photos: List<PrivatePhoto>) {
        val stage = "$root.stage"; val previous = "$root.previous"
        if (manager.fileExistsAtPath(stage)) manager.removeItemAtPath(stage, null)
        manager.createDirectoryAtPath(stage, true, null, null)
        photos.forEach { photo ->
            require(Sha256.digest(photo.bytes) == photo.sha256)
            photo.bytes.usePinned { pinned ->
                val data = NSData.create(bytes = pinned.addressOf(0), length = photo.bytes.size.toULong())
                check(data.writeToFile("$stage/${photo.id.value}", true))
            }
        }
        if (manager.fileExistsAtPath(previous)) manager.removeItemAtPath(previous, null)
        check(manager.moveItemAtPath(root, previous, null))
        runCatching {
            check(manager.moveItemAtPath(stage, root, null))
            manager.removeItemAtPath(previous, null)
        }.getOrElse { failure ->
            manager.moveItemAtPath(previous, root, null)
            throw failure
        }
    }
    override suspend fun clear() { exportAll().forEach { delete(it.id) } }
    override fun close() = Unit
    private fun path(id: PhotoId): String { require('/' !in id.value); return "$root/${id.value}" }
}

class IosPendingPhotoImportStore : PendingPhotoImportStore {
    private val manager = NSFileManager.defaultManager
    private val root: String = (manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL)
        .URLByAppendingPathComponent("SproutPace", true)!!
        .URLByAppendingPathComponent("PendingPhotos", true)!!.path!!

    init { manager.createDirectoryAtPath(root, true, null, null) }

    override suspend fun read(token: String): ByteArray? = NSData.dataWithContentsOfFile(path(token))?.let { data ->
        data.bytes?.readBytes(data.length.toInt())
    }
    override suspend fun delete(token: String) {
        val target = path(token)
        if (manager.fileExistsAtPath(target)) check(manager.removeItemAtPath(target, null))
    }
    override suspend fun clear() {
        manager.contentsOfDirectoryAtPath(root, null).orEmpty().filterIsInstance<String>().forEach { delete(it) }
    }
    override fun close() = Unit
    private fun path(token: String): String = "$root/${requireSafePendingPhotoToken(token)}"
}
