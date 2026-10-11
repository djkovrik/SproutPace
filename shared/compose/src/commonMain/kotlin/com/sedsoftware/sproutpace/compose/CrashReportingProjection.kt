package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenFact
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.platform.CrashReportingState
import com.sedsoftware.sproutpace.platform.DisableReason

internal fun ScreenSnapshot.withCrashReporting(state: CrashReportingState): ScreenSnapshot = copy(
    items = items.map { item ->
        if (item.id == "crash-reporting") item.copy(stateKey = state.textKey()) else item
    },
    facts = facts.filterNot { it.id == "crash-reporting-effective" } + ScreenFact(
        "crash-reporting-effective", TextKey("settings_crash_reporting_effective"), valueKey = state.textKey(),
    ),
)

private fun CrashReportingState.textKey() = TextKey(when (this) {
    is CrashReportingState.Active -> "crash_reporting_active"
    is CrashReportingState.Unavailable -> "crash_reporting_unavailable"
    is CrashReportingState.Disabled -> when (reason) {
        DisableReason.USER_CHOICE -> "crash_reporting_user_off"
        DisableReason.NON_RELEASE_ENVIRONMENT -> "crash_reporting_build_disabled"
    }
})
