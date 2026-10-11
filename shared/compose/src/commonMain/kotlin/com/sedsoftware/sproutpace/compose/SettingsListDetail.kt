package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.ScreenModel

internal val settingsDetailScreens = setOf(
    UiScreen.ZONES, UiScreen.TRAY_SIZES, UiScreen.NOTIFICATIONS,
    UiScreen.BACKUP_RESTORE, UiScreen.ADVERTISING_PRIVACY,
)

internal fun SproutPaceUiState.withSettingsContext(settings: ScreenModel?): SproutPaceUiState =
    copy(settingsContext = settings?.takeIf { screen in settingsDetailScreens }?.toUiState())

@Composable
internal fun SettingsListDetail(
    state: SproutPaceUiState,
    onAction: (UiAction) -> Unit,
    detail: @Composable () -> Unit,
) {
    val settings = state.settingsContext ?: state
    val destinations = listOf(
        UiScreen.SETTINGS to "screen_settings_title",
        UiScreen.ZONES to "screen_zones_title",
        UiScreen.TRAY_SIZES to "screen_tray_sizes_title",
        UiScreen.NOTIFICATIONS to "screen_notifications_title",
        UiScreen.BACKUP_RESTORE to "screen_backup_restore_title",
    ) + if (settings.items.any { it.id == "privacy" }) {
        listOf(UiScreen.ADVERTISING_PRIVACY to "screen_advertising_privacy_title")
    } else emptyList()
    Row(Modifier.fillMaxSize().semantics { testTag = "settings-list-detail" }) {
        LazyColumn(
            modifier = Modifier.width(SETTINGS_PANE_WIDTH).fillMaxHeight().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(destinations, key = { it.first.name }) { (screen, key) ->
                NavigationDrawerItem(
                    label = { Text(localizedText(key), style = MaterialTheme.typography.titleSmall) },
                    selected = state.screen == screen,
                    onClick = { if (state.screen != screen) onAction(UiAction.Navigate(screen)) },
                )
            }
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
    }
}

private val SETTINGS_PANE_WIDTH = 240.dp
