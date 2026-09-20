package com.sedsoftware.sproutpace.compose

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetUiSourceContractTest {
    @Test
    fun productionRendererReferencesEveryApprovedCustomVisualRole() {
        val source = projectFile(
            "src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ScreenContent.kt",
            "shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ScreenContent.kt",
        ).readText()
        REQUIRED_RESOURCES.forEach { resource ->
            assertTrue("Missing typed production resource reference: $resource", source.contains("Res.drawable.$resource"))
        }
        assertTrue("Decorative artwork must retain null semantics", source.contains("contentDescription = null"))
        assertTrue("Labeled actions must keep their localized text", source.contains("Text(localizedText(labelKey))"))
        assertTrue(
            "Empty artwork must be restricted to Today, Trays, and Plan",
            source.contains("setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN)"),
        )
        listOf("today_empty_title", "trays_empty_title", "plan_empty_title").forEach { key ->
            assertTrue("Missing screen-specific empty copy: $key", source.contains(key))
        }
    }

    @Test
    fun snapshotPrimaryActionsUseExplicitStableUiIds() {
        val source = projectFile(
            "src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/RootUiAdapter.kt",
            "shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/RootUiAdapter.kt",
        ).readText()
        mapOf(
            "action_new_batch" to "new-batch",
            "action_add_zone" to "save-zone",
            "action_add_preset" to "save-tray-size",
        ).forEach { (key, id) ->
            assertTrue("Missing primary action mapping for $key", source.contains("\"$key\" to \"$id\""))
        }
        assertTrue("EMPTY must hide contextual actions", source.contains("listOfNotNull(snapshotPrimaryAction)"))
    }

    private fun projectFile(vararg candidates: String): File =
        candidates.asSequence().map(::File).firstOrNull(File::isFile)
            ?: error("Missing project file; tried ${candidates.joinToString()}")

    private companion object {
        val REQUIRED_RESOURCES = listOf(
            "logo_sproutpace", "ic_settings", "ic_add", "ic_edit", "ic_delete", "ic_add_photo",
            "ic_notifications", "ic_backup", "ic_repeat", "ic_check_circle", "stage_soaking",
            "stage_sown", "stage_blackout", "stage_light_growth", "stage_harvest",
            "illustration_empty_today", "culture_radish", "culture_broccoli", "culture_arugula",
            "culture_mustard", "culture_pea", "culture_sunflower", "culture_basil", "culture_garden_cress",
        )
    }
}
