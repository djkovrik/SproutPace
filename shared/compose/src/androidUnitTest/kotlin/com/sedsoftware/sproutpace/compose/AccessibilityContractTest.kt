package com.sedsoftware.sproutpace.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityContractTest {
    @Test
    fun interactiveRendererDeclaresMinimumTargetsRolesErrorsAndStateDescriptions() {
        val source = projectFile(
            "src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ScreenContent.kt",
            "shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ScreenContent.kt",
        ).readText()
        assertTrue("48dp minimum targets", source.contains("heightIn(min = 48.dp)"))
        assertTrue("switch role", source.contains("role = Role.Switch"))
        assertTrue("radio role", source.contains("role = Role.RadioButton"))
        assertTrue("checkbox role", source.contains("role = Role.Checkbox"))
        assertTrue("validation error semantics", source.contains("error(text)"))
        assertTrue("state descriptions", source.contains("stateDescription ="))
        assertTrue("headings", source.contains("heading()"))
        assertTrue("context inputs are expandable", source.contains("toggle-context-fields"))
        assertTrue("destructive actions use error color", source.contains("contentColor = MaterialTheme.colorScheme.error"))
        assertTrue(
            "facts and actions precede edit mechanics",
            source.indexOf("actionItems(state, onAction)") < source.indexOf("fieldItems(state, contextFieldsExpanded"),
        )
    }

    @Test
    fun englishAndRussianProductionResourcesHaveIdenticalKeys() {
        val english = resourceKeys(
            projectFile(
                "src/commonMain/composeResources/values/strings.xml",
                "shared/compose/src/commonMain/composeResources/values/strings.xml",
            ),
        )
        val russian = resourceKeys(
            projectFile(
                "src/commonMain/composeResources/values-ru/strings.xml",
                "shared/compose/src/commonMain/composeResources/values-ru/strings.xml",
            ),
        )
        assertEquals("EN/RU resource keys must remain lossless", english, russian)
    }

    @Test
    fun productionRendererHasNoSampleOnlyTextPath() {
        val productionSources = listOf("App.kt", "RootUiAdapter.kt", "ScreenContent.kt").joinToString("\n") { name ->
            projectFile(
                "src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/$name",
                "shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/$name",
            ).readText()
        }
        assertTrue("sample_* keys must not drive production UI", !Regex("sample_[a-z0-9_]+").containsMatchIn(productionSources))
    }

    private fun resourceKeys(file: File): Set<String> =
        Regex("<string name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    private fun projectFile(vararg candidates: String): File =
        candidates.asSequence().map(::File).firstOrNull(File::isFile)
            ?: error("Missing project file; tried ${candidates.joinToString()}")
}
