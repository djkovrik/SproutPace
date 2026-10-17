package com.sedsoftware.sproutpace.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.integration.*
import com.sedsoftware.sproutpace.component.store.unwrap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ScreenComponentContractTest {
    @Test fun ac074_photoDraftProtocolIsOpaqueAndScreenPhotoOwnsItsBytes() {
        val bytes = byteArrayOf(1, 2, 3)
        val photo = ScreenPhoto("photo-stable", bytes, TextKey("observation_photo_content_description"))
        bytes[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), photo.bytes)
        assertEquals("pending-photo:picker-token.jpg", pendingPhotoDraftValue("picker-token.jpg"))
        assertEquals("picker-token.jpg", pendingPhotoToken("pending-photo:picker-token.jpg"))
        assertNull(pendingPhotoToken("photo-stable"))
        assertFails { pendingPhotoDraftValue("folder/photo.jpg") }
    }

    @Test fun ac010_saveZonePublishesThePersistedZoneOnce() = runTest {
        assertCommandScenario(ScreenId.ZONES, ScreenCommand("save-zone", "zone-1"), itemSnapshot("zone-1", "My shelf"))
    }

    @Test fun ac011_savedZoneIsRestoredByStableIdentity() = runTest {
        assertSnapshotScenario(ScreenId.ZONES, itemSnapshot("zone-1", "My shelf"), expectedItemId = "zone-1")
    }

    @Test fun ac012_zoneUpdateKeepsCapacityConflictVisible() = runTest {
        assertCommandScenario(
            ScreenId.ZONES,
            ScreenCommand("update-zone", "zone-1"),
            itemSnapshot("zone-1", "My shelf").copy(conflictResolutionKeys = listOf(TextKey("choose_another_zone"))),
            expectedConflictKey = "choose_another_zone",
        )
    }

    @Test fun ac014_saveMetricTrayPublishesThePresetOnce() = runTest {
        assertCommandScenario(ScreenId.TRAY_SIZES, ScreenCommand("save-tray-size", "tray-1"), itemSnapshot("tray-1", "10 x 20 cm"))
    }

    @Test fun ac020_offlineCatalogExposesEightStableCultures() = runTest {
        val cultures = listOf("radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress")
        val snapshot = ScreenSnapshot(items = cultures.map { ScreenItem(it, titleKey = TextKey("culture_${it}_name")) }, offline = true)
        assertSnapshotScenario(ScreenId.CATALOG, snapshot, expectedCount = 8)
    }

    @Test fun ac021_savePersonalRecipePublishesDistinctIdentity() = runTest {
        assertCommandScenario(ScreenId.RECIPE, ScreenCommand("save-recipe", "recipe-personal"), itemSnapshot("recipe-personal", "My mix"))
    }

    @Test fun ac026_seedRecommendationIsExposedAsEditableContent() = runTest {
        assertSnapshotScenario(ScreenId.BATCH_EDITOR, itemSnapshot("seed-grams", "12.5 g"), expectedItemId = "seed-grams")
    }

    @Test fun ac027_offlineRecipeExposesSafetyAndSourceSections() = runTest {
        val snapshot = ScreenSnapshot(
            items = listOf(
                ScreenItem("safety", titleKey = TextKey("recipe_safety_title")),
                ScreenItem("source", titleKey = TextKey("recipe_source_title")),
            ),
            offline = true,
        )
        assertSnapshotScenario(ScreenId.RECIPE, snapshot, expectedCount = 2)
    }

    @Test fun ac030_savePlannedBatchPublishesNewStableIdentity() = runTest {
        assertCommandScenario(ScreenId.BATCH_EDITOR, ScreenCommand("save-batch", "batch-new"), itemSnapshot("batch-new", "Radish"))
    }

    @Test fun ac031_batchDetailRestoresByStableIdentity() = runTest {
        assertSnapshotScenario(ScreenId.BATCH_DETAIL, itemSnapshot("batch-1", "Radish"), stableId = "batch-1", expectedItemId = "batch-1")
    }

    @Test fun ac032_correctBatchPublishesRecalculatedFutureState() = runTest {
        assertCommandScenario(ScreenId.BATCH_EDITOR, ScreenCommand("correct-batch", "batch-1"), itemSnapshot("future-action", "Harvest"))
    }

    @Test fun ac035_startSoakingPublishesElapsedTargetState() = runTest {
        assertCommandScenario(ScreenId.BATCH_DETAIL, ScreenCommand("start-soaking", "batch-1"), itemSnapshot("soak-target", "8 h"))
    }

    @Test fun ac036_confirmSowingPublishesAssignedPlace() = runTest {
        assertCommandScenario(ScreenId.BATCH_DETAIL, ScreenCommand("confirm-sowing", "batch-1"), itemSnapshot("zone-1", "Grow shelf"))
    }

    @Test fun ac037_confirmStagePublishesActualTransitionOnce() = runTest {
        assertCommandScenario(ScreenId.BATCH_DETAIL, ScreenCommand("confirm-stage", "batch-1"), itemSnapshot("stage-event-1", "Blackout"))
    }

    @Test fun ac038_overflowMoveKeepsUnassignedResolutionVisible() = runTest {
        assertCommandScenario(
            ScreenId.BATCH_DETAIL,
            ScreenCommand("assign-overflow", "batch-1"),
            itemSnapshot("batch-1", "Unassigned").copy(conflictResolutionKeys = listOf(TextKey("leave_unassigned"))),
            expectedConflictKey = "leave_unassigned",
        )
    }

    @Test fun ac039_undoLatestTransitionPublishesOneRebuiltState() = runTest {
        assertCommandScenario(ScreenId.BATCH_DETAIL, ScreenCommand("undo-stage", "batch-1"), itemSnapshot("stage-sown", "Sown"))
    }

    @Test fun ac040_skipStagePublishesExplicitSkippedState() = runTest {
        assertCommandScenario(ScreenId.BATCH_DETAIL, ScreenCommand("skip-stage", "batch-1"), itemSnapshot("stage-skipped", "Skipped"))
    }

    @Test fun ac050_savePlanPublishesSuggestionsNotBatches() = runTest {
        assertCommandScenario(ScreenId.PLAN, ScreenCommand("save-plan", "plan-1"), itemSnapshot("suggestion-1", "Sep 23"))
    }

    @Test fun ac051_planRestoresCadenceAndStableIdentity() = runTest {
        assertSnapshotScenario(ScreenId.PLAN, itemSnapshot("plan-1", "Every 3 days"), expectedItemId = "plan-1")
    }

    @Test fun ac052_updatePlanPublishesOnlyFutureSuggestions() = runTest {
        assertCommandScenario(ScreenId.PLAN, ScreenCommand("update-plan", "plan-1"), itemSnapshot("future-suggestion", "Sep 27"))
    }

    @Test fun ac054_pausePlanPublishesPausedStateWithoutRemovingExistingWork() = runTest {
        val snapshot = ScreenSnapshot(items = listOf(ScreenItem("plan-1", authoredTitle = "Paused"), ScreenItem("batch-1", authoredTitle = "Radish")))
        assertCommandScenario(ScreenId.PLAN, ScreenCommand("pause-plan", "plan-1"), snapshot)
    }

    @Test fun ac055_capacityConflictPublishesAllResolutionChoices() = runTest {
        val snapshot = ScreenSnapshot(
            items = listOf(ScreenItem("conflict-1", authoredTitle = "Capacity conflict")),
            conflictResolutionKeys = listOf(TextKey("choose_another_date"), TextKey("choose_another_zone"), TextKey("change_capacity")),
        )
        assertSnapshotScenario(ScreenId.PLAN, snapshot, expectedCount = 1, expectedConflictCount = 3)
    }

    @Test fun ac057_continuousQueuePublishesExactlyOneNextSuggestion() = runTest {
        assertSnapshotScenario(ScreenId.PLAN, itemSnapshot("next-suggestion", "Radish"), expectedCount = 1)
    }

    @Test fun ac060_todayPublishesNextOverdueAndTodayContext() = runTest {
        val snapshot = ScreenSnapshot(
            items = listOf(
                ScreenItem("next", titleKey = TextKey("today_next_action")),
                ScreenItem("overdue", titleKey = TextKey("today_overdue_group")),
                ScreenItem("today", titleKey = TextKey("today_due_group")),
            ),
        )
        assertSnapshotScenario(ScreenId.TODAY, snapshot, expectedCount = 3)
    }

    @Test fun ac062_snoozePublishesSameStableActionIdentity() = runTest {
        assertCommandScenario(ScreenId.TODAY, ScreenCommand("snooze", "action-1"), itemSnapshot("action-1", "11:00"))
    }

    @Test fun ac066_soakTargetSchedulingPublishesOneStableReminder() = runTest {
        assertCommandScenario(ScreenId.BATCH_DETAIL, ScreenCommand("schedule-soak-target", "batch-1"), itemSnapshot("soak:batch-1", "16:00"))
    }

    @Test fun ac080_harvestPublishesTerminalHistoryState() = runTest {
        assertCommandScenario(ScreenId.CLOSE_BATCH, ScreenCommand("close-harvest", "batch-1"), itemSnapshot("batch-1", "Harvested"))
    }

    @Test fun ac081_failurePublishesDistinctTerminalHistoryState() = runTest {
        assertCommandScenario(ScreenId.CLOSE_BATCH, ScreenCommand("close-failed", "batch-1"), itemSnapshot("batch-1", "Failed"))
    }

    @Test
    fun everyRenderedScreenHasAnIsolatedComponentOwnedPreview() {
        val previews: List<ScreenComponent> = listOf(
            FirstRunComponentPreview(), TodayComponentPreview(), TraysComponentPreview(),
            BatchEditorComponentPreview(), BatchDetailComponentPreview(), PlanComponentPreview(),
            CatalogComponentPreview(), RecipeComponentPreview(), ObservationEditorComponentPreview(),
            CloseBatchComponentPreview(), SettingsComponentPreview(), ZonesComponentPreview(),
            TraySizesComponentPreview(), BackupRestoreComponentPreview(),
            AdvertisingPrivacyComponentPreview(), NotificationsComponentPreview(),
        )
        assertEquals(ScreenId.entries.toSet(), previews.map { it.model.value.screen }.toSet())
        assertTrue(previews.all { it.model.value.titleKey.value.startsWith("screen_") })
        assertEquals(ScreenId.entries.toSet(), ScreenContractRegistry.contracts.keys)
        assertTrue(ScreenContractRegistry.contracts.values.all { ScreenVariant.CONTENT in it.variants })
        assertTrue(previews.all { it.model.value.facts.isNotEmpty() })
        assertTrue(previews.all { it.model.value.actions.isNotEmpty() })
    }

    @Test
    fun approvedEmptyVariantsRemainReachableWhenContractsProvideFieldsAndFacts() = runTest {
        val emptyScreens = setOf(
            ScreenId.TODAY,
            ScreenId.TRAYS,
            ScreenId.PLAN,
            ScreenId.CATALOG,
            ScreenId.ZONES,
            ScreenId.TRAY_SIZES,
        )

        emptyScreens.forEach { screen ->
            val component = ScreenComponentFactory(
                ScreenComponentDependencies(DefaultStoreFactory(), FakeManager(ScreenSnapshot()), UnconfinedTestDispatcher(testScheduler)),
            ).create(screen, DefaultComponentContext(LifecycleRegistry()), null) {}

            assertEquals(LoadState.EMPTY, component.model.value.loadState, screen.name)
        }

        val catalog = ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), FakeManager(ScreenSnapshot()), UnconfinedTestDispatcher(testScheduler)),
        ).create(ScreenId.CATALOG, DefaultComponentContext(LifecycleRegistry()), null) {}
        assertEquals("query", catalog.model.value.fields.single().id.value)

        val editor = ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), FakeManager(ScreenSnapshot()), UnconfinedTestDispatcher(testScheduler)),
        ).create(ScreenId.BATCH_EDITOR, DefaultComponentContext(LifecycleRegistry()), null) {}
        assertEquals(LoadState.CONTENT, editor.model.value.loadState)
        assertTrue(editor.model.value.fields.isNotEmpty())
    }

    @Test
    fun allSixteenScreensExposeTypedLocaleNeutralInteractionContracts() {
        assertEquals(ScreenId.entries.toSet(), ScreenContractRegistry.contracts.keys)
        ScreenContractRegistry.contracts.values.forEach { contract ->
            assertTrue(contract.facts.isNotEmpty(), contract.id.name)
            assertTrue(contract.actions.isNotEmpty(), contract.id.name)
            assertTrue(contract.fields.all { it.labelKey.value.startsWith("field_") }, contract.id.name)
            assertTrue(contract.fields.flatMap(ScreenField::options).all { it.labelKey != null }, contract.id.name)
            assertTrue(contract.actions.all { it.labelKey.value.startsWith("action_") }, contract.id.name)
        }
    }

    @Test
    fun operationRegistryMatchesEveryProductionManagerOperationAndDeclaresSubjectSemantics() {
        val expected = mapOf(
            ScreenId.FIRST_RUN to setOf("continue-setup"),
            ScreenId.TODAY to setOf("complete-action", "snooze", "skip-action", "time-zone-change"),
            ScreenId.TRAYS to setOf("delete-batch"),
            ScreenId.BATCH_EDITOR to setOf("save-batch", "correct-batch"),
            ScreenId.BATCH_DETAIL to setOf(
                "start-soaking", "confirm-sowing", "confirm-stage", "assign-overflow", "undo-stage",
                "skip-stage", "schedule-soak-target", "record-actual-mass", "time-zone-change",
                "complete-action", "skip-action", "delete-batch",
            ),
            ScreenId.PLAN to setOf(
                "save-plan", "update-plan", "pause-plan", "resume-plan", "confirm-suggestion", "delete-plan",
            ),
            ScreenId.CATALOG to emptySet(),
            ScreenId.RECIPE to setOf("save-recipe", "update-recipe", "duplicate-recipe", "delete-recipe"),
            ScreenId.OBSERVATION_EDITOR to setOf("save-observation", "delete-observation"),
            ScreenId.CLOSE_BATCH to setOf(
                "close-harvest", "close-failed", "close-abandoned", "save-outcome", "record-actual-mass",
            ),
            ScreenId.SETTINGS to setOf("set-theme", "set-crash-reporting", "erase-all", "delete-all-data"),
            ScreenId.ZONES to setOf("save-zone", "update-zone", "delete-zone"),
            ScreenId.TRAY_SIZES to setOf("save-tray-size", "update-tray-size", "delete-tray-size"),
            ScreenId.BACKUP_RESTORE to setOf(
                "create-backup", "preview-restore", "restore-backup", "export-csv", "erase-all", "delete-all-data", "cancel-restore-preview",
            ),
            ScreenId.ADVERTISING_PRIVACY to setOf("record-consent"),
            ScreenId.NOTIFICATIONS to setOf("update-notifications"),
        )

        assertEquals(expected, ScreenContractRegistry.contracts.mapValues { it.value.operations.keys })
        ScreenContractRegistry.contracts.values.flatMap { it.operations.values }.forEach { operation ->
            assertTrue(operation.operation.isNotBlank())
            assertTrue(operation.subjectRequirement in CommandSubjectRequirement.entries)
        }
    }

    @Test
    fun everyRegistryFieldOptionAndActionUsesItsSpecificSemanticTextKey() {
        val expectedFieldKeys = mapOf(
            ScreenId.FIRST_RUN to setOf(
                "field_zone_name", "field_zone_type", "field_zone_capacity", "field_tray_name",
                "field_optional_dark_zone", "field_dark_zone_name", "field_dark_zone_capacity",
                "field_tray_shape", "field_tray_width_cm", "field_tray_length_cm",
                "field_tray_diameter_cm", "field_plan_cadence_days", "field_plan_culture_ids",
                "field_plan_mode", "field_daily_summary_enabled", "field_summary_time",
            ),
            ScreenId.TODAY to setOf("field_due_kind", "field_due_at", "field_local_date_time", "field_time_zone"),
            ScreenId.TRAYS to setOf("field_tray_category"),
            ScreenId.BATCH_EDITOR to setOf(
                "field_batch_id", "field_recipe_selector", "field_culture_selector", "field_tray_selector",
                "field_substrate", "field_actual_seed_mass", "field_zone_selector", "field_anchor_state",
                "field_anchor_at", "field_seed_supplier", "field_cultivar", "field_seed_lot",
                "field_germination_percentage", "field_planned_sow_date", "field_fixed_soaking_date",
                "field_fixed_sown_date", "field_fixed_blackout_date", "field_fixed_light_date",
                "field_fixed_harvest_date", "field_time_zone", "field_actual_soaking_at",
                "field_actual_sown_at", "field_actual_blackout_at", "field_actual_light_at",
                "field_actual_harvest_ready_at",
            ),
            ScreenId.BATCH_DETAIL to setOf(
                "field_event_id", "field_occurred_at", "field_next_state", "field_zone_selector",
                "field_leave_unassigned", "field_skipped_state", "field_skip_reason", "field_resulting_state",
                "field_due_at", "field_scheduled_action_selector", "field_actual_harvest_mass",
                "field_time_zone",
            ),
            ScreenId.PLAN to setOf(
                "field_plan_id", "field_plan_anchor_date", "field_plan_cadence_days",
                "field_plan_culture_ids", "field_plan_mode", "field_plan_paused",
            ),
            ScreenId.CATALOG to setOf("field_catalog_query"),
            ScreenId.RECIPE to setOf(
                "field_recipe_id", "field_recipe_name", "field_culture_selector", "field_substrate",
                "field_seed_supplier", "field_cultivar", "field_seed_lot",
                "field_germination_percentage", "field_recipe_stage_1", "field_stage_1_minimum_hours",
                "field_stage_1_maximum_hours", "field_stage_1_guidance", "field_recipe_stage_2",
                "field_stage_2_minimum_hours", "field_stage_2_maximum_hours", "field_stage_2_guidance",
                "field_recipe_stage_3", "field_stage_3_minimum_hours", "field_stage_3_maximum_hours",
                "field_stage_3_guidance", "field_recipe_stage_4", "field_stage_4_minimum_hours",
                "field_stage_4_maximum_hours", "field_stage_4_guidance", "field_recipe_stage_5",
                "field_stage_5_minimum_hours", "field_stage_5_maximum_hours", "field_stage_5_guidance",
                "field_new_id",
            ),
            ScreenId.OBSERVATION_EDITOR to setOf(
                "field_observation_id", "field_batch_selector", "field_occurred_at",
                "field_observation_text", "field_observation_photo",
            ),
            ScreenId.CLOSE_BATCH to setOf(
                "field_occurred_at", "field_outcome", "field_actual_harvest_mass",
                "field_outcome_assessment", "field_outcome_reason", "field_outcome_note",
            ),
            ScreenId.SETTINGS to setOf("field_theme", "field_crash_reporting_enabled"),
            ScreenId.ZONES to setOf("field_zone_id", "field_zone_name", "field_zone_type", "field_zone_capacity"),
            ScreenId.TRAY_SIZES to setOf(
                "field_tray_id", "field_tray_name", "field_tray_shape", "field_tray_width_cm",
                "field_tray_length_cm", "field_tray_diameter_cm",
            ),
            ScreenId.BACKUP_RESTORE to setOf("field_document"),
            ScreenId.ADVERTISING_PRIVACY to setOf(
                "field_advertising_choice", "field_consent_expiry", "field_policy_version",
            ),
            ScreenId.NOTIFICATIONS to setOf(
                "field_daily_summary_enabled", "field_summary_time", "field_soak_reminders_enabled",
            ),
        )

        assertEquals(
            expectedFieldKeys,
            ScreenContractRegistry.contracts.mapValues { (_, contract) ->
                contract.fields.mapTo(mutableSetOf()) { it.labelKey.value }
            },
        )
        ScreenContractRegistry.contracts.values.forEach { contract ->
            contract.fields.forEach { field ->
                val optionGroup = when (field.id.value) {
                    "type" -> "zone_type"
                    "shape" -> "tray_shape"
                    "mode" -> "plan_mode"
                    "dueKind" -> "due_kind"
                    "anchorState", "nextState", "skippedState", "resultingState" -> "batch_state"
                    "outcome" -> "outcome"
                    "assessment" -> "outcome_assessment"
                    "theme" -> "theme"
                    "choice" -> "advertising_choice"
                    "category" -> "tray_category"
                    else -> null
                }
                field.options.forEach { option ->
                    val key = assertNotNull(option.labelKey).value
                    if (field.id.value.startsWith("stage")) {
                        assertTrue(key.startsWith("stage_") || key == "option_recipe_stage_none", key)
                    } else {
                        assertEquals("option_${assertNotNull(optionGroup)}_${option.id}", key)
                        assertTrue(key.startsWith("option_"), "$key on ${contract.id}")
                    }
                    assertNotEquals("settings_theme_system", key)
                    assertNotEquals("field_recipe", key)
                }
            }
            contract.actions.forEach { descriptor ->
                assertEquals(
                    "action_${descriptor.id.value.replace('-', '_')}",
                    descriptor.labelKey.value,
                    "${contract.id}:${descriptor.id.value}",
                )
                assertNotEquals("action_edit", descriptor.labelKey.value)
            }
        }
    }

    @Test
    fun ac065_notificationsCommandKeepsDailyTimeAndSoakChoicesIndependent() {
        val draft = mapOf(
            ScreenFieldId("dailySummaryEnabled") to "false",
            ScreenFieldId("summaryTime") to "07:45",
            ScreenFieldId("soakRemindersEnabled") to "true",
        )

        val preparation = ScreenContractRegistry.prepareCommand(
            screen = ScreenId.NOTIFICATIONS,
            actionId = ScreenActionId("update-notifications"),
            currentSubjectId = null,
            draft = draft,
        )

        val command = assertIs<CommandPreparation.Ready>(preparation).command
        assertEquals("update-notifications", command.operation)
        assertEquals(
            mapOf(
                "dailySummaryEnabled" to "false",
                "summaryTime" to "07:45",
                "soakRemindersEnabled" to "true",
            ),
            command.arguments,
        )
        assertFalse("enabled" in command.arguments)
    }

    @Test
    fun selectorsAndEnumsExposeExactKindsAndStableDomainOptionIds() {
        val selectors = setOf("recipeId", "cultureId", "trayId", "zoneId", "batchId")
        ScreenContractRegistry.contracts.values.flatMap(ScreenContract::fields)
            .filter { it.id.value in selectors }
            .forEach { assertEquals(ScreenFieldKind.SINGLE_CHOICE, it.kind, it.id.value) }
        ScreenContractRegistry.contracts.values.flatMap(ScreenContract::fields)
            .filter { it.id.value == "cultureIds" }
            .forEach { assertEquals(ScreenFieldKind.MULTI_CHOICE, it.kind, it.id.value) }

        assertEquals(
            setOf("natural_light", "grow_light"),
            ScreenContractRegistry.contracts.getValue(ScreenId.FIRST_RUN).field("type").optionIds(),
        )
        assertEquals(
            setOf("natural_light", "grow_light", "dark"),
            ScreenContractRegistry.contracts.getValue(ScreenId.ZONES).field("type").optionIds(),
        )
        val theme = ScreenContractRegistry.contracts.getValue(ScreenId.SETTINGS).field("theme")
        assertEquals(setOf("system", "light", "dark"), theme.optionIds())
        assertEquals(3, theme.options.mapNotNull { it.labelKey }.distinct().size)
        assertEquals(
            setOf("cadence", "continuous_queue"),
            ScreenContractRegistry.contracts.getValue(ScreenId.PLAN).field("mode").optionIds(),
        )
        assertEquals(
            setOf("successful", "mixed", "unsuccessful"),
            ScreenContractRegistry.contracts.getValue(ScreenId.CLOSE_BATCH).field("assessment").optionIds(),
        )
        val batchStates = setOf(
            "planned", "soaking", "sown", "blackout_or_weight", "light_growth",
            "ready_for_harvest", "harvested", "failed", "abandoned",
        )
        listOf("anchorState", "nextState", "skippedState", "resultingState").forEach { fieldId ->
            val fields = ScreenContractRegistry.contracts.values.flatMap(ScreenContract::fields)
                .filter { it.id.value == fieldId }
            assertTrue(fields.isNotEmpty(), fieldId)
            fields.forEach { field ->
                assertEquals(ScreenFieldKind.SINGLE_CHOICE, field.kind, fieldId)
                assertEquals(batchStates, field.optionIds(), fieldId)
            }
        }
        ScreenContractRegistry.contracts.values.forEach { contract ->
            contract.operations.values.flatMap(ScreenOperationContract::arguments).forEach { argument ->
                assertEquals(contract.field(argument.name).kind, argument.kind, "${contract.id}:${argument.name}")
            }
        }
    }

    @Test
    fun firstRunAndRepresentativeCreateEditCommandsMatchProductionManagerArguments() {
        val rectangleSetup = linkedMapOf(
            "zoneName" to "Shelf", "type" to "grow_light", "capacity" to "4",
            "trayName" to "Home tray", "shape" to "rectangle", "widthCm" to "25.4",
            "lengthCm" to "50.8", "cadenceDays" to "7", "cultureIds" to "radish,pea",
            "mode" to "continuous_queue",
        ).mapKeys { ScreenFieldId(it.key) }
        val setup = assertIs<CommandPreparation.Ready>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.FIRST_RUN, ScreenActionId("continue-setup"), null, rectangleSetup,
            ),
        ).command
        assertNull(setup.subjectId)
        assertEquals(rectangleSetup.mapKeys { it.key.value }, setup.arguments)
        assertIs<CommandPreparation.Invalid>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.FIRST_RUN,
                ScreenActionId("continue-setup"),
                null,
                rectangleSetup - ScreenFieldId("widthCm"),
            ),
        )
        assertIs<CommandPreparation.Invalid>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.FIRST_RUN,
                ScreenActionId("continue-setup"),
                null,
                rectangleSetup + (ScreenFieldId("type") to "dark"),
            ),
        )
        val circleSetup = rectangleSetup
            .minus(ScreenFieldId("widthCm"))
            .minus(ScreenFieldId("lengthCm")) + mapOf(
                ScreenFieldId("shape") to "circle",
                ScreenFieldId("diameterCm") to "20",
            )
        val circleCommand = assertIs<CommandPreparation.Ready>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.FIRST_RUN, ScreenActionId("continue-setup"), null, circleSetup,
            ),
        ).command
        assertEquals("20", circleCommand.arguments.getValue("diameterCm"))
        assertFalse(circleCommand.arguments.containsKey("widthCm"))
        assertIs<CommandPreparation.Invalid>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.FIRST_RUN,
                ScreenActionId("continue-setup"),
                null,
                circleSetup - ScreenFieldId("diameterCm"),
            ),
        )

        val createZone = assertIs<CommandPreparation.Ready>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.ZONES,
                ScreenActionId("save-zone"),
                null,
                mapOf(
                    ScreenFieldId("id") to "zone-new",
                    ScreenFieldId("name") to "Shelf",
                    ScreenFieldId("type") to "natural_light",
                    ScreenFieldId("capacity") to "3",
                ),
            ),
        ).command
        assertEquals("zone-new", createZone.subjectId)
        assertEquals(mapOf("name" to "Shelf", "type" to "natural_light", "capacity" to "3"), createZone.arguments)

        val updateZone = assertIs<CommandPreparation.Ready>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.ZONES,
                ScreenActionId("update-zone"),
                "zone-existing",
                mapOf(
                    ScreenFieldId("id") to "ignored-draft-id",
                    ScreenFieldId("name") to "Cabinet",
                    ScreenFieldId("type") to "dark",
                    ScreenFieldId("capacity") to "2",
                ),
            ),
        ).command
        assertEquals("zone-existing", updateZone.subjectId)
        assertEquals("dark", updateZone.arguments.getValue("type"))

        val createBatch = assertIs<CommandPreparation.Ready>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.BATCH_EDITOR,
                ScreenActionId("save-batch"),
                null,
                mapOf(
                    ScreenFieldId("id") to "batch-new",
                    ScreenFieldId("recipeId") to "recipe-1",
                    ScreenFieldId("trayId") to "tray-1",
                    ScreenFieldId("substrate") to "coir",
                    ScreenFieldId("plannedSowDate") to "2026-09-20",
                ),
            ),
        ).command
        assertEquals("batch-new", createBatch.subjectId)
        assertEquals(setOf("recipeId", "trayId", "substrate", "plannedSowDate"), createBatch.arguments.keys)

        val createRecipeDraft = mapOf(
            "id" to "recipe-new", "name" to "My mix", "cultureId" to "pea", "substrate" to "coir",
            "stage1" to "sown", "stage1MinimumHours" to "0", "stage1MaximumHours" to "0",
            "stage1Guidance" to "Spread evenly", "stage2" to "light_growth",
            "stage2MinimumHours" to "72", "stage2MaximumHours" to "120",
            "stage2Guidance" to "Keep under light",
        ).mapKeys { ScreenFieldId(it.key) }
        val createRecipe = assertIs<CommandPreparation.Ready>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.RECIPE, ScreenActionId("save-recipe"), null, createRecipeDraft,
            ),
        ).command
        assertEquals("recipe-new", createRecipe.subjectId)
        assertEquals("0", createRecipe.arguments.getValue("stage1MinimumHours"))
        assertEquals("Keep under light", createRecipe.arguments.getValue("stage2Guidance"))
        assertIs<CommandPreparation.Invalid>(
            ScreenContractRegistry.prepareCommand(
                ScreenId.RECIPE,
                ScreenActionId("save-recipe"),
                null,
                createRecipeDraft + (ScreenFieldId("stage1MinimumHours") to "-1"),
            ),
        )
    }

    @Test
    fun typedSubmitRetainsDraftValidatesAndBuildsDeterministicCommand() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = FakeManager(ScreenSnapshot())
        val keeper = InstanceKeeperDispatcher()
        val dependencies = ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher)
        val first = ZonesComponentDefault(
            DefaultComponentContext(LifecycleRegistry(), instanceKeeper = keeper), null, dependencies, {},
        )

        first.onAction(ScreenAction.Submit(ScreenActionId("save-zone")))
        assertEquals(0, manager.executions)
        assertEquals(
            setOf("id", "name", "type", "capacity"),
            first.model.value.validationErrors.keys.mapTo(mutableSetOf()) { it.value },
        )

        first.onAction(ScreenAction.EditField(ScreenFieldId("id"), "zone-1"))
        first.onAction(ScreenAction.EditField(ScreenFieldId("name"), "My shelf"))
        first.onAction(ScreenAction.ChooseOption(ScreenFieldId("type"), "grow_light"))
        first.onAction(ScreenAction.EditField(ScreenFieldId("capacity"), "3"))

        val recreated = ZonesComponentDefault(
            DefaultComponentContext(LifecycleRegistry(), instanceKeeper = keeper), null, dependencies, {},
        )
        assertEquals("My shelf", recreated.model.value.fields.single { it.id.value == "name" }.value)
        assertTrue(recreated.model.value.hasUnsavedChanges)

        recreated.onAction(ScreenAction.Submit(ScreenActionId("save-zone")))
        assertEquals(1, manager.executions)
        assertEquals(
            "save-zone:zone-1:capacity=3:name=My shelf:type=grow_light",
            manager.commands.single().deduplicationKey,
        )
        assertFalse(recreated.model.value.hasUnsavedChanges)
        assertTrue(recreated.model.value.validationErrors.isEmpty())
    }

    @Test
    fun cancelDraftNeverExecutesDataAndRestoresManagerValues() = runTest {
        val manager = FakeManager(
            ScreenSnapshot(fields = listOf(field("name").copy(value = "Saved shelf"))),
        )
        val component = ZonesComponentDefault(
            DefaultComponentContext(LifecycleRegistry()), "zone-1",
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)), {},
        )

        component.onAction(ScreenAction.EditField(ScreenFieldId("name"), "Unsaved shelf"))
        component.onAction(ScreenAction.CancelDraft)

        assertEquals(0, manager.executions)
        assertEquals("Saved shelf", component.model.value.fields.single { it.id.value == "name" }.value)
        assertFalse(component.model.value.hasUnsavedChanges)
    }

    @Test
    fun typedDestructiveSubmitUsesExistingConfirmationAndExactlyOnceExecution() = runTest {
        val manager = ScenarioManager(ScreenSnapshot())
        val outputs = mutableListOf<ScreenOutput>()
        val component = ZonesComponentDefault(
            DefaultComponentContext(LifecycleRegistry()), "zone-1",
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)), outputs::add,
        )

        component.onAction(ScreenAction.Submit(ScreenActionId("delete-zone")))

        val request = assertIs<ScreenOutput.ConfirmationRequested>(outputs.single())
        assertEquals("delete-zone", request.command.operation)
        assertTrue(manager.commands.isEmpty())

        component.onAction(ScreenAction.ExecuteConfirmed(request.command))
        component.onAction(ScreenAction.ExecuteConfirmed(request.command))
        assertEquals(listOf(ScreenId.ZONES to request.command), manager.commands)
    }

    @Test
    fun storeStateMapsToPublicModelAndRetainsSelectionAcrossRecreation() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = FakeManager(
            ScreenSnapshot(items = listOf(ScreenItem("zone-1", authoredTitle = "My shelf"))),
        )
        val keeper = InstanceKeeperDispatcher()
        val lifecycle = LifecycleRegistry()
        val dependencies = ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher)
        val first = ZonesComponentDefault(
            DefaultComponentContext(lifecycle, instanceKeeper = keeper), null, dependencies, {},
        )
        first.onAction(ScreenAction.Select("zone-1"))

        val recreated = ZonesComponentDefault(
            DefaultComponentContext(lifecycle, instanceKeeper = keeper), null, dependencies, {},
        )
        assertEquals("zone-1", recreated.model.value.selectedItemId)
        assertEquals("My shelf", recreated.model.value.items.single().authoredTitle)
    }

    @Test
    fun traysComponentKeepsExplicitHistoryChoiceInTypedStoreState() = runTest {
        val category = ScreenContractRegistry.contracts.getValue(ScreenId.TRAYS).fields
            .single { it.id.value == "category" }
            .copy(value = "planned")
        val manager = FakeManager(
            ScreenSnapshot(
                items = listOf(
                    ScreenItem("planned", authoredTitle = "Planned", stateKey = TextKey("option_batch_state_planned")),
                    ScreenItem("terminal", authoredTitle = "Harvested", stateKey = TextKey("option_batch_state_harvested")),
                ),
                fields = listOf(category),
            ),
        )
        val component = TraysComponentDefault(
            DefaultComponentContext(LifecycleRegistry()), null,
            ScreenComponentDependencies(
                DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler),
            ),
            {},
        )

        component.onAction(ScreenAction.ChooseOption(ScreenFieldId("category"), "history"))

        assertEquals(
            "history",
            component.model.value.fields.single { it.id.value == "category" }.value,
        )
        assertEquals(
            setOf("planned", "terminal"),
            component.model.value.items.mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun lifecyclePauseResumeRetainsPublicSelectionAndDestroyDisposesSafely() = runTest {
        val lifecycle = LifecycleRegistry()
        val manager = ScenarioManager(itemSnapshot("zone-1", "My shelf"))
        val component = ZonesComponentDefault(
            DefaultComponentContext(lifecycle), null,
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)), {},
        )

        lifecycle.resume()
        component.onAction(ScreenAction.Select("zone-1"))
        lifecycle.pause()
        lifecycle.resume()

        assertEquals("zone-1", component.model.value.selectedItemId)
        assertEquals(1, manager.watches.size)
        lifecycle.pause()
        lifecycle.destroy()
    }

    @Test
    fun duplicateCommandCallbacksInvokeManagerExactlyOnceWhileInFlight() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = FakeManager(
            ScreenSnapshot(
                items = listOf(
                    ScreenItem(
                        "today:action-1",
                        authoredTitle = "Sow",
                        commands = listOf(ScreenItemCommand("complete-action", "action-1")),
                    ),
                ),
            ),
        )
        val component = TodayComponentDefault(
            DefaultComponentContext(LifecycleRegistry()), null,
            ScreenComponentDependencies(DefaultStoreFactory(), manager, dispatcher), {},
        )
        val command = ScreenCommand("complete-action", "action-1", deduplicationKey = "complete:action-1")
        component.onAction(ScreenAction.Execute(command))
        component.onAction(ScreenAction.Execute(command))
        assertEquals(1, manager.executions)
    }

    @Test
    fun itemTargetedCommandsRequireExactExplicitSubject() = runTest {
        val manager = FakeManager(
            ScreenSnapshot(
                items = listOf(
                    ScreenItem(
                        "today:action-1",
                        authoredTitle = "Sow",
                        commands = listOf(
                            ScreenItemCommand("complete-action", "action-1"),
                            ScreenItemCommand("skip-action", "action-1"),
                        ),
                    ),
                ),
            ),
        )
        val component = TodayComponentDefault(
            DefaultComponentContext(LifecycleRegistry()),
            null,
            ScreenComponentDependencies(
                DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler),
            ),
            {},
        )

        component.onAction(ScreenAction.Execute(ScreenCommand("complete-action", "action-1")))
        component.onAction(ScreenAction.Execute(ScreenCommand("skip-action", "today:action-1")))
        component.onAction(ScreenAction.Execute(ScreenCommand("complete-action")))

        assertEquals(listOf(ScreenCommand("complete-action", "action-1")), manager.commands)
        assertEquals(TextKey("validation_required_selection"), component.model.value.validationErrors[ScreenFieldId("id")])
    }

    @Test
    fun destructiveOperationsAreClassifiedByExactCommandName() {
        val expected = mapOf(
            "correct-batch" to DestructiveOperation.SAVE_BATCH_CORRECTION,
            "undo-stage" to DestructiveOperation.UNDO_LAST_TRANSITION,
            "delete-zone" to DestructiveOperation.DELETE_ZONE,
            "delete-tray-preset" to DestructiveOperation.DELETE_TRAY_PRESET,
            "delete-recipe" to DestructiveOperation.DELETE_RECIPE,
            "delete-planned-batch" to DestructiveOperation.DELETE_BATCH,
            "permanently-delete-batch" to DestructiveOperation.DELETE_BATCH,
            "delete-plan" to DestructiveOperation.DELETE_PLAN,
            "delete-observation" to DestructiveOperation.DELETE_OBSERVATION,
            "replace-restore" to DestructiveOperation.REPLACE_RESTORE,
            "delete-all-data" to DestructiveOperation.DELETE_ALL_DATA,
            "discard-changes" to DestructiveOperation.DISCARD_CHANGES,
        )

        expected.forEach { (operation, classification) ->
            assertEquals(classification, DestructiveCommandClassifier.classify(operation))
        }
        assertNull(DestructiveCommandClassifier.classify("delete-zone-preview"))
        assertNull(DestructiveCommandClassifier.classify("complete-action"))
    }

    @Test
    fun destructiveExecuteRequestsConfirmationBeforeStoreAndConfirmedPathExecutesOnce() = runTest {
        val command = ScreenCommand(
            operation = "correct-batch",
            subjectId = "batch-1",
            arguments = mapOf("actualSeedMassGrams" to "15.5"),
            deduplicationKey = "correct-batch:batch-1:15.5",
        )
        val manager = ScenarioManager(ScreenSnapshot(), itemSnapshot("future-action", "Harvest"))
        val outputs = mutableListOf<ScreenOutput>()
        val component = BatchEditorComponentDefault(
            DefaultComponentContext(LifecycleRegistry()), "batch-1",
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)),
            outputs::add,
        )

        component.onAction(ScreenAction.Execute(command))

        assertTrue(manager.commands.isEmpty())
        assertEquals(
            listOf<ScreenOutput>(
                ScreenOutput.ConfirmationRequested(
                    ScreenId.BATCH_EDITOR,
                    command,
                    DestructiveOperation.SAVE_BATCH_CORRECTION,
                ),
            ),
            outputs,
        )

        component.onAction(ScreenAction.ExecuteConfirmed(command))
        component.onAction(ScreenAction.ExecuteConfirmed(command))

        assertEquals(listOf(ScreenId.BATCH_EDITOR to command), manager.commands)
        assertEquals(ScreenOutput.Completed("correct-batch", "batch-1"), outputs.last())
    }

    @Test
    fun failedCommandMapsToErrorStateWithoutPublishingACompletionLabel() = runTest {
        val cause = IllegalStateException("write failed")
        val manager = object : ScreenDataManager {
            override fun watch(screen: ScreenId, stableId: String?) = kotlinx.coroutines.flow.flowOf(
                ScreenSnapshot(
                    items = listOf(
                        ScreenItem(
                            "today:action-1",
                            authoredTitle = "Sow",
                            commands = listOf(ScreenItemCommand("complete-action", "action-1")),
                        ),
                    ),
                ),
            )
            override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> = Result.failure(cause)
        }
        val outputs = mutableListOf<ScreenOutput>()
        val component = TodayComponentDefault(
            DefaultComponentContext(LifecycleRegistry()), null,
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher(testScheduler)), outputs::add,
        )

        component.onAction(ScreenAction.Execute(ScreenCommand("complete-action", "action-1")))

        assertEquals(LoadState.ERROR, component.model.value.loadState)
        assertEquals(TextKey("error_operation_failed"), component.model.value.errorKey)
        assertTrue(outputs.isEmpty())
        assertFalse(component.model.value.busy)
    }

    @Test
    fun cancellationAwareResultBoundaryRethrowsCancellation() {
        assertFailsWith<CancellationException> {
            Result.failure<Unit>(CancellationException("cancelled")).unwrap(
                onSuccess = {},
                onFailure = {},
            )
        }
    }

    @Test
    fun resultBoundaryPreservesNullableSuccessAndFailureCause() {
        var nullableSuccess = false
        Result.success<String?>(null).unwrap(onSuccess = { nullableSuccess = true }, onFailure = { fail(it.message) })
        assertTrue(nullableSuccess)

        val cause = IllegalStateException("disk unavailable")
        var observed: Throwable? = null
        Result.failure<Unit>(cause).unwrap(onSuccess = {}, onFailure = { observed = it })
        assertSame(cause, observed)
    }

    private fun ScreenContract.field(id: String): ScreenField = fields.single { it.id.value == id }

    private fun ScreenField.optionIds(): Set<String> = options.mapTo(mutableSetOf(), ScreenOption::id)

    private class FakeManager(initial: ScreenSnapshot) : ScreenDataManager {
        private val state = MutableStateFlow(initial)
        var executions = 0
        val commands = mutableListOf<ScreenCommand>()
        override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> = state
        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            executions++
            commands += command
            return Result.success(state.value)
        }
    }

    private fun itemSnapshot(id: String, title: String) = ScreenSnapshot(
        items = listOf(ScreenItem(id = id, authoredTitle = title)),
    )

    private suspend fun assertSnapshotScenario(
        screen: ScreenId,
        snapshot: ScreenSnapshot,
        stableId: String? = null,
        expectedItemId: String? = null,
        expectedCount: Int = snapshot.items.size,
        expectedConflictCount: Int = snapshot.conflictResolutionKeys.size,
    ) {
        val manager = ScenarioManager(snapshot)
        val component = ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher()),
        ).create(screen, DefaultComponentContext(LifecycleRegistry()), stableId) {}

        assertEquals(LoadState.CONTENT, component.model.value.loadState)
        assertEquals(expectedCount, component.model.value.items.size)
        expectedItemId?.let { assertEquals(it, component.model.value.items.single().id) }
        assertEquals(expectedConflictCount, component.model.value.conflictResolutionKeys.size)
        assertEquals(screen to stableId, manager.watches.single())
    }

    private suspend fun assertCommandScenario(
        screen: ScreenId,
        command: ScreenCommand,
        result: ScreenSnapshot,
        expectedConflictKey: String? = null,
    ) {
        val manager = ScenarioManager(ScreenSnapshot(), result)
        val outputs = mutableListOf<ScreenOutput>()
        val component = ScreenComponentFactory(
            ScreenComponentDependencies(DefaultStoreFactory(), manager, UnconfinedTestDispatcher()),
        ).create(screen, DefaultComponentContext(LifecycleRegistry()), command.subjectId, outputs::add)

        component.onAction(ScreenAction.Execute(command))
        if (command.destructiveOperation != null) {
            assertTrue(manager.commands.isEmpty())
            assertEquals(
                ScreenOutput.ConfirmationRequested(screen, command, command.destructiveOperation!!),
                outputs.single(),
            )
            outputs.clear()
            component.onAction(ScreenAction.ExecuteConfirmed(command))
            component.onAction(ScreenAction.ExecuteConfirmed(command))
        } else {
            component.onAction(ScreenAction.Execute(command))
        }

        assertEquals(listOf(screen to command), manager.commands)
        assertEquals(listOf<ScreenOutput>(ScreenOutput.Completed(command.operation, command.subjectId)), outputs)
        assertEquals(result.items, component.model.value.items)
        expectedConflictKey?.let {
            assertTrue(component.model.value.conflictResolutionKeys.any { key -> key.value == it })
        }
        assertFalse(component.model.value.busy)
    }

    private class ScenarioManager(
        initial: ScreenSnapshot,
        private val result: ScreenSnapshot = initial,
    ) : ScreenDataManager {
        private val state = MutableStateFlow(initial)
        val watches = mutableListOf<Pair<ScreenId, String?>>()
        val commands = mutableListOf<Pair<ScreenId, ScreenCommand>>()

        override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> {
            watches += screen to stableId
            return state
        }

        override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> {
            commands += screen to command
            state.value = result
            return Result.success(result)
        }
    }
}
