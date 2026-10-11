package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.data.ArchiveDocumentPort
import com.sedsoftware.sproutpace.data.InMemoryPrivateFileStore
import com.sedsoftware.sproutpace.data.LocalBackupArchiveCodec
import com.sedsoftware.sproutpace.data.LocalBackupService
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupProductionProjectionTest {
    @Test
    fun archivePreviewUsesItsRealCreationTimeFormatAndNamedRecordCounts() = runTest {
        fixture { database, backup, manager, documents ->
            database.saveZone(GrowZone(GrowZoneId("one"), "Shelf", GrowZoneType.GROW_LIGHT, 8))
            database.saveZone(GrowZone(GrowZoneId("two"), "Window", GrowZoneType.NATURAL_LIGHT, 3))
            documents.bytes["archive"] = backup.createArchiveBytes()
            val snapshot = manager.execute(ScreenId.BACKUP_RESTORE,
                command("preview-restore", "archive")).getOrThrow()
            val facts = snapshot.facts.associateBy { it.id }
            assertEquals("2026-10-07T15:45:00Z", facts.getValue("restore-created-at").authoredValue)
            assertEquals(LocalBackupArchiveCodec.FORMAT_VERSION.toString(),
                facts.getValue("restore-format-version").authoredValue)
            assertEquals("2", facts.getValue("restore-record-count-zones").authoredValue)
            assertEquals("backup_count_zones", facts.getValue("restore-record-count-zones").labelKey.value)
            assertEquals("backup_count_trays", facts.getValue("restore-record-count-trayPresets").labelKey.value)
            assertEquals("0", facts.getValue("restore-photo-count").authoredValue)
            assertEquals("backup_count_photos", facts.getValue("restore-photo-count").labelKey.value)
        }
    }

    @Test
    fun invalidArchiveClearsPreviouslyValidPreviewWithoutMutatingDatabase() = runTest {
        fixture { database, backup, manager, documents ->
            documents.bytes["valid"] = backup.createArchiveBytes()
            manager.execute(ScreenId.BACKUP_RESTORE, command("preview-restore", "valid")).getOrThrow()
            documents.bytes["invalid"] = "not an archive".encodeToByteArray()
            val before = database.snapshot()
            assertTrue(manager.execute(ScreenId.BACKUP_RESTORE, command("preview-restore", "invalid")).isFailure)
            assertEquals(before, database.snapshot())
            assertTrue(manager.watch(ScreenId.BACKUP_RESTORE).first().facts.none { it.id.startsWith("restore-") })
        }
    }

    @Test
    fun restoreRequiresPreviewOfTheSameSelectedDocument() = runTest {
        fixture { database, backup, manager, documents ->
            documents.bytes["first"] = backup.createArchiveBytes()
            documents.bytes["second"] = backup.createArchiveBytes()
            val before = database.snapshot()
            assertTrue(manager.execute(ScreenId.BACKUP_RESTORE, command("restore-backup", "first")).isFailure)
            manager.execute(ScreenId.BACKUP_RESTORE, command("preview-restore", "first")).getOrThrow()
            assertTrue(manager.execute(ScreenId.BACKUP_RESTORE, command("restore-backup", "second")).isFailure)
            assertEquals(before, database.snapshot())
        }
    }

    private fun command(operation: String, document: String) = ScreenCommand(
        operation, arguments = mapOf("documentId" to document), deduplicationKey = "$operation:$document",
    )

    private suspend fun fixture(
        block: suspend (LocalDatabase, LocalBackupService, ProductionScreenDataManager, Documents) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SproutPaceDatabase.Schema.create(driver)
        val database = LocalDatabase(object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }, Dispatchers.Unconfined)
        val settings = MultiplatformProductSettings(MapSettings())
        val files = InMemoryPrivateFileStore()
        val documents = Documents()
        val backup = LocalBackupService(database, files, settings, Dispatchers.Unconfined,
            documents = documents, now = { Instant.parse("2026-10-07T15:45:00Z") })
        val manager = ProductionScreenDataManager(database, settings, backupService = backup,
            now = { Instant.parse("2026-10-10T12:00:00Z") }, today = { LocalDate.parse("2026-10-10") })
        try {
            block(database, backup, manager, documents)
        } finally {
            database.close()
            files.close()
        }
    }

    private class Documents : ArchiveDocumentPort {
        val bytes = mutableMapOf<String, ByteArray>()
        override suspend fun read(documentId: String): ByteArray? = bytes[documentId]
        override suspend fun write(documentId: String, bytes: ByteArray) { this.bytes[documentId] = bytes }
    }
}
