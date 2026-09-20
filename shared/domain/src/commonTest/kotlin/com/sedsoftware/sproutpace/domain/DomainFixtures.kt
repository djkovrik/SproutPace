package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

internal object DomainFixtures {
    val instant: Instant = Instant.parse("2026-09-20T08:00:00Z")
    val source = SourceMetadata("https://example.test/source", LocalizationKey("source_test"), LocalDate(2026, 9, 20))
    val density = SeedDensity(0.02, 1, source)
    val stages = listOf(
        RecipeStage(GrowStage.SOAKING, DurationRange(8.hours, 12.hours), guidanceKey = LocalizationKey("test_soaking")),
        RecipeStage(GrowStage.SOWN, DurationRange(Duration.ZERO, Duration.ZERO), guidanceKey = LocalizationKey("test_sown")),
        RecipeStage(GrowStage.BLACKOUT_OR_WEIGHT, DurationRange(2.days, 3.days), guidanceKey = LocalizationKey("test_blackout")),
        RecipeStage(GrowStage.LIGHT_GROWTH, DurationRange(4.days, 6.days), guidanceKey = LocalizationKey("test_light")),
        RecipeStage(GrowStage.READY_FOR_HARVEST, DurationRange(Duration.ZERO, Duration.ZERO), guidanceKey = LocalizationKey("test_harvest")),
    )
    val recipe = RecipeSnapshot(RecipeId("recipe-1"), null, "User recipe", stages, density)
    val tray = TraySnapshot("Tray", TrayShape.Rectangle(10.0, 20.0))

    fun batch(
        state: BatchState = BatchState.PLANNED,
        placement: Placement = Placement.None,
        events: List<StageEvent> = emptyList(),
        actions: List<ScheduledAction> = emptyList(),
    ) = GrowBatch(
        id = BatchId("batch-1"), recipe = recipe, tray = tray, substrateSnapshot = "Coco",
        recommendedSeedMass = PositiveGrams(4.0), actualSeedMass = PositiveGrams(4.0),
        state = state, placement = placement, events = events, actions = actions,
    )
}
