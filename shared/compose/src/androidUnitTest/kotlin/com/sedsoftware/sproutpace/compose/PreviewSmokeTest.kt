package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import com.sedsoftware.sproutpace.compose.paparazzi.SproutPacePreviewCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSmokeTest {
    @Test
    fun previewEntryPointNeedsNoProductionRoot() {
        val previewContent: @Composable () -> Unit = {
            App(state = SproutPaceUiState(screen = UiScreen.FIRST_RUN))
        }

        assertNotNull(previewContent)
    }

    @Test
    fun scannerFindsDeterministicProductionPreviewSet() {
        val cases = SproutPacePreviewCatalog.cases
        assertTrue("Expected a nonzero production preview set", cases.isNotEmpty())
        assertEquals(cases.sortedBy { it.id }.map { it.id }, cases.map { it.id })
        assertEquals(cases.size, cases.map { it.id }.distinct().size)
    }
}
