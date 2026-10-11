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
    const val VERSION: Int = 2
    val reviewedOn: LocalDate = LocalDate(2026, 10, 11)

    private val utahState = SourceMetadata(
        url = "https://extension.usu.edu/yardandgarden/research/grow-your-own-microgreens",
        titleKey = LocalizationKey("source_utah_state_growing_guide"),
        reviewedOn = reviewedOn,
    )
    private val mustardGuide = SourceMetadata(
        url = "https://deliseeds.com/products/green-mustard-microgreens-seeds",
        titleKey = LocalizationKey("source_deliseeds_mustard"),
        reviewedOn = reviewedOn,
    )
    private val cressGuide = SourceMetadata(
        url = "https://deliseeds.com/products/cress-microgreens-seeds",
        titleKey = LocalizationKey("source_deliseeds_cress"),
        reviewedOn = reviewedOn,
    )

    val cultures: List<BuiltInCulture> = listOf(
        culture("radish", 42.0, NOMINAL_1020_AREA, utahState, null, 1..2, 8..12),
        culture("broccoli", 28.0, NOMINAL_1020_AREA, utahState, null, 2..4, 8..12),
        culture("arugula", 10.0, NOMINAL_1020_AREA, utahState, null, 1..2, 6..8),
        culture("mustard", 10.0, SUPPLIER_TRAY_AREA, mustardGuide, null, 3..4, 8..12),
        culture("pea", 150.0, NOMINAL_1020_AREA, utahState, 6..6, 3..3, 8..10),
        culture("sunflower", 48.0, NOMINAL_1020_AREA, utahState, 12..12, 3..3, 9..10),
        culture("basil", 28.0, NOMINAL_1020_AREA, utahState, null, 4..7, 12..16),
        culture("garden_cress", 10.0, SUPPLIER_TRAY_AREA, cressGuide, null, 3..5, 8..12),
    )

    val cultureIds: Set<CultureId> = cultures.mapTo(linkedSetOf()) { it.id }

    private fun culture(
        id: String,
        seedGrams: Double,
        referenceAreaCm2: Double,
        source: SourceMetadata,
        soak: IntRange?,
        blackoutDays: IntRange,
        sowToHarvestDays: IntRange,
    ): BuiltInCulture {
        // Source ranges are retained in the culture-specific guidance and provenance.
        // Use the upper covered duration as a planning allowance, then subtract it
        // from BOTH harvest endpoints. Independent stage sums now reproduce the
        // source's sow-to-harvest window; light duration is an app allocation, not a trial result.
        val coveredDays = blackoutDays.last
        require(sowToHarvestDays.first >= coveredDays)
        val stageList = buildList {
            soak?.let {
                add(
                    StageGuidance(
                        GrowStage.SOAKING,
                        DurationRange(it.first.hours, it.last.hours),
                        LocalizationKey("culture_${id}_v2_soaking"),
                    ),
                )
            }
            add(StageGuidance(GrowStage.SOWN, DurationRange(0.hours, 0.hours), LocalizationKey("culture_${id}_v2_sowing")))
            add(
                StageGuidance(
                    GrowStage.BLACKOUT_OR_WEIGHT,
                    DurationRange(coveredDays.days, coveredDays.days),
                    LocalizationKey("culture_${id}_v2_blackout"),
                ),
            )
            add(
                StageGuidance(
                    GrowStage.LIGHT_GROWTH,
                    DurationRange((sowToHarvestDays.first - coveredDays).days, (sowToHarvestDays.last - coveredDays).days),
                    LocalizationKey("culture_${id}_v2_light_growth"),
                ),
            )
            add(StageGuidance(GrowStage.READY_FOR_HARVEST, DurationRange(0.hours, 0.hours), LocalizationKey("culture_${id}_v2_harvest")))
        }
        return BuiltInCulture(
            id = CultureId(id),
            nameKey = LocalizationKey("culture_${id}_name"),
            scientificNameKey = LocalizationKey("culture_${id}_scientific_name"),
            illustrationResourceName = "culture_${id}_cover",
            stages = stageList,
            seedDensity = SeedDensity(seedGrams / referenceAreaCm2, VERSION, source),
            substrateNoteKey = LocalizationKey("culture_${id}_v2_substrate_note"),
            lightingNoteKey = LocalizationKey("culture_${id}_v2_lighting_note"),
            safetyNoteKeys = listOf(
                LocalizationKey("safety_untreated_seed"),
                LocalizationKey("safety_clean_container"),
                LocalizationKey("safety_drinking_water"),
                LocalizationKey("safety_drainage"),
                LocalizationKey("safety_avoid_excess_moisture"),
                LocalizationKey("safety_allergy_awareness"),
                LocalizationKey("safety_images_not_diagnostic"),
            ),
            sources = listOf(source),
        )
    }
}

// USU says 1020, without an internal growing area: nominal 10 × 20 in is
// an explicit normalization assumption, not a measurement of the user's tray.
private const val NOMINAL_1020_AREA = 1290.32
// Deliseeds explicitly specifies 25 × 50 cm; do not substitute nominal inches.
private const val SUPPLIER_TRAY_AREA = 1250.0
