package com.sedsoftware.sproutpace.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class BatchState {
    PLANNED, SOAKING, SOWN, BLACKOUT_OR_WEIGHT, LIGHT_GROWTH, READY_FOR_HARVEST,
    HARVESTED, FAILED, ABANDONED;

    val isTerminal: Boolean get() = this == HARVESTED || this == FAILED || this == ABANDONED
    val occupiesPlace: Boolean get() = this == SOWN || this == BLACKOUT_OR_WEIGHT ||
        this == LIGHT_GROWTH || this == READY_FOR_HARVEST
}

enum class StageEventKind { CONFIRMED, SKIPPED }

data class StageEvent(
    val id: StageEventId,
    val from: BatchState,
    val to: BatchState,
    val occurredAt: Instant,
    val kind: StageEventKind,
    val reason: String? = null,
    val placementBefore: Placement,
    val placementAfter: Placement,
    /** Earlier timestamps retained when the user explicitly corrects an actual event date. */
    val priorOccurredAt: List<Instant> = emptyList(),
) {
    init {
        require(from != to)
        if (kind == StageEventKind.SKIPPED) require(!reason.isNullOrBlank())
    }
}

sealed interface DueMoment {
    data class ElapsedTarget(val instant: Instant) : DueMoment
    data class CivilTime(val localDateTime: LocalDateTime, val timeZoneId: String) : DueMoment {
        init { require(timeZoneId.isNotBlank()) }
    }
}

enum class ScheduledActionStatus { PENDING, COMPLETED, SKIPPED, CANCELLED }
enum class ScheduledActionKind { GENERAL, SOAK_TARGET }
enum class ScheduledActionOrigin { UNSPECIFIED, SOAK_DURATION }

data class ScheduledAction(
    val id: ScheduledActionId,
    val batchId: BatchId,
    val stage: GrowStage?,
    val due: DueMoment,
    val status: ScheduledActionStatus = ScheduledActionStatus.PENDING,
    val userFixed: Boolean = false,
    val kind: ScheduledActionKind = ScheduledActionKind.GENERAL,
    val origin: ScheduledActionOrigin = ScheduledActionOrigin.UNSPECIFIED,
) {
    init {
        if (origin == ScheduledActionOrigin.SOAK_DURATION) {
            require(kind == ScheduledActionKind.SOAK_TARGET && stage == null && due is DueMoment.ElapsedTarget)
        }
    }
}

data class GrowBatch(
    val id: BatchId,
    val recipe: RecipeSnapshot,
    val tray: TraySnapshot,
    val substrateSnapshot: String,
    val recommendedSeedMass: PositiveGrams,
    val actualSeedMass: PositiveGrams,
    val state: BatchState,
    val placement: Placement,
    val events: List<StageEvent>,
    val actions: List<ScheduledAction>,
    val supplier: String? = null,
    val cultivar: String? = null,
    val seedLot: String? = null,
    val germinationPercentage: Percentage? = null,
    val outcome: BatchOutcome? = null,
    val plannedSowDate: LocalDate? = null,
    val cultureIdSnapshot: CultureId? = recipe.builtInCultureId,
) {
    init {
        require(substrateSnapshot.isNotBlank())
        require(events.zipWithNext().all { (first, second) -> first.occurredAt <= second.occurredAt })
        require(!state.occupiesPlace || placement !is Placement.None)
        require(state.isTerminal == (outcome != null))
    }
}

enum class OutcomeAssessment { SUCCESSFUL, MIXED, UNSUCCESSFUL }

sealed interface BatchOutcome {
    val occurredAt: Instant
    val note: String?

    data class Harvested(
        override val occurredAt: Instant,
        val actualMass: PositiveGrams? = null,
        val assessment: OutcomeAssessment? = null,
        override val note: String? = null,
    ) : BatchOutcome

    data class Failed(
        override val occurredAt: Instant,
        val reason: String? = null,
        override val note: String? = null,
    ) : BatchOutcome

    data class Abandoned(
        override val occurredAt: Instant,
        val reason: String? = null,
        override val note: String? = null,
    ) : BatchOutcome
}

data class ScheduleConflict(
    val actionId: ScheduledActionId,
    val fixedDue: DueMoment,
    val proposedDue: DueMoment,
)

data class BatchChange(
    val batch: GrowBatch,
    val cancelledActionIds: Set<ScheduledActionId> = emptySet(),
    val conflicts: List<ScheduleConflict> = emptyList(),
)
