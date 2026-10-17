package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.DestructiveOperation
import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.PlanEntryStatus
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.*
import com.sedsoftware.sproutpace.platform.EpochMillisClock
import com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService
import com.sedsoftware.sproutpace.platform.FakeReminderDriver
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.platform.ReminderLifecycleCoordinator
import com.sedsoftware.sproutpace.platform.ReminderReconciler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.util.Base64
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

/** Public edits, confirmations and navigation reach actual SQLite/photo/reminder production code. */
class ProductionPlanningActionsTest {
    @Test fun ac032_publicCorrectionPreservesPlannedScheduleAndCorrectsActualHistoryAcrossReopen() = runTest {
        withPublicWorkspace { directory, prefs ->
            lateinit var corrected: GrowBatch
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                val planned = fixture.batch()
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                fixture.submit("edit-batch")
                assertEquals(ScreenId.BATCH_EDITOR, fixture.model().screen)
                fixture.edit(mapOf("actualSeedMassGrams" to "15.5"))
                fixture.submit("correct-batch", confirm = false)
                assertEquals(DestructiveOperation.SAVE_BATCH_CORRECTION,
                    fixture.root.confirmation.value.child?.configuration?.destructiveOperation)
                assertEquals(planned, fixture.batch())
                fixture.root.confirmation.value.child!!.instance.confirm()
                assertEquals(15.5, fixture.batch().actualSeedMass.value)
                assertEquals(planned.actions, fixture.batch().actions)
                fixture.stage("batch", "confirm-sowing", "2026-10-10T10:00:00Z")
                val active = fixture.batch()
                fixture.submit("edit-batch")
                fixture.edit(mapOf("actualSownAt" to "2026-10-10T12:00:00Z"))
                fixture.submit("correct-batch")
                corrected = fixture.batch()
                assertEquals(active.events.single().id, corrected.events.single().id)
                assertEquals(listOf(active.events.single().occurredAt), corrected.events.single().priorOccurredAt)
                assertEquals(Instant.parse("2026-10-10T12:00:00Z"), corrected.events.single().occurredAt)
                val oldLight = active.actions.single { it.stage == GrowStage.LIGHT_GROWTH }
                val newLight = corrected.actions.single { it.stage == GrowStage.LIGHT_GROWTH }
                assertEquals(SchedulingEngine.resolvedInstant(oldLight) + 2.hours, SchedulingEngine.resolvedInstant(newLight))
                assertEquals(active.recipe, corrected.recipe)
                assertNull(fixture.model().errorKey)
            }
            PublicProductionFixture(directory, prefs).use { fixture ->
                assertEquals(corrected, fixture.batch())
                fixture.open(ScreenId.BATCH_EDITOR, "batch")
                assertEquals("2026-10-10T12:00:00Z", fixture.field("actualSownAt"))
                assertEquals("15.5", fixture.field("actualSeedMassGrams"))
            }
        }
    }

    @Test fun ac033_034_publicDeletionConfirmsRealityAndRemovesOwnedRowsAndPhotosAfterReopen() = runTest {
        for (active in listOf(false, true)) withPublicWorkspace { directory, prefs ->
            var photo: PhotoId? = null
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                if (active) {
                    fixture.stage("batch", "confirm-sowing", "2026-10-10T10:00:00Z")
                    fixture.pendingPhotos.stage("photo-to-delete", PHOTO)
                    fixture.save(ScreenId.OBSERVATION_EDITOR, "batch", "save-observation", mapOf(
                        "text" to "Private observation", "photoId" to pendingPhotoDraftValue("photo-to-delete")))
                    photo = fixture.database.snapshot().observations.single().photoId
                    assertContentEquals(PHOTO, fixture.files.read(assertNotNull(photo)))
                }
                val previous = fixture.database.snapshot()
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                fixture.submit("delete-batch", confirm = false)
                assertEquals(DestructiveOperation.DELETE_BATCH,
                    fixture.root.confirmation.value.child?.configuration?.destructiveOperation)
                assertEquals(previous, fixture.database.snapshot())
                fixture.root.dismissConfirmation()
                assertEquals(previous, fixture.database.snapshot())
                fixture.submit("delete-batch")
                assertTrue(fixture.database.snapshot().batches.isEmpty())
                assertTrue(fixture.database.snapshot().observations.isEmpty())
                photo?.let { assertNull(fixture.files.read(it)) }
                fixture.open(ScreenId.TRAYS)
                assertTrue(fixture.model().items.none { it.batch != null })
            }
            PublicProductionFixture(directory, prefs).use { reopened ->
                assertTrue(reopened.database.snapshot().batches.isEmpty())
                assertTrue(reopened.database.snapshot().observations.isEmpty())
                photo?.let { assertNull(reopened.files.read(it)) }
            }
        }
    }

    @Test fun ac035_037_038_039_soakActualOverflowStagePreviewAndUndoUsePublicLifecycleCommands() = runTest {
        withPublicWorkspace { directory, prefs ->
            lateinit var result: GrowBatch
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.ZONES, "zone", "update-zone", mapOf("capacity" to "1"))
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                fixture.edit(mapOf("occurredAt" to "2026-10-10T10:00:00Z"))
                fixture.submit("start-soaking")
                val soaking = fixture.batch()
                assertEquals(BatchState.SOAKING, soaking.state)
                assertEquals(Placement.None, soaking.placement)
                assertEquals(BatchState.SOAKING, soaking.events.single().to)
                val soakTarget = soaking.actions.single { it.kind == ScheduledActionKind.SOAK_TARGET }
                assertIs<DueMoment.ElapsedTarget>(soakTarget.due)
                assertTrue(SchedulingEngine.resolvedInstant(soakTarget) > soaking.events.single().occurredAt)
                fixture.createBatch("occupier", "radish")
                fixture.stage("occupier", "confirm-sowing", "2026-10-10T10:05:00Z")
                fixture.stage("batch", "confirm-sowing", "2026-10-10T10:10:00Z", unassigned = true)
                assertEquals(BatchState.SOWN, fixture.batch().state)
                assertIs<Placement.Unassigned>(fixture.batch().placement)
                assertTrue(TextKey("leave_unassigned") in fixture.model().conflictResolutionKeys)
                assertEquals(1, fixture.database.snapshot().batches.count { it.state.occupiesPlace && it.placement is Placement.Assigned })
                val sown = fixture.batch()
                fixture.edit(mapOf("occurredAt" to "2026-10-10T10:20:00Z", "zoneId" to ""))
                fixture.submit("confirm-stage", confirm = false)
                val preview = assertNotNull(fixture.model().stagePreview)
                assertEquals("blackout_or_weight", preview.command.arguments["nextState"])
                assertEquals(sown, fixture.batch())
                fixture.dispatch(UiAction.ConfirmStagePreview)
                assertEquals(BatchState.BLACKOUT_OR_WEIGHT, fixture.batch().state)
                assertEquals(3, fixture.batch().events.size)
                fixture.submit("undo-stage", confirm = false)
                assertEquals(DestructiveOperation.UNDO_LAST_TRANSITION,
                    fixture.root.confirmation.value.child?.configuration?.destructiveOperation)
                val confirmation = fixture.root.confirmation.value.child!!.instance
                confirmation.confirm()
                confirmation.confirm()
                result = fixture.batch()
                assertEquals(BatchState.SOWN, result.state)
                assertEquals(sown.events, result.events)
                assertEquals(sown.placement, result.placement)
                assertEquals(result.actions.size, result.actions.map { it.id }.distinct().size)
            }
            PublicProductionFixture(directory, prefs).use { fixture ->
                assertEquals(result, fixture.batch())
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                assertTrue(TextKey("leave_unassigned") in fixture.model().conflictResolutionKeys)
            }
        }
    }

    @Test fun ac051_052_053_054_055_056_cadenceAcceptEditPauseResumeDeletePreserveConfirmedBatches() = runTest {
        withPublicWorkspace { directory, prefs ->
            lateinit var confirmed: List<GrowBatch>
            lateinit var suggestionsBeforeRestart: List<ScreenItem>
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.ZONES, "zone", "update-zone", mapOf("capacity" to "1"))
                fixture.open(ScreenId.PLAN)
                fixture.edit(mapOf("id" to "public-plan", "cadenceDays" to "0"))
                fixture.submit("save-plan")
                assertTrue(fixture.model().validationErrors.keys.any { it.value == "cadenceDays" })
                assertTrue(fixture.database.snapshot().plans.isEmpty())
                fixture.edit(planFields("cadence"))
                fixture.submit("save-plan")
                val proposals = fixture.proposals()
                assertEquals(3, proposals.size)
                assertTrue(proposals.any { it.planEntry?.status == PlanEntryStatus.NEEDS_RESOLUTION })
                assertTrue(TextKey("choose_another_zone") in fixture.model().conflictResolutionKeys)
                val suggestion = proposals.first()
                val prior = fixture.database.snapshot()
                fixture.submit("confirm-suggestion", suggestion.id)
                assertEquals(ScreenId.BATCH_EDITOR, fixture.model().screen)
                assertEquals(prior, fixture.database.snapshot())
                assertEquals(suggestion.planEntry?.harvestDate.toString(), fixture.field("fixedHarvestDate"))
                fixture.edit(mapOf("id" to "accepted", "trayId" to "tray", "substrate" to "Coir"))
                fixture.submit("save-batch")
                confirmed = fixture.database.snapshot().batches
                assertEquals(2, confirmed.size)
                fixture.open(ScreenId.PLAN, "public-plan")
                suggestionsBeforeRestart = fixture.proposals()
            }
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.open(ScreenId.PLAN, "public-plan")
                assertEquals("1", fixture.field("cadenceDays"))
                assertEquals("radish,pea", fixture.field("cultureIds"))
                assertEquals("cadence", fixture.field("mode"))
                assertEquals(suggestionsBeforeRestart, fixture.proposals())
                fixture.edit(mapOf("cadenceDays" to "4", "cultureIds" to "pea,radish", "mode" to "continuous_queue"))
                fixture.submit("update-plan")
                assertEquals(confirmed, fixture.database.snapshot().batches)
                val next = fixture.proposals()
                assertEquals(1, next.size)
                assertEquals("pea", next.single().planEntry?.cultureId)
                fixture.submit("pause-plan")
                assertTrue(fixture.proposals().isEmpty())
                assertEquals(confirmed, fixture.database.snapshot().batches)
                fixture.submit("resume-plan")
                assertEquals(next, fixture.proposals())
                fixture.submit("delete-plan", confirm = false)
                assertEquals(1, fixture.database.snapshot().plans.size)
                fixture.root.confirmation.value.child!!.instance.confirm()
                assertTrue(fixture.database.snapshot().plans.isEmpty())
                assertTrue(fixture.proposals().isEmpty())
                assertEquals(confirmed, fixture.database.snapshot().batches)
            }
            PublicProductionFixture(directory, prefs).use { fixture ->
                assertTrue(fixture.database.snapshot().plans.isEmpty())
                assertEquals(confirmed, fixture.database.snapshot().batches)
            }
        }
    }

    @Test fun ac057_continuousQueueWaitsForAcceptedBatchThenRotatesFromActualClosureWithoutCreatingBatches() = runTest {
        withPublicWorkspace { directory, prefs ->
            lateinit var next: ScreenItem
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                fixture.save(ScreenId.PLAN, null, "save-plan", planFields("continuous_queue") + mapOf("cadenceDays" to "5"))
                val first = fixture.proposals().single()
                assertEquals("radish", first.planEntry?.cultureId)
                assertEquals(LocalDate(2026, 11, 4), first.planEntry?.harvestDate)
                fixture.submit("confirm-suggestion", first.id)
                assertEquals(1, fixture.database.snapshot().batches.size)
                fixture.edit(mapOf("id" to "queue-accepted", "trayId" to "tray", "substrate" to "Coir"))
                fixture.submit("save-batch")
                fixture.open(ScreenId.PLAN, "public-plan")
                assertTrue(fixture.proposals().isEmpty())
                fixture.save(ScreenId.CLOSE_BATCH, "queue-accepted", "save-outcome", mapOf(
                    "outcome" to "failed", "occurredAt" to "2026-11-06T10:00:00Z", "reason" to "Did not germinate"))
                fixture.open(ScreenId.PLAN, "public-plan")
                next = fixture.proposals().single()
                assertEquals("pea", next.planEntry?.cultureId)
                assertEquals(LocalDate(2026, 11, 11), next.planEntry?.harvestDate)
                assertEquals(2, fixture.database.snapshot().batches.size)
                assertEquals(BatchState.FAILED, fixture.batch("queue-accepted").state)
            }
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.open(ScreenId.PLAN, "public-plan")
                assertEquals(next, fixture.proposals().single())
                assertEquals(2, fixture.database.snapshot().batches.size)
            }
        }
    }

    @Test fun ac062_publicSnoozeChangesOnlyTheSelectedStableReminderAndSurvivesReopen() = runTest {
        withPublicWorkspace { directory, prefs ->
            lateinit var result: GrowBatch
            PublicProductionFixture(directory, prefs).use { fixture ->
                fixture.seed()
                fixture.open(ScreenId.BATCH_DETAIL, "batch")
                fixture.submit("start-soaking")
                val initial = fixture.batch()
                fixture.open(ScreenId.TODAY)
                val row = fixture.model().items.single { it.task?.actionId == "soak:batch" }
                val due = Instant.parse("2026-10-11T11:30:00Z")
                fixture.dispatch(UiAction.SubmitItem("snooze", assertNotNull(row.task).actionId,
                    mapOf("dueKind" to "elapsed", "dueAt" to due.toString())))
                result = fixture.batch()
                assertEquals(initial.state, result.state)
                assertEquals(initial.events, result.events)
                assertEquals(initial.actions.size, result.actions.size)
                assertEquals(DueMoment.ElapsedTarget(due), result.actions.single { it.id.value == "soak:batch" }.due)
                assertEquals(initial.actions.filterNot { it.id.value == "soak:batch" },
                    result.actions.filterNot { it.id.value == "soak:batch" })
                assertEquals(due, fixture.model().items.single { it.task?.actionId == "soak:batch" }.task?.dueAt)
            }
            PublicProductionFixture(directory, prefs).use { fixture ->
                assertEquals(result, fixture.batch())
                fixture.open(ScreenId.TODAY)
                assertEquals(1, fixture.model().items.count { it.task?.actionId == "soak:batch" })
            }
        }
    }

    @Test fun ac064_065_publicPreferencesPermissionRecoveryAndSummaryDeliveryUseCurrentProductionWork() = runTest {
        withPublicWorkspace { directory, prefs ->
            var now = Instant.parse("2026-10-10T08:59:00Z")
            val permission = FakeNotificationPermissionService(com.sedsoftware.sproutpace.platform.PermissionState.Denied(false))
            PublicProductionFixture(directory, prefs, now = { now }, decorateManagerWithSettings = { manager, settings ->
                ReminderAwareScreenDataManager(manager, settings, permission, {})
            }).use { fixture ->
                fixture.seed()
                fixture.open(ScreenId.NOTIFICATIONS)
                fixture.edit(mapOf("dailySummaryEnabled" to "true", "soakRemindersEnabled" to "false", "summaryTime" to "09:00"))
                assertTrue(fixture.settings.snapshot().notificationsEnabled)
                assertEquals(PermissionState.PERMANENTLY_DENIED, fixture.model().permissionState)
                fixture.submit("open-system-settings")
                assertEquals(PlatformActionKind.OPEN_SYSTEM_SETTINGS, fixture.platformRequests.single().kind)
                fixture.open(ScreenId.TODAY)
                assertEquals(PermissionState.PERMANENTLY_DENIED, fixture.model().permissionState)
                assertTrue(fixture.model().items.any { it.task?.pending == true })
                val source = SnapshotReminderPlanSource({ fixture.database.snapshot() }, fixture.settings,
                    { now.toEpochMilliseconds() }, { TimeZone.UTC })
                val summary = assertNotNull(source.currentPlan().dailySummary)
                permission.setSystemState(com.sedsoftware.sproutpace.platform.PermissionState.Granted)
                val driver = FakeReminderDriver()
                val coordinator = ReminderLifecycleCoordinator(permission, source, ReminderReconciler(driver),
                    EpochMillisClock { now.toEpochMilliseconds() })
                coordinator.reconcile(ReconciliationReason.PERMISSION_RESTORED)
                coordinator.reconcile(ReconciliationReason.APP_START)
                assertEquals(setOf(summary.id), driver.registeredIds())
                now = Instant.fromEpochMilliseconds(summary.fireAtEpochMillis)
                assertTrue(coordinator.onDelivery { source.shouldDeliver(summary.id, summary.fireAtEpochMillis) })
                fixture.save(ScreenId.CLOSE_BATCH, "batch", "save-outcome", mapOf("outcome" to "abandoned"))
                assertFalse(coordinator.onDelivery { source.shouldDeliver(summary.id, summary.fireAtEpochMillis) })
                assertNull(source.currentPlan().dailySummary)
                coordinator.reconcile(ReconciliationReason.PRODUCT_DATA_CHANGED)
                assertTrue(driver.registeredIds().isEmpty())
                assertEquals(PhysicalReminderId("sproutpace:daily-summary"), summary.id)
            }
        }
    }

    private suspend fun PublicProductionFixture.batch(id: String = "batch") = database.snapshot().batches.single { it.id.value == id }
    private fun PublicProductionFixture.proposals() = model().items.filter { it.actionId == "confirm-suggestion" }
    private fun planFields(mode: String) = mapOf("id" to "public-plan", "anchorDate" to "2026-10-30",
        "cadenceDays" to "1", "cultureIds" to "radish,pea", "mode" to mode)

    private fun PublicProductionFixture.createBatch(id: String, culture: String) = save(ScreenId.BATCH_EDITOR, null,
        "save-batch", mapOf("id" to id, "cultureId" to culture, "trayId" to "tray", "substrate" to "Coir",
            "plannedSowDate" to "2026-10-10"))

    private fun PublicProductionFixture.stage(id: String, operation: String, at: String, unassigned: Boolean = false) {
        open(ScreenId.BATCH_DETAIL, id)
        edit(mapOf("occurredAt" to at, "zoneId" to "zone", "unassigned" to unassigned.toString()))
        submit(operation, confirm = false)
        assertNotNull(model().stagePreview)
        dispatch(UiAction.ConfirmStagePreview)
        assertNull(model().errorKey)
    }

    private companion object {
        val PHOTO: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    }
}
