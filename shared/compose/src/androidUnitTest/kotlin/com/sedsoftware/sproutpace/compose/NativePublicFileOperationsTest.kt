package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.data.BatchHistoryCsv
import com.sedsoftware.sproutpace.data.LocalBackupArchiveCodec
import com.sedsoftware.sproutpace.domain.model.ObservationId
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativePublicFileOperationsTest {
    @Test fun ac072Ac073_publicObservationEditAndConfirmedDeletePreserveOtherTimelineAndPhotosAfterReopen() = runTest {
        withPublicWorkspace { directory, preferences ->
            val retained = PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                attachObservation(fixture, "observation-a", "Original", byteArrayOf(1, 2))
                attachObservation(fixture, "observation-b", "Other", byteArrayOf(3, 4))
                advanceUntilIdle()
                val before = fixture.database.snapshot().observations.associateBy { it.id.value }
                val originalPhoto = requireNotNull(before.getValue("observation-a").photoId)
                val peerPhoto = requireNotNull(before.getValue("observation-b").photoId)
                fixture.save(ScreenId.OBSERVATION_EDITOR, "observation-a", "save-observation", mapOf(
                    "text" to "Edited \"Рост\"", "occurredAt" to "2026-10-10T11:30:00Z"))
                advanceUntilIdle()
                val edited = fixture.database.snapshot().observations.single { it.id.value == "observation-a" }
                assertEquals(ObservationId("observation-a"), edited.id)
                assertEquals("Edited \"Рост\"", edited.text)
                assertEquals(Instant.parse("2026-10-10T11:30:00Z"), edited.occurredAt)
                assertEquals(originalPhoto, edited.photoId)
                assertContentEquals(byteArrayOf(1, 2), fixture.files.read(originalPhoto))
                assertEquals(before.getValue("observation-b"), fixture.database.snapshot().observations.single { it.id.value == "observation-b" })
                fixture.open(ScreenId.OBSERVATION_EDITOR, "observation-a")
                fixture.submit("delete-observation", confirm = false)
                assertEquals(DestructiveOperation.DELETE_OBSERVATION, fixture.root.confirmation.value.child?.configuration?.destructiveOperation)
                assertEquals(2, fixture.database.snapshot().observations.size)
                assertNotNull(fixture.files.read(originalPhoto))
                fixture.root.dismissConfirmation()
                assertEquals(2, fixture.database.snapshot().observations.size)
                fixture.submit("delete-observation")
                advanceUntilIdle()
                assertNull(fixture.files.read(originalPhoto))
                assertContentEquals(byteArrayOf(3, 4), fixture.files.read(peerPhoto))
                assertEquals(listOf(before.getValue("observation-b")), fixture.database.snapshot().observations)
                fixture.backup.createArchive()
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                assertEquals(retained, reopened.backup.createArchive())
                reopened.open(ScreenId.OBSERVATION_EDITOR, "observation-b")
                assertEquals("Other", reopened.field("text"))
                assertEquals(1, reopened.files.exportAll().size)
            }
        }
    }

    @Test fun ac090Ac091Ac092_publicBackupCreatePreviewAndConfirmedReplaceRoundtripRealRowsAndNativePhotos() = runTest {
        withPublicWorkspace { directory, preferences ->
            val original = PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                attachObservation(fixture, "observation-a", "Рост", byteArrayOf(1, 2, 3))
                attachObservation(fixture, "observation-b", "Roots", byteArrayOf(4, 5, 6))
                val before = fixture.backup.createArchive()
                fixture.open(ScreenId.BACKUP_RESTORE)
                fixture.exportDocument = "exported-backup"
                fixture.submit("create-backup")
                advanceUntilIdle()
                val request = fixture.platformRequests.last()
                assertEquals(PlatformActionKind.CREATE_DOCUMENT, request.kind)
                assertEquals("application/zip", request.mediaType)
                val bytes = assertNotNull(fixture.documents.read("exported-backup"))
                val decoded = LocalBackupArchiveCodec().decode(bytes)
                assertEquals(before, decoded)
                assertEquals(2, decoded.photos.size)
                assertEquals(2, decoded.snapshot.observations.size)
                fixture.save(ScreenId.ZONES, "zone", "update-zone", mapOf("name" to "Changed shelf"))
                val changed = fixture.backup.createArchive()
                assertNotEquals(before, changed)
                fixture.documents.write("imported", bytes)
                fixture.open(ScreenId.BACKUP_RESTORE)
                fixture.submit("preview-restore")
                advanceUntilIdle()
                assertEquals(PlatformActionKind.CHOOSE_DOCUMENT, fixture.platformRequests.last().kind)
                assertEquals("2", fixture.model().facts.single { it.id == "restore-photo-count" }.authoredValue)
                assertEquals("2", fixture.model().facts.single { it.id == "restore-record-count-observations" }.authoredValue)
                assertEquals(changed, fixture.backup.createArchive())
                fixture.submit("restore-backup", confirm = false)
                assertEquals(DestructiveOperation.REPLACE_RESTORE, fixture.root.confirmation.value.child?.configuration?.destructiveOperation)
                assertEquals(changed, fixture.backup.createArchive())
                fixture.root.dismissConfirmation()
                assertEquals(changed, fixture.backup.createArchive())
                fixture.submit("restore-backup")
                advanceUntilIdle()
                assertEquals(before, fixture.backup.createArchive())
                assertEquals(listOf(before.snapshot), fixture.reconciledSnapshots)
                assertNull(fixture.files.readRecoveryArchive())
                before
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                assertEquals(original, reopened.backup.createArchive())
                assertEquals(2, reopened.files.exportAll().size)
            }
        }
    }

    @Test fun ac093_publicMalformedArchiveReportsFailureWithoutChangingRowsPhotosOrReminders() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                attachObservation(fixture, "observation-a", "Preserve", byteArrayOf(8, 9))
                val before = fixture.backup.createArchive()
                fixture.documents.write("imported-corrupt", byteArrayOf(1, 2, 3))
                fixture.selectedDocument = "imported-corrupt"
                fixture.open(ScreenId.BACKUP_RESTORE)
                fixture.submit("preview-restore")
                advanceUntilIdle()
                assertEquals(LoadState.ERROR, fixture.model().loadState)
                assertNotNull(fixture.model().errorKey)
                assertTrue(fixture.model().facts.none { it.id == "restore-photo-count" })
                assertNull(fixture.root.confirmation.value.child)
                assertEquals(before, fixture.backup.createArchive())
                assertTrue(fixture.reconciledSnapshots.isEmpty())
                assertNull(fixture.files.readRecoveryArchive())
            }
        }
    }

    @Test fun ac094_publicHistoryCsvExportsUtf8EscapedTerminalRowsWithoutNativePhotoPayloads() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                val photo = "private-photo-content-never-csv".encodeToByteArray()
                attachObservation(fixture, "observation-a", "Photo note", photo)
                val photoId = requireNotNull(fixture.database.snapshot().observations.single().photoId)
                fixture.save(ScreenId.BATCH_EDITOR, "planned", "save-batch", mapOf(
                    "id" to "planned", "cultureId" to "pea", "trayId" to "tray", "substrate" to "Coco",
                    "actualSeedMassGrams" to "12.5", "plannedSowDate" to "2026-10-11"))
                fixture.save(ScreenId.CLOSE_BATCH, "batch", "save-outcome", mapOf(
                    "outcome" to "harvested", "actualMassGrams" to "42.5", "note" to "Рост, \"хороший\"\nвторая строка"))
                advanceUntilIdle()
                fixture.open(ScreenId.BACKUP_RESTORE)
                fixture.exportDocument = "exported-history-csv"
                fixture.submit("export-csv")
                advanceUntilIdle()
                assertEquals(PlatformActionKind.CREATE_DOCUMENT, fixture.platformRequests.last().kind)
                assertEquals("text/csv", fixture.platformRequests.last().mediaType)
                val csv = assertNotNull(fixture.documents.read("exported-history-csv")).decodeToString()
                assertTrue(csv.startsWith(BatchHistoryCsv.HEADER + "\r\n"))
                assertTrue(csv.contains(",HARVESTED,"))
                assertTrue(csv.contains(",42.5,"))
                assertTrue(csv.contains("\"Рост, \"\"хороший\"\"\nвторая строка\""))
                assertFalse(csv.contains("planned"))
                assertFalse(csv.contains(photoId.value))
                assertFalse(csv.contains(photo.decodeToString()))
                assertContentEquals(photo, fixture.files.read(photoId))
            }
        }
    }

    private fun attachObservation(fixture: PublicProductionFixture, id: String, text: String, photo: ByteArray) {
        fixture.open(ScreenId.OBSERVATION_EDITOR, id)
        fixture.edit(mapOf("id" to id, "batchId" to "batch", "occurredAt" to "2026-10-10T10:00:00Z", "text" to text))
        fixture.pendingPhotos.stage("picker-$id", photo)
        fixture.selectedPhoto = pendingPhotoDraftValue("picker-$id")
        fixture.dispatch(UiAction.RequestPlatform(PlatformActionRequest(PlatformActionKind.CHOOSE_PHOTO, "photoId")))
        fixture.submit("save-observation")
    }
}
