package com.sedsoftware.sproutpace.compose

import androidx.compose.runtime.Composable
import com.sedsoftware.sproutpace.component.ScreenFieldKind

internal fun canonicalFieldInput(kind: ScreenFieldKind, value: String): String =
    if (kind == ScreenFieldKind.POSITIVE_DECIMAL) value.replace(',', '.') else value

internal fun displayFieldInput(kind: ScreenFieldKind, value: String, separator: String): String =
    if (kind == ScreenFieldKind.POSITIVE_DECIMAL) value.replace(".", separator) else value

@Composable
internal fun localizedDecimal(value: String): String =
    displayFieldInput(ScreenFieldKind.POSITIVE_DECIMAL, value, localizedText("number_decimal_separator"))
