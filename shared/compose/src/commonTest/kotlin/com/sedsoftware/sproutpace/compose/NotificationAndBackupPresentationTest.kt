package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.platform.PermissionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationAndBackupPresentationTest {
    @Test
    fun disabledRemindersNeverRequestPermissionOrOfferSettingsRecovery() {
        listOf(PermissionState.Unknown, PermissionState.Granted,
            PermissionState.Denied(true), PermissionState.Denied(false)).forEach { permission ->
            assertTrue(notificationPermissionActionIds(false, permission).isEmpty())
        }
    }

    @Test
    fun permissionRecoveryMatchesCurrentOsState() {
        assertEquals(setOf("request-notification-permission"), notificationPermissionActionIds(true, PermissionState.Unknown))
        assertEquals(setOf("open-system-settings"), notificationPermissionActionIds(true, PermissionState.Denied(false)))
        assertEquals(setOf("request-notification-permission", "open-system-settings"),
            notificationPermissionActionIds(true, PermissionState.Denied(true)))
        listOf(PermissionState.Granted, PermissionState.Checking, PermissionState.Requesting,
            PermissionState.Unavailable).forEach { assertTrue(notificationPermissionActionIds(true, it).isEmpty()) }
    }

    @Test
    fun restoreRequiresValidatedMetadataAndIsUnavailableDuringProgressOrErrors() {
        val idle = SproutPaceUiState(screen = UiScreen.BACKUP_RESTORE)
        assertEquals(BackupScreenPhase.IDLE, backupScreenPhase(idle))
        val preview = idle.copy(facts = listOf(
            UiFact("restore-created-at", "restore_archive_date", null, "2026-10-10T10:00:00Z"),
            UiFact("restore-format-version", "backup_format_version", null, "1"),
        ))
        assertEquals(BackupScreenPhase.VALID_PREVIEW, backupScreenPhase(preview))
        assertEquals(BackupScreenPhase.PROGRESS, backupScreenPhase(preview.copy(busy = true)))
        assertEquals(BackupScreenPhase.ERROR, backupScreenPhase(preview.copy(errorKey = "data_archive_invalid")))
        assertEquals(BackupScreenPhase.IDLE, backupScreenPhase(preview.copy(facts = preview.facts.take(1))))
    }

    @Test
    fun completedOperationShowsItsResultInsteadOfAStaleRestorePreview() {
        val state = SproutPaceUiState(screen = UiScreen.BACKUP_RESTORE, facts = listOf(
            UiFact("backup-result", "screen_backup_restore_title", "backup_created", null),
            UiFact("restore-created-at", "restore_archive_date", null, "2026-10-10T10:00:00Z"),
            UiFact("restore-format-version", "backup_format_version", null, "1"),
        ))
        assertEquals(BackupScreenPhase.RESULT, backupScreenPhase(state))
        assertEquals(BackupScreenPhase.ERROR, backupScreenPhase(state.copy(variant = UiVariant.ERROR)))
    }
}
