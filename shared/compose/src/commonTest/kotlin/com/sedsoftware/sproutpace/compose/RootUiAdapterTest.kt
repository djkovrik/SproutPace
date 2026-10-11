package com.sedsoftware.sproutpace.compose

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.sedsoftware.sproutpace.component.LayoutMode
import com.sedsoftware.sproutpace.component.BatchIdentity
import com.sedsoftware.sproutpace.domain.model.BatchState
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.ScreenAction
import com.sedsoftware.sproutpace.component.ScreenActionDescriptor
import com.sedsoftware.sproutpace.component.ScreenActionId
import com.sedsoftware.sproutpace.component.ScreenComponent
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenFact
import com.sedsoftware.sproutpace.component.ScreenField
import com.sedsoftware.sproutpace.component.ScreenFieldId
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenItemCommand
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.component.ScreenOption
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.component.REMOVE_PHOTO_VALUE
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.root.AppRootComponent
import com.sedsoftware.sproutpace.root.ConfirmationComponent
import com.sedsoftware.sproutpace.root.DeepLink
import com.sedsoftware.sproutpace.root.NavigationChrome
import com.sedsoftware.sproutpace.root.NavigationPresentation
import com.sedsoftware.sproutpace.root.PrimaryDestination
import com.sedsoftware.sproutpace.data.AdvertisingPrivacyState
import com.sedsoftware.sproutpace.data.ConsentChoice
import com.sedsoftware.sproutpace.monetization.AdSurface
import com.sedsoftware.sproutpace.monetization.AdPlacementRequest
import com.sedsoftware.sproutpace.monetization.AdPreparation
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import com.sedsoftware.sproutpace.monetization.DisabledReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RootUiAdapterTest {
    @Test fun acceptingSuggestionNavigatesWithExactIdentityAndCancelDoesNotSubmit() {
        val component = RecordingScreen(ScreenModel(ScreenId.PLAN, LoadState.CONTENT, TextKey("screen_plan_title")))
        val root = RecordingRoot(component)
        val router = RootActionRouter(root, component) { "unrelated-plan" }
        router.dispatch(UiAction.Submit("confirm-suggestion", "rotation-b:2"))
        assertEquals(ScreenId.BATCH_EDITOR to "rotation-b:2", root.opened)
        assertTrue(component.actions.isEmpty())
        router.dispatch(UiAction.Back)
        assertEquals(1, root.backCount)
        assertTrue(component.actions.isEmpty())
    }

    @Test fun emptySnapshotPrimaryActionIsTheOnlyVisibleFilledCta() {
        val ui = ScreenModel(
            screen = ScreenId.TODAY,
            loadState = LoadState.EMPTY,
            titleKey = TextKey("screen_today_title"),
            primaryActionKey = TextKey("action_new_batch"),
            actions = listOf(
                ScreenActionDescriptor(ScreenActionId("snooze"), TextKey("action_snooze"), "snooze"),
                ScreenActionDescriptor(
                    ScreenActionId("time-zone-change"),
                    TextKey("action_update_time_zone"),
                    "time-zone-change",
                ),
            ),
        ).toUiState()

        assertEquals(UiVariant.EMPTY, ui.variant)
        assertEquals(listOf("new-batch"), ui.actions.map(UiScreenAction::id))
        assertEquals("action_new_batch", ui.actions.single().labelKey)
        assertTrue(ui.actions.single().primary)
        assertEquals(null, ui.actions.single().operation)
    }

    @Test fun editorCreationPrimaryActionsUseExplicitExecutableIds() {
        fun mapped(screen: ScreenId, key: String) = ScreenModel(
            screen = screen,
            loadState = LoadState.EMPTY,
            titleKey = TextKey("screen_${screen.name.lowercase()}_title"),
            primaryActionKey = TextKey(key),
        ).toUiState().actions.single()

        val zone = mapped(ScreenId.ZONES, "action_add_zone")
        assertEquals("save-zone", zone.id)
        assertEquals("save-zone", zone.operation)
        assertTrue(zone.primary)

        val preset = mapped(ScreenId.TRAY_SIZES, "action_add_preset")
        assertEquals("save-tray-size", preset.id)
        assertEquals("save-tray-size", preset.operation)
        assertTrue(preset.primary)
    }

    @Test fun unsupportedSnapshotPrimaryActionFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            ScreenModel(
                screen = ScreenId.TODAY,
                loadState = LoadState.EMPTY,
                titleKey = TextKey("screen_today_title"),
                primaryActionKey = TextKey("action_not_registered"),
            ).toUiState()
        }
    }

    @Test fun restoreFactsExposeTheDestructiveRestorePreviewVariant() {
        val ui = ScreenModel(
            screen = ScreenId.BACKUP_RESTORE,
            loadState = LoadState.CONTENT,
            titleKey = TextKey("screen_backup_restore_title"),
            facts = listOf(
                ScreenFact("restore-created-at", TextKey("field_date"), authoredValue = "2026-09-27T10:00:00Z"),
            ),
        ).toUiState()

        assertEquals(UiVariant.RESTORE_PREVIEW, ui.variant)
    }

    @Test fun eligibleVisitOpensConsentBeforeReturningAnyAdIdentifier() = runTest {
        val runtime = RecordingAdvertisingRuntime(AdPreparation.ConsentRequired("policy-7"))
        var consentNavigationCount = 0

        val id = prepareAdvertisingVisit(
            runtime,
            AdPlacementRequest(AdSurface.CATALOG, 5, offline = false, slotIndex = 4),
        ) { consentNavigationCount += 1 }

        assertEquals(null, id)
        assertEquals(1, runtime.prepareCount)
        assertEquals(1, consentNavigationCount)
    }

    @Test fun eligibleVisitGatePermitsAtMostOnePreparation() {
        val gate = AdvertisingVisitGate()
        val initial = AdPlacementRequest(AdSurface.CATALOG, 5, offline = false, slotIndex = 4)

        assertEquals(null, gate.claim(null))
        assertEquals(initial, gate.claim(initial))
        assertEquals(null, gate.claim(initial.copy(realItemCount = 8)))
    }

    @Test fun privacyAcceptDeclineAndWithdrawalUseRuntimeBoundary() = runTest {
        val runtime = RecordingAdvertisingRuntime(AdPreparation.Disabled(DisabledReason.HOST_DISABLED))
        var completed = 0

        assertTrue(handleAdvertisingAction(UiAction.AcceptAdvertisingConsent, runtime) { completed += 1 })
        assertTrue(handleAdvertisingAction(UiAction.DeclineAdvertisingConsent, runtime) { completed += 1 })
        assertTrue(handleAdvertisingAction(UiAction.WithdrawAdvertisingConsent, runtime) { completed += 1 })

        assertEquals(listOf("accept", "decline", "withdraw"), runtime.actions)
        assertEquals(2, completed)
    }

    @Test fun advertisingEligibilityUsesOnlyApprovedTypedSurfacesAndExactIndices() {
        fun items(count: Int) = (1..count).map { index ->
            UiItem("item-$index", null, "Item $index", null, null, null, false)
        }

        assertEquals(null, SproutPaceUiState(screen = UiScreen.CATALOG, offline = false, items = items(4)).adPlacementRequestOrNull())
        val catalog = SproutPaceUiState(screen = UiScreen.CATALOG, offline = false, items = items(5)).adPlacementRequestOrNull()
        assertEquals(AdSurface.CATALOG, catalog?.surface)
        assertEquals(4, catalog?.slotIndex)

        assertEquals(
            null,
            SproutPaceUiState(screen = UiScreen.TRAYS, offline = false, trayCategory = TrayCategory.ACTIVE, items = items(6))
                .adPlacementRequestOrNull(),
        )
        assertEquals(
            null,
            SproutPaceUiState(screen = UiScreen.TRAYS, offline = false, trayCategory = TrayCategory.HISTORY, items = items(5))
                .adPlacementRequestOrNull(),
        )
        val history = SproutPaceUiState(
            screen = UiScreen.TRAYS,
            offline = false,
            trayCategory = TrayCategory.HISTORY,
            items = items(6),
        ).adPlacementRequestOrNull()
        assertEquals(AdSurface.HISTORY, history?.surface)
        assertEquals(5, history?.slotIndex)
        assertEquals(4, SproutPaceUiState(screen = UiScreen.CATALOG, offline = false, items = items(5)).inlineAdInsertionIndexOrNull())
        assertEquals(
            5,
            SproutPaceUiState(
                screen = UiScreen.TRAYS,
                offline = false,
                trayCategory = TrayCategory.HISTORY,
                items = items(6),
            ).inlineAdInsertionIndexOrNull(),
        )
        assertEquals(null, SproutPaceUiState(screen = UiScreen.CATALOG, offline = true, items = items(8)).adPlacementRequestOrNull())
        assertEquals(
            null,
            SproutPaceUiState(
                screen = UiScreen.TRAYS,
                offline = true,
                trayCategory = TrayCategory.HISTORY,
                items = items(8),
            ).adPlacementRequestOrNull(),
        )
        assertEquals(
            null,
            SproutPaceUiState(screen = UiScreen.TODAY, offline = false, items = items(20))
                .adPlacementRequestOrNull(),
        )
    }

    @Test fun privacyDecisionActionsAreEqualAndAcceptedStateOffersWithdrawal() {
        fun privacy(choice: ConsentChoice) = AdvertisingPrivacyState(
            policyVersion = "policy-7",
            expiresAtEpochMillis = 2_000,
            choice = choice,
            fetchedAtEpochMillis = 1_000,
        )
        val base = SproutPaceUiState(
            screen = UiScreen.ADVERTISING_PRIVACY,
            fields = listOf(
                UiField(
                    id = "policyVersion",
                    labelKey = "field_policy_version",
                    kind = ScreenFieldKind.TEXT,
                    value = "policy-7",
                    required = true,
                    readOnly = true,
                    options = emptyList(),
                    errorKey = null,
                ),
            ),
        )

        val undecided = base.withAdvertisingPrivacy(privacy(ConsentChoice.UNDECIDED), nowEpochMillis = 1_500)
        assertEquals(
            listOf(UiAction.AcceptAdvertisingConsent, UiAction.DeclineAdvertisingConsent),
            undecided.actions.take(2).mapNotNull(UiScreenAction::toAdvertisingUiAction),
        )
        assertTrue(undecided.actions.take(2).none { it.primary })
        assertEquals("open-privacy-policy", undecided.actions.last().id)
        assertEquals(listOf("policyVersion"), undecided.fields.map(UiField::id))

        val accepted = base.withAdvertisingPrivacy(privacy(ConsentChoice.ACCEPTED), nowEpochMillis = 1_500)
        assertEquals(UiAction.WithdrawAdvertisingConsent, accepted.actions.first().toAdvertisingUiAction())
        assertEquals("open-privacy-policy", accepted.actions.last().id)
    }

    @Test fun executableMappingCoversAllSixteenScreenIdsAndRealModelFields() {
        ScreenId.entries.forEach { screen ->
            val model = ScreenModel(
                screen = screen,
                loadState = LoadState.CONTENT,
                titleKey = TextKey("screen_today_title"),
                items = listOf(
                    ScreenItem(
                        "item-1",
                        authoredTitle = "User batch",
                        stateKey = if (screen == ScreenId.TRAYS) TextKey("option_batch_state_harvested") else null,
                        batch = BatchIdentity("item-1", "Tray 1", null, null, BatchState.HARVESTED),
                        actionId = "open-batch",
                        commands = listOf(ScreenItemCommand("complete-action", "scheduled-1")),
                    ),
                ),
                selectedItemId = "item-1",
                offline = false,
                busy = false,
                showInlineAdSlot = true,
                fields = listOfNotNull(
                    ScreenField(
                        ScreenFieldId("theme"), TextKey("field_theme"), ScreenFieldKind.SINGLE_CHOICE,
                        value = "dark", required = true,
                        options = listOf(ScreenOption("dark", TextKey("option_theme_dark"))),
                    ),
                    ScreenField(
                        ScreenFieldId("category"), TextKey("field_tray_category"), ScreenFieldKind.SINGLE_CHOICE,
                        value = "history", required = true,
                    ).takeIf { screen == ScreenId.TRAYS },
                ),
                facts = listOf(ScreenFact("fact-1", TextKey("summary_title"), authoredValue = "Verbatim value")),
                actions = listOf(
                    ScreenActionDescriptor(ScreenActionId("complete-action"), TextKey("action_complete_action"), "complete-action"),
                ),
            )
            val ui = model.toUiState()
            assertEquals(screen.name, ui.screen.name)
            assertEquals("item-1", ui.items.single().id)
            assertEquals("User batch", ui.items.single().authoredTitle)
            assertTrue(ui.items.single().selected)
            assertEquals(UiItemCommand("complete-action", "scheduled-1"), ui.items.single().commands.single())
            assertEquals("theme", ui.fields.single().id)
            assertEquals("dark", ui.fields.single().value)
            assertEquals("option_theme_dark", ui.fields.single().options.single().labelKey)
            if (screen == ScreenId.TRAYS) assertEquals(TrayCategory.HISTORY, ui.trayCategory)
            assertEquals("Verbatim value", ui.facts.single().authoredValue)
            assertEquals("complete-action", ui.actions.single().operation)
            assertEquals(false, ui.offline)
            assertEquals(true, ui.showInlineAd)
        }
        assertEquals(16, ScreenId.entries.size)
    }

    @Test fun traysCategoryIsTypedAndFiltersOnlyTheSelectedPeerGroup() {
        fun model(category: String) = ScreenModel(
            screen = ScreenId.TRAYS,
            loadState = LoadState.CONTENT,
            titleKey = TextKey("screen_trays_title"),
            items = listOf(
                ScreenItem("planned", authoredTitle = "Planned", batch = BatchIdentity("planned", "Tray", null, null, BatchState.PLANNED)),
                ScreenItem("active", authoredTitle = "Active", batch = BatchIdentity("active", "Tray", null, null, BatchState.SOWN)),
                ScreenItem("history", authoredTitle = "History", stateKey = TextKey("outcome_harvested"), batch = BatchIdentity("history", "Tray", null, null, BatchState.HARVESTED)),
            ),
            fields = listOf(
                ScreenField(
                    ScreenFieldId("category"), TextKey("field_tray_category"), ScreenFieldKind.SINGLE_CHOICE,
                    value = category, required = true,
                ),
            ),
        )

        assertEquals(listOf("planned"), model("planned").toUiState().items.map(UiItem::id))
        assertEquals(listOf("active"), model("active").toUiState().items.map(UiItem::id))
        val history = model("history").toUiState()
        assertEquals(TrayCategory.HISTORY, history.trayCategory)
        assertEquals(listOf("history"), history.items.map(UiItem::id))
        assertEquals("outcome_harvested", history.items.single().stateKey)
        assertTrue(history.fields.none { it.id == "category" })
    }

    @Test fun settingsChildKeepsParentContextAndRoutesSiblingThroughRoot() {
        val parent = ScreenModel(screen = ScreenId.SETTINGS, loadState = LoadState.CONTENT,
            titleKey = TextKey("screen_settings_title"),
            items = listOf(ScreenItem("privacy", titleKey = TextKey("screen_advertising_privacy_title"))))
        val child = SproutPaceUiState(screen = UiScreen.ZONES).withSettingsContext(parent)
        assertEquals(listOf("privacy"), child.settingsContext?.items?.map { it.id })
        assertNull(SproutPaceUiState(screen = UiScreen.BATCH_EDITOR).withSettingsContext(parent).settingsContext)
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        RootActionRouter(root, component) { null }.dispatch(UiAction.Navigate(UiScreen.NOTIFICATIONS))
        assertEquals(ScreenId.NOTIFICATIONS to null, root.opened)
        assertTrue(component.actions.isEmpty())
    }

    @Test fun primaryAndDetailNavigationRouteThroughRootWithoutLocalBackStack() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        val router = RootActionRouter(root, component) { "batch-7" }

        router.dispatch(UiAction.Navigate(UiScreen.CATALOG))
        assertEquals(PrimaryDestination.CATALOG, root.selected)
        router.dispatch(UiAction.Navigate(UiScreen.SETTINGS))
        assertEquals(ScreenId.SETTINGS, root.opened?.first)
        router.dispatch(UiAction.Submit("open-batch"))
        assertEquals(ScreenId.BATCH_DETAIL to "batch-7", root.opened)
        router.dispatch(UiAction.Submit("open-batch", "batch-from-item"))
        assertEquals(ScreenId.BATCH_DETAIL to "batch-from-item", root.opened)
        router.dispatch(UiAction.Back)
        assertEquals(1, root.backCount)
    }

    @Test fun repeatNavigatesToNewEditorWithoutSubmittingMutation() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        val router = RootActionRouter(root, component) { "selected-history" }
        router.dispatch(UiAction.Submit("repeat-batch", "historical-batch"))
        assertEquals(ScreenId.BATCH_EDITOR to "repeat-batch:historical-batch", root.opened)
        assertTrue(component.actions.isEmpty())
    }

    @Test fun todayStageItemCommandsOpenLifecycleScreensWithoutSubmittingStatusMutations() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        val router = RootActionRouter(root, component) { "unrelated-selection" }
        router.dispatch(UiAction.SubmitItem("open-batch", "stage-task-batch"))
        assertEquals(ScreenId.BATCH_DETAIL to "stage-task-batch", root.opened)
        router.dispatch(UiAction.SubmitItem("close-batch", "harvest-task-batch"))
        assertEquals(ScreenId.CLOSE_BATCH to "harvest-task-batch", root.opened)
        router.dispatch(UiAction.SubmitItem("repeat-batch", "terminal-batch"))
        assertEquals(ScreenId.BATCH_EDITOR to "repeat-batch:terminal-batch", root.opened)
        assertTrue(component.actions.isEmpty())
    }

    @Test fun retrySelectionEditingChoiceLayoutAndSubmitRouteTypedActionsToActiveComponent() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        val router = RootActionRouter(root, component) { "selected-2" }

        router.dispatch(UiAction.Retry)
        router.dispatch(UiAction.Select("selected-2"))
        router.dispatch(UiAction.EditField("theme", "dark"))
        router.dispatch(UiAction.ChooseOption("theme", "dark"))
        router.dispatch(UiAction.SelectTrayCategory(TrayCategory.HISTORY))
        router.dispatch(UiAction.SetLayout(UiLayout.EXPANDED))
        router.dispatch(UiAction.Submit("complete-action"))

        assertIs<ScreenAction.Retry>(component.actions[0])
        assertEquals(ScreenAction.Select("selected-2"), component.actions[1])
        assertEquals(ScreenAction.EditField(ScreenFieldId("theme"), "dark"), component.actions[2])
        assertEquals(ScreenAction.ChooseOption(ScreenFieldId("theme"), "dark"), component.actions[3])
        assertEquals(ScreenAction.ChooseOption(ScreenFieldId("category"), "history"), component.actions[4])
        assertEquals(ScreenAction.SetLayout(LayoutMode.EXPANDED), component.actions[5])
        assertEquals(ScreenAction.Submit(ScreenActionId("complete-action")), component.actions[6])
    }

    @Test fun itemCommandRoutesValidatedOperationWithExplicitSubject() {
        val today = RecordingScreen(
            ScreenModel(
                screen = ScreenId.TODAY,
                loadState = LoadState.CONTENT,
                titleKey = TextKey("screen_today_title"),
                items = listOf(
                    ScreenItem(
                        "next:scheduled-1",
                        authoredTitle = "Sow",
                        commands = listOf(ScreenItemCommand("complete-action", "scheduled-1")),
                    ),
                ),
            ),
        )
        val todayRouter = RootActionRouter(RecordingRoot(today), today) { null }

        todayRouter.dispatch(UiAction.SubmitItem("complete-action", "scheduled-1"))

        val completedCommand = assertIs<ScreenAction.Execute>(today.actions.single()).command
        assertNotNull(completedCommand.invocationId)
        assertEquals(ScreenCommand("complete-action", "scheduled-1", invocationId = completedCommand.invocationId), completedCommand)

        val detail = RecordingScreen(
            ScreenModel(
                screen = ScreenId.BATCH_DETAIL,
                loadState = LoadState.CONTENT,
                titleKey = TextKey("screen_batch_detail_title"),
                items = listOf(
                    ScreenItem(
                        "action:scheduled-2",
                        authoredTitle = "Harvest",
                        commands = listOf(ScreenItemCommand("skip-action", "scheduled-2")),
                    ),
                ),
            ),
        )
        val detailRouter = RootActionRouter(RecordingRoot(detail), detail) { null }

        detailRouter.dispatch(UiAction.SubmitItem("skip-action", "scheduled-2"))

        val skippedCommand = assertIs<ScreenAction.Execute>(detail.actions.single()).command
        assertNotNull(skippedCommand.invocationId)
        assertEquals(ScreenCommand("skip-action", "scheduled-2", invocationId = skippedCommand.invocationId), skippedCommand)
    }

    @Test fun platformActionsUseTypedSeamAndFeedDocumentResultBackIntoDraft() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        val requests = mutableListOf<PlatformActionRequest>()
        val router = RootActionRouter(
            root,
            component,
            PlatformActionHandler { request, result -> requests += request; result("document-42") },
        ) { null }

        router.dispatch(UiAction.RequestPlatform(PlatformActionRequest(PlatformActionKind.CHOOSE_DOCUMENT, "documentId")))

        assertEquals(PlatformActionRequest(PlatformActionKind.CHOOSE_DOCUMENT, "documentId"),
            requests.single().copy(onFailure = null))
        assertEquals(ScreenAction.EditField(ScreenFieldId("documentId"), "document-42"), component.actions.single())
    }

    @Test fun replacingPendingPhotoDiscardsOldTokenBeforeEditingDraft() {
        val oldDraft = pendingPhotoDraftValue("old.jpg")
        val newDraft = pendingPhotoDraftValue("new.jpg")
        val component = RecordingScreen(observationModel(oldDraft))
        val requests = mutableListOf<PlatformActionRequest>()
        lateinit var complete: (String) -> Unit
        val router = RootActionRouter(
            RecordingRoot(component),
            component,
            PlatformActionHandler { request, result -> requests += request; complete = result },
        ) { "observation-1" }

        router.dispatch(UiAction.RequestPlatform(PlatformActionRequest(PlatformActionKind.CHOOSE_PHOTO, "photoId")))
        complete(newDraft)

        assertEquals(
            listOf(
                PlatformActionRequest(PlatformActionKind.CHOOSE_PHOTO, "photoId"),
                PlatformActionRequest(PlatformActionKind.DISCARD_PENDING_PHOTO, opaqueValue = oldDraft),
            ),
            requests.map { it.copy(onFailure = null) },
        )
        assertEquals(ScreenAction.EditField(ScreenFieldId("photoId"), newDraft), component.actions.single())
    }

    @Test fun failedPhotoReplacementReportsErrorWithoutEditingOrDiscardingCurrentPhoto() {
        val component = RecordingScreen(observationModel(pendingPhotoDraftValue("keep.jpg")))
        val requests = mutableListOf<PlatformActionRequest>()
        val router = RootActionRouter(RecordingRoot(component), component,
            PlatformActionHandler { request, _ ->
                requests += request
                request.onFailure?.invoke(PlatformActionFailure.PHOTO_IMPORT)
            }) { null }
        router.dispatch(UiAction.RequestPlatform(PlatformActionRequest(PlatformActionKind.CHOOSE_PHOTO, "photoId")))
        assertEquals(listOf<ScreenAction>(ScreenAction.ReportFailure(TextKey("error_photo_import_failed"))), component.actions)
        assertEquals(listOf(PlatformActionKind.CHOOSE_PHOTO), requests.map { it.kind })
    }

    @Test fun failedBackupPickOrDestinationReportsErrorWithoutSubmitting() {
        listOf(
            Triple("preview-restore", PlatformActionFailure.DOCUMENT_IMPORT, "error_document_read_failed"),
            Triple("create-backup", PlatformActionFailure.DOCUMENT_DESTINATION, "error_document_write_failed"),
        ).forEach { (operation, failure, key) ->
            val component = RecordingScreen()
            val router = RootActionRouter(RecordingRoot(component), component,
                PlatformActionHandler { request, _ -> request.onFailure?.invoke(failure) }) { null }
            router.dispatch(UiAction.Submit(operation))
            assertEquals(listOf<ScreenAction>(ScreenAction.ReportFailure(TextKey(key))), component.actions)
        }
    }

    @Test fun photoCancellationLeavesDraftUntouchedAndRemoveDiscardsPendingToken() {
        val draft = pendingPhotoDraftValue("picked.png")
        val component = RecordingScreen(observationModel(draft))
        val requests = mutableListOf<PlatformActionRequest>()
        val router = RootActionRouter(
            RecordingRoot(component),
            component,
            PlatformActionHandler { request, _ -> requests += request },
        ) { "observation-1" }

        router.dispatch(UiAction.RequestPlatform(PlatformActionRequest(PlatformActionKind.CHOOSE_PHOTO, "photoId")))
        assertTrue(component.actions.isEmpty())
        router.dispatch(
            UiAction.ClearPickerSelection("photoId", draft, PlatformActionKind.CHOOSE_PHOTO),
        )

        assertEquals(
            PlatformActionRequest(PlatformActionKind.DISCARD_PENDING_PHOTO, opaqueValue = draft),
            requests.last(),
        )
        assertEquals(ScreenAction.EditField(ScreenFieldId("photoId"), REMOVE_PHOTO_VALUE), component.actions.single())
    }

    @Test fun clearingDocumentSelectionUsesBlankValueWithoutPhotoCleanup() {
        val component = RecordingScreen()
        val requests = mutableListOf<PlatformActionRequest>()
        val router = RootActionRouter(
            RecordingRoot(component),
            component,
            PlatformActionHandler { request, _ -> requests += request },
        ) { null }

        router.dispatch(
            UiAction.ClearPickerSelection(
                fieldId = "documentId",
                currentValue = "content://downloads/backup",
                kind = PlatformActionKind.CHOOSE_DOCUMENT,
            ),
        )

        assertTrue(requests.isEmpty())
        assertEquals(ScreenAction.EditField(ScreenFieldId("documentId"), ""), component.actions.single())
    }

    @Test fun cancellingObservationDraftDiscardsOnlyPendingPhoto() {
        val draft = pendingPhotoDraftValue("picked.png")
        val component = RecordingScreen(observationModel(draft))
        val requests = mutableListOf<PlatformActionRequest>()
        val router = RootActionRouter(
            RecordingRoot(component),
            component,
            PlatformActionHandler { request, _ -> requests += request },
        ) { "observation-1" }

        router.dispatch(UiAction.CancelDraft)

        assertEquals(
            PlatformActionRequest(PlatformActionKind.DISCARD_PENDING_PHOTO, opaqueValue = draft),
            requests.single(),
        )
        assertEquals(ScreenAction.CancelDraft, component.actions.single())
    }

    @Test fun createBackupChoosesDestinationThenEditsAndSubmitsExactlyOnce() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        lateinit var complete: (String) -> Unit
        val requests = mutableListOf<PlatformActionRequest>()
        val router = RootActionRouter(
            root,
            component,
            PlatformActionHandler { request, result -> requests += request; complete = result },
        ) { null }

        router.dispatch(UiAction.Submit("create-backup"))
        assertEquals(
            PlatformActionRequest(
                PlatformActionKind.CREATE_DOCUMENT,
                fieldId = "documentId",
                suggestedName = "sproutpace-backup.zip",
                mediaType = "application/zip",
            ),
            requests.single().copy(onFailure = null),
        )
        assertTrue(component.actions.isEmpty())

        complete("content://documents/backup")
        complete("content://documents/duplicate")

        assertEquals(
            listOf(
                ScreenAction.EditField(ScreenFieldId("documentId"), "content://documents/backup"),
                ScreenAction.Submit(ScreenActionId("create-backup")),
            ),
            component.actions,
        )
    }

    @Test fun exportCsvUsesStableDestinationContractAndCancellationDoesNothing() {
        val component = RecordingScreen()
        val root = RecordingRoot(component)
        val requests = mutableListOf<PlatformActionRequest>()
        lateinit var complete: (String) -> Unit
        val router = RootActionRouter(
            root,
            component,
            PlatformActionHandler { request, result -> requests += request; complete = result },
        ) { null }

        router.dispatch(UiAction.Submit("export-csv"))

        assertEquals(
            PlatformActionRequest(
                PlatformActionKind.CREATE_DOCUMENT,
                fieldId = "documentId",
                suggestedName = "sproutpace-history.csv",
                mediaType = "text/csv",
            ),
            requests.single().copy(onFailure = null),
        )
        complete("")
        assertTrue(component.actions.isEmpty())
    }

    @Test fun primarySelectionFallsBackToNearestBackStackDestination() {
        assertEquals(UiScreen.TRAYS, selectedPrimaryScreen(ScreenId.BATCH_DETAIL, listOf(ScreenId.TODAY, ScreenId.TRAYS)))
        assertEquals(UiScreen.TODAY, selectedPrimaryScreen(ScreenId.SETTINGS, emptyList()))
    }
}

private fun observationModel(photoValue: String) = ScreenModel(
    screen = ScreenId.OBSERVATION_EDITOR,
    loadState = LoadState.CONTENT,
    titleKey = TextKey("screen_observation_editor_title"),
    fields = listOf(
        ScreenField(
            id = ScreenFieldId("photoId"),
            labelKey = TextKey("field_observation_photo"),
            kind = ScreenFieldKind.TEXT,
            value = photoValue,
            required = false,
        ),
    ),
)

private class RecordingAdvertisingRuntime(
    private val preparation: AdPreparation,
) : AdvertisingRuntime {
    override val privacyState = MutableStateFlow<AdvertisingPrivacyState?>(null)
    override val networkAvailable = MutableStateFlow(true)
    var prepareCount = 0
    val actions = mutableListOf<String>()
    override suspend fun prepare(request: AdPlacementRequest): AdPreparation {
        prepareCount += 1
        return preparation
    }
    override suspend fun acceptConsent() = true.also { actions += "accept" }
    override suspend fun declineConsent() = true.also { actions += "decline" }
    override suspend fun withdrawConsent() = true.also { actions += "withdraw" }
    override fun close() = Unit
}

private class RecordingScreen(
    initial: ScreenModel = ScreenModel(ScreenId.TODAY, LoadState.CONTENT, TextKey("screen_today_title")),
) : ScreenComponent {
    override val model: Value<ScreenModel> = MutableValue(initial)
    val actions = mutableListOf<ScreenAction>()
    override fun onAction(action: ScreenAction) { actions += action }
}

private class RecordingRoot(component: ScreenComponent) : AppRootComponent {
    override val stack: Value<ChildStack<AppRootComponent.Config, ScreenComponent>> = MutableValue(
        ChildStack(AppRootComponent.Config(ScreenId.TODAY), component),
    )
    override val confirmation: Value<ChildSlot<AppRootComponent.ConfirmationConfig, ConfirmationComponent>> = MutableValue(ChildSlot())
    var selected: PrimaryDestination? = null
    var opened: Pair<ScreenId, String?>? = null
    var backCount = 0
    override fun select(destination: PrimaryDestination) { selected = destination }
    override fun open(screen: ScreenId, stableId: String?) { opened = screen to stableId }
    override fun openDeepLink(link: DeepLink) = false
    override fun back() { backCount += 1 }
    override fun dismissConfirmation() = Unit
    override fun presentation(layoutMode: LayoutMode) = NavigationPresentation(NavigationChrome.BOTTOM_BAR, 1, true)
}
