package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.data.DomainJsonCodec
import com.sedsoftware.sproutpace.data.LocalBackupArchiveCodec
import com.sedsoftware.sproutpace.data.LocalDataException
import com.sedsoftware.sproutpace.data.SnapshotValidation
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Public router -> retained Store -> real SQLite, private photos and archive staging. */
class DataLifecycleRegressionProductionTest {
    @Test fun ac038_assignedStageMoveToFullZoneCanBeRecordedUnassignedAndSurvivesReopen() = runTest {
        withPublicWorkspace { directory, preferences ->
            val committed = PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                f.save(ScreenId.ZONES, "full-zone", "save-zone", mapOf(
                    "id" to "full-zone", "name" to "Full shelf", "type" to "grow_light", "capacity" to "1"))
                createBatch(f, "peer")
                sow(f, "peer", "full-zone")
                sow(f, "batch", "zone")
                f.open(ScreenId.BATCH_DETAIL, "batch")
                assertTrue(f.model().actions.any { it.id.value == "assign-overflow" })
                assertTrue("unassigned" in batchActionFieldIds("confirm-stage"))
                f.edit(mapOf("zoneId" to "full-zone", "unassigned" to "true"))
                f.submit("confirm-stage")
                advanceUntilIdle()
                val preview = assertNotNull(f.model().stagePreview)
                assertEquals("true", preview.command.arguments["unassigned"])
                assertEquals(BatchState.SOWN, f.database.snapshot().batches.single { it.id.value == "batch" }.state)
                f.dispatch(UiAction.ConfirmStagePreview)
                advanceUntilIdle()
                assertNull(f.model().errorKey)
                val changed = f.database.snapshot().batches.single { it.id.value == "batch" }
                assertEquals(BatchState.BLACKOUT_OR_WEIGHT, changed.state)
                assertEquals(Placement.Unassigned("Shelf"), changed.placement)
                assertEquals(Placement.Assigned(GrowZoneId("zone"), "Shelf"), changed.events.last().placementBefore)
                assertEquals(changed.placement, changed.events.last().placementAfter)
                f.open(ScreenId.ZONES)
                assertEquals(0, f.model().items.single { it.id == "zone" }.occupancy?.occupied)
                assertEquals(1, f.model().items.single { it.id == "full-zone" }.occupancy?.occupied)
                changed
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                assertEquals(committed, reopened.database.snapshot().batches.single { it.id.value == "batch" })
                reopened.open(ScreenId.BATCH_DETAIL, "batch")
                assertEquals("state_temporarily_unassigned_title",
                    reopened.model().items.single { it.id == "batch" }.supportingKey?.value)
                assertTrue(reopened.model().conflictResolutionKeys.any { it.value == "leave_unassigned" })
            }
        }
    }

    @Test fun ac038_publicLeaveUnassignedWorksWithoutChangingStageAndRetainsPriorName() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                sow(f, "batch", "zone")
                f.open(ScreenId.BATCH_DETAIL, "batch")
                assertTrue(f.model().actions.any { it.id.value == "assign-overflow" })
                f.submit("assign-overflow")
                advanceUntilIdle()
                val original = f.database.snapshot().batches.single()
                assertEquals(BatchState.SOWN, original.state)
                assertEquals(Placement.Unassigned("Shelf"), original.placement)
                // A fresh visit/invocation must not erase the retained historical name.
                f.open(ScreenId.BATCH_DETAIL, "batch")
                f.submit("assign-overflow")
                assertEquals(original, f.database.snapshot().batches.single())
            }
        }
    }

    @Test fun ac013Ac084_closedHistoryKeepsActualFinalZoneAfterRenameDeleteAndRestart() = runTest {
        withPublicWorkspace { directory, preferences ->
            val outcomes = listOf("harvested", "failed", "abandoned")
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                f.save(ScreenId.ZONES, "final-zone", "save-zone", mapOf(
                    "id" to "final-zone", "name" to "Original final shelf", "type" to "grow_light", "capacity" to "3"))
                outcomes.forEachIndexed { index, outcome ->
                    val id = if (index == 0) "batch" else "batch-$outcome".also { createBatch(f, it) }
                    sow(f, id, "zone")
                    // A correction can move the actual batch without another stage event.
                    f.save(ScreenId.BATCH_EDITOR, id, "correct-batch", mapOf("zoneId" to "final-zone"))
                    assertEquals("Shelf", (f.database.snapshot().batches.single { it.id.value == id }
                        .events.last().placementAfter as Placement.Assigned).zoneNameSnapshot)
                    f.save(ScreenId.CLOSE_BATCH, id, "save-outcome", mapOf(
                        "outcome" to outcome, "reason" to "Cycle ended", "occurredAt" to f.now().toString()))
                    advanceUntilIdle()
                    val closed = f.database.snapshot().batches.single { it.id.value == id }
                    assertEquals(Placement.None, closed.placement)
                    assertEquals(Placement.Assigned(GrowZoneId("final-zone"), "Original final shelf"),
                        closed.closedPlacementSnapshot)
                    assertEquals("Original final shelf", f.model().items.single { it.id == id }.batch?.zoneName)
                }
                f.open(ScreenId.ZONES)
                assertEquals(0, f.model().items.single { it.id == "final-zone" }.occupancy?.occupied)
                f.save(ScreenId.ZONES, "final-zone", "update-zone", mapOf("name" to "Renamed final shelf"))
                f.open(ScreenId.ZONES, "final-zone")
                f.submit("delete-zone")
                assertTrue(f.database.snapshot().zones.none { it.id.value == "final-zone" })
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                // A historical zone may have been deleted; it is not an active foreign-key requirement.
                assertIs<RestoreValidation.Valid>(SnapshotValidation.validate(reopened.backup.createArchive().snapshot))
                reopened.open(ScreenId.TRAYS)
                reopened.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY))
                assertEquals(3, reopened.model().items.count { it.batch != null })
                assertTrue(reopened.model().items.filter { it.batch != null }
                    .all { it.batch?.zoneName == "Original final shelf" })
                val batches = reopened.database.snapshot().batches
                batches.forEach { batch ->
                    reopened.open(ScreenId.BATCH_DETAIL, batch.id.value)
                    assertEquals("Original final shelf", reopened.model().items.single { it.id == batch.id.value }.batch?.zoneName)
                    assertTrue(reopened.model().items.any { it.id == "event:outcome" })
                }
                // Repeating starts a new cycle and must not copy historical placement.
                reopened.submit("repeat-batch")
                reopened.submit("save-batch")
                val repeated = reopened.database.snapshot().batches.single { candidate -> batches.none { it.id == candidate.id } }
                assertEquals(Placement.None, repeated.closedPlacementSnapshot)
                assertEquals(BatchState.PLANNED, repeated.state)
            }
        }
    }

    @Test fun ac084_legacyClosedBatchWithoutClosureSnapshotStillProjectsEventZone() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                sow(f, "batch", "zone")
                f.save(ScreenId.CLOSE_BATCH, "batch", "save-outcome", mapOf("outcome" to "harvested"))
                val codec = DomainJsonCodec()
                val encoded = codec.encodeBatch(f.database.snapshot().batches.single())
                val legacyJson = JsonObject(Json.parseToJsonElement(encoded).jsonObject - "closedPlacementSnapshot").toString()
                val legacy = codec.decodeBatch(legacyJson)
                assertEquals(Placement.None, legacy.closedPlacementSnapshot)
                f.database.saveBatch(legacy)
                f.open(ScreenId.BATCH_DETAIL, "batch")
                assertEquals("Shelf", f.model().items.single { it.id == "batch" }.batch?.zoneName)
            }
        }
    }

    @Test fun ac093_crossBatchActionCollisionRejectsPreviewAndReplacementWithoutSideEffects() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                createBatch(f, "peer")
                f.pendingPhotos.stage("private-photo", byteArrayOf(8, 9, 10))
                f.save(ScreenId.OBSERVATION_EDITOR, "observation", "save-observation", mapOf(
                    "id" to "observation", "batchId" to "batch", "text" to "Retain photo",
                    "occurredAt" to f.now().toString(), "photoId" to pendingPhotoDraftValue("private-photo")))
                val before = f.backup.createArchive()
                val collision = before.snapshot.batches.first().actions.first().id
                val corruptBatches = before.snapshot.batches.mapIndexed { index, batch ->
                    if (index == 0) batch else batch.copy(actions = batch.actions.mapIndexed { actionIndex, action ->
                        if (actionIndex == 0) action.copy(id = collision) else action
                    })
                }
                assertTrue(corruptBatches.all { batch -> batch.actions.all { it.batchId == batch.id } })
                assertTrue(corruptBatches.all { batch -> batch.actions.map { it.id }.distinct().size == batch.actions.size })
                val corrupt = before.copy(snapshot = before.snapshot.copy(batches = corruptBatches))
                val invalid = assertIs<RestoreValidation.Invalid>(SnapshotValidation.validate(corrupt.snapshot))
                assertEquals(listOf("duplicate_action"), invalid.reasons)
                f.documents.write("imported-collision", LocalBackupArchiveCodec().encode(corrupt))
                f.selectedDocument = "imported-collision"
                f.open(ScreenId.BACKUP_RESTORE)
                f.submit("preview-restore")
                advanceUntilIdle()
                assertEquals(LoadState.ERROR, f.model().loadState)
                assertNotNull(f.model().errorKey)
                assertTrue(f.model().facts.none { it.id == "restore-photo-count" })
                assertNull(f.root.confirmation.value.child)
                assertFailsWith<LocalDataException> { f.backup.replaceFromDocument("imported-collision") }
                assertEquals(before, f.backup.createArchive())
                assertEquals(1, f.files.exportAll().size)
                assertTrue(f.reconciledSnapshots.isEmpty())
                assertNull(f.files.readRecoveryArchive())
            }
        }
    }

    private fun createBatch(f: PublicProductionFixture, id: String) {
        f.save(ScreenId.BATCH_EDITOR, id, "save-batch", mapOf(
            "id" to id, "cultureId" to "pea", "trayId" to "tray", "substrate" to "Coco",
            "actualSeedMassGrams" to "12.5", "plannedSowDate" to "2026-10-10"))
    }

    private fun sow(f: PublicProductionFixture, id: String, zone: String) {
        f.open(ScreenId.BATCH_DETAIL, id)
        f.edit(mapOf("zoneId" to zone, "unassigned" to "false"))
        f.submit("confirm-sowing")
        assertNotNull(f.model().stagePreview)
        f.dispatch(UiAction.ConfirmStagePreview)
        assertEquals(BatchState.SOWN, f.model().items.single { it.id == id }.batch?.state)
    }
}
