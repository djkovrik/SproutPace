package com.sedsoftware.sproutpace.compose

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class TimeZoneDisplayTest {
    @Test fun zoneDisplayUsesSystemLocaleWithoutExposingTheStoredIdentifier() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val english = timeZoneDisplayName("Europe/Moscow", Locale.getDefault())
            Locale.setDefault(Locale.forLanguageTag("ru"))
            val russian = timeZoneDisplayName("Europe/Moscow", Locale.getDefault())
            assertNotEquals(english, russian)
            assertFalse(english.contains('/'))
            assertFalse(russian.contains('/'))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
