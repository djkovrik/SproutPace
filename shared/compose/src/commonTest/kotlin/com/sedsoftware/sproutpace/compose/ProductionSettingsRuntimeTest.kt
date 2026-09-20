package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenItem
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.platform.CrashReportingState
import com.sedsoftware.sproutpace.platform.DisableReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductionSettingsRuntimeTest {
    @Test
    fun ac112_settingsShowsLiveEffectiveAndUnavailableCrashState() = runTest {
        val runtime = MutableStateFlow<CrashReportingState>(CrashReportingState.Active(true))
        val manager = CrashAwareScreenDataManager(SettingsManager(), runtime)

        assertEquals("crash_reporting_active", manager.watch(ScreenId.SETTINGS).first().crashState())
        runtime.value = CrashReportingState.Disabled(false, DisableReason.USER_CHOICE)
        assertEquals("crash_reporting_user_off", manager.watch(ScreenId.SETTINGS).first().crashState())
        runtime.value = CrashReportingState.Disabled(true, DisableReason.NON_RELEASE_ENVIRONMENT)
        assertEquals("crash_reporting_build_disabled", manager.watch(ScreenId.SETTINGS).first().crashState())
        runtime.value = CrashReportingState.Unavailable(true)
        assertEquals("crash_reporting_unavailable", manager.watch(ScreenId.SETTINGS).first().crashState())
    }

    private fun ScreenSnapshot.crashState() = items.single { it.id == "crash-reporting" }.stateKey?.value

    private class SettingsManager : ScreenDataManager {
        override fun watch(screen: ScreenId, stableId: String?): Flow<ScreenSnapshot> =
            MutableStateFlow(
                ScreenSnapshot(
                    items = listOf(
                        ScreenItem("crash-reporting", titleKey = TextKey("settings_crash_reporting")),
                    ),
                ),
            )

        override suspend fun execute(
            screen: ScreenId,
            command: com.sedsoftware.sproutpace.component.ScreenCommand,
        ) = Result.success(ScreenSnapshot())
    }
}
