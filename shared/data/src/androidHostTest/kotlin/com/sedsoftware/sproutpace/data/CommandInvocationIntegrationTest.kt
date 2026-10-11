package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.domain.model.BatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommandInvocationIntegrationTest {
    @Test
    fun pauseResumePauseResumeIsNotSuppressedByAnEarlierInvocation() = runTest {
        withManager { manager, database ->
            manager.execute(ScreenId.PLAN, prepared(ScreenId.PLAN, "save-plan", "plan", mapOf(
                "cadenceDays" to "3", "cultureIds" to "pea", "mode" to "continuous_queue",
            ))).getOrThrow()
            repeat(2) {
                manager.execute(ScreenId.PLAN, prepared(ScreenId.PLAN, "pause-plan", "plan")).getOrThrow()
                assertEquals(true, database.snapshot().plans.single().paused)
                manager.execute(ScreenId.PLAN, prepared(ScreenId.PLAN, "resume-plan", "plan")).getOrThrow()
                assertEquals(false, database.snapshot().plans.single().paused)
            }
        }
    }

    @Test
    fun newEditsCanReturnToEarlierValuesWhileSameInvocationRemainsIdempotent() = runTest {
        withManager { manager, database ->
            listOf("A", "B", "A").forEach { name ->
                val command = prepared(ScreenId.TRAY_SIZES, "save-tray-size", "tray", mapOf(
                    "name" to name, "shape" to "rectangle", "widthCm" to "25.4", "lengthCm" to "50.8",
                ))
                manager.execute(ScreenId.TRAY_SIZES, command).getOrThrow()
                manager.execute(ScreenId.TRAY_SIZES, command).getOrThrow()
                assertEquals(name, database.snapshot().trayPresets.single().name)
            }
        }
    }

    @Test
    fun transitionUndoTransitionUndoUsesNewInvocationsButRepeatedCallbacksDoNotMutate() = runTest {
        withManager { manager, database ->
            manager.execute(ScreenId.TRAY_SIZES, prepared(ScreenId.TRAY_SIZES, "save-tray-size", "tray", mapOf(
                "name" to "Tray", "shape" to "rectangle", "widthCm" to "25.4", "lengthCm" to "50.8",
            ))).getOrThrow()
            val save = prepared(ScreenId.BATCH_EDITOR, "save-batch", "batch", mapOf(
                "cultureId" to "pea", "trayId" to "tray", "substrate" to "Coco",
                "plannedSowDate" to "2026-10-10", "timeZoneId" to "UTC",
            ))
            manager.execute(ScreenId.BATCH_EDITOR, save).getOrThrow()
            manager.execute(ScreenId.BATCH_EDITOR, save).getOrThrow()
            assertEquals(1, database.snapshot().batches.size)
            repeat(2) {
                val transition = prepared(ScreenId.BATCH_DETAIL, "start-soaking", "batch", mapOf(
                    "eventId" to "same-event", "occurredAt" to "2026-10-10T09:00:00Z",
                ))
                manager.execute(ScreenId.BATCH_DETAIL, transition).getOrThrow()
                val afterTransition = database.snapshot().batches.single()
                assertEquals(BatchState.SOAKING, afterTransition.state)
                manager.execute(ScreenId.BATCH_DETAIL, transition).getOrThrow()
                assertEquals(afterTransition, database.snapshot().batches.single())
                val undo = prepared(ScreenId.BATCH_DETAIL, "undo-stage", "batch")
                manager.execute(ScreenId.BATCH_DETAIL, undo).getOrThrow()
                val afterUndo = database.snapshot().batches.single()
                assertEquals(BatchState.PLANNED, afterUndo.state)
                manager.execute(ScreenId.BATCH_DETAIL, undo).getOrThrow()
                assertEquals(afterUndo, database.snapshot().batches.single())
            }
        }
    }

    @Test
    fun preparationSeparatesInvocationFromPayloadAndCopyKeepsInvocation() {
        val first = prepared(ScreenId.SETTINGS, "set-theme", null, mapOf("theme" to "dark"))
        val second = prepared(ScreenId.SETTINGS, "set-theme", null, mapOf("theme" to "dark"))
        assertEquals(first.deduplicationKey, second.deduplicationKey)
        assertNotEquals(first.executionKey, second.executionKey)
        assertEquals(first.executionKey, first.copy().executionKey)
    }

    @Test
    fun reusingInvocationWithDifferentPayloadIsRejectedWithoutMutation() = runTest {
        withManager { manager, database ->
            val command = prepared(ScreenId.TRAY_SIZES, "save-tray-size", "tray", mapOf(
                "name" to "Original", "shape" to "rectangle", "widthCm" to "25.4", "lengthCm" to "50.8",
            ))
            manager.execute(ScreenId.TRAY_SIZES, command).getOrThrow()
            val before = database.snapshot()
            assertTrue(manager.execute(ScreenId.TRAY_SIZES,
                command.copy(arguments = command.arguments + ("name" to "Changed"))).isFailure)
            assertEquals(before, database.snapshot())
            manager.execute(ScreenId.TRAY_SIZES, command.copy(deduplicationKey = "retry-metadata")).getOrThrow()
            assertEquals(before, database.snapshot())
        }
    }

    private fun prepared(
        screen: ScreenId, operation: String, subject: String?, args: Map<String, String> = emptyMap(),
    ): ScreenCommand =
        assertIs<CommandPreparation.Ready>(ScreenContractRegistry.prepareCommand(
            screen, ScreenActionId(operation), subject, args.mapKeys { ScreenFieldId(it.key) },
        )).command

    private suspend fun withManager(block: suspend (ProductionScreenDataManager, LocalDatabase) -> Unit) {
        val factory = TestSqlDriverFactory()
        val database = LocalDatabase(factory, Dispatchers.Unconfined)
        try {
            block(ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings())), database)
        } finally {
            database.close()
            factory.close()
        }
    }
}
