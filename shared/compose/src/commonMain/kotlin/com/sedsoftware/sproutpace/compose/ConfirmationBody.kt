package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.ScreenCommand

internal fun confirmationSubject(command: ScreenCommand, source: SproutPaceUiState?): UiItem? {
    val subjectId = command.subjectId ?: return null
    val item = source?.items?.firstOrNull { it.id == subjectId }
    if (item != null) return item
    val authoredName = source?.fields?.firstOrNull { it.id == "name" }?.value?.takeIf { it.isNotBlank() }
    return authoredName?.let { UiItem(subjectId, null, it, null, null, null, selected = false) }
}

@Composable
internal fun ConfirmationBody(command: ScreenCommand, source: SproutPaceUiState?, bodyKey: String) {
    val subject = confirmationSubject(command, source)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        subject?.let {
            Text(localizedOrAuthored(it.titleKey, it.authoredTitle), style = MaterialTheme.typography.titleMedium)
            if (it.batch != null) ProjectionContent(it)
        }
        Text(localizedText(bodyKey))
    }
}
