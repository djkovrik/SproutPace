package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.data.ProductionScreenFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfirmationSubjectTest {
    @Test
    fun batchConfirmationUsesExactIdentityEvenForSameCulture() {
        val source = typedPreviewState(UiScreen.TRAYS, scenario = ProductionScreenFixtures.Scenario.ACTIVE)
        source.items.forEach { item ->
            assertEquals(item, confirmationSubject(ScreenCommand("delete-batch", item.id), source))
        }
    }

    @Test
    fun zoneEditorConfirmationUsesSavedHumanNameAndResetHasNoUnrelatedSubject() {
        val source = typedPreviewState(UiScreen.ZONES, selectedId = "shelf")
        val item = source.items.first()
        assertEquals(item, confirmationSubject(ScreenCommand("delete-zone", item.id), source))
        assertNull(confirmationSubject(ScreenCommand("delete-all-data"), source))
    }
}
