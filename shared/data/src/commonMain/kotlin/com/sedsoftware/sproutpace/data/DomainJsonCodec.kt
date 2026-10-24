package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.DurationRange
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.HarvestCadenceDays
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanId
import com.sedsoftware.sproutpace.domain.model.HarvestPlanMode
import com.sedsoftware.sproutpace.domain.model.LocalizationKey
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.OutcomeAssessment
import com.sedsoftware.sproutpace.domain.model.Percentage
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeStage
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.ScheduledActionKind
import com.sedsoftware.sproutpace.domain.model.ScheduledActionOrigin
import com.sedsoftware.sproutpace.domain.model.SeedDensity
import com.sedsoftware.sproutpace.domain.model.SourceMetadata
import com.sedsoftware.sproutpace.domain.model.StageEvent
import com.sedsoftware.sproutpace.domain.model.StageEventId
import com.sedsoftware.sproutpace.domain.model.StageEventKind
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class DomainJsonCodec(
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = false },
) {
    fun encodeRecipe(value: UserGrowRecipe): String = json.encodeToString(RecipeDto.serializer(), value.toDto())
    fun decodeRecipe(value: String): UserGrowRecipe = json.decodeFromString(RecipeDto.serializer(), value).toDomain()
    fun encodeBatch(value: GrowBatch): String = json.encodeToString(BatchDto.serializer(), value.toDto())
    fun decodeBatch(value: String): GrowBatch = json.decodeFromString(BatchDto.serializer(), value).toDomain()
    fun encodePlan(value: HarvestPlan): String = json.encodeToString(PlanDto.serializer(), value.toDto())
    fun decodePlan(value: String): HarvestPlan = json.decodeFromString(PlanDto.serializer(), value).toDomain()
    fun encodeObservation(value: Observation): String = json.encodeToString(ObservationDto.serializer(), value.toDto())
    fun decodeObservation(value: String): Observation = json.decodeFromString(ObservationDto.serializer(), value).toDomain()
}

@Serializable private data class SourceDto(val url: String, val titleKey: String, val reviewedOn: String)
@Serializable private data class DensityDto(val grams: Double, val version: Int, val source: SourceDto)
@Serializable private data class StageDto(
    val stage: String,
    val minimumMillis: Long,
    val maximumMillis: Long,
    val guidanceKey: String?,
    val userGuidance: String?,
)
@Serializable private data class RecipeDto(
    val id: String, val name: String, val cultureId: String?, val stages: List<StageDto>,
    val density: DensityDto, val substrate: String, val supplier: String?, val cultivar: String?,
    val seedLot: String?, val germinationPercentage: Int?,
)
@Serializable private data class RecipeSnapshotDto(
    val sourceRecipeId: String?, val builtInCultureId: String?, val nameSnapshot: String?,
    val stages: List<StageDto>, val density: DensityDto,
)
@Serializable private data class TrayDto(
    val name: String, val shape: String, val primaryCm: Double, val secondaryCm: Double?,
)
@Serializable private data class PlacementDto(val kind: String, val zoneId: String?, val zoneName: String?)
@Serializable private data class EventDto(
    val id: String, val from: String, val to: String, val occurredAt: String, val kind: String,
    val reason: String?, val before: PlacementDto, val after: PlacementDto,
    val priorOccurredAt: List<String> = emptyList(),
)
@Serializable private data class DueDto(
    val kind: String, val instant: String?, val localDateTime: String?, val timeZoneId: String?,
)
@Serializable private data class ActionDto(
    val id: String, val batchId: String, val stage: String?, val due: DueDto,
    val status: String, val userFixed: Boolean,
    val kind: String = "GENERAL",
    val origin: String = "UNSPECIFIED",
)
@Serializable private data class OutcomeDto(
    val kind: String, val occurredAt: String, val actualMass: Double?, val assessment: String?,
    val reason: String?, val note: String?,
)
@Serializable private data class BatchDto(
    val id: String, val recipe: RecipeSnapshotDto, val tray: TrayDto, val substrate: String,
    val recommendedMass: Double, val actualMass: Double, val state: String, val placement: PlacementDto,
    val events: List<EventDto>, val actions: List<ActionDto>, val supplier: String?, val cultivar: String?,
    val seedLot: String?, val germinationPercentage: Int?, val outcome: OutcomeDto?,
    val plannedSowDate: String? = null, val cultureIdSnapshot: String? = null,
    val closedPlacementSnapshot: PlacementDto? = null,
)
@Serializable private data class PlanDto(
    val id: String,
    val anchorDate: String = "1970-01-01",
    val cadenceDays: Int,
    val rotation: List<String>,
    val mode: String,
    val paused: Boolean,
)
@Serializable private data class ObservationDto(
    val id: String, val batchId: String, val occurredAt: String, val text: String, val photoId: String?,
)

private fun SourceMetadata.toDto() = SourceDto(url, titleKey.value, reviewedOn.toString())
private fun SourceDto.toDomain() = SourceMetadata(url, LocalizationKey(titleKey), LocalDate.parse(reviewedOn))
private fun SeedDensity.toDto() = DensityDto(gramsPerSquareCentimetre, version, source.toDto())
private fun DensityDto.toDomain() = SeedDensity(grams, version, source.toDomain())
private fun RecipeStage.toDto() = StageDto(
    stage.name, duration.minimum.inWholeMilliseconds, duration.maximum.inWholeMilliseconds,
    guidanceKey?.value, userGuidance,
)
private fun StageDto.toDomain() = RecipeStage(
    GrowStage.valueOf(stage), DurationRange(minimumMillis.milliseconds, maximumMillis.milliseconds),
    guidanceKey?.let(::LocalizationKey), userGuidance,
)
private fun UserGrowRecipe.toDto() = RecipeDto(
    id.value, name, cultureId?.value, stages.map { it.toDto() }, density.toDto(), substrate,
    supplier, cultivar, seedLot, germinationPercentage?.value,
)
private fun RecipeDto.toDomain() = UserGrowRecipe(
    RecipeId(id), name, cultureId?.let(::CultureId), stages.map { it.toDomain() }, density.toDomain(),
    substrate, supplier, cultivar, seedLot, germinationPercentage?.let(::Percentage),
)
private fun RecipeSnapshot.toDto() = RecipeSnapshotDto(
    sourceRecipeId?.value, builtInCultureId?.value, nameSnapshot, stages.map { it.toDto() }, density.toDto(),
)
private fun RecipeSnapshotDto.toDomain() = RecipeSnapshot(
    sourceRecipeId?.let(::RecipeId), builtInCultureId?.let(::CultureId), nameSnapshot,
    stages.map { it.toDomain() }, density.toDomain(),
)
private fun TraySnapshot.toDto(): TrayDto = when (val value = shape) {
    is TrayShape.Rectangle -> TrayDto(nameSnapshot, "RECTANGLE", value.widthCentimetres, value.lengthCentimetres)
    is TrayShape.Circle -> TrayDto(nameSnapshot, "CIRCLE", value.diameterCentimetres, null)
}
private fun TrayDto.toDomain() = TraySnapshot(
    name,
    if (shape == "RECTANGLE") TrayShape.Rectangle(primaryCm, requireNotNull(secondaryCm))
    else TrayShape.Circle(primaryCm),
)
private fun Placement.toDto(): PlacementDto = when (this) {
    Placement.None -> PlacementDto("NONE", null, null)
    is Placement.Assigned -> PlacementDto("ASSIGNED", zoneId.value, zoneNameSnapshot)
    is Placement.Unassigned -> PlacementDto("UNASSIGNED", null, priorZoneNameSnapshot)
}
private fun PlacementDto.toDomain(): Placement = when (kind) {
    "NONE" -> Placement.None
    "ASSIGNED" -> Placement.Assigned(GrowZoneId(requireNotNull(zoneId)), requireNotNull(zoneName))
    "UNASSIGNED" -> Placement.Unassigned(zoneName)
    else -> error("Unsupported placement kind: $kind")
}
private fun StageEvent.toDto() = EventDto(
    id.value, from.name, to.name, occurredAt.toString(), kind.name, reason,
    placementBefore.toDto(), placementAfter.toDto(), priorOccurredAt.map(Instant::toString),
)
private fun EventDto.toDomain() = StageEvent(
    StageEventId(id), BatchState.valueOf(from), BatchState.valueOf(to), Instant.parse(occurredAt),
    StageEventKind.valueOf(kind), reason, before.toDomain(), after.toDomain(), priorOccurredAt.map(Instant::parse),
)
private fun DueMoment.toDto(): DueDto = when (this) {
    is DueMoment.ElapsedTarget -> DueDto("ELAPSED", instant.toString(), null, null)
    is DueMoment.CivilTime -> DueDto("CIVIL", null, localDateTime.toString(), timeZoneId)
}
private fun DueDto.toDomain(): DueMoment = when (kind) {
    "ELAPSED" -> DueMoment.ElapsedTarget(Instant.parse(requireNotNull(instant)))
    "CIVIL" -> DueMoment.CivilTime(LocalDateTime.parse(requireNotNull(localDateTime)), requireNotNull(timeZoneId))
    else -> error("Unsupported due kind: $kind")
}
private fun ScheduledAction.toDto() = ActionDto(
    id.value, batchId.value, stage?.name, due.toDto(), status.name, userFixed, kind.name, origin.name,
)
private fun ActionDto.toDomain() = ScheduledAction(
    ScheduledActionId(id), BatchId(batchId), stage?.let(GrowStage::valueOf), due.toDomain(),
    ScheduledActionStatus.valueOf(status), userFixed, ScheduledActionKind.valueOf(kind), ScheduledActionOrigin.valueOf(origin),
)
private fun BatchOutcome.toDto(): OutcomeDto = when (this) {
    is BatchOutcome.Harvested -> OutcomeDto("HARVESTED", occurredAt.toString(), actualMass?.value, assessment?.name, null, note)
    is BatchOutcome.Failed -> OutcomeDto("FAILED", occurredAt.toString(), null, null, reason, note)
    is BatchOutcome.Abandoned -> OutcomeDto("ABANDONED", occurredAt.toString(), null, null, reason, note)
}
private fun OutcomeDto.toDomain(): BatchOutcome = when (kind) {
    "HARVESTED" -> BatchOutcome.Harvested(
        Instant.parse(occurredAt),
        actualMass?.let(::PositiveGrams),
        assessment?.let(OutcomeAssessment::valueOf),
        note,
    )
    "FAILED" -> BatchOutcome.Failed(Instant.parse(occurredAt), reason, note)
    "ABANDONED" -> BatchOutcome.Abandoned(Instant.parse(occurredAt), reason, note)
    else -> error("Unsupported outcome kind: $kind")
}
private fun GrowBatch.toDto() = BatchDto(
    id.value, recipe.toDto(), tray.toDto(), substrateSnapshot, recommendedSeedMass.value,
    actualSeedMass.value, state.name, placement.toDto(), events.map { it.toDto() }, actions.map { it.toDto() },
    supplier, cultivar, seedLot, germinationPercentage?.value, outcome?.toDto(),
    plannedSowDate?.toString(), cultureIdSnapshot?.value, closedPlacementSnapshot.toDto(),
)
private fun BatchDto.toDomain() = GrowBatch(
    BatchId(id), recipe.toDomain(), tray.toDomain(), substrate, PositiveGrams(recommendedMass),
    PositiveGrams(actualMass), BatchState.valueOf(state), placement.toDomain(), events.map { it.toDomain() },
    actions.map { it.toDomain() }, supplier, cultivar, seedLot, germinationPercentage?.let(::Percentage), outcome?.toDomain(),
    plannedSowDate?.let(LocalDate::parse), cultureIdSnapshot?.let(::CultureId) ?: recipe.builtInCultureId?.let(::CultureId),
    closedPlacementSnapshot?.toDomain() ?: Placement.None,
)
private fun HarvestPlan.toDto() = PlanDto(
    id.value,
    anchorDate.toString(),
    cadence.value,
    cultureRotation.map { it.value },
    mode.name,
    paused,
)
private fun PlanDto.toDomain() = HarvestPlan(
    HarvestPlanId(id), LocalDate.parse(anchorDate), HarvestCadenceDays(cadenceDays),
    rotation.map(::CultureId), HarvestPlanMode.valueOf(mode), paused,
)
private fun Observation.toDto() = ObservationDto(id.value, batchId.value, occurredAt.toString(), text, photoId?.value)
private fun ObservationDto.toDomain() = Observation(
    ObservationId(id), BatchId(batchId), Instant.parse(occurredAt), text, photoId?.let(::PhotoId),
)
