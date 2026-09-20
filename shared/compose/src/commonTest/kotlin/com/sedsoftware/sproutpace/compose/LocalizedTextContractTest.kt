package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenContractRegistry
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LocalizedTextContractTest {
    @Test
    fun everyTypedScreenContractKeyResolvesStrictly() {
        ScreenContractRegistry.contracts.values.forEach { contract ->
            strictTextResource("screen_${contract.id.name.lowercase()}_title")
            contract.fields.forEach { field ->
                strictTextResource(field.labelKey.value)
                field.options.forEach { option -> option.labelKey?.value?.let(::strictTextResource) }
            }
            contract.facts.forEach { fact ->
                strictTextResource(fact.labelKey.value)
                fact.valueKey?.value?.let(::strictTextResource)
            }
            contract.actions.forEach { action -> strictTextResource(action.labelKey.value) }
        }
        setOf(
            "choose_another_date", "choose_another_zone", "change_capacity", "leave_unassigned",
            "error_operation_failed", "preview_item_title", "preview_item_supporting",
            "plan_active", "plan_paused", "privacy_accepted", "privacy_declined", "privacy_undecided",
            "action_status_pending", "action_status_completed", "action_status_skipped", "action_status_cancelled",
            "safety_untreated_seed", "safety_clean_container", "safety_drinking_water", "safety_drainage",
            "safety_avoid_excess_moisture", "safety_allergy_awareness", "safety_images_not_diagnostic",
            "source_penn_state_growing_guide", "source_penn_state_abcs",
        ).forEach(::strictTextResource)
        val cultures = listOf("radish", "broccoli", "arugula", "mustard", "pea", "sunflower", "basil", "garden_cress")
        val suffixes = listOf(
            "name", "scientific_name", "soaking", "sowing", "blackout", "light_growth", "harvest",
            "substrate_note", "lighting_note",
        )
        cultures.forEach { culture -> suffixes.forEach { suffix -> strictTextResource("culture_${culture}_$suffix") } }
    }

    @Test
    fun unknownKeyFailsInsteadOfShowingRawKeyInProduction() {
        assertFailsWith<IllegalArgumentException> { strictTextResource("missing_production_key") }
    }
}
