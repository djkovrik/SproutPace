package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestCadenceDays
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanId
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.StageEvent
import com.sedsoftware.sproutpace.domain.model.StageEventId
import com.sedsoftware.sproutpace.domain.model.StageEventKind
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.datetime.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** Domain fixtures use the same renderer and interaction builder as persisted production data. */
object ProductionScreenFixtures {
    enum class Scenario {
        CONTENT, EMPTY, CAPACITY_CONFLICT, UNASSIGNED, RESTORE_PREVIEW, HISTORY, ACTIVE, PRIVACY_AVAILABLE,
        DETAIL_PLANNED, DETAIL_SOAKING, DETAIL_SOWN, DETAIL_BLACKOUT, DETAIL_READY, DETAIL_HARVESTED, DETAIL_FAILED, DETAIL_ABANDONED,
        SKIPPED, CORRECTION, PERSONAL_RECIPE, NEW_RECIPE, CRESS_RECIPE,
        NEW_ZONE, EDIT_ZONE, NEW_TRAY, EDIT_TRAY, EDIT_CIRCLE, EDIT_OBSERVATION, NOTIFICATIONS_DISABLED,
        PHOTO, LONG_NAMES, BACKUP_CREATED, BACKUP_RESTORED, CSV_EXPORTED, BACKUP_WRITE_FAILED,
        OUTDATED_RECIPE, FIXED_READY, REPEATED_BATCH, NO_IMAGE_RECIPE, LONG_CULTURE_NAMES, NEEDS_RESOLUTION,
        FUTURE_ZONE_CONFLICT,
    }

    fun selectedId(screen: ScreenId): String? = when (screen) {
        ScreenId.BATCH_DETAIL, ScreenId.OBSERVATION_EDITOR -> "active-radish"
        ScreenId.CLOSE_BATCH -> "ready-broccoli"
        ScreenId.RECIPE -> "radish"
        ScreenId.PLAN -> "rotation"
        else -> null
    }

    fun snapshot(
        screen: ScreenId,
        stableId: String? = selectedId(screen),
        scenario: Scenario = Scenario.CONTENT,
    ): ScreenSnapshot {
        val workspace = workspace(scenario)
        val selection = fixtureSelection(scenario, stableId, screen, workspace)
        val notificationsEnabled = scenario != Scenario.NOTIFICATIONS_DISABLED
        val preferences = SettingsSnapshot(
            ThemePreference.SYSTEM, false, notificationsEnabled, SummaryLocalTime(9, 0), notificationsEnabled,
            privacy = if (scenario == Scenario.PRIVACY_AVAILABLE) AdvertisingPrivacyState(
                policyVersion = "2026-10", fetchedAtEpochMillis = NOW.toEpochMilliseconds() - 1_000,
                expiresAtEpochMillis = NOW.toEpochMilliseconds() + 86_400_000,
                choice = ConsentChoice.ACCEPTED,
            ) else null,
        )
        val archive = if (scenario == Scenario.RESTORE_PREVIEW) archive(workspace) else null
        val renderer = ProductionScreenSnapshotRenderer({ NOW }, { TODAY }, { TimeZone.UTC })
        val builder = ProductionScreenInteractionBuilder(
            now = { NOW }, today = { TODAY }, backupDocumentId = { archive?.let { "archive-fixture" } },
            backupPreview = { archive?.let { BackupPreview.Valid(it) } },
            timeZone = { TimeZone.UTC },
        )
        val photos = if (scenario == Scenario.PHOTO) mapOf(PhotoId("available-photo") to fixturePhotoBytes()) else emptyMap()
        val result = builder.apply(renderer.snapshot(screen, selection, workspace, preferences, photos),
            screen, selection, workspace, preferences, DraftIdentitySession("fixture")).withBackupResult(when (scenario) {
                Scenario.BACKUP_CREATED -> "backup_created"
                Scenario.BACKUP_RESTORED -> "backup_restored"
                Scenario.CSV_EXPORTED -> "backup_csv_exported"
                else -> null
            })
        return if (scenario in setOf(Scenario.HISTORY, Scenario.ACTIVE) && screen == ScreenId.TRAYS) {
            result.copy(fields = result.fields.map {
                if (it.id == ScreenFieldId("category")) it.copy(value = if (scenario == Scenario.HISTORY) "history" else "active") else it
            })
        } else result
    }

    private fun fixtureSelection(scenario: Scenario, fallback: String?, screen: ScreenId, workspace: Workspace): String? {
        if (scenario == Scenario.NEEDS_RESOLUTION && screen == ScreenId.BATCH_EDITOR) {
            return ProductionScreenSnapshotRenderer({ NOW }, { TODAY }, { TimeZone.UTC })
                .snapshot(ScreenId.PLAN, "rotation", workspace, SettingsSnapshot(ThemePreference.SYSTEM, false, false,
                    SummaryLocalTime(), false, null)).items.first {
                    it.planEntry?.status == com.sedsoftware.sproutpace.component.PlanEntryStatus.NEEDS_RESOLUTION
                }.id
        }
        return scenarioSelection(scenario, fallback, screen)
    }

    private fun scenarioSelection(scenario: Scenario, fallback: String?, screen: ScreenId): String? = when (scenario) {
        Scenario.DETAIL_PLANNED -> "planned-pea"
        Scenario.DETAIL_SOAKING -> "soaking-pea"
        Scenario.DETAIL_READY -> "ready-broccoli"
        Scenario.DETAIL_HARVESTED -> "history-0"
        Scenario.DETAIL_FAILED -> "history-1"
        Scenario.DETAIL_ABANDONED -> "history-2"
        Scenario.CORRECTION -> "active-radish"
        Scenario.PERSONAL_RECIPE -> "house-radish"
        Scenario.NEW_RECIPE -> null
        Scenario.CRESS_RECIPE -> "garden_cress"
        Scenario.NEW_ZONE -> "zone-new"
        Scenario.EDIT_ZONE -> "shelf"
        Scenario.NEW_TRAY -> "tray-new"
        Scenario.EDIT_TRAY -> "shallow"
        Scenario.EDIT_CIRCLE -> "round"
        Scenario.EDIT_OBSERVATION -> "growth"
        Scenario.OUTDATED_RECIPE -> "house-radish"
        Scenario.NO_IMAGE_RECIPE -> "house-radish"
        Scenario.REPEATED_BATCH -> "repeat-history-0"
        Scenario.FIXED_READY -> if (screen == ScreenId.PLAN) fallback else "ready-broccoli"
        else -> fallback
    }

    private fun workspace(scenario: Scenario): Workspace {
        if (scenario == Scenario.EMPTY) return Workspace(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val capacity = fixtureCapacity(scenario)
        val zone = GrowZone(GrowZoneId("shelf"), if (scenario == Scenario.LONG_NAMES)
            "Очень длинное название зоны на кухонном подоконнике возле большого восточного окна" else "Window shelf",
            GrowZoneType.NATURAL_LIGHT, capacity)
        val tray = TraySizePreset(TraySizePresetId("shallow"),
            if (scenario == Scenario.LONG_NAMES) LONG_TRAY_NAME else "Shallow tray", TrayShape.Rectangle(25.0, 35.0))
        val radish = SproutPaceCatalog.cultures.first { it.id.value == "radish" }
        val density = if (scenario == Scenario.OUTDATED_RECIPE) radish.seedDensity.copy(source =
            radish.seedDensity.source.copy(reviewedOn = LocalDate.parse("2020-01-01"))) else radish.seedDensity
        val recipe = UserGrowRecipe(RecipeId("house-radish"),
            if (scenario == Scenario.LONG_NAMES) "Семейный рецепт редиса для большого лотка на кухонном подоконнике" else "House radish",
            if (scenario == Scenario.NO_IMAGE_RECIPE) null else radish.id,
            radish.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, density, "Coconut coir")
        val batches = listOf(
            batch("planned-pea", "pea", BatchState.PLANNED, "2026-10-11", "2026-10-11T12:00:00Z"),
            batch("soaking-pea", "pea", BatchState.SOAKING, "2026-10-10", "2026-10-10T12:00:00Z"),
            batch("active-radish", "radish", BatchState.LIGHT_GROWTH, "2026-10-03", "2026-10-09T12:00:00Z"),
            batch("ready-broccoli", "broccoli", BatchState.READY_FOR_HARVEST, "2026-10-02", "2026-10-17T12:00:00Z"),
        ) + List(6) { index ->
            val state = when (index % 3) { 0 -> BatchState.HARVESTED; 1 -> BatchState.FAILED; else -> BatchState.ABANDONED }
            batch("history-$index", if (index % 2 == 0) "radish" else "pea", state, "2026-09-${20 + index}", null)
        }
        val selectedBatches = scenarioBatches(scenario, batches)
        return Workspace(
            zones = listOf(zone, GrowZone(GrowZoneId("cupboard"), "Kitchen cupboard", GrowZoneType.DARK, CUPBOARD_CAPACITY)),
            trays = listOf(tray, TraySizePreset(TraySizePresetId("round"), "Round bowl", TrayShape.Circle(ROUND_DIAMETER_CM))),
            recipes = listOf(recipe), batches = if (scenario == Scenario.LONG_NAMES) selectedBatches.map {
                it.copy(tray = it.tray.copy(nameSnapshot = "Очень длинное название лотка для семейного урожая редиса и гороха"))
            } else selectedBatches,
            plans = listOf(HarvestPlan(HarvestPlanId("rotation"), LocalDate.parse("2026-10-20"), HarvestCadenceDays(PLAN_CADENCE_DAYS),
                if (scenario == Scenario.LONG_CULTURE_NAMES) listOf(CultureId("sunflower"), CultureId("garden_cress"))
                else listOf(CultureId("radish"), CultureId("pea")), HarvestPlanMode.CADENCE)),
            observations = listOf(
                Observation(ObservationId("growth"), BatchId("active-radish"), Instant.parse("2026-10-08T08:00:00Z"),
                    if (scenario == Scenario.LONG_NAMES) LONG_OBSERVATION else "Even growth and fresh leaves",
                    if (scenario == Scenario.PHOTO) PhotoId("available-photo") else null),
                Observation(ObservationId("missing-photo"), BatchId("active-radish"), Instant.parse("2026-10-09T08:00:00Z"),
                    "Roots reached the bottom of the tray", PhotoId("unavailable-photo")),
            ),
        )
    }

    private fun scenarioBatches(scenario: Scenario, batches: List<GrowBatch>): List<GrowBatch> =
        if (scenario in setOf(Scenario.DETAIL_SOWN, Scenario.DETAIL_BLACKOUT)) batches.map { batch ->
            if (batch.id.value != "active-radish") batch else {
                val target = if (scenario == Scenario.DETAIL_SOWN) BatchState.SOWN else BatchState.BLACKOUT_OR_WEIGHT
                batch.copy(state = target, events = batch.events.take(if (target == BatchState.SOWN) 1 else 2))
            }
        } else if (scenario == Scenario.UNASSIGNED) batches.map {
            if (it.id.value == "active-radish") it.copy(placement = Placement.Unassigned("Window shelf")) else it
        } else if (scenario == Scenario.SKIPPED) batches.map { batch ->
            if (batch.id.value != "active-radish") batch else batch.copy(events = batch.events
                .filterNot { it.to == BatchState.BLACKOUT_OR_WEIGHT }.map {
                    if (it.to == BatchState.LIGHT_GROWTH) it.copy(from = BatchState.SOWN,
                        kind = StageEventKind.SKIPPED, reason = "Moved directly to light after inspection") else it
                })
        } else if (scenario == Scenario.FIXED_READY) batches.map { batch ->
            if (batch.id.value == "ready-broccoli") batch.copy(actions = batch.actions.map { it.copy(userFixed = true) }) else batch
        } else if (scenario == Scenario.FUTURE_ZONE_CONFLICT) {
            futureZoneBatches(batches)
        } else if (scenario == Scenario.REPEATED_BATCH) {
            batches + batches.single { it.id.value == "history-0" }.repeatedAs(BatchId("repeat-history-0"))
        } else batches

    private fun fixtureCapacity(scenario: Scenario): Int = when (scenario) {
        Scenario.CAPACITY_CONFLICT, Scenario.NEEDS_RESOLUTION -> 1
        Scenario.FUTURE_ZONE_CONFLICT -> FUTURE_ZONE_CAPACITY
        else -> DEFAULT_ZONE_CAPACITY
    }

    private fun futureZoneBatches(batches: List<GrowBatch>): List<GrowBatch> {
        val future = batches.single { it.id.value == "planned-pea" }
            .copy(placement = Placement.Assigned(GrowZoneId("shelf"), "Window shelf"))
        return batches.filterNot { it.id == future.id } + future + future.copy(
            id = BatchId("future-second-pea"), actions = emptyList(),
        )
    }

    /** Valid tiny PNG exercises the real decoder without a platform asset or file service. */
    private fun fixturePhotoBytes(): ByteArray = kotlin.io.encoding.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAGUlEQVR4nGPUiHITYWDARCwM/AxYweCUAAA1lwHb01DjNwAAAABJRU5ErkJggg==",
    )

    private fun batch(id: String, cultureId: String, state: BatchState, sowDate: String, dueAt: String?): GrowBatch {
        val culture = SproutPaceCatalog.cultures.first { it.id.value == cultureId }
        val batchId = BatchId(id)
        val placement = if (state.occupiesPlace) Placement.Assigned(GrowZoneId("shelf"), "Window shelf") else Placement.None
        val events = batchEvents(id, state, LocalDate.parse(sowDate))
        val closedAt = events.lastOrNull()?.occurredAt ?: NOW
        return GrowBatch(
            id = batchId, recipe = RecipeSnapshot(null, culture.id, null,
                culture.stages.map { RecipeStage(it.stage, it.duration, it.instructionKey) }, culture.seedDensity),
            tray = TraySnapshot(if (cultureId == "pea") "Deep tray" else "Shallow tray",
                TrayShape.Rectangle(TRAY_WIDTH_CM, TRAY_LENGTH_CM)),
            substrateSnapshot = "Coconut coir", recommendedSeedMass = PositiveGrams(RECOMMENDED_GRAMS),
            actualSeedMass = PositiveGrams(ACTUAL_GRAMS),
            state = state, placement = placement, events = events,
            actions = dueAt?.let { listOf(ScheduledAction(ScheduledActionId("action-$id"), batchId,
                if (state == BatchState.PLANNED || state == BatchState.SOAKING) GrowStage.SOWN else GrowStage.READY_FOR_HARVEST,
                DueMoment.ElapsedTarget(Instant.parse(it)), userFixed = id == "planned-pea")) }.orEmpty(),
            plannedSowDate = LocalDate.parse(sowDate),
            outcome = when (state) {
                BatchState.HARVESTED -> BatchOutcome.Harvested(closedAt, PositiveGrams(HARVEST_GRAMS))
                BatchState.FAILED -> BatchOutcome.Failed(closedAt, "Uneven germination")
                BatchState.ABANDONED -> BatchOutcome.Abandoned(closedAt, "Started another batch")
                else -> null
            },
        )
    }

    private fun batchEvents(id: String, state: BatchState, sowDate: LocalDate): List<StageEvent> {
        val stages = when (state) {
            BatchState.PLANNED -> emptyList()
            BatchState.SOAKING -> listOf(BatchState.SOAKING to -1)
            else -> buildList {
                add(BatchState.SOWN to 0)
                add(BatchState.BLACKOUT_OR_WEIGHT to 1)
                add(BatchState.LIGHT_GROWTH to 3)
                if (state == BatchState.READY_FOR_HARVEST || state.isTerminal) add(BatchState.READY_FOR_HARVEST to 7)
                if (state.isTerminal) add(state to 8)
            }
        }
        return stages.mapIndexed { index, (target, days) ->
            val from = stages.getOrNull(index - 1)?.first ?: BatchState.PLANNED
            fun placement(value: BatchState): Placement = if (value.occupiesPlace) {
                Placement.Assigned(GrowZoneId("shelf"), "Window shelf")
            } else Placement.None
            StageEvent(StageEventId("$id-event-$index"), from, target,
                Instant.parse("${sowDate + DatePeriod(days = days)}T08:00:00Z"), StageEventKind.CONFIRMED,
                placementBefore = placement(from), placementAfter = placement(target))
        }
    }

    private fun archive(workspace: Workspace): LocalBackupArchive {
        val observations = workspace.observations.map { it.copy(photoId = null) }
        val manifest = SnapshotValidation.manifest(workspace.zones, workspace.trays, workspace.recipes,
            workspace.batches, workspace.plans, observations, emptyMap())
        val snapshot = ProductSnapshot(manifest, workspace.zones, workspace.trays, workspace.recipes,
            workspace.batches, workspace.plans, observations)
        check(SnapshotValidation.validate(snapshot) is RestoreValidation.Valid)
        return LocalBackupArchive(Instant.parse("2026-10-07T15:45:00Z"), snapshot, emptyList())
    }

    private val NOW = Instant.parse("2026-10-10T10:00:00Z")
    private val TODAY = LocalDate.parse("2026-10-10")
    private const val CUPBOARD_CAPACITY = 4
    private const val FUTURE_ZONE_CAPACITY = 3
    private const val DEFAULT_ZONE_CAPACITY = 8
    private const val ROUND_DIAMETER_CM = 20.0
    private const val TRAY_WIDTH_CM = 25.0
    private const val TRAY_LENGTH_CM = 35.0
    private const val PLAN_CADENCE_DAYS = 5
    private const val RECOMMENDED_GRAMS = 18.0
    private const val ACTUAL_GRAMS = 20.0
    private const val HARVEST_GRAMS = 184.0
    private const val LONG_TRAY_NAME = "Очень длинное название лотка для семейного урожая редиса и гороха"
    private const val LONG_OBSERVATION = "Наблюдение за ростом: листья раскрылись равномерно, корни достигли дна лотка. " +
        "Проверить влажность субстрата вечером и перенести лоток ближе к окну."
}
