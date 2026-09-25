package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrimaryTabLayoutTest {
    @Test
    fun creationFabIsLimitedToScreensWithOnePersistentCreationAction() {
        assertEquals("new-batch", UiScreen.TODAY.fabActionId())
        assertEquals("new-batch", UiScreen.TRAYS.fabActionId())
        assertEquals("create-recipe", UiScreen.CATALOG.fabActionId())
        assertNull(UiScreen.PLAN.fabActionId())
    }
}
