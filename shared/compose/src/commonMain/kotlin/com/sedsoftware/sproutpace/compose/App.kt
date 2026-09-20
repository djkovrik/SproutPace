package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.PermissionState
import com.sedsoftware.sproutpace.component.ScreenFieldKind
import com.sedsoftware.sproutpace.root.AppRootComponent
import com.sedsoftware.sproutpace.data.ThemePreference
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sproutpace.shared.compose.generated.resources.*

enum class UiScreen {
    FIRST_RUN, TODAY, TRAYS, BATCH_EDITOR, BATCH_DETAIL, PLAN, CATALOG, RECIPE,
    OBSERVATION_EDITOR, CLOSE_BATCH, SETTINGS, ZONES, TRAY_SIZES, BACKUP_RESTORE,
    ADVERTISING_PRIVACY, NOTIFICATIONS,
}

enum class UiVariant {
    CONTENT, EMPTY, VALIDATION_ERROR, ERROR, PERMISSION_DENIED, CONFLICT,
    TEMPORARILY_UNASSIGNED, PROGRESS, RESTORE_PREVIEW, DESTRUCTIVE_CONFIRMATION,
    PRIVACY_DECISION,
}

enum class UiLayout { COMPACT, MEDIUM, EXPANDED, LARGE, EXTRA_LARGE }

enum class TrayCategory(val id: String, val labelKey: String) {
    PLANNED("planned", "trays_planned"),
    ACTIVE("active", "trays_active"),
    HISTORY("history", "trays_history"),
}

data class UiItem(
    val id: String,
    val titleKey: String?,
    val authoredTitle: String?,
    val supportingKey: String?,
    val stateKey: String?,
    val actionId: String?,
    val selected: Boolean,
    val commands: List<UiItemCommand> = emptyList(),
    val photo: UiPhoto? = null,
)

class UiPhoto(val id: String, bytes: ByteArray?, val contentDescriptionKey: String) {
    val bytes: ByteArray? = bytes?.copyOf()
    override fun equals(other: Any?): Boolean = other is UiPhoto && id == other.id &&
        contentDescriptionKey == other.contentDescriptionKey &&
        (bytes?.contentEquals(other.bytes ?: return false) ?: (other.bytes == null))
    override fun hashCode(): Int = 31 * (31 * id.hashCode() + (bytes?.contentHashCode() ?: 0)) +
        contentDescriptionKey.hashCode()
}

data class UiItemCommand(val operation: String, val subjectId: String)

data class UiOption(val id: String, val labelKey: String?, val authoredLabel: String?)

data class UiField(
    val id: String,
    val labelKey: String,
    val kind: ScreenFieldKind,
    val value: String,
    val required: Boolean,
    val readOnly: Boolean,
    val options: List<UiOption>,
    val errorKey: String?,
)

data class UiFact(
    val id: String,
    val labelKey: String,
    val valueKey: String?,
    val authoredValue: String?,
)

data class UiScreenAction(
    val id: String,
    val labelKey: String,
    val operation: String?,
    val enabled: Boolean,
    val primary: Boolean,
)

data class SproutPaceUiState(
    val screen: UiScreen = UiScreen.TODAY,
    val variant: UiVariant = UiVariant.CONTENT,
    val selectedPrimary: UiScreen = UiScreen.TODAY,
    val busy: Boolean = false,
    val offline: Boolean = true,
    val permissionState: PermissionState = PermissionState.NOT_REQUIRED,
    val trayCategory: TrayCategory = TrayCategory.PLANNED,
    val showInlineAd: Boolean = false,
    val inlineAdUnitId: String? = null,
    val items: List<UiItem> = emptyList(),
    val fields: List<UiField> = emptyList(),
    val facts: List<UiFact> = emptyList(),
    val actions: List<UiScreenAction> = emptyList(),
    val conflictKeys: List<String> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val errorKey: String? = null,
)

enum class PlatformActionKind {
    CHOOSE_DOCUMENT,
    CREATE_DOCUMENT,
    CHOOSE_PHOTO,
    DISCARD_PENDING_PHOTO,
    REQUEST_NOTIFICATION_PERMISSION,
    OPEN_SYSTEM_SETTINGS,
    OPEN_PRIVACY_POLICY,
}

data class PlatformActionRequest(
    val kind: PlatformActionKind,
    val fieldId: String? = null,
    val suggestedName: String? = null,
    val mediaType: String? = null,
    val opaqueValue: String? = null,
)

fun interface PlatformActionHandler {
    fun perform(request: PlatformActionRequest, onResult: (String) -> Unit)
}

val NoOpPlatformActionHandler = PlatformActionHandler { _, _ -> }

sealed interface UiAction {
    data class Navigate(val screen: UiScreen) : UiAction
    data class Select(val id: String) : UiAction
    data class EditField(val fieldId: String, val value: String) : UiAction
    data class ChooseOption(val fieldId: String, val optionId: String) : UiAction
    data class SelectTrayCategory(val category: TrayCategory) : UiAction
    data class Submit(val actionId: String) : UiAction
    data class SubmitItem(val operation: String, val subjectId: String) : UiAction
    data class RequestPlatform(val request: PlatformActionRequest) : UiAction
    data class RemovePhoto(val fieldId: String, val currentValue: String) : UiAction
    data class SetLayout(val layout: UiLayout) : UiAction
    data object CancelDraft : UiAction
    data object Retry : UiAction
    data object AcceptAdvertisingConsent : UiAction
    data object DeclineAdvertisingConsent : UiAction
    data object WithdrawAdvertisingConsent : UiAction
    data object Back : UiAction
}

@Composable
fun App(
    root: AppRootComponent,
    platformActions: PlatformActionHandler = NoOpPlatformActionHandler,
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = RootApp(root = root, platformActions = platformActions, onThemeChanged = onThemeChanged)

internal interface ThemePreferenceOwner {
    val themePreference: StateFlow<ThemePreference>
}

internal interface AdvertisingRuntimeOwner {
    val advertisingRuntime: AdvertisingRuntime
}

@Composable
internal fun App(
    state: SproutPaceUiState = SproutPaceUiState(),
    onAction: (UiAction) -> Unit = {},
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = PreviewApp(state, onAction, onThemeChanged)

@Composable
internal fun PreviewApp(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit = {},
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = com.sedsoftware.sproutpace.compose.theme.AppTheme(onThemeChanged = onThemeChanged) {
    SproutPaceApp(state, onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SproutPaceApp(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
    advertisingRuntime: AdvertisingRuntime? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        var contextFieldsExpanded by remember(state.screen) { mutableStateOf(false) }
        val layout = when {
            maxWidth < 600.dp -> UiLayout.COMPACT
            maxWidth < 840.dp -> UiLayout.MEDIUM
            maxWidth < 1200.dp -> UiLayout.EXPANDED
            maxWidth < 1600.dp -> UiLayout.LARGE
            else -> UiLayout.EXTRA_LARGE
        }
        val compact = layout == UiLayout.COMPACT
        LaunchedEffect(layout) { onAction(UiAction.SetLayout(layout)) }
        val topLevel = state.screen in primaryDestinations
        val body: @Composable () -> Unit = {
            Scaffold(
                modifier = Modifier.semantics { testTag = "screen-${state.screen.name.lowercase()}" },
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(state.screen.titleResource())) },
                        navigationIcon = {
                            if (!topLevel && state.screen != UiScreen.FIRST_RUN) {
                                IconButton(onClick = { onAction(UiAction.Back) }) {
                                    Icon(vectorResource(Res.drawable.ic_arrow_back), stringResource(Res.string.action_back))
                                }
                            }
                        },
                        actions = {
                            if (state.screen == UiScreen.TODAY) {
                                IconButton(onClick = { onAction(UiAction.Navigate(UiScreen.SETTINGS)) }) {
                                    Icon(vectorResource(Res.drawable.ic_settings), stringResource(Res.string.action_settings))
                                }
                            }
                        },
                    )
                },
                bottomBar = { if (compact && topLevel) PrimaryBottomBar(state.selectedPrimary, onAction) },
            ) { padding ->
                ScreenContent(
                    state = state,
                    layout = layout,
                    padding = padding,
                    contextFieldsExpanded = contextFieldsExpanded || state.variant == UiVariant.VALIDATION_ERROR,
                    onContextFieldsExpandedChange = { contextFieldsExpanded = it },
                    onAction = onAction,
                    advertisingRuntime = advertisingRuntime,
                )
            }
        }
        if (!compact && topLevel) {
            Row(Modifier.fillMaxSize()) {
                PrimaryRail(state.selectedPrimary, onAction)
                Box(Modifier.weight(1f).fillMaxSize()) { body() }
            }
        } else {
            body()
        }
    }
}

private data class Destination(val screen: UiScreen, val label: StringResource, val icon: DrawableResource)

private val destinations = listOf(
    Destination(UiScreen.TODAY, Res.string.nav_today, Res.drawable.ic_nav_today),
    Destination(UiScreen.TRAYS, Res.string.nav_trays, Res.drawable.ic_nav_trays),
    Destination(UiScreen.PLAN, Res.string.nav_plan, Res.drawable.ic_nav_plan),
    Destination(UiScreen.CATALOG, Res.string.nav_catalog, Res.drawable.ic_nav_catalog),
)

private val primaryDestinations = destinations.map { it.screen }.toSet()

@Composable
private fun PrimaryBottomBar(selected: UiScreen, onAction: (UiAction) -> Unit) = NavigationBar(
    modifier = Modifier.semantics { testTag = "primary-navigation-bottom" },
) {
    destinations.forEach { destination ->
        NavigationBarItem(
            selected = destination.screen == selected,
            onClick = { onAction(UiAction.Navigate(destination.screen)) },
            icon = { Icon(vectorResource(destination.icon), null) },
            label = { Text(stringResource(destination.label)) },
        )
    }
}

@Composable
private fun PrimaryRail(selected: UiScreen, onAction: (UiAction) -> Unit) = NavigationRail(
    modifier = Modifier.semantics { testTag = "primary-navigation-rail" },
) {
    destinations.forEach { destination ->
        NavigationRailItem(
            selected = destination.screen == selected,
            onClick = { onAction(UiAction.Navigate(destination.screen)) },
            icon = { Icon(vectorResource(destination.icon), null) },
            label = { Text(stringResource(destination.label)) },
        )
    }
}

internal fun UiScreen.titleResource(): StringResource = strictTextResource("screen_${name.lowercase()}_title")
