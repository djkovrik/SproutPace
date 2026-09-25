package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals

class TodayLayoutTest {
    @Test
    fun overdueItemsAreSeparatedFromDueTodayItems() {
        val overdue = item(supportingKey = "today_moisture_overdue")
        val dueToday = item(supportingKey = "today_due")

        assertEquals(TodayItemGroup.OVERDUE, overdue.todayGroup())
        assertEquals(TodayItemGroup.DUE_TODAY, dueToday.todayGroup())
    }

    @Test
    fun authoredOrUnknownItemsRemainInTheSafeDueTodayGroup() {
        assertEquals(TodayItemGroup.DUE_TODAY, item(authoredTitle = "User batch").todayGroup())
    }

    private fun item(
        supportingKey: String? = null,
        authoredTitle: String? = null,
    ) = UiItem(
        id = "item",
        titleKey = null,
        authoredTitle = authoredTitle,
        supportingKey = supportingKey,
        stateKey = null,
        actionId = null,
        selected = false,
    )
}
