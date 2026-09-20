package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.ActionScheduleCorrection
import com.sedsoftware.sproutpace.domain.engine.BatchLifecycleEngine
import com.sedsoftware.sproutpace.domain.engine.BatchPlanningEngine
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Owns recipe and batch editor mutations so the screen manager remains an orchestration boundary. */
internal class RecipeBatchCommandHandler(
    private val database: LocalDatabase,
    private val loadWorkspace: suspend () -> Workspace,
    private val now: () -> Instant,
    private val today: () -> LocalDate,
) {
    suspend fun saveRecipe(command: ScreenCommand) {
        val culture = SproutPaceCatalog.cultures.firstOrNull { it.id.value == command.required("cultureId") }
            ?: error("Unknown culture")
        val existing = loadWorkspace().recipes.firstOrNull { it.id.value == command.subject() }
        val stages = if (command.arguments.containsKey("stage1")) {
            command.authoredRecipeStages()
        } else {
            existing?.stages ?: error("Recipe stage configuration is required")
        }
        database.saveRecipe(UserGrowRecipe(
            id = RecipeId(command.subject()),
            name = command.required("name"),
            cultureId = culture.id,
            stages = stages,
            density = culture.seedDensity,
            substrate = command.required("substrate"),
            supplier = command.updatedOptional("supplier", existing?.supplier),
            cultivar = command.updatedOptional("cultivar", existing?.cultivar),
            seedLot = command.updatedOptional("seedLot", existing?.seedLot),
            germinationPercentage = if (command.arguments.containsKey("germinationPercentage")) {
                command.optionalPercentage("germinationPercentage")
            } else existing?.germinationPercentage,
        ))
    }

    suspend fun duplicateRecipe(command: ScreenCommand) {
        val workspace = loadWorkspace()
        val source = workspace.recipes.firstOrNull { it.id.value == command.subject() }
            ?: throw LocalDataException(LocalDataErrorKey.INVALID_COMMAND)
        val targetId = RecipeId(command.required("newId"))
        require(workspace.recipes.none { it.id == targetId })
        database.saveRecipe(source.copy(
            id = targetId,
            name = command.arguments["name"]?.takeIf(String::isNotBlank) ?: source.name,
        ))
    }

    suspend fun saveBatch(command: ScreenCommand) {
        val workspace = loadWorkspace()
        val recipeId = command.arguments["recipeId"]
        val recipeSnapshot = recipeSnapshot(workspace, recipeId, command.arguments["cultureId"])
        val tray = workspace.trays.firstOrNull { it.id.value == command.required("trayId") }
            ?: error("Unknown tray")
        val sourceRecipe = workspace.recipes.firstOrNull { it.id.value == recipeId }
        val recommended = MeasurementEngine.recommendedSeedMass(tray.shape, recipeSnapshot.density)
        val plannedSowDate = command.optional("plannedSowDate")?.let(LocalDate::parse) ?: today()
        val timeZoneId = command.optional("timeZoneId") ?: DEFAULT_TIME_ZONE
        val batchId = BatchId(command.subject())
        database.saveBatch(GrowBatch(
            id = batchId,
            recipe = recipeSnapshot,
            tray = TraySnapshot(tray.name, tray.shape),
            substrateSnapshot = command.optional("substrate") ?: sourceRecipe?.substrate
                ?: command.required("substrate"),
            recommendedSeedMass = recommended,
            actualSeedMass = command.optional("actualSeedMassGrams")?.let(::positiveGrams) ?: recommended,
            state = BatchState.PLANNED,
            placement = Placement.None,
            events = emptyList(),
            actions = BatchPlanningEngine.plannedActions(
                batchId, recipeSnapshot.stages, plannedSowDate, command.fixedStageDates(), timeZoneId,
            ),
            supplier = command.optional("supplier") ?: sourceRecipe?.supplier,
            cultivar = command.optional("cultivar") ?: sourceRecipe?.cultivar,
            seedLot = command.optional("seedLot") ?: sourceRecipe?.seedLot,
            germinationPercentage = command.optionalPercentage("germinationPercentage")
                ?: sourceRecipe?.germinationPercentage,
            plannedSowDate = plannedSowDate,
            cultureIdSnapshot = command.optional("cultureId")?.let(::CultureId)
                ?: sourceRecipe?.cultureId ?: recipeSnapshot.builtInCultureId,
        ))
    }

    suspend fun correctBatch(command: ScreenCommand) {
        val workspace = loadWorkspace()
        val original = workspace.batch(command.subject())
        val tray = selectedTray(command, workspace)
        val plannedSowDate = command.optional("plannedSowDate")?.let(LocalDate::parse) ?: original.plannedSowDate
        val timeZoneId = command.optional("timeZoneId") ?: original.timeZoneId() ?: DEFAULT_TIME_ZONE
        val fixedDates = correctedFixedDates(command, original)
        val fields = correctedFields(command, original, workspace, tray, plannedSowDate)
        val events = BatchPlanningEngine.correctActualEventDates(fields, command.correctedActualDates())
        val scheduled = events.withCorrectedSchedule(plannedSowDate, fixedDates, timeZoneId)
        val corrected = rebuildDependentFutureActions(command, scheduled)
        database.saveBatch(corrected)
    }

    private fun recipeSnapshot(workspace: Workspace, recipeId: String?, cultureId: String?): RecipeSnapshot = when {
        recipeId != null -> workspace.recipes.firstOrNull { it.id.value == recipeId }?.let {
            RecipeSnapshot(it.id, it.cultureId, it.name, it.stages, it.density)
        } ?: error("Unknown recipe")
        cultureId != null -> SproutPaceCatalog.cultures.firstOrNull { it.id.value == cultureId }?.let {
            RecipeSnapshot(
                null,
                it.id,
                null,
                it.stages.map { stage -> RecipeStage(stage.stage, stage.duration, stage.instructionKey) },
                it.seedDensity,
            )
        } ?: error("Unknown culture")
        else -> error("recipeId or cultureId is required")
    }

    private fun selectedTray(command: ScreenCommand, workspace: Workspace): TraySizePreset? =
        command.optional("trayId")?.let { trayId ->
            workspace.trays.firstOrNull { it.id.value == trayId } ?: error("Unknown tray")
        }

    private fun correctedFields(
        command: ScreenCommand,
        batch: GrowBatch,
        workspace: Workspace,
        tray: TraySizePreset?,
        plannedSowDate: LocalDate?,
    ): GrowBatch = batch.copy(
        tray = tray?.let { TraySnapshot(it.name, it.shape) } ?: batch.tray,
        substrateSnapshot = command.optional("substrate") ?: batch.substrateSnapshot,
        recommendedSeedMass = tray?.let { MeasurementEngine.recommendedSeedMass(it.shape, batch.recipe.density) }
            ?: batch.recommendedSeedMass,
        actualSeedMass = command.optional("actualSeedMassGrams")?.let(::positiveGrams) ?: batch.actualSeedMass,
        placement = if (command.arguments.containsKey("zoneId")) placement(command, workspace) else batch.placement,
        supplier = command.updatedOptional("supplier", batch.supplier),
        cultivar = command.updatedOptional("cultivar", batch.cultivar),
        seedLot = command.updatedOptional("seedLot", batch.seedLot),
        germinationPercentage = if (command.arguments.containsKey("germinationPercentage")) {
            command.optionalPercentage("germinationPercentage")
        } else batch.germinationPercentage,
        plannedSowDate = plannedSowDate,
        cultureIdSnapshot = command.optional("cultureId")?.let(::CultureId) ?: batch.cultureIdSnapshot,
    )

    private fun correctedFixedDates(command: ScreenCommand, batch: GrowBatch): Map<GrowStage, LocalDate> =
        if (FIXED_DATE_FIELDS.any(command.arguments::containsKey)) {
            command.fixedStageDates()
        } else {
            batch.actions.mapNotNull { action ->
                val due = action.due as? DueMoment.CivilTime
                val stage = action.stage
                if (action.userFixed && stage != null && due != null) stage to due.localDateTime.date else null
            }.toMap()
        }

    private fun GrowBatch.withCorrectedSchedule(
        plannedSowDate: LocalDate?,
        fixedDates: Map<GrowStage, LocalDate>,
        timeZoneId: String,
    ): GrowBatch {
        if (state != BatchState.PLANNED || plannedSowDate == null) {
            return copy(actions = BatchPlanningEngine.applyFixedDates(actions, fixedDates, timeZoneId))
        }
        val planned = BatchPlanningEngine.plannedActions(id, recipe.stages, plannedSowDate, fixedDates, timeZoneId)
        val explicitReminders = actions.filterNot { it.id.value.startsWith(PLANNED_ACTION_ID_PREFIX) }
        return copy(actions = explicitReminders + planned)
    }

    private fun rebuildDependentFutureActions(command: ScreenCommand, batch: GrowBatch): GrowBatch {
        val hasExplicitAnchor = "anchorState" in command.arguments || "anchorAt" in command.arguments
        if (batch.state == BatchState.PLANNED && !hasExplicitAnchor) return batch
        return BatchLifecycleEngine.applyCorrection(
            batch,
            ActionScheduleCorrection(
                anchorState = command.arguments["anchorState"]?.let { enumValue<BatchState>(it) } ?: batch.state,
                anchorAt = command.arguments["anchorAt"]?.let(Instant::parse)
                    ?: batch.events.lastOrNull()?.occurredAt
                    ?: now(),
            ),
        ).batch
    }

    private fun GrowBatch.timeZoneId(): String? = actions.asSequence()
        .mapNotNull { (it.due as? DueMoment.CivilTime)?.timeZoneId }
        .firstOrNull()

    private fun ScreenCommand.updatedOptional(name: String, current: String?): String? =
        if (arguments.containsKey(name)) optional(name) else current

    private fun positiveGrams(raw: String): PositiveGrams =
        PositiveGrams(raw.toDoubleOrNull() ?: error("Invalid actualSeedMassGrams"))

    private companion object {
        const val DEFAULT_TIME_ZONE = "UTC"
        const val PLANNED_ACTION_ID_PREFIX = "planned:"
    }
}
