package com.sedsoftware.sproutpace.domain

import com.sedsoftware.sproutpace.domain.contract.domainResult
import com.sedsoftware.sproutpace.domain.engine.SnapshotEngine
import com.sedsoftware.sproutpace.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.*

class ContractsAndSnapshotTest {
    @Test fun managerBoundaryPreservesOriginalFailureCause() = runTest {
        val cause = IllegalStateException("storage failed")
        val result = domainResult<Unit> { throw cause }
        assertSame(cause, result.exceptionOrNull())
    }

    @Test fun managerBoundaryNeverSwallowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            domainResult<Unit> { throw CancellationException("cancelled") }
        }
    }

    @Test fun observationsAreOrderedByActualTimestampAndPreserveText() {
        val later = Observation(ObservationId("later"), BatchId("batch-1"), Instant.parse("2026-09-20T10:00:00Z"), "  exact user text  ")
        val earlier = Observation(ObservationId("earlier"), BatchId("batch-1"), Instant.parse("2026-09-20T09:00:00Z"), "first")
        val ordered = SnapshotEngine.orderedObservations(listOf(later, earlier))
        assertEquals(listOf(earlier, later), ordered)
        assertEquals("  exact user text  ", ordered.last().text)
    }

    @Test fun backupSnapshotValidationRejectsBrokenRelationshipsBeforeReplace() {
        val manifest = BackupManifest(
            1, 1,
            mapOf("zones" to 0, "trayPresets" to 0, "recipes" to 0, "batches" to 0, "plans" to 0, "observations" to 1),
            emptyMap(),
        )
        val orphan = Observation(ObservationId("orphan"), BatchId("missing"), DomainFixtures.instant, "note")
        val snapshot = ProductSnapshot(manifest, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(orphan))
        val invalid = assertIs<RestoreValidation.Invalid>(SnapshotEngine.validate(snapshot))
        assertTrue("observation_without_batch" in invalid.reasons)
    }
}
