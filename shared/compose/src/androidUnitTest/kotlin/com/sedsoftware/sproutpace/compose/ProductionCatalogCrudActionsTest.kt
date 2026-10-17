package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Public feature scenarios complement isolated Store contracts; all results come from production SQLite. */
class ProductionCatalogCrudActionsTest {
    @Test fun freshOfflineCatalogAndEveryBuiltInGuideExposeShippedContentAndSources() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences, initialScreen = ScreenId.FIRST_RUN).use { f ->
                assertEquals(ScreenId.FIRST_RUN, f.model().screen)
                f.edit(mapOf("zoneName" to "Unsaved setup"))
                f.dispatch(UiAction.Navigate(UiScreen.CATALOG))
                val ids = setOf("radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress")
                assertEquals(ids, f.model().items.map { it.id }.toSet())
                assertTrue(f.model().offline)
                assertTrue(f.database.snapshot().batches.isEmpty())
                f.dispatch(UiAction.Back)
                assertEquals(ScreenId.FIRST_RUN, f.model().screen)
                assertEquals("Unsaved setup", f.field("zoneName"))
                f.dispatch(UiAction.Navigate(UiScreen.CATALOG))
                ids.forEach { id ->
                    f.dispatch(UiAction.Submit("open-recipe", id))
                    assertEquals(ScreenId.RECIPE, f.model().screen)
                    val model = f.model()
                    val guide = assertNotNull(model.items.first().recipe)
                    val culture = SproutPaceCatalog.cultures.single { it.id.value == id }
                    assertTrue(guide.readOnly)
                    assertEquals(culture.stages.map { it.duration.minimum.inWholeHours }, guide.stages.map { it.minimumHours })
                    assertEquals(culture.seedDensity.gramsPerSquareCentimetre, guide.gramsPerSquareCentimetre)
                    assertEquals(culture.sources.map { it.url }, guide.sources.map { it.url })
                    assertTrue(model.items.any { it.id.startsWith("safety:") })
                    assertTrue(model.items.any { it.id.startsWith("source:") })
                    assertFalse(model.actions.any { it.id.value in setOf("save-recipe", "update-recipe", "delete-recipe") })
                    assertTrue(model.actions.any { it.id.value == "duplicate-recipe" })
                    f.dispatch(UiAction.Back)
                    assertEquals(ScreenId.CATALOG, f.model().screen)
                }
                assertTrue(f.database.snapshot().recipes.isEmpty())
            }
        }
    }

    @Test fun publicZoneFieldsOccupancyAndConfirmedDeletionSurviveReopenWithoutErasingHistory() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                val original = f.database.snapshot().batches.single()
                val assigned = Placement.Assigned(GrowZoneId("zone"), "Shelf")
                f.database.saveBatch(original.copy(state = BatchState.SOWN, placement = assigned))
                f.database.saveBatch(original.copy(id = BatchId("history"), state = BatchState.HARVESTED,
                    placement = assigned, outcome = BatchOutcome.Harvested(f.now()),
                    actions = original.actions.map { it.copy(batchId = BatchId("history"), status = ScheduledActionStatus.CANCELLED) }))
                f.save(ScreenId.ZONES, "zone", "update-zone", mapOf("name" to "Renamed Shelf", "capacity" to "3"))
                assertEquals("Renamed Shelf", f.database.snapshot().zones.single().name)
            }
            PublicProductionFixture(directory, preferences).use { f ->
                f.open(ScreenId.ZONES, "zone")
                assertEquals("Renamed Shelf", f.field("name"))
                assertEquals("grow_light", f.field("type"))
                assertEquals("3", f.field("capacity"))
                val occupancy = assertNotNull(f.model().items.single { it.id == "zone" }.occupancy)
                assertEquals(1, occupancy.occupied)
                assertEquals(2, occupancy.available)
                f.submit("delete-zone", confirm = false)
                assertEquals("delete-zone", assertNotNull(f.root.confirmation.value.child).instance.command.operation)
                assertEquals(1, f.database.snapshot().zones.size)
                f.root.confirmation.value.child!!.instance.cancel()
                assertEquals(1, f.database.snapshot().zones.size)
                f.submit("delete-zone")
                val snapshot = f.database.snapshot()
                assertTrue(snapshot.zones.isEmpty())
                assertEquals(2, snapshot.batches.size)
                assertIs<Placement.Unassigned>(snapshot.batches.single { it.id.value == "batch" }.placement)
                assertEquals("Shelf", (snapshot.batches.single { it.id.value == "history" }.placement as Placement.Assigned).zoneNameSnapshot)
                f.open(ScreenId.TRAYS)
                f.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY))
                assertEquals("Shelf", f.model().toUiState().items.single { it.id == "history" }.batch?.zoneName)
            }
            PublicProductionFixture(directory, preferences).use { f ->
                f.open(ScreenId.ZONES)
                assertTrue(f.model().items.isEmpty())
                f.open(ScreenId.BATCH_DETAIL, "history")
                assertEquals(BatchState.HARVESTED, f.model().items.single { it.id == "history" }.batch?.state)
            }
        }
    }

    @Test fun publicPresetReadUpdateAndConfirmedDeleteKeepExistingSnapshotsAndChangeFutureSelection() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                f.save(ScreenId.TRAY_SIZES, "tray", "update-tray-size", mapOf(
                    "name" to "Round", "shape" to "circle", "diameterCm" to "16.5"))
                val original = f.database.snapshot().batches.single()
                assertEquals(TraySnapshot("Tray", TrayShape.Rectangle(20.0, 30.0)), original.tray)
            }
            PublicProductionFixture(directory, preferences).use { f ->
                f.open(ScreenId.TRAY_SIZES, "tray")
                assertEquals("Round", f.field("name"))
                assertEquals("circle", f.field("shape"))
                assertEquals("16.5", f.field("diameterCm"))
                f.save(ScreenId.BATCH_EDITOR, "future", "save-batch", mapOf(
                    "cultureId" to "radish", "trayId" to "tray", "substrate" to "Coir", "plannedSowDate" to "2026-10-11"))
                val future = f.database.snapshot().batches.single { it.id.value != "batch" }
                assertEquals(TraySnapshot("Round", TrayShape.Circle(16.5)), future.tray)
                f.open(ScreenId.TRAY_SIZES, "tray")
                f.submit("delete-tray-size", confirm = false)
                assertNotNull(f.root.confirmation.value.child)
                assertEquals(1, f.database.snapshot().trayPresets.size)
                f.root.confirmation.value.child!!.instance.confirm()
                assertTrue(f.database.snapshot().trayPresets.isEmpty())
                assertEquals(2, f.database.snapshot().batches.size)
                f.open(ScreenId.BATCH_EDITOR)
                assertTrue(f.model().fields.single { it.id.value == "trayId" }.options.isEmpty())
                f.open(ScreenId.BATCH_DETAIL, "batch")
                assertEquals("Tray", f.model().items.single { it.id == "batch" }.batch?.trayName)
                f.open(ScreenId.BATCH_DETAIL, future.id.value)
                assertEquals("Round", f.model().items.single { it.id == future.id.value }.batch?.trayName)
            }
        }
    }

    @Test fun publicBuiltInAndPersonalDuplicationUpdateAndConfirmedDeleteKeepBatchRecipeSnapshot() = runTest {
        withPublicWorkspace { directory, preferences ->
            var originalSnapshot: RecipeSnapshot? = null
            var historyId: BatchId? = null
            PublicProductionFixture(directory, preferences).use { f ->
                f.seed()
                f.save(ScreenId.RECIPE, "radish", "duplicate-recipe", mapOf(
                    "newId" to "personal", "name" to "My radish", "substrate" to "Coir"))
                val personal = f.database.snapshot().recipes.single()
                assertEquals("personal", personal.id.value)
                assertEquals(SproutPaceCatalog.cultures.single { it.id.value == "radish" }.seedDensity, personal.density)
                f.open(ScreenId.RECIPE, "personal")
                assertFalse(assertNotNull(f.model().items.first().recipe).readOnly)
                f.edit(mapOf("newId" to "copy", "name" to "Independent copy"))
                f.submit("duplicate-recipe")
                assertEquals(setOf("personal", "copy"), f.database.snapshot().recipes.map { it.id.value }.toSet())
                f.save(ScreenId.BATCH_EDITOR, "recipe-history", "save-batch", mapOf(
                    "recipeId" to "personal", "trayId" to "tray", "substrate" to "Coir", "plannedSowDate" to "2026-10-11"))
                val batch = f.database.snapshot().batches.single { it.id.value != "batch" }
                historyId = batch.id
                originalSnapshot = batch.recipe
                f.database.saveBatch(batch.copy(state = BatchState.HARVESTED, outcome = BatchOutcome.Harvested(f.now())))
                f.save(ScreenId.RECIPE, "personal", "update-recipe", mapOf(
                    "name" to "Edited radish", "substrate" to "Hemp", "stage2Guidance" to "My later guidance"))
                val snapshot = f.database.snapshot()
                assertEquals(originalSnapshot, snapshot.batches.single { it.id == historyId }.recipe)
                assertEquals("Edited radish", snapshot.recipes.single { it.id.value == "personal" }.name)
                assertEquals("Independent copy", snapshot.recipes.single { it.id.value == "copy" }.name)
            }
            PublicProductionFixture(directory, preferences).use { f ->
                f.open(ScreenId.RECIPE, "personal")
                assertEquals("Edited radish", f.field("name"))
                assertEquals("Hemp", f.field("substrate"))
                assertEquals("My later guidance", f.field("stage2Guidance"))
                f.submit("delete-recipe", confirm = false)
                assertNotNull(f.root.confirmation.value.child)
                assertEquals(2, f.database.snapshot().recipes.size)
                f.root.confirmation.value.child!!.instance.confirm()
                assertEquals(listOf("copy"), f.database.snapshot().recipes.map { it.id.value })
                assertEquals(originalSnapshot, f.database.snapshot().batches.single { it.id == historyId }.recipe)
                f.open(ScreenId.TRAYS)
                f.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY))
                assertEquals(BatchState.HARVESTED, f.model().toUiState().items.single { it.id == historyId?.value }.batch?.state)
                f.open(ScreenId.RECIPE, "radish")
                assertTrue(assertNotNull(f.model().items.first().recipe).readOnly)
                assertEquals(SproutPaceCatalog.cultures.single { it.id.value == "radish" }.stages.map { it.instructionKey.value },
                    f.model().items.filter { it.id.startsWith("stage:") }.map { it.supportingKey?.value })
            }
        }
    }
}
