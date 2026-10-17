package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.engine.MeasurementEngine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Base64
import kotlin.test.*

class DraftGuidanceAndPhotoProductionTest {
    @Test
    fun unsavedSelectionsRecalculateRecommendationWithoutReplacingExplicitSeedMass() = runTest {
        withPublicWorkspace { directory, preferences ->
            PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                    "name" to "Small", "shape" to "rectangle", "widthCm" to "10", "lengthCm" to "20"))
                fixture.save(ScreenId.TRAY_SIZES, null, "save-tray-size", mapOf(
                    "name" to "Large", "shape" to "rectangle", "widthCm" to "20", "lengthCm" to "30"))
                fixture.save(ScreenId.RECIPE, null, "save-recipe", mapOf(
                    "name" to "Pea recipe", "cultureId" to "pea", "substrate" to "Coco",
                    "stage1" to "sown", "stage1MinimumHours" to "0", "stage1MaximumHours" to "0",
                    "stage1Guidance" to "Sow", "stage2" to "light_growth", "stage2MinimumHours" to "24",
                    "stage2MaximumHours" to "48", "stage2Guidance" to "Grow", "stage3" to "ready_for_harvest",
                    "stage3MinimumHours" to "0", "stage3MaximumHours" to "0", "stage3Guidance" to "Harvest",
                    "stage4" to "none", "stage5" to "none"))
                val setup = fixture.database.snapshot()
                val small = setup.trayPresets.single { it.name == "Small" }
                val large = setup.trayPresets.single { it.name == "Large" }
                val recipe = setup.recipes.single()
                val radish = SproutPaceCatalog.cultures.single { it.id.value == "radish" }
                fixture.open(ScreenId.BATCH_EDITOR)
                fixture.dispatch(UiAction.ChooseOption("recipeId", ""))
                fixture.dispatch(UiAction.ChooseOption("cultureId", "radish"))
                fixture.dispatch(UiAction.ChooseOption("trayId", small.id.value))
                fun recommendation() = fixture.model().facts.single { it.id == "recommended-seed-mass" }.authoredValue
                assertEquals(MeasurementEngine.recommendedSeedMass(small.shape, radish.seedDensity).value.toString(), recommendation())
                fixture.dispatch(UiAction.ChooseOption("trayId", large.id.value))
                assertEquals(MeasurementEngine.recommendedSeedMass(large.shape, radish.seedDensity).value.toString(), recommendation())
                fixture.edit(mapOf("actualSeedMassGrams" to "99"))
                fixture.dispatch(UiAction.ChooseOption("cultureId", "pea"))
                val pea = SproutPaceCatalog.cultures.single { it.id.value == "pea" }
                assertEquals(MeasurementEngine.recommendedSeedMass(large.shape, pea.seedDensity).value.toString(), recommendation())
                fixture.dispatch(UiAction.ChooseOption("recipeId", recipe.id.value))
                val expected = MeasurementEngine.recommendedSeedMass(large.shape, recipe.density)
                assertEquals(expected.value.toString(), recommendation())
                assertEquals("99", fixture.field("actualSeedMassGrams"))
                assertTrue(fixture.database.snapshot().batches.isEmpty())
                fixture.edit(mapOf("substrate" to "Coco", "plannedSowDate" to "2026-10-10"))
                fixture.submit("save-batch")
                val batch = fixture.database.snapshot().batches.single()
                assertEquals(expected, batch.recommendedSeedMass)
                assertEquals(99.0, batch.actualSeedMass.value)
            }
        }
    }

    @Test
    fun pendingPhotoReplacementRemovalAndCommittedReopenUseActualImageBytes() = runTest {
        withPublicWorkspace { directory, preferences ->
            val first = Base64.getDecoder().decode(FIRST_PHOTO)
            val second = Base64.getDecoder().decode(SECOND_PHOTO)
            val observationId = PublicProductionFixture(directory, preferences).use { fixture ->
                fixture.seed()
                fixture.open(ScreenId.OBSERVATION_EDITOR, "batch")
                fun preview() = fixture.model().toUiState().observationPreviewPhoto()
                suspend fun choose(token: String, bytes: ByteArray) {
                    fixture.pendingPhotos.stage(token, bytes)
                    fixture.selectedPhoto = pendingPhotoDraftValue(token)
                    fixture.dispatch(UiAction.RequestPlatform(PlatformActionRequest(PlatformActionKind.CHOOSE_PHOTO, "photoId")))
                    advanceUntilIdle()
                    assertEquals(pendingPhotoDraftValue(token), preview()?.id)
                    assertContentEquals(bytes, preview()?.bytes)
                    assertTrue(fixture.database.snapshot().observations.isEmpty())
                }
                choose("first", first)
                choose("replacement", second)
                fixture.dispatch(UiAction.ClearPickerSelection("photoId", fixture.field("photoId"), PlatformActionKind.CHOOSE_PHOTO))
                assertNull(preview())
                choose("retained", second)
                fixture.edit(mapOf("text" to "Measured growth"))
                fixture.submit("save-observation")
                val observation = fixture.database.snapshot().observations.single()
                val photoId = assertNotNull(observation.photoId)
                assertContentEquals(second, fixture.files.read(photoId))
                observation.id.value
            }
            PublicProductionFixture(directory, preferences).use { reopened ->
                reopened.open(ScreenId.OBSERVATION_EDITOR, observationId)
                val preview = assertNotNull(reopened.model().toUiState().observationPreviewPhoto())
                assertContentEquals(second, preview.bytes)
                assertEquals(reopened.field("photoId"), preview.id)
            }
        }
    }

    private companion object {
        const val FIRST_PHOTO = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAGUlEQVR4nGPUiHITYWDARCwM/AxYweCUAAA1lwHb01DjNwAAAABJRU5ErkJggg=="
        const val SECOND_PHOTO = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a2ioAAAAASUVORK5CYII="
    }
}
