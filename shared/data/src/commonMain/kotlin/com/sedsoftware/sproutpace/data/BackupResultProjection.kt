package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.component.ScreenFact
import com.sedsoftware.sproutpace.component.ScreenSnapshot
import com.sedsoftware.sproutpace.component.TextKey

/** Shared by the manager and deterministic fixtures after a successful backup operation. */
internal fun ScreenSnapshot.withBackupResult(resultKey: String?): ScreenSnapshot =
    if (resultKey == null) this else copy(facts = facts + ScreenFact(
        "backup-result", TextKey("backup_completed"), valueKey = TextKey(resultKey),
    ))
