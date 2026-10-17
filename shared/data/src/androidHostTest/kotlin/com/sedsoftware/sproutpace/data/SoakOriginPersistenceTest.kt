package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleEngine
import com.sedsoftware.sproutpace.domain.model.*
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours

class SoakOriginPersistenceTest {
    @Test fun invalidAutomaticOriginMetadataIsRejectedBeforeEnteringDomain() {
        val codec = DomainJsonCodec()
        val payload = codec.encodeBatch(soakingBatch())
        assertFailsWith<IllegalArgumentException> {
            codec.decodeBatch(payload.replace("\"kind\":\"SOAK_TARGET\"", "\"kind\":\"GENERAL\""))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.decodeBatch(payload.replace("\"origin\":\"SOAK_DURATION\"", "\"origin\":\"UNKNOWN\""))
        }
    }

    @Test fun explicitRetargetAndSnoozeDropAutomaticIntentBeforeActualDateCorrection() = runTest {
        for (snooze in listOf(false, true)) {
            val factory = TestSqlDriverFactory()
            val database = LocalDatabase(factory, Dispatchers.Unconfined)
            try {
                val batch = soakingBatch()
                database.saveBatch(batch)
                val manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()))
                val explicitDue = CLOCK + 7.hours
                val action = batch.actions.single()
                val command = if (snooze) ScreenCommand("snooze", action.id.value,
                    mapOf("dueKind" to "elapsed", "dueAt" to explicitDue.toString()))
                else ScreenCommand("schedule-soak-target", batch.id.value,
                    mapOf("actionId" to action.id.value, "dueAt" to explicitDue.toString()))
                manager.execute(if (snooze) ScreenId.TODAY else ScreenId.BATCH_DETAIL, command).getOrThrow()
                val explicit = database.snapshot().batches.single().actions.single()
                assertEquals(ScheduledActionOrigin.UNSPECIFIED, explicit.origin)
                manager.execute(ScreenId.BATCH_EDITOR, ScreenCommand("correct-batch", batch.id.value,
                    mapOf("actualSoakingAt" to (CLOCK + 1.hours).toString()))).getOrThrow()
                assertEquals(explicit, database.snapshot().batches.single().actions.single())
            } finally {
                database.close()
                factory.close()
            }
        }
    }

    @Test fun sqliteAndArchiveRoundTripOriginWhileLegacyUnmarkedIntentStaysUnchanged() = runTest {
        val factory = TestSqlDriverFactory()
        val database = LocalDatabase(factory, Dispatchers.Unconfined)
        try {
            val batch = soakingBatch()
            database.saveBatch(batch)
            val snapshot = database.snapshot()
            assertEquals(batch, snapshot.batches.single())
            val archive = LocalBackupArchive(snapshot, emptyList())
            val archiveCodec = LocalBackupArchiveCodec()
            assertEquals(archive, archiveCodec.decode(archiveCodec.encode(archive)))
            val codec = DomainJsonCodec()
            val payload = Json.parseToJsonElement(codec.encodeBatch(batch)).jsonObject
            val legacy = JsonObject(payload + ("actions" to JsonArray(payload.getValue("actions").jsonArray.map {
                JsonObject(it.jsonObject - "origin")
            })))
            val decoded = codec.decodeBatch(legacy.toString())
            assertEquals(ScheduledActionOrigin.UNSPECIFIED, decoded.actions.single().origin)
            assertEquals(decoded.actions, ActionScheduleEngine.rebuild(decoded,
                ActionScheduleCorrection(BatchState.SOAKING, CLOCK + 1.hours)).actions)
        } finally {
            database.close()
            factory.close()
        }
    }

    private fun soakingBatch(): GrowBatch {
        val culture = SproutPaceCatalog.cultures.first { it.id.value == "pea" }
        val id = BatchId("soak-batch")
        return GrowBatch(id, RecipeSnapshot(null, culture.id, null,
            culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
            TraySnapshot("Tray", TrayShape.Rectangle(20.0, 30.0)), "Coco", PositiveGrams(10.0), PositiveGrams(10.0),
            BatchState.SOAKING, Placement.None,
            listOf(StageEvent(StageEventId("soak-start"), BatchState.PLANNED, BatchState.SOAKING, CLOCK,
                StageEventKind.CONFIRMED, placementBefore = Placement.None, placementAfter = Placement.None)),
            listOf(ScheduledAction(ScheduledActionId("soak-target"), id, null, DueMoment.ElapsedTarget(CLOCK + 6.hours),
                kind = ScheduledActionKind.SOAK_TARGET, origin = ScheduledActionOrigin.SOAK_DURATION)))
    }

    private companion object {
        val CLOCK = Instant.parse("2026-10-17T09:00:00Z")
    }
}
