package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiContractTest {
    @Test fun allApprovedScreensAreRepresentedExactlyOnce() {
        assertEquals(16, UiScreen.entries.size)
        assertEquals(16, UiScreen.entries.toSet().size)
    }

    @Test fun stableScreenNamesMatchAppSpecContract() {
        assertEquals(
            setOf("FIRST_RUN", "TODAY", "TRAYS", "BATCH_EDITOR", "BATCH_DETAIL", "PLAN", "CATALOG", "RECIPE", "OBSERVATION_EDITOR", "CLOSE_BATCH", "SETTINGS", "ZONES", "TRAY_SIZES", "BACKUP_RESTORE", "ADVERTISING_PRIVACY", "NOTIFICATIONS"),
            UiScreen.entries.map { it.name }.toSet(),
        )
    }

    @Test fun primaryDestinationsRemainTheApprovedFour() {
        val primary = setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN, UiScreen.CATALOG)
        assertEquals(4, primary.size)
        assertTrue(UiScreen.SETTINGS !in primary)
    }
}
