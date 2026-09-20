package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.compose.theme.resolveDarkTheme
import com.sedsoftware.sproutpace.data.ThemePreference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeRuntimeContractTest {
    @Test
    fun ac121_systemFollowsHostAndOverridesRemainStable() {
        assertFalse(resolveDarkTheme(ThemePreference.SYSTEM, systemIsDark = false))
        assertTrue(resolveDarkTheme(ThemePreference.SYSTEM, systemIsDark = true))
        assertFalse(resolveDarkTheme(ThemePreference.LIGHT, systemIsDark = true))
        assertTrue(resolveDarkTheme(ThemePreference.DARK, systemIsDark = false))
    }
}
