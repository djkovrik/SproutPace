package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenActionPresentationTest {
    @Test
    fun `notification summaries are not repeated above their editable controls`() {
        assertFalse(SproutPaceUiState(screen = UiScreen.NOTIFICATIONS).showsItemCollection())
        assertTrue(SproutPaceUiState(screen = UiScreen.BACKUP_RESTORE).showsItemCollection())
    }

    @Test
    fun `item actions are not repeated as full width screen actions`() {
        val state = SproutPaceUiState(
            screen = UiScreen.BACKUP_RESTORE,
            items = listOf(item("create", "create-backup")),
            actions = listOf(action("create-backup"), action("restore-backup")),
        )

        assertEquals(listOf("restore-backup"), state.visibleScreenActions().map(UiScreenAction::id))
    }

    @Test
    fun `close batch keeps one save action beside the outcome form`() {
        val state = SproutPaceUiState(
            screen = UiScreen.CLOSE_BATCH,
            actions = listOf(
                action("save-outcome", primary = true),
                action("close-harvest"),
                action("close-failed"),
                action("close-abandoned"),
                action("record-actual-mass"),
            ),
        )

        assertEquals(listOf("save-outcome"), state.visibleScreenActions().map(UiScreenAction::id))
    }

    @Test
    fun `cards with direct actions do not add a meaningless selection focus stop`() {
        assertFalse(itemCardIsSelectable(UiScreen.BACKUP_RESTORE, item("backup", "create-backup")))
        assertFalse(
            itemCardIsSelectable(
                UiScreen.BATCH_DETAIL,
                item("batch", null).copy(commands = listOf(UiItemCommand("open-batch", "batch"))),
            ),
        )
        assertTrue(itemCardIsSelectable(UiScreen.CATALOG, item("passive", null)))
        assertFalse(itemCardIsSelectable(UiScreen.FIRST_RUN, item("progress", null)))
    }

    @Test
    fun `first run exposes only the fields belonging to the current step`() {
        assertEquals(setOf("zoneName", "type", "capacity"), firstRunFieldIds(FirstRunStep.ZONE, null))
        assertEquals(
            setOf("trayName", "shape", "widthCm", "lengthCm"),
            firstRunFieldIds(FirstRunStep.TRAY, "rectangle"),
        )
        assertEquals(
            setOf("trayName", "shape", "diameterCm"),
            firstRunFieldIds(FirstRunStep.TRAY, "circle"),
        )
        assertEquals(setOf("cadenceDays", "cultureIds", "mode"), firstRunFieldIds(FirstRunStep.PLAN, null))
    }

    private fun item(id: String, actionId: String?) = UiItem(
        id = id,
        titleKey = null,
        authoredTitle = id,
        supportingKey = null,
        stateKey = null,
        actionId = actionId,
        selected = false,
    )

    private fun action(id: String, primary: Boolean = false) = UiScreenAction(
        id = id,
        labelKey = "action_${id.replace('-', '_')}",
        operation = id,
        enabled = true,
        primary = primary,
    )
}
