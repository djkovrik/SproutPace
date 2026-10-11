package com.sedsoftware.sproutpace.compose

import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import com.sedsoftware.sproutpace.compose.paparazzi.SproutPacePreviewCatalog
import com.sedsoftware.sproutpace.compose.paparazzi.deviceFor
import com.android.resources.ScreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiPreviewContractTest {
    @Test
    fun landscapePreviewsKeepDeclaredOrientationAndAspectRatio() {
        val info = SproutPacePreviewCatalog.cases.map { it.preview.previewInfo }
            .first { it.name == "16 Expanded Notifications" }
        val device = deviceFor(info)
        assertEquals(ScreenOrientation.LANDSCAPE, device.orientation)
        assertTrue(device.screenWidth > device.screenHeight)
        assertEquals(info.widthDp.toDouble() / info.heightDp, device.screenWidth.toDouble() / device.screenHeight, 0.001)
    }

    @Test
    fun productionScenariosAndActualConfirmationDialogsAreDiscoverable() {
        val names = SproutPacePreviewCatalog.cases.map { it.preview.previewInfo.name }.toSet()
        val required = setOf(
            "03 Active Light", "03 Active Dark", "03 History RU 200 Outcomes",
            "04 Correction Light", "04 Invalid Mass EN", "05 Planned Light", "05 Soaking Dark",
            "05 Ready Light", "05 Harvested Dark", "05 Failed Light", "05 Abandoned Dark",
            "05 Skipped Stage RU 200", "08 Personal Edit Light", "08 Personal Create Medium",
            "08 Garden Cress RU 200", "08 Invalid Range RU 200", "08 Expanded Stages And Sources",
            "09 Edit Observation RU 200", "10 Failed Outcome Light", "10 Abandoned Outcome Dark",
            "12 Edit Zone Expanded", "12 Invalid Capacity RU 200", "13 Circle Editor RU 200",
            "14 Actual Restore Counts RU 200", "14 Actual Restore Counts Expanded",
            "14 Replace Restore Dialog EN", "14 Replace Restore Dialog RU 200",
            "04 Discard Draft Dialog EN", "04 Discard Draft Dialog RU 200", "05 Undo Stage Dialog Dark",
            "16 Disabled Notifications Light", "16 Disabled Notifications RU 200",
        )
        assertTrue("Missing production scenario captures: ${required - names}", names.containsAll(required))
    }

    @Test
    fun everyPrimaryScreenHasLightAndDarkCoverage() {
        val names = SproutPacePreviewCatalog.cases.map { it.preview.previewInfo.name }.toSet()
        val expected = PRIMARY_SCREEN_NAMES.flatMap { screen ->
            listOf("$screen Light", "$screen Dark")
        }
        assertTrue("Missing primary light/dark previews: ${expected - names}", names.containsAll(expected))
        assertEquals(16, UiScreen.entries.size)
    }

    @Test
    fun localeFontScaleAndAdaptiveStressVariantsAreExecutable() {
        val infos = SproutPacePreviewCatalog.cases.map { it.preview.previewInfo }
        val primaryStressNames = PRIMARY_SCREEN_NAMES.mapTo(linkedSetOf()) { "$it RU 200%" }
        val russianLargeText = infos.filter { it.locale == "ru" && it.fontScale == 2f && it.name in primaryStressNames }
        assertEquals(
            "Every primary screen must render the compact RU/200% accessibility stress case",
            primaryStressNames,
            russianLargeText.mapTo(linkedSetOf()) { it.name },
        )
        assertTrue(russianLargeText.all { it.widthDp == 393 && it.heightDp == 851 })
        assertEquals(
            ADAPTIVE_PREVIEW_NAMES,
            infos.filter { it.widthDp >= 840 && it.name in ADAPTIVE_PREVIEW_NAMES }.mapTo(linkedSetOf()) { it.name },
        )
        assertTrue(infos.any { it.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES })
        assertTrue(infos.any { it.uiMode and UI_MODE_NIGHT_MASK != UI_MODE_NIGHT_YES })
    }

    @Test
    fun advertisingInspectionPreviewsAreDiscoverableAndTallEnough() {
        val infosByName = SproutPacePreviewCatalog.cases.associate { case ->
            case.preview.previewInfo.name to case.preview.previewInfo
        }
        ADVERTISING_INSPECTION_PREVIEWS.forEach { (name, minimumHeightDp) ->
            val info = infosByName[name]
            assertTrue("Missing advertising inspection preview: $name", info != null)
            assertTrue(
                "Advertising inspection preview must expose its insertion point: $name",
                info != null && info.widthDp == 393 && info.heightDp >= minimumHeightDp,
            )
        }
    }

    @Test
    fun approvedCustomAssetInspectionPreviewIsDiscoverableAndTallEnough() {
        val infos = SproutPacePreviewCatalog.cases.map { it.preview.previewInfo }
        listOf("08 Recipe Asset Inspection", "08 Recipe Asset Inspection Dark").forEach { name ->
            val recipe = infos.singleOrNull { it.name == name }
            assertTrue("Missing Recipe custom-asset inspection preview: $name", recipe != null)
            assertTrue("Recipe asset inspection must expose all stage artwork", recipe != null && recipe.heightDp >= 1800)
            val expectedNight = name.endsWith("Dark")
            assertEquals(expectedNight, recipe!!.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES)
        }
    }

    @Test
    fun approvedEmptyIllustrationScreensHaveDeterministicLightAndDarkPreviews() {
        val names = SproutPacePreviewCatalog.cases.map { it.preview.previewInfo.name }.toSet()
        val required = setOf(
            "Empty state Light",
            "Empty state Dark",
            "03 Trays Empty Light",
            "03 Trays Empty Dark",
            "06 Plan Empty Light",
            "06 Plan Empty Dark",
        )
        assertTrue("Missing empty illustration previews: ${required - names}", names.containsAll(required))
    }

    @Test
    fun writesDeterministicCoverageInventory() {
        val cases = SproutPacePreviewCatalog.cases
        val output = File("build/reports/paparazzi/preview-coverage.json")
        output.parentFile.mkdirs()
        output.writeText(
            cases.joinToString(
                prefix = "{\n  \"previewCount\": ${cases.size},\n  \"cases\": [\n",
                postfix = "\n  ]\n}\n",
                separator = ",\n",
            ) { case ->
                val info = case.preview.previewInfo
                val theme = if (info.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES) "dark" else "light"
                buildString {
                    append("    {\"id\":\"")
                    append(case.id.jsonEscape())
                    append("\",\"name\":\"")
                    append(info.name.jsonEscape())
                    append("\",\"theme\":\"$theme\"")
                    append(",\"locale\":\"${info.locale.ifBlank { "en" }}\"")
                    append(",\"fontScale\":${info.fontScale}")
                    append(",\"widthDp\":${info.widthDp}")
                    append(",\"heightDp\":${info.heightDp}}")
                }
            },
        )
        assertTrue(output.isFile)
        assertEquals(cases.size, Regex("\\\"id\\\"").findAll(output.readText()).count())
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        val ADVERTISING_INSPECTION_PREVIEWS = mapOf(
            "03 Trays History Loaded Ad Inspection" to 1300,
            "07 Catalog Loaded Ad Inspection" to 1800,
            "07 Catalog Hidden Ad Offline Inspection" to 1800,
        )

        val ADAPTIVE_PREVIEW_NAMES = linkedSetOf(
            "Expanded today",
            "Expanded list detail",
            "Expanded batch detail",
            "Expanded plan",
            "Expanded catalog",
        )

        val PRIMARY_SCREEN_NAMES = listOf(
            "01 First run",
            "02 Today",
            "03 Trays",
            "04 Batch editor",
            "05 Batch detail",
            "06 Plan",
            "07 Catalog",
            "08 Recipe",
            "09 Observation",
            "10 Close batch",
            "11 Settings",
            "12 Zones",
            "13 Tray sizes",
            "14 Backup",
            "15 Privacy",
            "16 Notifications",
        )
    }
}
