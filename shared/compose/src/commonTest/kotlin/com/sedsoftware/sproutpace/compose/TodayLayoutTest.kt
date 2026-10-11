package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import com.sedsoftware.sproutpace.component.TaskDayGroup
import com.sedsoftware.sproutpace.component.TaskProjection
import kotlinx.datetime.Instant

class TodayLayoutTest {
    @Test
    fun overdueItemsAreSeparatedFromDueTodayItems() {
        val overdue = item(supportingKey = "today_due").copy(task = task(TaskDayGroup.OVERDUE))
        val dueToday = item(supportingKey = "today_moisture_overdue").copy(task = task(TaskDayGroup.TODAY))

        assertEquals(TodayItemGroup.OVERDUE, overdue.todayGroup())
        assertEquals(TodayItemGroup.DUE_TODAY, dueToday.todayGroup())
    }

    @Test
    fun undatedSuggestionsAndFutureTasksAreNotCalledToday() {
        assertEquals(TodayItemGroup.UPCOMING, item(authoredTitle = "User batch").todayGroup())
        assertEquals(TodayItemGroup.UPCOMING, item().copy(task = task(TaskDayGroup.UPCOMING)).todayGroup())
    }

    private fun task(group: TaskDayGroup) = TaskProjection("action", Instant.parse("2026-10-10T09:00:00Z"), group, false)

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
