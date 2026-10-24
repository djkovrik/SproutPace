package com.sedsoftware.sproutpace

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** Covers the native starting window, which is displayed before Compose can apply AppTheme. */
class LaunchThemeContractTest {
    @Test
    fun firstLaunchUsesLightAndNightPlatformThemesWithMatchingWindowAndSplashBackgrounds() {
        val application = xml("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("@style/Theme.SproutPace", application.getAttribute("android:theme"))
        listOf("values" to "Light.", "values-night" to "").forEach { (folder, light) ->
            val base = style(folder, "Theme.SproutPace.Base")
            assertEquals("@android:style/Theme.Material.${light}NoActionBar", base.getAttribute("parent"))
            assertEquals("@color/app_window_background", items(base)["android:windowBackground"])
            assertEquals("@color/app_window_background", items(base)["android:colorBackground"])
            assertEquals("@color/app_navigation_bar", items(base)["android:navigationBarColor"])
            assertEquals((folder == "values").toString(), items(base)["android:windowLightStatusBar"])
            assertEquals((folder == "values").toString(), items(base)["android:windowLightNavigationBar"])
            assertEquals("false", items(base)["android:forceDarkAllowed"])
            assertEquals("Theme.SproutPace.Base", style(folder, "Theme.SproutPace").getAttribute("parent"))
        }
        val splash = style("values-v31", "Theme.SproutPace")
        assertEquals("Theme.SproutPace.Base", splash.getAttribute("parent"))
        assertEquals("@color/app_window_background", items(splash)["android:windowSplashScreenBackground"])
    }

    @Test
    fun legacyLightNavigationButtonsKeepAContrastingBarUntilDarkIconsAreSupported() {
        assertEquals("#101710", color("values", "app_navigation_bar"))
        assertEquals("@color/app_window_background", color("values-v26", "app_navigation_bar"))
    }

    @Test
    fun nativeBackgroundColorsMatchTheProductionComposeSurfaces() {
        val palette = projectFile("../shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/theme/Color.kt")
            .readText()
        listOf("values" to "Light", "values-night" to "Dark").forEach { (folder, mode) ->
            val colors = xml("src/main/res/$folder/colors.xml").getElementsByTagName("color")
            val background = (0 until colors.length).map { colors.item(it) as Element }
                .single { it.getAttribute("name") == "app_window_background" }.textContent.trim()
            val compose = Regex("Surface$mode = Color\\(0xFF([A-Fa-f0-9]{6})\\)")
                .find(palette)!!.groupValues[1]
            assertEquals("#$compose", background)
        }
    }

    private fun style(folder: String, name: String): Element {
        val styles = xml("src/main/res/$folder/themes.xml").getElementsByTagName("style")
        return (0 until styles.length).map { styles.item(it) as Element }.single { it.getAttribute("name") == name }
    }

    private fun items(style: Element): Map<String, String> {
        val items = style.getElementsByTagName("item")
        return (0 until items.length).map { items.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private fun color(folder: String, name: String): String {
        val colors = xml("src/main/res/$folder/colors.xml").getElementsByTagName("color")
        return (0 until colors.length).map { colors.item(it) as Element }
            .single { it.getAttribute("name") == name }.textContent.trim()
    }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(projectFile(path))

    private fun projectFile(path: String): File = listOf(File(path), File("androidApp", path)).first { it.isFile }
}
