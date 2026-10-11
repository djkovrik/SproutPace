package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.CommandPreparation
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.WorkspaceSetupState
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SetupReadinessIntegrationTest {
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase
    private lateinit var manager: ProductionScreenDataManager

    @BeforeTest
    fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
        manager = ProductionScreenDataManager(database, MultiplatformProductSettings(MapSettings()),
            now = { Instant.parse("2026-10-10T09:00:00Z") }, today = { LocalDate.parse("2026-10-10") })
    }

    @AfterTest
    fun tearDown() {
        database.close()
        factory.close()
    }

    @Test
    fun skippedSetupExposesResumeAndConfiguredWorkspaceWithoutPlanExposesPlanCreation() = runTest {
        manager.watch(ScreenId.CATALOG).first()
        listOf(ScreenId.TODAY, ScreenId.PLAN).forEach { screen ->
            val snapshot = manager.watch(screen).first()
            assertEquals(WorkspaceSetupState.NOT_CONFIGURED, snapshot.setupState)
            assertEquals(listOf("resume-setup"), snapshot.actions.map { it.id.value })
            assertTrue(snapshot.fields.isEmpty())
        }
        database.saveZone(GrowZone(GrowZoneId("zone"), "Shelf", GrowZoneType.GROW_LIGHT, 3))
        database.saveTrayPreset(TraySizePreset(TraySizePresetId("tray"), "Tray", TrayShape.Rectangle(20.0, 30.0)))
        assertEquals("open-plan", manager.watch(ScreenId.TODAY).first().actions.single().id.value)
        val plan = manager.watch(ScreenId.PLAN).first()
        assertEquals(WorkspaceSetupState.NO_PLAN, plan.setupState)
        assertTrue(plan.fields.any { it.id.value == "cadenceDays" })
        assertTrue(plan.actions.any { it.id.value == "save-plan" })
        assertTrue(database.snapshot().batches.isEmpty())
    }

    @Test
    fun finishingSetupProducesRecommendationButNoBatchUntilExplicitSave() = runTest {
        val snapshot = manager.watch(ScreenId.FIRST_RUN).first()
        val draft = snapshot.fields.associate { it.id to it.value } + mapOf(
            ScreenFieldId("zoneName") to "Kitchen shelf", ScreenFieldId("trayName") to "Small tray",
        )
        val setup = assertIs<CommandPreparation.Ready>(ScreenContractRegistry.prepareCommand(
            ScreenId.FIRST_RUN, ScreenActionId("continue-setup"), null, draft,
        )).command
        manager.execute(ScreenId.FIRST_RUN, setup).getOrThrow()
        val plan = manager.watch(ScreenId.PLAN).first()
        assertEquals(WorkspaceSetupState.NO_BATCHES, plan.setupState)
        assertTrue(plan.items.any { it.planEntry != null })
        assertTrue(database.snapshot().batches.isEmpty())
        manager.execute(ScreenId.BATCH_EDITOR, ScreenCommand("save-batch", "saved-batch", mapOf(
            "cultureId" to "radish", "trayId" to "first-run-tray", "substrate" to "Coir",
            "plannedSowDate" to "2026-10-10",
        ))).getOrThrow()
        assertEquals(1, database.snapshot().batches.size)
        assertEquals(WorkspaceSetupState.ALL_CLEAR, manager.watch(ScreenId.TODAY).first().setupState)
    }
}
