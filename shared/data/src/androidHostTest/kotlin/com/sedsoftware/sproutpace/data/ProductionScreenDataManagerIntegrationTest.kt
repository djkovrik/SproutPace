package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.REMOVE_PHOTO_VALUE
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductionScreenDataManagerIntegrationTest {
    @Test
    fun advertisingPrivacySettingsEntryRequiresFreshApplicablePayload() = runTest {
        fun valid(choice: ConsentChoice = ConsentChoice.ACCEPTED) = AdvertisingPrivacyState(
            policyVersion = "policy-7",
            fetchedAtEpochMillis = Instant.parse(NOW).toEpochMilliseconds() - 1_000,
            expiresAtEpochMillis = Instant.parse(NOW).toEpochMilliseconds() + 1_000,
            choice = choice,
        )
        suspend fun ids() = manager.watch(ScreenId.SETTINGS).first().items.map { it.id }

        assertFalse("privacy" in ids())
        settings.setPrivacyState(valid())
        assertTrue("privacy" in ids())
        assertEquals(
            "open-advertising-privacy",
            manager.watch(ScreenId.SETTINGS).first().items.single { it.id == "privacy" }.actionId,
        )
        settings.setPrivacyState(valid(ConsentChoice.WITHDRAWN))
        assertTrue("privacy" in ids())
        settings.setPrivacyState(valid().copy(expiresAtEpochMillis = Instant.parse(NOW).toEpochMilliseconds()))
        assertFalse("privacy" in ids())
        settings.setPrivacyState(valid().copy(consentRequired = false))
        assertFalse("privacy" in ids())
    }

    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var settings: MultiplatformProductSettings
    private lateinit var manager: ProductionScreenDataManager
    private lateinit var files: InMemoryPrivateFileStore
    private lateinit var pendingPhotos: InMemoryPendingPhotoImportStore
    private lateinit var documents: ManagerDocuments

    @BeforeTest
    fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        settings = MultiplatformProductSettings(MapSettings())
        files = InMemoryPrivateFileStore()
        pendingPhotos = InMemoryPendingPhotoImportStore()
        documents = ManagerDocuments()
        val workspace = LocalWorkspaceRepository(database, files, Dispatchers.Unconfined)
        val backup = LocalBackupService(
            database, files, settings, Dispatchers.Unconfined, documents = documents,
            now = { Instant.parse(NOW) },
        )
        manager = ProductionScreenDataManager(
            database = database,
            settings = settings,
            workspaceRepository = workspace,
            pendingPhotos = pendingPhotos,
            backupService = backup,
            now = { Instant.parse(NOW) },
            today = { LocalDate.parse(TODAY) },
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
        files.close()
        factory.close()
    }

    @Test
    fun ac010Ac011Ac012Ac014_setupPersistsAndExposesCapacityConsequences() = runTest {
        saveZone(capacity = 1)
        saveTray()
        saveRecipe()
        saveBatch()
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("confirm-sowing", "batch-1", mapOf("eventId" to "event-sown", "occurredAt" to "2026-09-20T09:00:00Z", "zoneId" to "zone-1")),
        )

        val zones = manager.watch(ScreenId.ZONES).first()
        val trays = manager.watch(ScreenId.TRAY_SIZES).first()
        assertEquals("zone-1", zones.items.single().id)
        assertEquals("Grow shelf", zones.items.single().authoredTitle)
        assertEquals("tray-1", trays.items.single().id)

        execute(
            ScreenId.ZONES,
            ScreenCommand(
                "update-zone", "zone-1",
                mapOf("name" to "Renamed shelf", "type" to "grow_light", "capacity" to "0"),
            ),
        )
        assertEquals(0, database.snapshot().zones.single().capacity)
        assertEquals("Renamed shelf", database.snapshot().zones.single().name)
        assertEquals("capacity_conflict_title", manager.watch(ScreenId.ZONES).first().items.single().stateKey?.value)
    }

    @Test
    fun ac010Ac014Ac050_firstRunSetupIsAtomicIdempotentAndImmediatelyFeasible() = runTest {
        saveZone()
        saveTray()
        savePlan()
        val firstRun = manager.watch(ScreenId.FIRST_RUN).first()
        assertEquals("Grow shelf", firstRun.field("zoneName"))
        assertEquals("grow_light", firstRun.field("type"))
        assertEquals("1", firstRun.field("capacity"))
        assertEquals("10 x 20 tray", firstRun.field("trayName"))
        assertEquals("rectangle", firstRun.field("shape"))
        assertEquals("25.4", firstRun.field("widthCm"))
        assertEquals("50.8", firstRun.field("lengthCm"))
        assertEquals("7", firstRun.field("cadenceDays"))
        assertEquals("radish", firstRun.field("cultureIds"))
        assertEquals("continuous_queue", firstRun.field("mode"))
        val command = firstRun.prepared(ScreenId.FIRST_RUN, "continue-setup")

        execute(ScreenId.FIRST_RUN, command)
        val afterFirst = database.snapshot()
        execute(ScreenId.FIRST_RUN, command.copy(deduplicationKey = "continue-setup:retry"))
        val afterRetry = database.snapshot()

        assertEquals(afterFirst, afterRetry)
        assertEquals(setOf("zone-1", "first-run-zone"), afterRetry.zones.mapTo(mutableSetOf()) { it.id.value })
        assertEquals(setOf("tray-1", "first-run-tray"), afterRetry.trayPresets.mapTo(mutableSetOf()) { it.id.value })
        assertEquals(setOf("plan-1", "first-run-plan"), afterRetry.plans.mapTo(mutableSetOf()) { it.id.value })
        val setupZone = afterRetry.zones.single { it.id.value == "first-run-zone" }
        assertEquals(GrowZoneType.GROW_LIGHT, setupZone.type)
        assertEquals(1, setupZone.capacity)
        val setupTray = afterRetry.trayPresets.single { it.id.value == "first-run-tray" }
        assertEquals(TrayShape.Rectangle(25.4, 50.8), setupTray.shape)
        val setupPlan = afterRetry.plans.single { it.id.value == "first-run-plan" }
        assertEquals(LocalDate.parse(TODAY), setupPlan.anchorDate)
        assertEquals(HarvestPlanMode.CONTINUOUS_QUEUE, setupPlan.mode)
        assertEquals(listOf("radish"), setupPlan.cultureRotation.map { it.value })
        assertTrue(afterRetry.batches.isEmpty())
        assertIs<RestoreValidation.Valid>(SnapshotValidation.validate(afterRetry))

        val plan = manager.watch(ScreenId.PLAN).first()
        assertTrue(plan.items.any { it.id.startsWith("first-run-plan:") })
        assertTrue(plan.conflictResolutionKeys.isEmpty())
        val today = manager.watch(ScreenId.TODAY).first()
        assertTrue(today.items.any { ":continuous:" in it.id })
    }

    @Test
    fun ac010Ac014Ac050_invalidFirstRunSetupNeverMutatesExistingData() = runTest {
        saveZone()
        val before = database.snapshot()
        val valid = manager.watch(ScreenId.FIRST_RUN).first()
            .prepared(ScreenId.FIRST_RUN, "continue-setup")
        val invalidCommands = listOf(
            valid.copy(
                arguments = valid.arguments + ("type" to "dark"),
                deduplicationKey = "continue-setup:dark",
            ),
            valid.copy(
                arguments = valid.arguments + ("widthCm" to "0"),
                deduplicationKey = "continue-setup:zero-width",
            ),
            valid.copy(
                arguments = valid.arguments + ("cultureIds" to "unknown"),
                deduplicationKey = "continue-setup:unknown-culture",
            ),
        )

        invalidCommands.forEach { command ->
            assertTrue(manager.execute(ScreenId.FIRST_RUN, command).isFailure)
            assertEquals(before, database.snapshot())
        }
    }

    @Test
    fun ac020Ac021Ac026Ac027_catalogRecipeAndEditableRecommendationRemainOffline() = runTest {
        saveTray()
        saveRecipe()
        saveBatch()

        val catalog = manager.watch(ScreenId.CATALOG).first()
        assertEquals(8, catalog.items.count { it.id in CULTURE_IDS })
        assertTrue(catalog.offline)
        assertEquals("My peas", catalog.items.first { it.id == "recipe-1" }.authoredTitle)

        val recommendation = manager.watch(ScreenId.BATCH_EDITOR, "batch-1").first()
        assertNotNull(recommendation.items.firstOrNull { it.id == "seed-grams" })

        val recipe = manager.watch(ScreenId.RECIPE, "pea").first()
        assertTrue(recipe.items.any { it.id.startsWith("safety:") })
        assertTrue(recipe.items.any { it.id.startsWith("source:") })
        assertTrue(recipe.offline)
    }

    @Test
    fun ac030Ac031Ac032_batchSnapshotSurvivesSourceChangesAndCorrection() = runTest {
        saveZone()
        saveTray()
        saveRecipe()
        saveBatch()
        val original = database.snapshot().batches.single()
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand(
                "schedule-soak-target", "batch-1",
                mapOf("actionId" to "corrected-action", "dueAt" to "2026-09-22T10:00:00Z"),
            ),
        )

        execute(
            ScreenId.RECIPE,
            ScreenCommand(
                "update-recipe", "recipe-1",
                mapOf("name" to "Changed recipe", "cultureId" to "pea", "substrate" to "mat"),
            ),
        )
        execute(
            ScreenId.BATCH_EDITOR,
            ScreenCommand(
                "correct-batch", "batch-1",
                mapOf("actualSeedMassGrams" to "15.5", "anchorState" to "planned", "anchorAt" to NOW),
                deduplicationKey = "correct-batch:batch-1:15.5",
            ),
        )

        val restored = database.snapshot().batches.single()
        assertEquals(original.recipe, restored.recipe)
        assertEquals(original.tray, restored.tray)
        assertEquals(15.5, restored.actualSeedMass.value)
        assertEquals(
            DueMoment.ElapsedTarget(Instant.parse(NOW)),
            restored.actions.single { it.id.value == "corrected-action" }.due,
        )
        assertEquals("batch-1", manager.watch(ScreenId.BATCH_DETAIL, "batch-1").first().selectedItemId)
    }

    @Test
    fun ac023_recipeUpdateAppliesOnlyToBatchesCreatedAfterTheUpdate() = runTest {
        saveTray()
        saveRecipe()
        saveBatch()
        val originalSnapshot = database.snapshot().batches.single().recipe

        execute(
            ScreenId.RECIPE,
            ScreenCommand(
                "update-recipe", "recipe-1",
                mapOf("name" to "Updated peas", "cultureId" to "pea", "substrate" to "hemp mat"),
            ),
        )
        execute(
            ScreenId.BATCH_EDITOR,
            ScreenCommand(
                "save-batch", "batch-2",
                mapOf(
                    "recipeId" to "recipe-1", "trayId" to "tray-1",
                    "actualSeedMassGrams" to "10.0", "plannedSowDate" to "2026-09-22",
                    "timeZoneId" to "Europe/Moscow",
                ),
            ),
        )

        val batches = database.snapshot().batches.associateBy { it.id.value }
        assertEquals(originalSnapshot, batches.getValue("batch-1").recipe)
        assertEquals("coco coir", batches.getValue("batch-1").substrateSnapshot)
        assertEquals("Updated peas", batches.getValue("batch-2").recipe.nameSnapshot)
        assertEquals("hemp mat", batches.getValue("batch-2").substrateSnapshot)
        assertEquals(originalSnapshot.stages, batches.getValue("batch-2").recipe.stages)
    }

    @Test
    fun ac021Ac022Ac030Ac032_authoredStagesAndCompleteBatchCorrectionsRoundTrip() = runTest {
        saveZone()
        saveTray()
        saveRecipe()

        val authored = database.snapshot().recipes.single()
        assertEquals(
            listOf(
                GrowStage.SOAKING, GrowStage.SOWN, GrowStage.BLACKOUT_OR_WEIGHT,
                GrowStage.LIGHT_GROWTH, GrowStage.READY_FOR_HARVEST,
            ),
            authored.stages.map { it.stage },
        )
        assertEquals("Rinse and soak", authored.stages.first().userGuidance)
        assertEquals("Local seeds", authored.supplier)
        assertEquals("Dwarf grey", authored.cultivar)
        assertEquals("LOT-42", authored.seedLot)
        assertEquals(94, authored.germinationPercentage?.value)

        saveBatch()
        val created = database.snapshot().batches.single()
        assertEquals(LocalDate.parse(TODAY), created.plannedSowDate)
        assertEquals("pea", created.cultureIdSnapshot?.value)
        assertEquals("Local seeds", created.supplier)
        assertEquals("Dwarf grey", created.cultivar)
        assertEquals("LOT-42", created.seedLot)
        assertEquals(94, created.germinationPercentage?.value)
        assertEquals(authored.stages, created.recipe.stages)
        assertEquals(authored.stages.map { it.stage }, created.actions.map { it.stage })
        val createdHarvest = created.actions.single { it.stage == GrowStage.READY_FOR_HARVEST }
        assertTrue(createdHarvest.userFixed)
        assertEquals(
            LocalDate.parse("2026-09-27"),
            (createdHarvest.due as DueMoment.CivilTime).localDateTime.date,
        )

        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand(
                "start-soaking", "batch-1",
                mapOf("eventId" to "event-soak", "occurredAt" to "2026-09-20T08:00:00Z"),
            ),
        )
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand(
                "confirm-sowing", "batch-1",
                mapOf(
                    "eventId" to "event-sown", "occurredAt" to "2026-09-20T09:00:00Z",
                    "zoneId" to "zone-1",
                ),
            ),
        )
        listOf(
            Triple("event-blackout", "2026-09-20T10:00:00Z", "blackout_or_weight"),
            Triple("event-light", "2026-09-20T11:00:00Z", "light_growth"),
            Triple("event-ready", "2026-09-20T12:00:00Z", "ready_for_harvest"),
        ).forEach { (eventId, occurredAt, nextState) ->
            execute(
                ScreenId.BATCH_DETAIL,
                ScreenCommand(
                    "confirm-stage", "batch-1",
                    mapOf("eventId" to eventId, "occurredAt" to occurredAt, "nextState" to nextState),
                    deduplicationKey = "confirm-stage:$eventId",
                ),
            )
        }
        execute(
            ScreenId.TRAY_SIZES,
            ScreenCommand(
                "save-tray-size", "tray-2",
                mapOf("name" to "5 x 10", "shape" to "rectangle", "widthCm" to "12.7", "lengthCm" to "25.4"),
            ),
        )
        val beforeCorrection = database.snapshot().batches.single()
        val priorLightDue = beforeCorrection.actions.single { it.stage == GrowStage.LIGHT_GROWTH }.due

        execute(
            ScreenId.BATCH_EDITOR,
            ScreenCommand(
                "correct-batch", "batch-1",
                mapOf(
                    "cultureId" to "radish", "trayId" to "tray-2", "substrate" to "hemp mat",
                    "actualSeedMassGrams" to "6.5", "supplier" to "New supplier", "cultivar" to "Ruby",
                    "seedLot" to "LOT-99", "germinationPercentage" to "88",
                    "plannedSowDate" to "2026-09-21",
                    "fixedSoakingDate" to "2026-09-20", "fixedSownDate" to "2026-09-21",
                    "fixedBlackoutDate" to "2026-09-22", "fixedLightDate" to "2026-09-23",
                    "fixedHarvestDate" to "2026-09-28",
                    "timeZoneId" to "Europe/Berlin", "zoneId" to "zone-1",
                    "actualSoakingAt" to "2026-09-20T07:30:00Z",
                    "actualSownAt" to "2026-09-20T09:30:00Z",
                    "actualBlackoutAt" to "2026-09-20T10:30:00Z",
                    "actualLightAt" to "2026-09-20T11:30:00Z",
                    "actualHarvestReadyAt" to "2026-09-20T12:30:00Z",
                    "anchorState" to "sown", "anchorAt" to "2026-09-20T09:30:00Z",
                ),
            ),
        )

        val corrected = database.snapshot().batches.single()
        assertEquals("radish", corrected.cultureIdSnapshot?.value)
        assertEquals("5 x 10", corrected.tray.nameSnapshot)
        assertEquals("hemp mat", corrected.substrateSnapshot)
        assertEquals(6.5, corrected.actualSeedMass.value)
        assertEquals("New supplier", corrected.supplier)
        assertEquals("Ruby", corrected.cultivar)
        assertEquals("LOT-99", corrected.seedLot)
        assertEquals(88, corrected.germinationPercentage?.value)
        assertEquals(LocalDate.parse("2026-09-21"), corrected.plannedSowDate)
        assertEquals(
            listOf("event-soak", "event-sown", "event-blackout", "event-light", "event-ready"),
            corrected.events.map { it.id.value },
        )
        val expectedActualDates = mapOf(
            "event-soak" to ("2026-09-20T07:30:00Z" to "2026-09-20T08:00:00Z"),
            "event-sown" to ("2026-09-20T09:30:00Z" to "2026-09-20T09:00:00Z"),
            "event-blackout" to ("2026-09-20T10:30:00Z" to "2026-09-20T10:00:00Z"),
            "event-light" to ("2026-09-20T11:30:00Z" to "2026-09-20T11:00:00Z"),
            "event-ready" to ("2026-09-20T12:30:00Z" to "2026-09-20T12:00:00Z"),
        )
        expectedActualDates.forEach { (eventId, dates) ->
            val event = corrected.events.first { it.id.value == eventId }
            assertEquals(Instant.parse(dates.first), event.occurredAt, eventId)
            assertEquals(listOf(Instant.parse(dates.second)), event.priorOccurredAt, eventId)
        }
        val expectedFixedDates = mapOf(
            GrowStage.SOAKING to "2026-09-20",
            GrowStage.SOWN to "2026-09-21",
            GrowStage.BLACKOUT_OR_WEIGHT to "2026-09-22",
            GrowStage.LIGHT_GROWTH to "2026-09-23",
            GrowStage.READY_FOR_HARVEST to "2026-09-28",
        )
        expectedFixedDates.forEach { (stage, date) ->
            val action = corrected.actions.single {
                it.stage == stage && it.id.value.startsWith("planned:batch-1:")
            }
            assertEquals(LocalDate.parse(date), (action.due as DueMoment.CivilTime).localDateTime.date, stage.name)
            assertTrue(action.userFixed, stage.name)
        }
        assertTrue(corrected.actions.single { it.stage == GrowStage.LIGHT_GROWTH }.due != priorLightDue)
        assertEquals("2026-09-20T09:30:00Z", manager.watch(ScreenId.BATCH_EDITOR, "batch-1").first().field("actualSownAt"))
        assertEquals(
            "2026-09-20T12:30:00Z",
            manager.watch(ScreenId.BATCH_EDITOR, "batch-1").first().field("actualHarvestReadyAt"),
        )
    }

    @Test
    fun ac035Ac036Ac037Ac038Ac039Ac040_lifecycleUsesEnginesAndStableEvents() = runTest {
        seedBatchPrerequisites()
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("start-soaking", "batch-1", mapOf("eventId" to "event-soak", "occurredAt" to "2026-09-20T08:00:00Z")),
        )
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("confirm-sowing", "batch-1", mapOf("eventId" to "event-sown", "occurredAt" to "2026-09-20T09:00:00Z", "zoneId" to "zone-1")),
        )
        val stageCommand = ScreenCommand(
            "confirm-stage", "batch-1",
            mapOf("eventId" to "event-blackout", "occurredAt" to "2026-09-20T10:00:00Z", "nextState" to "blackout_or_weight"),
            deduplicationKey = "stage:event-blackout",
        )
        execute(ScreenId.BATCH_DETAIL, stageCommand)
        execute(ScreenId.BATCH_DETAIL, stageCommand)
        assertEquals(3, database.snapshot().batches.single().events.size)

        execute(ScreenId.BATCH_DETAIL, ScreenCommand("assign-overflow", "batch-1"))
        assertIs<Placement.Unassigned>(database.snapshot().batches.single().placement)
        assertTrue(manager.watch(ScreenId.BATCH_DETAIL, "batch-1").first().conflictResolutionKeys.isNotEmpty())

        execute(ScreenId.BATCH_DETAIL, ScreenCommand("undo-stage", "batch-1"))
        assertEquals(BatchState.SOWN, database.snapshot().batches.single().state)
        assertEquals(
            database.snapshot().batches.single().actions.map { it.id }.distinct(),
            database.snapshot().batches.single().actions.map { it.id },
        )
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand(
                "skip-stage", "batch-1",
                mapOf(
                    "eventId" to "event-skip", "occurredAt" to "2026-09-20T11:00:00Z",
                    "skippedState" to "blackout_or_weight", "resultingState" to "light_growth", "reason" to "Already uncovered",
                ),
            ),
        )
        assertEquals(BatchState.LIGHT_GROWTH, database.snapshot().batches.single().state)
        assertEquals("event-skip", database.snapshot().batches.single().events.last().id.value)
    }

    @Test
    fun ac050Ac051Ac052Ac054Ac055Ac057_plansArePersistedAndSuggestionsStayUnconfirmed() = runTest {
        saveZone(capacity = 0)
        savePlan(mode = "cadence")

        val initial = manager.watch(ScreenId.PLAN).first()
        assertTrue(initial.items.any { it.id == "plan-1" })
        assertTrue(initial.items.any { it.id.startsWith("plan-1:") })
        assertEquals(3, initial.conflictResolutionKeys.size)
        assertTrue(database.snapshot().batches.isEmpty())

        execute(
            ScreenId.PLAN,
            ScreenCommand(
                "update-plan", "plan-1",
                mapOf("cadenceDays" to "5", "cultureIds" to "pea,radish", "mode" to "continuous_queue"),
            ),
        )
        execute(ScreenId.PLAN, ScreenCommand("pause-plan", "plan-1"))
        assertTrue(database.snapshot().plans.single().paused)
        execute(ScreenId.PLAN, ScreenCommand("resume-plan", "plan-1"))
        val resumed = manager.watch(ScreenId.PLAN).first()
        assertFalse(database.snapshot().plans.single().paused)
        assertEquals(5, database.snapshot().plans.single().cadence.value)
        assertTrue(resumed.items.any { it.id == "plan-1:0" })
        assertTrue(
            manager.watch(ScreenId.TODAY).first().items.any { it.id.startsWith("plan-1:continuous:") },
        )

        execute(ScreenId.PLAN, ScreenCommand("confirm-suggestion", "plan-1:0"))
        assertTrue(database.snapshot().batches.isEmpty())
    }

    @Test
    fun ac060Ac062Ac066Ac068_todaySnoozeAndTimeZoneChangesPreserveStableActions() = runTest {
        seedBatchPrerequisites()
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("schedule-soak-target", "batch-1", mapOf("dueAt" to "2026-09-20T10:30:00Z")),
        )
        val scheduled = database.snapshot().batches.single().actions.single { it.id.value == "soak:batch-1" }
        assertEquals("soak:batch-1", scheduled.id.value)
        assertTrue(
            manager.watch(ScreenId.TODAY).first().items.any { item ->
                item.commands.any { it.subjectId == scheduled.id.value }
            },
        )

        execute(
            ScreenId.TODAY,
            ScreenCommand(
                "snooze", scheduled.id.value,
                mapOf("dueKind" to "elapsed", "dueAt" to "2026-09-20T11:00:00Z"),
            ),
        )
        val snoozed = database.snapshot().batches.single().actions.single { it.id.value == "soak:batch-1" }
        assertEquals(scheduled.id, snoozed.id)
        assertEquals(Instant.parse("2026-09-20T11:00:00Z"), (snoozed.due as DueMoment.ElapsedTarget).instant)

        val batch = database.snapshot().batches.single()
        val civil = ScheduledAction(
            ScheduledActionId("civil:batch-1"), batch.id, GrowStage.SOWN,
            DueMoment.CivilTime(LocalDateTime.parse("2026-09-21T09:00:00"), "Europe/Moscow"),
        )
        database.saveBatch(batch.copy(actions = batch.actions + civil))
        execute(
            ScreenId.TODAY,
            ScreenCommand("time-zone-change", arguments = mapOf("timeZoneId" to "Europe/Berlin"), deduplicationKey = "timezone:berlin"),
        )
        val actions = database.snapshot().batches.single().actions.associateBy { it.id.value }
        assertIs<DueMoment.ElapsedTarget>(actions.getValue("soak:batch-1").due)
        assertEquals("Europe/Berlin", (actions.getValue("civil:batch-1").due as DueMoment.CivilTime).timeZoneId)
    }

    @Test
    fun ac061Ac063_completeAndSkipUseExplicitActionTargetsAndKeepLaterBatchWork() = runTest {
        seedBatchPrerequisites()
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("schedule-soak-target", "batch-1", mapOf("dueAt" to "2026-09-20T10:30:00Z")),
        )
        val batch = database.snapshot().batches.single()
        val second = ScheduledAction(
            ScheduledActionId("sow:batch-1"), batch.id, GrowStage.SOWN,
            DueMoment.ElapsedTarget(Instant.parse("2026-09-20T11:00:00Z")),
        )
        val later = ScheduledAction(
            ScheduledActionId("light:batch-1"), batch.id, GrowStage.LIGHT_GROWTH,
            DueMoment.ElapsedTarget(Instant.parse("2026-09-21T10:00:00Z")),
        )
        database.saveBatch(batch.copy(actions = batch.actions + second + later))

        val today = manager.watch(ScreenId.TODAY).first()
        val firstItem = today.items.first { item ->
            item.commands.any { it.subjectId == "soak:batch-1" }
        }
        assertTrue(firstItem.id != "soak:batch-1")
        assertEquals(
            setOf("complete-action", "skip-action"),
            firstItem.commands.mapTo(mutableSetOf()) { it.operation },
        )
        assertTrue(firstItem.commands.all { it.subjectId == "soak:batch-1" })

        execute(ScreenId.TODAY, ScreenCommand("complete-action", "soak:batch-1"))
        val completedOnce = database.snapshot()
        execute(
            ScreenId.TODAY,
            ScreenCommand(
                "complete-action", "soak:batch-1",
                deduplicationKey = "complete-action:soak:batch-1:retry",
            ),
        )
        assertEquals(completedOnce, database.snapshot())
        assertEquals(
            ScheduledActionStatus.COMPLETED,
            database.snapshot().batches.single().actions.first { it.id.value == "soak:batch-1" }.status,
        )

        execute(ScreenId.TODAY, ScreenCommand("skip-action", "sow:batch-1"))
        val persisted = database.snapshot()
        assertEquals(1, persisted.batches.size)
        assertEquals(
            ScheduledActionStatus.SKIPPED,
            persisted.batches.single().actions.first { it.id.value == "sow:batch-1" }.status,
        )
        assertEquals(
            ScheduledActionStatus.PENDING,
            persisted.batches.single().actions.first { it.id.value == "light:batch-1" }.status,
        )
        val detail = manager.watch(ScreenId.BATCH_DETAIL, "batch-1").first()
        assertTrue(detail.items.any { item -> item.commands.any { it.subjectId == "light:batch-1" } })
        assertTrue(detail.items.none { item -> item.commands.any { it.subjectId == "sow:batch-1" } })
    }

    @Test
    fun ac080Ac081Ac085_terminalOutcomesReleaseCapacityAndStoreOnlyActualMass() = runTest {
        seedBatchPrerequisites()
        execute(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("confirm-sowing", "batch-1", mapOf("eventId" to "event-sown", "occurredAt" to "2026-09-20T09:00:00Z", "zoneId" to "zone-1")),
        )
        execute(
            ScreenId.CLOSE_BATCH,
            ScreenCommand(
                "close-harvest", "batch-1",
                mapOf("occurredAt" to "2026-09-20T12:00:00Z", "actualMassGrams" to "42.5"),
            ),
        )
        val harvested = database.snapshot().batches.single()
        assertEquals(BatchState.HARVESTED, harvested.state)
        assertEquals(Placement.None, harvested.placement)
        assertEquals(42.5, (harvested.outcome as com.sedsoftware.sproutpace.domain.model.BatchOutcome.Harvested).actualMass?.value)

        val failed = harvested.copy(
            id = BatchId("batch-2"), state = BatchState.SOWN,
            placement = Placement.Assigned(com.sedsoftware.sproutpace.domain.model.GrowZoneId("zone-1"), "Grow shelf"),
            outcome = null,
        )
        database.saveBatch(failed)
        execute(
            ScreenId.CLOSE_BATCH,
            ScreenCommand("close-failed", "batch-2", mapOf("occurredAt" to "2026-09-20T12:30:00Z", "reason" to "mold")),
        )
        assertEquals(BatchState.FAILED, database.snapshot().batches.first { it.id.value == "batch-2" }.state)
    }

    @Test
    fun allSixteenWatchesAreDeterministicAndLocaleNeutral() = runTest {
        seedBatchPrerequisites()
        savePlan()
        ScreenId.entries.forEach { screen ->
            val stableId = when (screen) {
                ScreenId.BATCH_DETAIL, ScreenId.BATCH_EDITOR, ScreenId.CLOSE_BATCH, ScreenId.OBSERVATION_EDITOR -> "batch-1"
                ScreenId.RECIPE -> "pea"
                ScreenId.PLAN -> "plan-1"
                else -> null
            }
            val first = manager.watch(screen, stableId).first()
            val second = manager.watch(screen, stableId).first()
            assertEquals(first, second, screen.name)
            assertTrue(first.items.all { it.titleKey != null || !it.authoredTitle.isNullOrBlank() }, screen.name)
            val contract = ScreenContractRegistry.contracts.getValue(screen)
            assertEquals(contract.fields.map { it.id }, first.fields.map { it.id }, "${screen.name} fields")
            assertTrue(first.facts.map { it.id }.containsAll(contract.facts.map { it.id }), "${screen.name} facts")
            assertEquals(contract.actions, first.actions, "${screen.name} actions")
        }
    }

    @Test
    fun typedSnapshotsPreserveAuthoredValuesAndPrepareAcceptedCommands() = runTest {
        seedBatchPrerequisites()
        savePlan()
        execute(
            ScreenId.OBSERVATION_EDITOR,
            ScreenCommand(
                "save-observation", "observation-1",
                mapOf("batchId" to "batch-1", "occurredAt" to NOW, "text" to "Рост хороший"),
            ),
        )

        val zone = manager.watch(ScreenId.ZONES, "zone-1").first()
        assertEquals("Grow shelf", zone.field("name"))
        execute(ScreenId.ZONES, zone.prepared(ScreenId.ZONES, "update-zone"))

        val tray = manager.watch(ScreenId.TRAY_SIZES, "tray-1").first()
        assertEquals("10 x 20", tray.field("name"))
        execute(ScreenId.TRAY_SIZES, tray.prepared(ScreenId.TRAY_SIZES, "update-tray-size"))

        val recipe = manager.watch(ScreenId.RECIPE, "recipe-1").first()
        assertEquals("My peas", recipe.field("name"))
        assertEquals("coco coir", recipe.field("substrate"))
        execute(ScreenId.RECIPE, recipe.prepared(ScreenId.RECIPE, "update-recipe"))

        val batch = manager.watch(ScreenId.BATCH_EDITOR, "batch-1").first()
        assertEquals("12.0", batch.field("actualSeedMassGrams"))
        execute(ScreenId.BATCH_EDITOR, batch.prepared(ScreenId.BATCH_EDITOR, "correct-batch"))

        val createBatch = manager.watch(ScreenId.BATCH_EDITOR).first()
        val createBatchCommand = createBatch.prepared(ScreenId.BATCH_EDITOR, "save-batch")
        execute(ScreenId.BATCH_EDITOR, createBatchCommand)
        assertTrue(database.snapshot().batches.any { it.id.value == createBatchCommand.subjectId })

        val plan = manager.watch(ScreenId.PLAN, "plan-1").first()
        assertEquals("pea,radish", plan.field("cultureIds"))
        assertTrue(plan.fields.first { it.id.value == "cultureIds" }.options.any {
            it.id == "pea" && it.labelKey?.value == "culture_pea_name"
        })
        execute(ScreenId.PLAN, plan.prepared(ScreenId.PLAN, "update-plan"))

        val observation = manager.watch(ScreenId.OBSERVATION_EDITOR, "observation-1").first()
        assertEquals("Рост хороший", observation.field("text"))
        execute(
            ScreenId.OBSERVATION_EDITOR,
            observation.prepared(ScreenId.OBSERVATION_EDITOR, "save-observation"),
        )

        val close = manager.watch(ScreenId.CLOSE_BATCH, "batch-1").first()
        assertEquals(NOW, close.field("occurredAt"))
        assertEquals("harvested", close.field("outcome"))

        val settingsSnapshot = manager.watch(ScreenId.SETTINGS).first()
        execute(
            ScreenId.SETTINGS,
            settingsSnapshot.prepared(
                ScreenId.SETTINGS, "set-theme", overrides = mapOf("theme" to "dark"),
            ),
        )
        assertEquals(ThemePreference.DARK, settings.snapshot().theme)

        val privacy = manager.watch(ScreenId.ADVERTISING_PRIVACY).first()
        execute(ScreenId.ADVERTISING_PRIVACY, privacy.prepared(ScreenId.ADVERTISING_PRIVACY, "record-consent"))
        assertEquals(ConsentChoice.DECLINED, settings.snapshot().privacyState?.choice)

        val notifications = manager.watch(ScreenId.NOTIFICATIONS).first()
        execute(
            ScreenId.NOTIFICATIONS,
            notifications.prepared(
                ScreenId.NOTIFICATIONS,
                "update-notifications",
                overrides = mapOf(
                    "dailySummaryEnabled" to "false",
                    "summaryTime" to "07:45",
                    "soakRemindersEnabled" to "true",
                ),
            ),
        )
        assertFalse(settings.snapshot().notificationsEnabled)
        assertEquals(SummaryLocalTime(7, 45), settings.snapshot().summaryTime)
        assertTrue(settings.snapshot().soakRemindersEnabled)
    }

    @Test
    fun backupDocumentIdStaysOpaqueAndPreviewFactsDescribeArchive() = runTest {
        seedBatchPrerequisites()
        val opaqueDocumentId = "content://provider/tree/backup%2Fsproutpace"
        val initial = manager.watch(ScreenId.BACKUP_RESTORE).first()
        execute(
            ScreenId.BACKUP_RESTORE,
            initial.prepared(
                ScreenId.BACKUP_RESTORE, "create-backup",
                overrides = mapOf("documentId" to opaqueDocumentId),
            ),
        )

        val created = manager.watch(ScreenId.BACKUP_RESTORE).first()
        assertEquals(opaqueDocumentId, created.field("documentId"))
        execute(ScreenId.BACKUP_RESTORE, created.prepared(ScreenId.BACKUP_RESTORE, "preview-restore"))

        val preview = manager.watch(ScreenId.BACKUP_RESTORE).first()
        assertEquals(opaqueDocumentId, preview.field("documentId"))
        assertEquals(opaqueDocumentId, preview.facts.first { it.id == "document-id" }.authoredValue)
        assertEquals("1", preview.facts.first { it.id == "record-count-batches" }.authoredValue)
        assertTrue(manager.execute(
            ScreenId.BACKUP_RESTORE,
            preview.prepared(ScreenId.BACKUP_RESTORE, "restore-backup"),
        ).isSuccess)
    }

    @Test
    fun ac074_pickerBytesBecomeManagedPhotoAndSupportRestartReplaceAndRemove() = runTest {
        seedBatchPrerequisites()
        val firstBytes = "first-private-photo".encodeToByteArray()
        pendingPhotos.stage("picked-first.jpg", firstBytes)

        execute(
            ScreenId.OBSERVATION_EDITOR,
            ScreenCommand(
                "save-observation",
                "observation-1",
                mapOf(
                    "batchId" to "batch-1",
                    "occurredAt" to NOW,
                    "text" to "Roots visible",
                    "photoId" to pendingPhotoDraftValue("picked-first.jpg"),
                ),
            ),
        )

        val firstId = assertNotNull(database.snapshot().observations.single().photoId)
        assertTrue(firstId.value.startsWith("photo-"))
        assertFalse(firstId.value.contains("picked-first"))
        assertContentEquals(firstBytes, files.read(firstId))
        assertEquals(null, pendingPhotos.read("picked-first.jpg"))
        val firstReadback = manager.watch(ScreenId.BATCH_DETAIL, "batch-1").first()
            .items.single { it.id == "observation:observation-1" }
        assertEquals(firstId.value, assertNotNull(firstReadback.photo).id)
        assertContentEquals(firstBytes, firstReadback.photo?.bytes)

        val secondBytes = "replacement-private-photo".encodeToByteArray()
        pendingPhotos.stage("picked-second.png", secondBytes)
        execute(
            ScreenId.OBSERVATION_EDITOR,
            manager.watch(ScreenId.OBSERVATION_EDITOR, "observation-1").first().prepared(
                ScreenId.OBSERVATION_EDITOR,
                "save-observation",
                overrides = mapOf("photoId" to pendingPhotoDraftValue("picked-second.png")),
            ).copy(deduplicationKey = "save-observation:observation-1:replacement"),
        )
        val secondId = assertNotNull(database.snapshot().observations.single().photoId)
        assertTrue(secondId != firstId)
        assertEquals(null, files.read(firstId))
        assertContentEquals(secondBytes, files.read(secondId))

        execute(
            ScreenId.OBSERVATION_EDITOR,
            manager.watch(ScreenId.OBSERVATION_EDITOR, "observation-1").first().prepared(
                ScreenId.OBSERVATION_EDITOR,
                "save-observation",
                overrides = mapOf("photoId" to REMOVE_PHOTO_VALUE),
            ).copy(deduplicationKey = "save-observation:observation-1:remove-photo"),
        )
        assertEquals(null, database.snapshot().observations.single().photoId)
        assertEquals(null, files.read(secondId))
    }

    @Test
    fun ac074_missingPendingOrRawPathIsRejectedWithoutMutationOrOrphan() = runTest {
        seedBatchPrerequisites()
        val before = database.snapshot()

        val missing = manager.execute(
            ScreenId.OBSERVATION_EDITOR,
            ScreenCommand(
                "save-observation",
                "observation-1",
                mapOf(
                    "batchId" to "batch-1",
                    "occurredAt" to NOW,
                    "text" to "No photo",
                    "photoId" to pendingPhotoDraftValue("missing.jpg"),
                ),
            ),
        )
        assertTrue(missing.isFailure)
        assertEquals(before, database.snapshot())
        assertTrue(files.exportAll().isEmpty())

        val rawPath = manager.execute(
            ScreenId.OBSERVATION_EDITOR,
            ScreenCommand(
                "save-observation",
                "observation-2",
                mapOf(
                    "batchId" to "batch-1",
                    "occurredAt" to NOW,
                    "text" to "Unsafe path",
                    "photoId" to "/tmp/library/photo.jpg",
                ),
            ),
        )
        assertTrue(rawPath.isFailure)
        assertEquals(before, database.snapshot())
        assertTrue(files.exportAll().isEmpty())

        database.saveObservation(
            Observation(
                id = ObservationId("observation-missing-photo"),
                batchId = BatchId("batch-1"),
                occurredAt = Instant.parse(NOW),
                text = "Core note survives",
                photoId = PhotoId("photo-missing"),
            ),
        )
        val rendered = manager.watch(ScreenId.BATCH_DETAIL, "batch-1").first()
            .items.single { it.id == "observation:observation-missing-photo" }
        assertEquals("Core note survives", rendered.authoredTitle)
        assertEquals("observation_photo_unavailable", rendered.stateKey?.value)
        assertEquals(null, rendered.photo)
    }

    @Test
    fun malformedUnknownAndDeduplicationCollisionsNeverMutateState() = runTest {
        val before = database.snapshot()
        val wrongScreen = manager.execute(
            ScreenId.TODAY,
            ScreenCommand("save-zone", "zone-1", mapOf("name" to "Shelf", "type" to "grow_light", "capacity" to "1")),
        )
        assertTrue(wrongScreen.isFailure)
        assertEquals(before, database.snapshot())

        val malformed = manager.execute(
            ScreenId.ZONES,
            ScreenCommand("save-zone", "zone-1", mapOf("name" to "Shelf", "type" to "grow_light", "capacity" to "NaN")),
        )
        assertTrue(malformed.isFailure)
        assertEquals(before, database.snapshot())

        saveZone()
        val collision = manager.execute(
            ScreenId.ZONES,
            ScreenCommand(
                "save-zone", "zone-1", mapOf("name" to "Other", "type" to "dark", "capacity" to "9"),
                deduplicationKey = "save-zone:zone-1",
            ),
        )
        assertTrue(collision.isFailure)
        assertEquals("Grow shelf", database.snapshot().zones.single().name)
    }

    @Test
    fun ac013Ac017Ac024Ac025Ac083Ac090ToAc095_allProductionCommandsReachRealDataServices() = runTest {
        seedBatchPrerequisites()
        savePlan()
        execute(
            ScreenId.OBSERVATION_EDITOR,
            ScreenCommand(
                "save-observation", "observation-1",
                mapOf("batchId" to "batch-1", "occurredAt" to NOW, "text" to "Рост хороший"),
            ),
        )
        execute(
            ScreenId.RECIPE,
            ScreenCommand("duplicate-recipe", "recipe-1", mapOf("newId" to "recipe-copy", "name" to "Copy")),
        )
        execute(
            ScreenId.TRAYS,
            ScreenCommand("repeat-batch", "batch-1", mapOf("newId" to "batch-repeat")),
        )
        assertEquals(BatchState.PLANNED, database.snapshot().batches.first { it.id.value == "batch-repeat" }.state)
        assertTrue(database.snapshot().batches.first { it.id.value == "batch-repeat" }.events.isEmpty())

        execute(ScreenId.BACKUP_RESTORE, ScreenCommand("create-backup", arguments = mapOf("documentId" to "backup")))
        execute(ScreenId.BACKUP_RESTORE, ScreenCommand("preview-restore", arguments = mapOf("documentId" to "backup")))
        execute(ScreenId.BACKUP_RESTORE, ScreenCommand("export-csv", arguments = mapOf("documentId" to "csv")))
        assertTrue(documents.values.getValue("csv").decodeToString().startsWith(BatchHistoryCsv.HEADER))

        execute(ScreenId.OBSERVATION_EDITOR, ScreenCommand("delete-observation", "observation-1"))
        execute(ScreenId.TRAYS, ScreenCommand("delete-batch", "batch-repeat"))
        execute(ScreenId.RECIPE, ScreenCommand("delete-recipe", "recipe-copy"))
        execute(ScreenId.PLAN, ScreenCommand("delete-plan", "plan-1"))
        execute(ScreenId.ZONES, ScreenCommand("delete-zone", "zone-1"))
        execute(ScreenId.TRAY_SIZES, ScreenCommand("delete-tray-size", "tray-1"))
        execute(ScreenId.BACKUP_RESTORE, ScreenCommand("restore-backup", arguments = mapOf("documentId" to "backup")))
        assertEquals(setOf("batch-1", "batch-repeat"), database.snapshot().batches.mapTo(mutableSetOf()) { it.id.value })

        execute(ScreenId.BACKUP_RESTORE, ScreenCommand("delete-all-data"))
        assertTrue(database.snapshot().batches.isEmpty())
        assertTrue(files.exportAll().isEmpty())
    }

    private suspend fun seedBatchPrerequisites() {
        saveZone()
        saveTray()
        saveRecipe()
        saveBatch()
    }

    private suspend fun saveZone(capacity: Int = 1) = execute(
        ScreenId.ZONES,
        ScreenCommand(
            "save-zone", "zone-1",
            mapOf("name" to "Grow shelf", "type" to "grow_light", "capacity" to capacity.toString()),
        ),
    )

    private suspend fun saveTray() = execute(
        ScreenId.TRAY_SIZES,
        ScreenCommand(
            "save-tray-size", "tray-1",
            mapOf("name" to "10 x 20", "shape" to "rectangle", "widthCm" to "25.4", "lengthCm" to "50.8"),
        ),
    )

    private suspend fun saveRecipe() = execute(
        ScreenId.RECIPE,
        ScreenCommand(
            "save-recipe", "recipe-1",
            mapOf(
                "name" to "My peas", "cultureId" to "pea", "substrate" to "coco coir",
                "supplier" to "Local seeds", "cultivar" to "Dwarf grey", "seedLot" to "LOT-42",
                "germinationPercentage" to "94",
                "stage1" to "soaking", "stage1MinimumHours" to "8", "stage1MaximumHours" to "12",
                "stage1Guidance" to "Rinse and soak",
                "stage2" to "sown", "stage2MinimumHours" to "0", "stage2MaximumHours" to "0",
                "stage2Guidance" to "Spread seed evenly",
                "stage3" to "blackout_or_weight", "stage3MinimumHours" to "48", "stage3MaximumHours" to "72",
                "stage3Guidance" to "Keep covered and weighted",
                "stage4" to "light_growth", "stage4MinimumHours" to "72", "stage4MaximumHours" to "120",
                "stage4Guidance" to "Keep under light",
                "stage5" to "ready_for_harvest", "stage5MinimumHours" to "0", "stage5MaximumHours" to "0",
                "stage5Guidance" to "Harvest above the medium",
            ),
        ),
    )

    private suspend fun saveBatch() = execute(
        ScreenId.BATCH_EDITOR,
        ScreenCommand(
            "save-batch", "batch-1",
            mapOf(
                "recipeId" to "recipe-1", "trayId" to "tray-1", "actualSeedMassGrams" to "12.0",
                "plannedSowDate" to TODAY, "fixedHarvestDate" to "2026-09-27", "timeZoneId" to "Europe/Moscow",
            ),
        ),
    )

    private suspend fun savePlan(mode: String = "continuous_queue") = execute(
        ScreenId.PLAN,
        ScreenCommand(
            "save-plan", "plan-1",
            mapOf("cadenceDays" to "3", "cultureIds" to "pea,radish", "mode" to mode),
        ),
    )

    private suspend fun execute(screen: ScreenId, command: ScreenCommand) =
        manager.execute(screen, command).getOrThrow()

    private fun ScreenSnapshot.field(id: String): String =
        fields.first { it.id == ScreenFieldId(id) }.value

    private fun ScreenSnapshot.prepared(
        screen: ScreenId,
        action: String,
        overrides: Map<String, String> = emptyMap(),
    ): ScreenCommand {
        val draft = fields.associate { it.id to (overrides[it.id.value] ?: it.value) }
        val preparation = ScreenContractRegistry.prepareCommand(
            screen = screen,
            actionId = ScreenActionId(action),
            currentSubjectId = selectedItemId,
            draft = draft,
        )
        return assertIs<CommandPreparation.Ready>(preparation).command
    }

    private companion object {
        const val NOW = "2026-09-20T10:00:00Z"
        const val TODAY = "2026-09-20"
        val CULTURE_IDS = setOf("radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress")
    }

    private class ManagerDocuments : ArchiveDocumentPort {
        val values = mutableMapOf<String, ByteArray>()
        override suspend fun read(documentId: String): ByteArray? = values[documentId]?.copyOf()
        override suspend fun write(documentId: String, bytes: ByteArray) { values[documentId] = bytes.copyOf() }
    }
}
