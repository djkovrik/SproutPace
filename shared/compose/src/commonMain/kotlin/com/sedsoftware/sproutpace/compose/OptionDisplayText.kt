package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable

/** Resolve system text and dates at presentation time, preserving saved user names. */
@Composable
internal fun optionDisplayText(option: UiOption): String {
    val label = localizedOrAuthored(option.labelKey, option.authoredLabel)
    val identity = option.batchIdentity ?: return label
    return listOfNotNull(label, identity.trayName, identity.sowDate?.let { localizedDate(it) }, identity.zoneName)
        .joinToString(" · ")
}
