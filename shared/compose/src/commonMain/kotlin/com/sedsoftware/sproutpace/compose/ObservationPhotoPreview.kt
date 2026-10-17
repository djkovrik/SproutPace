package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sedsoftware.sproutpace.component.REMOVE_PHOTO_VALUE

internal fun SproutPaceUiState.observationPreviewPhoto(): UiPhoto? {
    val selected = fields.firstOrNull { it.id == "photoId" }?.value.orEmpty()
    if (screen != UiScreen.OBSERVATION_EDITOR || selected.isEmpty() || selected == REMOVE_PHOTO_VALUE) return null
    return items.firstOrNull { it.photo?.id == selected }?.photo
}

@Composable
internal fun ObservationPhotoPreview(state: SproutPaceUiState) {
    val selected = state.fields.firstOrNull { it.id == "photoId" }?.value.orEmpty()
    if (selected.isEmpty() || selected == REMOVE_PHOTO_VALUE) return
    val photo = state.observationPreviewPhoto()
    Column(Modifier.fillMaxWidth()) {
        if (photo?.bytes != null) {
            AsyncImage(model = photo.bytes, contentDescription = localizedText(photo.contentDescriptionKey),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp, max = 320.dp)
                    .semantics { testTag = "observation-photo-preview" })
        } else Text(localizedText("observation_photo_unavailable"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
