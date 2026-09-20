package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.days

class LocalDatabaseIntegrationTest {
    private lateinit var factory: TestSqlDriverFactory
    private lateinit var database: LocalDatabase

    @BeforeTest fun setUp() {
        factory = TestSqlDriverFactory()
        database = LocalDatabase(factory, Dispatchers.Unconfined)
    }
    @AfterTest fun tearDown() { database.close(); factory.close() }

    @Test fun setupRecipeBatchPlanAndObservationSurviveRestartBoundary() = runTest {
        val zone = GrowZone(GrowZoneId("zone-1"), "Shelf", GrowZoneType.GROW_LIGHT, 3)
        val tray = TraySizePreset(TraySizePresetId("tray-1"), "10 × 20", TrayShape.Rectangle(25.4, 50.8))
        val recipe = recipe()
        val batch = batch(recipe)
        val plan = HarvestPlan(HarvestPlanId("plan-1"), kotlinx.datetime.LocalDate(2026, 9, 20), HarvestCadenceDays(7), listOf(CultureId("radish")), HarvestPlanMode.CADENCE)
        val observation = Observation(ObservationId("observation-1"), batch.id, Instant.parse("2026-09-20T10:00:00Z"), "Рост хороший")

        database.saveZone(zone); database.saveTrayPreset(tray); database.saveRecipe(recipe)
        database.saveBatch(batch); database.savePlan(plan); database.saveObservation(observation)

        assertEquals(listOf(zone), database.watchZones().first())
        assertEquals(listOf(tray), database.watchTrayPresets().first())
        assertEquals(listOf(recipe), database.watchRecipes().first())
        assertEquals(listOf(batch), database.watchBatches().first())
        assertEquals(listOf(plan), database.watchPlans().first())
        assertEquals(listOf(observation), database.watchObservations().first())
    }

    @Test fun deletingSourcesDoesNotMutateBatchSnapshots() = runTest {
        val recipe = recipe(); val batch = batch(recipe)
        database.saveRecipe(recipe); database.saveBatch(batch)
        database.deleteRecipe(recipe.id)
        assertEquals(batch, database.watchBatches().first().single())
    }

    @Test fun replacementIsIdempotentAndRejectsOrphansBeforeMutation() = runTest {
        database.saveBatch(batch(recipe()))
        val original = database.snapshot()
        database.replace(original); database.replace(original)
        assertEquals(original.batches, database.snapshot().batches)
        val invalid = original.copy(
            manifest = original.manifest.copy(recordCounts = original.manifest.recordCounts + ("observations" to 1)),
            observations = listOf(Observation(ObservationId("orphan"), BatchId("missing"), Instant.parse("2026-09-20T10:00:00Z"), "x")),
        )
        assertIs<RestoreValidation.Invalid>(SnapshotValidation.validate(invalid))
        assertEquals(original.batches, database.snapshot().batches)
    }

    @Test fun foreignKeyFailureRollsBackWithoutPartialObservation() = runTest {
        val orphan = Observation(
            ObservationId("orphan"), BatchId("missing"), Instant.parse("2026-09-20T10:00:00Z"), "text",
        )
        assertFails { database.saveObservation(orphan) }
        assertTrue(database.snapshot().observations.isEmpty())
    }

    @Test fun catalogIdentityAndLocalizationKeysRoundTripWithoutTranslatedText() = runTest {
        assertEquals(
            setOf("radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress"),
            SproutPaceCatalog.cultureIds.mapTo(linkedSetOf()) { it.value },
        )
        val recipe = recipe(); database.saveRecipe(recipe)
        val restored = database.snapshot().recipes.single()
        assertEquals(recipe.stages.map { it.guidanceKey }, restored.stages.map { it.guidanceKey })
        assertEquals(CultureId("radish"), restored.cultureId)
    }

    @Test fun backupFileFailurePreservesDatabaseAndFiles() = runTest {
        val files = InMemoryPrivateFileStore()
        val settings = MultiplatformProductSettings(MapSettings())
        val service = LocalBackupService(database, files, settings, Dispatchers.Unconfined)
        val existing = batch(recipe()); database.saveBatch(existing)
        files.write(PhotoId("old"), byteArrayOf(1, 2, 3))
        val replacement = database.snapshot().copy(batches = listOf(existing.copy(id = BatchId("replacement"))))
        val archive = LocalBackupArchive(
            replacement.copy(manifest = SnapshotValidation.manifest(
                replacement.zones, replacement.trayPresets, replacement.recipes, replacement.batches,
                replacement.plans, replacement.observations, emptyMap(),
            )), emptyList(),
        )
        files.failNextReplace = IllegalStateException("insufficient storage")
        val failure = assertFailsWith<LocalDataException> { service.replaceArchive(archive) }
        assertEquals(LocalDataErrorKey.RESTORE_FAILED, failure.key)
        assertIs<IllegalStateException>(failure.cause)
        assertEquals(listOf(existing), database.snapshot().batches)
        assertContentEquals(byteArrayOf(1, 2, 3), files.read(PhotoId("old")))
    }

    @Test fun settingsFallbacksUpdatesAndResetAreTyped() {
        val raw = MapSettings().apply {
            putBoolean("dark_mode", true)
            putString("advertising_privacy.v1", "malformed")
        }
        val settings = MultiplatformProductSettings(raw)
        assertEquals(ThemePreference.DARK, settings.theme.value)
        assertNull(settings.privacyState.value)
        assertTrue(settings.crashReportingEnabled.value)
        settings.setTheme(ThemePreference.LIGHT)
        settings.setCrashReportingEnabled(false)
        settings.setPrivacyState(AdvertisingPrivacyState(policyVersion = "policy-1", expiresAtEpochMillis = 42, choice = ConsentChoice.ACCEPTED))
        assertEquals(ConsentChoice.ACCEPTED, settings.privacyState.value?.choice)
        settings.clearProductState()
        assertEquals(ThemePreference.SYSTEM, settings.theme.value)
        assertTrue(settings.crashReportingEnabled.value)
    }

    @Test fun csvIsUtf8EscapedDocumentedAndPhotoFree() = runTest {
        val harvested = batch(recipe()).copy(
            state = BatchState.HARVESTED,
            outcome = BatchOutcome.Harvested(Instant.parse("2026-09-20T10:00:00Z"), PositiveGrams(12.5), note = "great, \"fresh\"\nline"),
        )
        database.saveBatch(harvested)
        val csv = BatchHistoryCsv.encode(database.snapshot()).decodeToString()
        assertTrue(csv.startsWith(BatchHistoryCsv.HEADER))
        assertTrue(csv.contains("\"great, \"\"fresh\"\"\nline\""))
        assertFalse(csv.contains("photo"))
    }

    @Test fun eraseClearsDatabaseFilesAndSettings() = runTest {
        val files = InMemoryPrivateFileStore()
        val settings = MultiplatformProductSettings(MapSettings()).apply { setTheme(ThemePreference.DARK) }
        val service = LocalBackupService(database, files, settings, Dispatchers.Unconfined)
        database.saveBatch(batch(recipe())); files.write(PhotoId("photo-1"), byteArrayOf(9))
        service.eraseAll()
        assertTrue(database.snapshot().batches.isEmpty())
        assertTrue(files.exportAll().isEmpty())
        assertEquals(ThemePreference.SYSTEM, settings.theme.value)
    }

    private fun recipe(): UserGrowRecipe {
        val catalog = SproutPaceCatalog.cultures.first()
        return UserGrowRecipe(
            RecipeId("recipe-1"), "My radish", catalog.id,
            catalog.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) },
            catalog.seedDensity, "coco coir",
        )
    }

    private fun batch(recipe: UserGrowRecipe): GrowBatch = GrowBatch(
        id = BatchId("batch-1"),
        recipe = RecipeSnapshot(recipe.id, recipe.cultureId, recipe.name, recipe.stages, recipe.density),
        tray = TraySnapshot("10 × 20", TrayShape.Rectangle(25.4, 50.8)),
        substrateSnapshot = recipe.substrate,
        recommendedSeedMass = PositiveGrams(10.0),
        actualSeedMass = PositiveGrams(11.0),
        state = BatchState.PLANNED,
        placement = Placement.None,
        events = emptyList(), actions = emptyList(),
    )
}
