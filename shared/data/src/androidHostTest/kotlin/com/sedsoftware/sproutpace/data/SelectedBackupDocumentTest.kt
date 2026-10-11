package com.sedsoftware.sproutpace.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class SelectedBackupDocumentTest {
    @Test fun metadataRoundTripsWithoutChangingTheOpaqueLocationOrOriginalName() {
        val selected = SelectedBackupDocument("file:///private/imports/random-id.zip", "Мой урожай \"2026\".zip")
        assertEquals(selected, SelectedBackupDocument.decode(selected.encode()))
    }

    @Test fun legacyPathsRemainCompatibleAndDoNotInventAnOriginalName() {
        val path = "content://provider/document/archive%2F10"
        val decoded = SelectedBackupDocument.decode(path)
        assertEquals(path, decoded.location)
        assertNull(decoded.displayName)
    }

    @Test fun malformedMetadataDoesNotBecomeAFileLocation() {
        assertFails { SelectedBackupDocument.decode("sproutpace-document:invalid") }
    }
}
