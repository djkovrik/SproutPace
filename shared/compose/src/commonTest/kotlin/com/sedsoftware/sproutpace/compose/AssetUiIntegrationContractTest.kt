package com.sedsoftware.sproutpace.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import sproutpace.shared.compose.generated.resources.Res
import sproutpace.shared.compose.generated.resources.culture_radish
import sproutpace.shared.compose.generated.resources.ic_add
import sproutpace.shared.compose.generated.resources.ic_add_photo
import sproutpace.shared.compose.generated.resources.ic_backup
import sproutpace.shared.compose.generated.resources.ic_check_circle
import sproutpace.shared.compose.generated.resources.ic_delete
import sproutpace.shared.compose.generated.resources.ic_edit
import sproutpace.shared.compose.generated.resources.ic_notifications
import sproutpace.shared.compose.generated.resources.ic_repeat

class AssetUiIntegrationContractTest {
    @Test
    fun existingActionRolesUseTheApprovedIconFamily() {
        assertEquals(Res.drawable.ic_add, actionIcon("add-zone"))
        assertEquals(Res.drawable.ic_edit, actionIcon("update-recipe"))
        assertEquals(Res.drawable.ic_delete, actionIcon("delete-batch"))
        assertEquals(Res.drawable.ic_add_photo, actionIcon("choose-photo"))
        assertEquals(Res.drawable.ic_notifications, actionIcon("update-notifications"))
        assertEquals(Res.drawable.ic_backup, actionIcon("create-backup"))
        assertEquals(Res.drawable.ic_repeat, actionIcon("repeat-batch"))
        assertEquals(Res.drawable.ic_check_circle, actionIcon("complete-action"))
        assertNull(actionIcon("open-privacy-policy"))
    }

    @Test
    fun recipeFixtureExposesAllApprovedStageIllustrationsAndMatchingCulture() {
        val recipe = typedPreviewState(UiScreen.RECIPE)
        val stageKeys = recipe.items.mapNotNull(UiItem::titleKey).toSet()
        assertTrue(
            stageKeys.containsAll(
                setOf("stage_soaking", "stage_sown", "stage_blackout", "stage_light_growth", "stage_harvest"),
            ),
        )
        assertEquals("radish", recipe.fields.single { it.id == "cultureId" }.value)
        assertEquals(Res.drawable.culture_radish, cultureImage("radish"))
    }

    @Test
    fun emptyIllustrationIsLimitedToTheThreeApprovedEmptyScreens() {
        val illustrated = UiScreen.entries.filter { screen ->
            typedPreviewState(screen, UiVariant.EMPTY).showsEmptyIllustration()
        }.toSet()
        assertEquals(setOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN), illustrated)
        assertTrue(!typedPreviewState(UiScreen.TODAY).showsEmptyIllustration())
    }

    @Test
    fun illustratedEmptyScreensUseSpecificCopyAndOneNewBatchCta() {
        val expectedCopy = mapOf(
            UiScreen.TODAY to ("today_empty_title" to "today_empty_body"),
            UiScreen.TRAYS to ("trays_empty_title" to "trays_empty_body"),
            UiScreen.PLAN to ("plan_empty_title" to "plan_empty_body"),
        )

        expectedCopy.forEach { (screen, copy) ->
            val state = typedPreviewState(screen, UiVariant.EMPTY)
            assertEquals(copy, screen.emptyStateCopy())
            assertTrue(state.hidesContextForEmptyState())
            assertEquals(listOf("new-batch"), state.actions.map(UiScreenAction::id))
            assertTrue(state.actions.single().primary)
        }
        assertEquals(
            "state_empty_title" to "state_empty_body",
            UiScreen.CATALOG.emptyStateCopy(),
        )
    }
}
