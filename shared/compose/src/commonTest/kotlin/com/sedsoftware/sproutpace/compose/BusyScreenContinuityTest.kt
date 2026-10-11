package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.component.TextKey
import kotlin.test.Test
import kotlin.test.assertEquals

class BusyScreenContinuityTest {
    @Test
    fun operationProgressKeepsLoadedScreenSoSelectedEditorAndPreviewStayMounted() {
        ScreenId.entries.forEach { screen ->
            val loaded = ScreenModel(screen, LoadState.CONTENT, TextKey("screen_today_title"))
            assertEquals(loaded.toUiState().variant, loaded.copy(busy = true).toUiState().variant)
        }
    }
}
