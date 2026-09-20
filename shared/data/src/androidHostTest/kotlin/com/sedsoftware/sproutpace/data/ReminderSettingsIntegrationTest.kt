package com.sedsoftware.sproutpace.data

import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.domain.model.BackupManifest
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSettingsIntegrationTest {
    @Test
    fun ac065_defaultsToEnabledDailySummaryAtNineAndIndependentSoakReminder() {
        val persisted = MapSettings()
        val settings = MultiplatformProductSettings(persisted)

        assertTrue(settings.snapshot().notificationsEnabled)
        assertEquals(SummaryLocalTime(9, 0), settings.snapshot().summaryTime)
        assertTrue(settings.snapshot().soakRemindersEnabled)

        settings.setNotificationsEnabled(false)
        settings.setSummaryTime(SummaryLocalTime(7, 45))
        settings.setSoakRemindersEnabled(true)

        val restarted = MultiplatformProductSettings(persisted).snapshot()
        assertFalse(restarted.notificationsEnabled)
        assertEquals(SummaryLocalTime(7, 45), restarted.summaryTime)
        assertTrue(restarted.soakRemindersEnabled)
    }

    @Test
    fun malformedSummaryTimeFallsBackToNineWithoutChangingIndependentToggles() {
        val persisted = MapSettings().apply {
            putString("notification_summary_time.v1", "99:99")
            putBoolean("notifications_enabled.v1", true)
            putBoolean("soak_reminders_enabled.v1", false)
        }

        val restored = MultiplatformProductSettings(persisted).snapshot()

        assertEquals(SummaryLocalTime.DEFAULT, restored.summaryTime)
        assertTrue(restored.notificationsEnabled)
        assertFalse(restored.soakRemindersEnabled)
    }

    @Test
    fun ac067_backupRoundTripPreservesReminderChoicesAndLegacyArchiveUsesSafeDefaults() {
        val codec = LocalBackupArchiveCodec()
        val archive = LocalBackupArchive(
            createdAt = Instant.parse("2026-09-20T00:00:00Z"),
            snapshot = emptySnapshot(),
            photos = emptyList(),
            preferences = ProductPreferences(
                notificationsEnabled = false,
                summaryTime = SummaryLocalTime(18, 20),
                soakRemindersEnabled = false,
            ),
        )

        val encoded = codec.encode(archive)
        val decoded = codec.decode(encoded)
        assertEquals(archive.preferences, decoded.preferences)

        val lines = encoded.decodeToString().lines().toMutableList()
        val legacyPreferences = lines[1].split('\t').take(8).joinToString("\t")
        lines[1] = legacyPreferences
        val legacy = codec.decode(lines.joinToString("\n").encodeToByteArray())
        assertEquals(SummaryLocalTime.DEFAULT, legacy.preferences.summaryTime)
        assertTrue(legacy.preferences.soakRemindersEnabled)
    }

    private fun emptySnapshot() = ProductSnapshot(
        manifest = BackupManifest(1, 1, emptyMap(), emptyMap()),
        zones = emptyList(),
        trayPresets = emptyList(),
        recipes = emptyList(),
        batches = emptyList(),
        plans = emptyList(),
        observations = emptyList(),
    )
}
