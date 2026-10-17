package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.ScheduleConflict
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.ScheduledActionStatus
import com.sedsoftware.sproutpace.domain.model.ScheduledActionOrigin
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

data class ActionScheduleCorrection(
    val anchorState: BatchState,
    val anchorAt: Instant,
)

data class ActionScheduleRebuild(
    val actions: List<ScheduledAction>,
    val cancelledActionIds: Set<ScheduledActionId>,
    val conflicts: List<ScheduleConflict>,
)

object ActionScheduleEngine {
    fun rebuild(batch: GrowBatch, correction: ActionScheduleCorrection): ActionScheduleRebuild {
        if (batch.state.isTerminal) return unchanged(batch.actions)

        val configuredStages = batch.recipe.stages.map { it.stage }
        val anchorIndex = configuredStages.anchorIndex(correction.anchorState)
        val rebuilt = buildList {
            val seenIds = mutableSetOf<ScheduledActionId>()
            batch.actions.forEach { action ->
                if (action.id in seenIds) return@forEach
                val actionIndex = action.stage?.let(configuredStages::indexOf) ?: NOT_A_STAGE_ACTION
                val isObsolete = action.status == ScheduledActionStatus.PENDING &&
                    action.stage != null && actionIndex == STAGE_NOT_CONFIGURED
                if (isObsolete) return@forEach

                seenIds += action.id
                add(rebuildAutomaticSoak(batch, action, correction)
                    ?: rebuildAction(batch, action, actionIndex, anchorIndex, correction))
            }
        }
        val conflicts = rebuilt.mapNotNull { result -> result.conflict }
        val actions = rebuilt.map { result -> result.action }
        val survivingIds = actions.mapTo(mutableSetOf()) { it.id }
        return ActionScheduleRebuild(
            actions = actions,
            cancelledActionIds = batch.actions.mapTo(mutableSetOf()) { it.id } - survivingIds,
            conflicts = conflicts,
        )
    }

    private fun rebuildAutomaticSoak(
        batch: GrowBatch,
        action: ScheduledAction,
        correction: ActionScheduleCorrection,
    ): RebuiltAction? {
        val isActiveSoakCorrection = batch.state == BatchState.SOAKING && correction.anchorState == BatchState.SOAKING
        val isAutomaticSoak = action.origin == ScheduledActionOrigin.SOAK_DURATION &&
            action.status == ScheduledActionStatus.PENDING && !action.userFixed
        if (!isActiveSoakCorrection || !isAutomaticSoak) return null
        return batch.recipe.stages.firstOrNull { it.stage == GrowStage.SOAKING }?.let { soak ->
            RebuiltAction(action.copy(due = DueMoment.ElapsedTarget(
                correction.anchorAt + soak.duration.maximum,
            )))
        }
    }

    private fun rebuildAction(
        batch: GrowBatch,
        action: ScheduledAction,
        actionIndex: Int,
        anchorIndex: Int,
        correction: ActionScheduleCorrection,
    ): RebuiltAction {
        val isDependentFutureAction = action.status == ScheduledActionStatus.PENDING &&
            actionIndex >= 0 && actionIndex > anchorIndex
        if (!isDependentFutureAction) return RebuiltAction(action)

        val durationStart = anchorIndex.coerceAtLeast(0)
        val elapsed = batch.recipe.stages
            .subList(durationStart, actionIndex)
            .fold(Duration.ZERO) { total, stage -> total + stage.duration.maximum }
        val proposedDue = action.due.shiftedFrom(correction.anchorAt, elapsed)
        if (!action.userFixed) return RebuiltAction(action.copy(due = proposedDue))
        val conflict = if (action.due == proposedDue) null else {
            ScheduleConflict(action.id, action.due, proposedDue)
        }
        return RebuiltAction(action, conflict)
    }

    private fun unchanged(actions: List<ScheduledAction>): ActionScheduleRebuild {
        val unique = actions.distinctBy { it.id }
        val uniqueIds = unique.mapTo(mutableSetOf()) { it.id }
        return ActionScheduleRebuild(
            actions = unique,
            cancelledActionIds = actions.mapTo(mutableSetOf()) { it.id } - uniqueIds,
            conflicts = emptyList(),
        )
    }

    private fun List<GrowStage>.anchorIndex(state: BatchState): Int = when (state) {
        BatchState.PLANNED -> BEFORE_FIRST_STAGE
        BatchState.SOAKING -> indexOf(GrowStage.SOAKING)
        BatchState.SOWN -> indexOf(GrowStage.SOWN)
        BatchState.BLACKOUT_OR_WEIGHT -> indexOf(GrowStage.BLACKOUT_OR_WEIGHT)
        BatchState.LIGHT_GROWTH -> indexOf(GrowStage.LIGHT_GROWTH)
        BatchState.READY_FOR_HARVEST -> indexOf(GrowStage.READY_FOR_HARVEST)
        BatchState.HARVESTED, BatchState.FAILED, BatchState.ABANDONED -> STAGE_NOT_CONFIGURED
    }

    private fun DueMoment.shiftedFrom(anchor: Instant, offset: Duration): DueMoment = when (this) {
        is DueMoment.ElapsedTarget -> DueMoment.ElapsedTarget(anchor + offset)
        is DueMoment.CivilTime -> {
            val zone = TimeZone.of(timeZoneId)
            DueMoment.CivilTime((anchor + offset).toLocalDateTime(zone), timeZoneId)
        }
    }

    private data class RebuiltAction(
        val action: ScheduledAction,
        val conflict: ScheduleConflict? = null,
    )

    private const val BEFORE_FIRST_STAGE = -1
    private const val STAGE_NOT_CONFIGURED = -1
    private const val NOT_A_STAGE_ACTION = -2
}
