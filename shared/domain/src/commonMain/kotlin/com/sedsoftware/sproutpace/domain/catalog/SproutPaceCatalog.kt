package com.sedsoftware.sproutpace.domain.catalog

import com.sedsoftware.sproutpace.domain.model.BuiltInCulture
import com.sedsoftware.sproutpace.domain.model.CultureId
import com.sedsoftware.sproutpace.domain.model.DurationRange
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.LocalizationKey
import com.sedsoftware.sproutpace.domain.model.SeedDensity
import com.sedsoftware.sproutpace.domain.model.SourceMetadata
import com.sedsoftware.sproutpace.domain.model.StageGuidance
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object SproutPaceCatalog {
    const val VERSION: Int = 1
    val reviewedOn: LocalDate = LocalDate(2026, 9, 20)

    private val pennState = SourceMetadata(
        url = "https://extension.psu.edu/a-step-by-step-guide-for-growing-microgreens-at-home",
        titleKey = LocalizationKey("source_penn_state_growing_guide"),
        reviewedOn = reviewedOn,
    )
    private val pennStateAbcs = SourceMetadata(
        url = "https://extension.psu.edu/the-abcs-of-microgreens",
        titleKey = LocalizationKey("source_penn_state_abcs"),
        reviewedOn = reviewedOn,
    )

    val cultures: List<BuiltInCulture> = listOf(
        culture("radish", 0.028, soak = null, blackoutDays = 3..4, harvestDays = 7..10),
        culture("broccoli", 0.022, soak = null, blackoutDays = 3..4, harvestDays = 8..12),
        culture("arugula", 0.018, soak = null, blackoutDays = 3..4, harvestDays = 8..12),
        culture("mustard", 0.020, soak = null, blackoutDays = 3..4, harvestDays = 7..10),
        culture("pea", 0.085, soak = 8..12, blackoutDays = 3..5, harvestDays = 10..14),
        culture("sunflower", 0.090, soak = 8..12, blackoutDays = 3..5, harvestDays = 9..13),
        culture("basil", 0.014, soak = null, blackoutDays = 4..6, harvestDays = 14..21),
        culture("garden_cress", 0.018, soak = null, blackoutDays = 2..3, harvestDays = 7..10),
    )

    val cultureIds: Set<CultureId> = cultures.mapTo(linkedSetOf()) { it.id }

    private fun culture(
        id: String,
        density: Double,
        soak: IntRange?,
        blackoutDays: IntRange,
        harvestDays: IntRange,
    ): BuiltInCulture {
        val stageList = buildList {
            soak?.let {
                add(
                    StageGuidance(
                        GrowStage.SOAKING,
                        DurationRange(it.first.hours, it.last.hours),
                        LocalizationKey("culture_${id}_soaking"),
                    ),
                )
            }
            add(StageGuidance(GrowStage.SOWN, DurationRange(0.hours, 0.hours), LocalizationKey("culture_${id}_sowing")))
            add(
                StageGuidance(
                    GrowStage.BLACKOUT_OR_WEIGHT,
                    DurationRange(blackoutDays.first.days, blackoutDays.last.days),
                    LocalizationKey("culture_${id}_blackout"),
                ),
            )
            add(
                StageGuidance(
                    GrowStage.LIGHT_GROWTH,
                    DurationRange(harvestDays.first.days, harvestDays.last.days),
                    LocalizationKey("culture_${id}_light_growth"),
                ),
            )
            add(StageGuidance(GrowStage.READY_FOR_HARVEST, DurationRange(0.hours, 0.hours), LocalizationKey("culture_${id}_harvest")))
        }
        return BuiltInCulture(
            id = CultureId(id),
            nameKey = LocalizationKey("culture_${id}_name"),
            scientificNameKey = LocalizationKey("culture_${id}_scientific_name"),
            illustrationResourceName = "culture_${id}_cover",
            stages = stageList,
            seedDensity = SeedDensity(density, VERSION, pennState),
            substrateNoteKey = LocalizationKey("culture_${id}_substrate_note"),
            lightingNoteKey = LocalizationKey("culture_${id}_lighting_note"),
            safetyNoteKeys = listOf(
                LocalizationKey("safety_untreated_seed"),
                LocalizationKey("safety_clean_container"),
                LocalizationKey("safety_drinking_water"),
                LocalizationKey("safety_drainage"),
                LocalizationKey("safety_avoid_excess_moisture"),
                LocalizationKey("safety_allergy_awareness"),
                LocalizationKey("safety_images_not_diagnostic"),
            ),
            sources = listOf(pennState, pennStateAbcs),
        )
    }
}
