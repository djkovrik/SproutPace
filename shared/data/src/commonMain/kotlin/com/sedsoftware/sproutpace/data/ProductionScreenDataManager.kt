package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.isImmediatePreference
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenField
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenPhoto
import com.sedsoftware.sproutpace.component.ScreenItemCommand
import com.sedsoftware.sproutpace.component.ScreenOption
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.REMOVE_PHOTO_VALUE
import com.sedsoftware.sproutpace.component.pendingPhotoToken
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.CapacityEngine
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import com.sedsoftware.sproutpace.domain.engine.Occupancy
import com.sedsoftware.sproutpace.domain.engine.PlannerEngine
import com.sedsoftware.sproutpace.domain.engine.SchedulingEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CapacityDecision
import com.sedsoftware.sproutpace.domain.model.CapacityResolution
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
import com.sedsoftware.sproutpace.domain.model.Percentage
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.StageEventId
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Production, locale-neutral bridge between the generic screen Stores and the local product model.
 * All authored text remains authored text; app-owned copy is represented by stable [TextKey] values.
 */
class ProductionScreenDataManager(
    private val database: LocalDatabase,
    private val settings: ProductSettings,
    private val workspaceRepository: LocalWorkspaceRepository? = null,
    private val backupService: LocalBackupService? = null,
    private val pendingPhotos: PendingPhotoImportStore? = null,
    private val now: () -> Instant = { Clock.System.now() },
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val draftIdentity: () -> String = { kotlin.uuid.Uuid.random().toString() },
    private val timeUpdates: Flow<Long> = flowOf(0L),
) : ScreenDataManager {
    private val commandMutex = Mutex()
    private val completedCommands = mutableMapOf<String, CompletedCommand>()
    private var lastBackupDocumentId: String? = null
    private var lastBackupPreview: BackupPreview.Valid? = null
    private var validatedBackupDocumentId: String? = null
    private var backupResultKey: String? = null
    private val snapshotRenderer = ProductionScreenSnapshotRenderer(
        now = now,
        today = today,
        timeZone = timeZone,
    )
    private val interactionBuilder = ProductionScreenInteractionBuilder(
        now = now,
        today = today,
        backupDocumentId = { lastBackupDocumentId },
        backupPreview = { lastBackupPreview },
        timeZone = timeZone,
    )
    private val recipeBatchCommands = RecipeBatchCommandHandler(database, ::loadWorkspace, now, today)

    override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> = flow {
        val identities = DraftIdentitySession(draftIdentity())
        emitAll(combine(workspaceFlow(), settingsFlow(), timeUpdates) { workspace, preferences, _ ->
            render(screen, stableId, workspace, preferences, identities)
        })
    }

    /**
     * Reusing an invocation returns its committed result. New user actions must use a newly prepared
     * command or [ScreenCommand.newInvocation]; commands without an invocation retain their explicit
     * deduplication key as a legacy caller-supplied identity.
     */
    override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
        runCatching {
            commandMutex.withLock {
                completedCommands[command.executionKey]?.takeUnless {
                    (command.isImmediatePreference && command.invocationId == null) || command.operation in BACKUP_OPERATIONS
                }?.let { completed ->
                    require(completed.command.operation == command.operation &&
                        completed.command.subjectId == command.subjectId && completed.command.arguments == command.arguments) {
                        "Invocation identity reused for another command"
                    }
                    return@withLock completed.snapshot
                }
                validateOperation(screen, command.operation)
                applyCommand(screen, command)
                val rendered = render(screen, command.subjectId, loadWorkspace(), loadSettings())
                if ((!command.isImmediatePreference || command.invocationId != null) && command.operation !in BACKUP_OPERATIONS) {
                    completedCommands[command.executionKey] = CompletedCommand(command, rendered)
                }
                rendered
            }
        }.fold(
            onSuccess = Result.Companion::success,
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                Result.failure(
                if (failure is LocalDataException) failure
                else LocalDataException(LocalDataErrorKey.INVALID_COMMAND, failure),
                )
            },
        )

    override suspend fun previewStage(command: ScreenCommand): Result<com.sedsoftware.sproutpace.component.StageChangeProjection> =
        runCatching {
            val workspace = loadWorkspace()
            val batch = workspace.batch(command.subject())
            val targetPlacement = if (command.operation == "confirm-sowing" || command.hasPlacementChoice()) {
                placement(command, workspace)
            } else batch.placement
            stageTransition(batch, command, targetPlacement).preview(batch, command)
        }.onFailure { if (it is CancellationException) throw it }

    override suspend fun previewPhoto(value: String): Result<ScreenPhoto?> = runCatching {
        if (value.isBlank() || value == REMOVE_PHOTO_VALUE) return@runCatching null
        val pending = pendingPhotoToken(value)
        val bytes = if (pending != null) pendingPhotos?.read(pending) else workspaceRepository?.readPhoto(PhotoId(value))
        bytes?.takeIf { it.isNotEmpty() }?.let { ScreenPhoto(value, it, TextKey("observation_photo_content_description")) }
    }.onFailure { if (it is CancellationException) throw it }

    private fun workspaceFlow(): Flow<Workspace> {
        val core = combine(
            database.watchZones(), database.watchTrayPresets(), database.watchRecipes(),
            database.watchBatches(), database.watchPlans(),
        ) { zones, trays, recipes, batches, plans ->
            Workspace(zones, trays, recipes, batches, plans, emptyList())
        }
        return combine(core, database.watchObservations()) { workspace, observations ->
            workspace.copy(observations = observations)
        }
    }

    private fun settingsFlow(): Flow<SettingsSnapshot> {
        val core = combine(
            settings.theme,
            settings.crashReportingEnabled,
            settings.notificationsEnabled,
            settings.summaryTime,
            settings.soakRemindersEnabled,
        ) { theme, crash, dailySummaryEnabled, summaryTime, soakRemindersEnabled ->
            SettingsSnapshot(
                theme = theme,
                crashReporting = crash,
                dailySummaryEnabled = dailySummaryEnabled,
                summaryTime = summaryTime,
                soakRemindersEnabled = soakRemindersEnabled,
                privacy = null,
            )
        }
        return combine(core, settings.privacyState) { snapshot, privacy -> snapshot.copy(privacy = privacy) }
    }

    private suspend fun loadWorkspace(): Workspace {
        val snapshot = database.snapshot()
        return Workspace(
            snapshot.zones, snapshot.trayPresets, snapshot.recipes, snapshot.batches,
            snapshot.plans, snapshot.observations,
        )
    }

    private fun loadSettings() = settings.snapshot().let {
        SettingsSnapshot(
            theme = it.theme,
            crashReporting = it.crashReportingEnabled,
            dailySummaryEnabled = it.notificationsEnabled,
            summaryTime = it.summaryTime,
            soakRemindersEnabled = it.soakRemindersEnabled,
            privacy = it.privacyState,
        )
    }

    private suspend fun render(
        screen: ScreenId,
        stableId: String?,
        workspace: Workspace,
        preferences: SettingsSnapshot,
        draftIdentities: DraftIdentitySession = DraftIdentitySession(draftIdentity()),
    ): ScreenSnapshot {
        val photos = if (screen == ScreenId.BATCH_DETAIL || screen == ScreenId.OBSERVATION_EDITOR) {
            workspace.observations.mapNotNull { it.photoId }.distinct().mapNotNull { id ->
                runCatching { workspaceRepository?.readPhoto(id) }.getOrNull()?.let { id to it }
            }.toMap()
        } else emptyMap()
        val base = snapshotRenderer.snapshot(screen, stableId, workspace, preferences, photos)
        val result = interactionBuilder.apply(base, screen, stableId, workspace, preferences, draftIdentities)
        return if (screen == ScreenId.BACKUP_RESTORE) result.withBackupResult(backupResultKey) else result
    }

    private suspend fun applyCommand(screen: ScreenId, command: ScreenCommand) {
        when (command.operation) {
            "continue-setup" -> {
                require(screen == ScreenId.FIRST_RUN)
                continueSetup(command)
            }
            "save-zone", "update-zone" -> saveZone(command)
            "save-tray-size", "update-tray-size" -> saveTray(command)
            "save-recipe", "update-recipe" -> saveRecipe(command)
            "delete-zone" -> database.deleteZone(GrowZoneId(command.subject()))
            "delete-tray-size" -> database.deleteTrayPreset(TraySizePresetId(command.subject()))
            "delete-recipe" -> database.deleteRecipe(RecipeId(command.subject()))
            "duplicate-recipe" -> duplicateRecipe(command)
            in BATCH_LIFECYCLE_OPERATIONS -> applyBatchLifecycleCommand(command)
            in ACTION_OPERATIONS -> applyActionCommand(command)
            "save-plan", "update-plan" -> savePlan(command)
            "delete-plan" -> database.deletePlan(HarvestPlanId(command.subject()))
            "pause-plan", "resume-plan" -> pausePlan(command, command.operation == "pause-plan")
            "confirm-suggestion" -> Unit // Selection is intentionally non-persistent until save-batch.
            in OUTCOME_AND_OBSERVATION_OPERATIONS -> applyOutcomeOrObservationCommand(command)
            in BACKUP_OPERATIONS -> applyBackupCommand(command)
            "set-theme" -> settings.setTheme(command.enum("theme"))
            "set-crash-reporting" -> settings.setCrashReportingEnabled(command.boolean("enabled"))
            "update-notifications" -> {
                val daily = command.boolean("dailySummaryEnabled")
                val time = command.summaryLocalTime("summaryTime")
                val soak = command.boolean("soakRemindersEnabled")
                settings.setNotificationsEnabled(daily)
                settings.setSummaryTime(time)
                settings.setSoakRemindersEnabled(soak)
            }
            "record-consent" -> recordConsent(command)
            else -> error("Unsupported operation: ${command.operation}")
        }
    }

    private suspend fun applyBatchLifecycleCommand(command: ScreenCommand) {
        when (command.operation) {
            "save-batch" -> saveBatch(command)
            "correct-batch" -> correctBatch(command)
            "delete-batch" -> requiredWorkspaceRepository().deleteBatch(BatchId(command.subject()))
            "start-soaking" -> startSoaking(command)
            "confirm-sowing" -> confirmSowing(command)
            "confirm-stage" -> confirmStage(command)
            "assign-overflow" -> changeBatch(command) { batch ->
                com.sedsoftware.sproutpace.domain.model.BatchChange(
                    batch.copy(placement = Placement.Unassigned(when (val current = batch.placement) {
                        is Placement.Assigned -> current.zoneNameSnapshot
                        is Placement.Unassigned -> current.priorZoneNameSnapshot
                        Placement.None -> null
                    })),
                )
            }
            "undo-stage" -> changeBatch(command) { batch -> BatchLifecycleEngine.undoLastTransition(batch) }
            "skip-stage" -> skipStage(command)
            else -> error("Unsupported batch operation: ${command.operation}")
        }
    }

    private suspend fun startSoaking(command: ScreenCommand) = changeBatch(command) { batch ->
        val eventId = StageEventId(command.required("eventId"))
        existingEvent(batch, eventId) ?: run {
            val occurredAt = command.instant("occurredAt")
            val changed = BatchLifecycleEngine.startSoaking(batch, eventId, occurredAt)
            val soak = batch.recipe.stages.first { it.stage == GrowStage.SOAKING }
            val actionId = ScheduledActionId("soak:${batch.id.value}")
            val target = ScheduledAction(
                actionId,
                batch.id,
                null,
                DueMoment.ElapsedTarget(occurredAt + soak.duration.maximum),
                kind = com.sedsoftware.sproutpace.domain.model.ScheduledActionKind.SOAK_TARGET,
                origin = com.sedsoftware.sproutpace.domain.model.ScheduledActionOrigin.SOAK_DURATION,
            )
            changed.copy(
                batch = changed.batch.copy(actions = changed.batch.actions.filterNot { it.id == actionId } + target),
            )
        }
    }

    private suspend fun confirmSowing(command: ScreenCommand) = changeBatch(command) { batch ->
        val eventId = StageEventId(command.required("eventId"))
        existingEvent(batch, eventId) ?: stageTransition(batch, command, placement(command, loadWorkspace()))
    }

    private suspend fun confirmStage(command: ScreenCommand) = changeBatch(command) { batch ->
        val eventId = StageEventId(command.required("eventId"))
        existingEvent(batch, eventId) ?: stageTransition(batch, command,
            if (command.hasPlacementChoice()) placement(command, loadWorkspace()) else batch.placement)
    }

    private suspend fun skipStage(command: ScreenCommand) = changeBatch(command) { batch ->
        val eventId = StageEventId(command.required("eventId"))
        existingEvent(batch, eventId) ?: stageTransition(batch, command, batch.placement)
    }

    private suspend fun applyActionCommand(command: ScreenCommand) {
        when (command.operation) {
            "complete-action" -> updateAction(command, nonStageOnly = true) { it.copy(status = ScheduledActionStatus.COMPLETED) }
            "snooze" -> updateAction(command) { SchedulingEngine.snooze(it, command.due()) }
            "skip-action" -> updateAction(command, nonStageOnly = true) { it.copy(status = ScheduledActionStatus.SKIPPED) }
            "schedule-soak-target" -> scheduleSoakTarget(command)
            "time-zone-change" -> updateAllCivilActions(command.required("timeZoneId"))
            else -> error("Unsupported action operation: ${command.operation}")
        }
    }

    private suspend fun applyOutcomeOrObservationCommand(command: ScreenCommand) {
        when (command.operation) {
            "close-harvest", "close-failed", "close-abandoned", "save-outcome" -> closeBatch(command)
            "record-actual-mass" -> recordActualMass(command)
            "save-observation" -> saveObservation(command)
            "delete-observation" -> requiredWorkspaceRepository().deleteObservation(ObservationId(command.subject()))
            else -> error("Unsupported outcome or observation operation: ${command.operation}")
        }
    }

    private suspend fun applyBackupCommand(command: ScreenCommand) {
        val selectedDocument = command.arguments["documentId"]?.takeIf(String::isNotBlank)
        val documentId = selectedDocument?.let { SelectedBackupDocument.decode(it).location }
        backupResultKey = null
        lastBackupDocumentId = selectedDocument
        when (command.operation) {
            "create-backup" -> {
                requiredBackupService().writeArchive(requireNotNull(documentId))
                backupResultKey = "backup_created"
            }
            "cancel-restore-preview" -> {
                lastBackupPreview = null
                validatedBackupDocumentId = null
                lastBackupDocumentId = null
            }
            "preview-restore" -> {
                lastBackupPreview = null
                validatedBackupDocumentId = null
                when (val preview = requiredBackupService().readPreview(requireNotNull(documentId))) {
                    is BackupPreview.Invalid -> throw LocalDataException(preview.reasons.first())
                    is BackupPreview.Valid -> {
                        lastBackupPreview = preview
                        validatedBackupDocumentId = documentId
                    }
                }
            }
            "restore-backup" -> {
                require(lastBackupPreview != null && documentId == validatedBackupDocumentId)
                requiredBackupService().replaceFromDocument(requireNotNull(documentId))
                lastBackupPreview = null
                validatedBackupDocumentId = null
                backupResultKey = "backup_restored"
            }
            "export-csv" -> {
                requiredBackupService().writeCsv(requireNotNull(documentId))
                backupResultKey = "backup_csv_exported"
            }
            "erase-all", "delete-all-data" -> {
                lastBackupPreview = null
                validatedBackupDocumentId = null
                lastBackupDocumentId = null
                requiredBackupService().eraseAll()
                completedCommands.clear()
            }
            else -> error("Unsupported backup operation: ${command.operation}")
        }
    }

    private suspend fun continueSetup(command: ScreenCommand) {
        val type = when (command.required("type")) {
            "natural_light" -> GrowZoneType.NATURAL_LIGHT
            "grow_light" -> GrowZoneType.GROW_LIGHT
            else -> error("First-run zone must be light-capable")
        }
        val capacity = command.int("capacity")
        require(capacity > 0) { "First-run capacity must be positive" }
        val shapeName = command.required("shape")
        val expectedArguments = when (shapeName) {
            "rectangle" -> FIRST_RUN_ARGUMENTS + setOf("widthCm", "lengthCm")
            "circle" -> FIRST_RUN_ARGUMENTS + "diameterCm"
            else -> error("Unknown tray shape")
        }
        val optionalArguments = setOf("darkZoneEnabled", "dailySummaryEnabled") +
            (if (command.arguments["darkZoneEnabled"] == "true") setOf("darkZoneName", "darkZoneCapacity") else emptySet()) +
            (if (command.arguments["dailySummaryEnabled"] == "true") setOf("summaryTime") else emptySet())
        require(command.arguments.keys.containsAll(expectedArguments) &&
            command.arguments.keys.all { it in expectedArguments || it in optionalArguments }) {
            "Unexpected first-run setup arguments"
        }
        val darkZone = if (command.arguments["darkZoneEnabled"] == "true") GrowZone(
            GrowZoneId(FIRST_RUN_DARK_ZONE_ID), command.required("darkZoneName"), GrowZoneType.DARK,
            command.int("darkZoneCapacity"),
        ) else null
        val summaryTime = if (command.arguments["dailySummaryEnabled"] == "true")
            kotlinx.datetime.LocalTime.parse(command.required("summaryTime")).let { SummaryLocalTime(it.hour, it.minute) } else null
        val shape = when (shapeName) {
            "rectangle" -> TrayShape.Rectangle(command.double("widthCm"), command.double("lengthCm"))
            "circle" -> TrayShape.Circle(command.double("diameterCm"))
            else -> error("Unknown tray shape")
        }
        val cultures = command.required("cultureIds")
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .map(::CultureId)
        require(cultures.isNotEmpty() && cultures.all { it in SproutPaceCatalog.cultureIds }) {
            "At least one known culture is required"
        }
        val mode = when (command.required("mode")) {
            "cadence" -> HarvestPlanMode.CADENCE
            "continuous_queue" -> HarvestPlanMode.CONTINUOUS_QUEUE
            else -> error("Unknown plan mode")
        }
        val zone = GrowZone(
            GrowZoneId(FIRST_RUN_ZONE_ID),
            command.required("zoneName"),
            type,
            capacity,
        )
        val tray = TraySizePreset(
            TraySizePresetId(FIRST_RUN_TRAY_ID),
            command.required("trayName"),
            shape,
        )
        val plan = HarvestPlan(
            HarvestPlanId(FIRST_RUN_PLAN_ID),
            today(),
            HarvestCadenceDays(command.int("cadenceDays")),
            cultures,
            mode,
        )

        val current = database.snapshot()
        val zones = current.zones.filterNot { it.id == zone.id || it.id == darkZone?.id } + listOfNotNull(zone, darkZone)
        val trays = current.trayPresets.filterNot { it.id == tray.id } + tray
        val plans = current.plans.filterNot { it.id == plan.id } + plan
        val manifest = SnapshotValidation.manifest(
            zones,
            trays,
            current.recipes,
            current.batches,
            plans,
            current.observations,
            current.manifest.photoIntegrity,
        )
        database.replace(
            current.copy(
                manifest = manifest,
                zones = zones,
                trayPresets = trays,
                plans = plans,
            ),
        )
        command.arguments["dailySummaryEnabled"]?.let { settings.setNotificationsEnabled(it.toBooleanStrict()) }
        summaryTime?.let(settings::setSummaryTime)
    }

    private suspend fun saveZone(command: ScreenCommand) {
        database.saveZone(GrowZone(
            GrowZoneId(command.subject()), command.required("name"), command.enum("type"), command.int("capacity"),
        ))
    }

    private suspend fun saveTray(command: ScreenCommand) {
        val shape = when (command.required("shape").lowercase()) {
            "rectangle" -> TrayShape.Rectangle(command.double("widthCm"), command.double("lengthCm"))
            "circle" -> TrayShape.Circle(command.double("diameterCm"))
            else -> error("Unknown tray shape")
        }
        database.saveTrayPreset(TraySizePreset(TraySizePresetId(command.subject()), command.required("name"), shape))
    }

    private suspend fun saveRecipe(command: ScreenCommand) = recipeBatchCommands.saveRecipe(command)

    private suspend fun duplicateRecipe(command: ScreenCommand) = recipeBatchCommands.duplicateRecipe(command)

    private suspend fun saveBatch(command: ScreenCommand) = recipeBatchCommands.saveBatch(command)

    private suspend fun correctBatch(command: ScreenCommand) = recipeBatchCommands.correctBatch(command)

    private suspend fun changeBatch(
        command: ScreenCommand,
        change: suspend (GrowBatch) -> com.sedsoftware.sproutpace.domain.model.BatchChange,
    ) {
        val batch = loadWorkspace().batch(command.subject())
        database.saveBatch(change(batch).batch)
    }

    private fun existingEvent(batch: GrowBatch, eventId: StageEventId) =
        batch.events.firstOrNull { it.id == eventId }?.let { com.sedsoftware.sproutpace.domain.model.BatchChange(batch) }

    private suspend fun updateAction(
        command: ScreenCommand,
        nonStageOnly: Boolean = false,
        transform: (ScheduledAction) -> ScheduledAction,
    ) {
        val actionId = ScheduledActionId(command.subject())
        val workspace = loadWorkspace()
        val batch = workspace.batches.firstOrNull { candidate -> candidate.actions.any { it.id == actionId } } ?: error("Unknown action")
        val current = batch.actions.first { it.id == actionId }
        require(!nonStageOnly || current.stage == null) { "Stage actions require their lifecycle confirmation" }
        if (current.status != ScheduledActionStatus.PENDING) return
        val changed = transform(current)
        if (changed == current) return
        val updated = batch.copy(actions = batch.actions.map { if (it.id == actionId) changed else it })
        database.saveBatch(updated)
    }

    private suspend fun scheduleSoakTarget(command: ScreenCommand) {
        val workspace = loadWorkspace()
        val batch = workspace.batch(command.subject())
        val actionId = ScheduledActionId(command.arguments["actionId"] ?: "soak:${batch.id.value}")
        val action = ScheduledAction(actionId, batch.id, null, DueMoment.ElapsedTarget(command.instant("dueAt")),
            kind = com.sedsoftware.sproutpace.domain.model.ScheduledActionKind.SOAK_TARGET)
        database.saveBatch(batch.copy(actions = (batch.actions.filterNot { it.id == actionId } + action)))
    }

    private suspend fun updateAllCivilActions(timeZoneId: String) {
        require(timeZoneId.isNotBlank())
        TimeZone.of(timeZoneId)
        val snapshot = database.snapshot()
        database.replace(snapshot.copy(batches = snapshot.batches.map { batch ->
            batch.copy(actions = batch.actions.map { SchedulingEngine.afterTimeZoneChange(it, timeZoneId) })
        }))
    }

    private suspend fun savePlan(command: ScreenCommand) {
        val cultures = command.required("cultureIds").split(',').map(String::trim).filter(String::isNotBlank).map(::CultureId)
        require(cultures.all { it in SproutPaceCatalog.cultureIds }) { "Unknown culture" }
        val existing = loadWorkspace().plans.firstOrNull { it.id.value == command.subject() }
        database.savePlan(HarvestPlan(
            HarvestPlanId(command.subject()),
            command.arguments["anchorDate"]?.let { LocalDate.parse(it) } ?: existing?.anchorDate ?: today(),
            HarvestCadenceDays(command.int("cadenceDays")), cultures,
            command.enum("mode"), command.arguments["paused"]?.toBooleanStrictOrNull() ?: existing?.paused ?: false,
        ))
    }

    private suspend fun pausePlan(command: ScreenCommand, paused: Boolean) {
        val plan = loadWorkspace().plans.firstOrNull { it.id.value == command.subject() } ?: error("Unknown plan")
        database.savePlan(plan.copy(paused = paused))
    }

    private suspend fun closeBatch(command: ScreenCommand) {
        val batch = loadWorkspace().batch(command.subject())
        val occurredAt = command.instant("occurredAt")
        val outcome = when (command.arguments["outcome"]?.lowercase() ?: command.operation) {
            "harvested", "close-harvest" -> BatchOutcome.Harvested(
                occurredAt,
                command.arguments["actualMassGrams"]?.let { PositiveGrams(it.toDoubleOrNull() ?: error("Invalid actualMassGrams")) },
                command.arguments["assessment"]?.let { enumValue<com.sedsoftware.sproutpace.domain.model.OutcomeAssessment>(it) },
                command.arguments["note"],
            )
            "failed", "close-failed" -> BatchOutcome.Failed(occurredAt, command.arguments["reason"], command.arguments["note"])
            "abandoned", "close-abandoned" -> BatchOutcome.Abandoned(occurredAt, command.arguments["reason"], command.arguments["note"])
            else -> error("Unknown outcome")
        }
        if (batch.outcome == outcome) return
        database.saveBatch(BatchLifecycleEngine.close(batch, outcome).batch)
    }

    private suspend fun recordActualMass(command: ScreenCommand) {
        val batch = loadWorkspace().batch(command.subject())
        val mass = command.arguments["actualMassGrams"]
            ?.takeIf(String::isNotBlank)
            ?.let { PositiveGrams(it.toDoubleOrNull() ?: error("Invalid actualMassGrams")) }
        database.saveBatch(BatchLifecycleEngine.recordActualHarvestMass(batch, mass))
    }

    private suspend fun saveObservation(command: ScreenCommand) {
        val batchId = BatchId(command.required("batchId"))
        val workspace = loadWorkspace()
        require(workspace.batches.any { it.id == batchId }) { "Unknown batch" }
        val observationId = ObservationId(command.subject())
        val previous = workspace.observations.firstOrNull { it.id == observationId }
        val draftPhoto = command.arguments["photoId"]
        val pendingToken = pendingPhotoToken(draftPhoto)
        val change = when {
            draftPhoto == REMOVE_PHOTO_VALUE -> ObservationPhotoChange.Remove
            draftPhoto == null || draftPhoto == previous?.photoId?.value -> ObservationPhotoChange.Keep
            pendingToken != null -> {
                val bytes = requireNotNull(pendingPhotos?.read(pendingToken)) { "Pending photo is unavailable" }
                require(bytes.isNotEmpty()) { "Pending photo is empty" }
                requiredWorkspaceRepository().prepareManagedPhotoReplacement(observationId, bytes)
            }
            else -> throw LocalDataException(LocalDataErrorKey.INVALID_COMMAND)
        }
        requiredWorkspaceRepository().saveObservation(
            Observation(observationId, batchId, command.instant("occurredAt"), command.arguments["text"].orEmpty()),
            change,
        )
        pendingToken?.let { token -> runCatching { pendingPhotos?.delete(token) } }
    }

    private fun recordConsent(command: ScreenCommand) {
        val choice = command.enum<ConsentChoice>("choice")
        val expiresAt = command.required("expiresAtEpochMillis").toLongOrNull() ?: error("Invalid expiresAtEpochMillis")
        require(expiresAt > 0) { "Invalid expiresAtEpochMillis" }
        settings.setPrivacyState(AdvertisingPrivacyState(
            policyVersion = command.required("policyVersion"),
            expiresAtEpochMillis = expiresAt,
            choice = choice,
        ))
    }

    private fun requiredWorkspaceRepository(): LocalWorkspaceRepository = workspaceRepository
        ?: throw LocalDataException(LocalDataErrorKey.INVALID_COMMAND)

    private fun requiredBackupService(): LocalBackupService = backupService
        ?: throw LocalDataException(LocalDataErrorKey.INVALID_COMMAND)

    private data class CompletedCommand(val command: ScreenCommand, val snapshot: ScreenSnapshot)
}

private const val FIRST_RUN_ZONE_ID = "first-run-zone"
private const val FIRST_RUN_DARK_ZONE_ID = "first-run-dark-zone"
private const val FIRST_RUN_TRAY_ID = "first-run-tray"
private const val FIRST_RUN_PLAN_ID = "first-run-plan"
private val BATCH_LIFECYCLE_OPERATIONS = setOf(
    "save-batch", "correct-batch", "delete-batch", "start-soaking", "confirm-sowing",
    "confirm-stage", "assign-overflow", "undo-stage", "skip-stage",
)
private val ACTION_OPERATIONS = setOf(
    "complete-action", "snooze", "skip-action", "schedule-soak-target", "time-zone-change",
)
private val OUTCOME_AND_OBSERVATION_OPERATIONS = setOf(
    "close-harvest", "close-failed", "close-abandoned", "save-outcome", "record-actual-mass",
    "save-observation", "delete-observation",
)
private val BACKUP_OPERATIONS = setOf(
    "cancel-restore-preview",
    "create-backup", "preview-restore", "restore-backup", "export-csv", "erase-all", "delete-all-data",
)
private val FIRST_RUN_ARGUMENTS = setOf(
    "zoneName", "type", "capacity", "trayName", "shape", "cadenceDays", "cultureIds", "mode",
)
private val OPERATIONS = mapOf(
    ScreenId.FIRST_RUN to setOf("continue-setup"),
    ScreenId.TODAY to setOf("complete-action", "snooze", "skip-action", "time-zone-change"),
    ScreenId.TRAYS to setOf("delete-batch"),
    ScreenId.BATCH_EDITOR to setOf("save-batch", "correct-batch"),
    ScreenId.BATCH_DETAIL to setOf(
        "start-soaking", "confirm-sowing", "confirm-stage", "assign-overflow", "undo-stage", "skip-stage",
        "schedule-soak-target", "record-actual-mass", "complete-action", "skip-action", "time-zone-change",
        "delete-batch",
    ),
    ScreenId.PLAN to setOf("save-plan", "update-plan", "pause-plan", "resume-plan", "confirm-suggestion", "delete-plan"),
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

private fun validateOperation(screen: ScreenId, operation: String) {
    val allowed = OPERATIONS.getValue(screen)
    require(operation in allowed) { "Operation $operation is not valid for $screen" }
}

internal fun placement(command: ScreenCommand, workspace: Workspace): Placement {
    if (command.arguments["unassigned"]?.toBooleanStrictOrNull() == true) {
        val current = workspace.batches.firstOrNull { it.id.value == command.subjectId }?.placement
        val previousName = when (current) {
            is Placement.Assigned -> current.zoneNameSnapshot
            is Placement.Unassigned -> current.priorZoneNameSnapshot
            else -> null
        }
        return Placement.Unassigned(previousName)
    }
    val zone = workspace.zones.firstOrNull { it.id.value == command.required("zoneId") } ?: error("Unknown zone")
    return Placement.Assigned(zone.id, zone.name)
}

private fun ScreenCommand.hasPlacementChoice(): Boolean =
    !arguments["zoneId"].isNullOrBlank() || arguments["unassigned"]?.toBooleanStrictOrNull() == true

internal fun ScreenCommand.subject(): String =
    requireNotNull(subjectId?.takeIf(String::isNotBlank)) { "subjectId is required" }

internal fun ScreenCommand.required(name: String): String =
    requireNotNull(arguments[name]?.takeIf(String::isNotBlank)) { "$name is required" }

internal fun ScreenCommand.optional(name: String): String? = arguments[name]?.takeIf(String::isNotEmpty)

internal fun ScreenCommand.optionalPercentage(name: String): Percentage? = optional(name)?.let { raw ->
    Percentage(raw.toIntOrNull() ?: error("Invalid $name"))
}

internal fun ScreenCommand.authoredRecipeStages(): List<RecipeStage> {
    val stages = (1..5).mapNotNull { slot ->
        val rawStage = optional("stage$slot")?.takeUnless { it == "none" } ?: return@mapNotNull null
        val minimum = optional("stage${slot}MinimumHours")?.toLongOrNull()
            ?: error("stage${slot}MinimumHours is required")
        val maximum = optional("stage${slot}MaximumHours")?.toLongOrNull()
            ?: error("stage${slot}MaximumHours is required")
        require(minimum >= 0L && maximum >= minimum) { "Invalid duration for stage $slot" }
        RecipeStage(
            stage = enumValue(rawStage),
            duration = com.sedsoftware.sproutpace.domain.model.DurationRange(minimum.hours, maximum.hours),
            userGuidance = required("stage${slot}Guidance"),
        )
    }
    require(stages.isNotEmpty()) { "At least one authored stage is required" }
    require(stages.map(RecipeStage::stage).distinct().size == stages.size) { "Recipe stages must be unique" }
    require(stages.any { it.stage == GrowStage.SOWN }) { "A recipe requires a sown stage" }
    return stages
}

internal fun ScreenCommand.fixedStageDates(): Map<GrowStage, LocalDate> = listOf(
    GrowStage.SOAKING to "fixedSoakingDate",
    GrowStage.SOWN to "fixedSownDate",
    GrowStage.BLACKOUT_OR_WEIGHT to "fixedBlackoutDate",
    GrowStage.LIGHT_GROWTH to "fixedLightDate",
    GrowStage.READY_FOR_HARVEST to "fixedHarvestDate",
).mapNotNull { (stage, field) -> optional(field)?.let { stage to LocalDate.parse(it) } }.toMap()

internal val FIXED_DATE_FIELDS = setOf(
    "fixedSoakingDate", "fixedSownDate", "fixedBlackoutDate", "fixedLightDate", "fixedHarvestDate",
)

internal fun ScreenCommand.correctedActualDates(): Map<BatchState, Instant> = listOf(
    BatchState.SOAKING to "actualSoakingAt",
    BatchState.SOWN to "actualSownAt",
    BatchState.BLACKOUT_OR_WEIGHT to "actualBlackoutAt",
    BatchState.LIGHT_GROWTH to "actualLightAt",
    BatchState.READY_FOR_HARVEST to "actualHarvestReadyAt",
).mapNotNull { (state, field) -> optional(field)?.let { state to Instant.parse(it) } }.toMap()

private fun ScreenCommand.int(name: String): Int = required(name).toIntOrNull() ?: error("Invalid $name")
private fun ScreenCommand.double(name: String): Double = required(name).toDoubleOrNull() ?: error("Invalid $name")
private fun ScreenCommand.boolean(name: String): Boolean =
    required(name).toBooleanStrictOrNull() ?: error("Invalid $name")

private fun ScreenCommand.summaryLocalTime(name: String): SummaryLocalTime {
    val raw = required(name)
    val parts = raw.split(':')
    require(parts.size == 2 && parts[0].length == 2 && parts[1].length == 2) { "Invalid $name" }
    val value = SummaryLocalTime(
        hour = parts[0].toIntOrNull() ?: error("Invalid $name"),
        minute = parts[1].toIntOrNull() ?: error("Invalid $name"),
    )
    require(value.toString() == raw) { "Invalid $name" }
    return value
}

internal fun ScreenCommand.instant(name: String): Instant =
    runCatching { Instant.parse(required(name)) }.getOrElse { error("Invalid $name") }

internal inline fun <reified T : Enum<T>> ScreenCommand.enum(name: String): T = enumValue(required(name))

private fun ScreenCommand.due(): DueMoment = when (required("dueKind")) {
    "elapsed" -> DueMoment.ElapsedTarget(instant("dueAt"))
    "civil" -> DueMoment.CivilTime(
        runCatching { LocalDateTime.parse(required("localDateTime")) }.getOrElse { error("Invalid localDateTime") },
        required("timeZoneId"),
    )
    else -> error("Invalid dueKind")
}

internal inline fun <reified T : Enum<T>> enumValue(raw: String): T =
    enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: error("Invalid ${T::class.simpleName}")

internal data class Workspace(
    val zones: List<GrowZone>,
    val trays: List<TraySizePreset>,
    val recipes: List<UserGrowRecipe>,
    val batches: List<GrowBatch>,
    val plans: List<HarvestPlan>,
    val observations: List<Observation>,
) {
    fun batch(id: String): GrowBatch = batches.firstOrNull { it.id.value == id } ?: error("Unknown batch")
}

internal data class SettingsSnapshot(
    val theme: ThemePreference,
    val crashReporting: Boolean,
    val dailySummaryEnabled: Boolean,
    val summaryTime: SummaryLocalTime,
    val soakRemindersEnabled: Boolean,
    val privacy: AdvertisingPrivacyState?,
)
