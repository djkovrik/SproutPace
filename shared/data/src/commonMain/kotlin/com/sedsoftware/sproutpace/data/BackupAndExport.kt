package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.contract.BackupManager
import com.sedsoftware.sproutpace.domain.contract.domainResult
import com.sedsoftware.sproutpace.domain.model.BackupManifest
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.BatchOutcome
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.PhotoId
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.time.Clock

enum class LocalDataErrorKey(val resourceKey: String) {
    INVALID_COMMAND("error_local_data_invalid_command"),
    ARCHIVE_UNREADABLE("error_backup_unreadable"),
    ARCHIVE_UNSUPPORTED("error_backup_unsupported_version"),
    ARCHIVE_INVALID("error_backup_invalid"),
    ARCHIVE_DUPLICATE_RECORD("error_backup_duplicate_record"),
    ARCHIVE_CORRUPT_PHOTO("error_backup_corrupt_photo"),
    ARCHIVE_PHOTO_MISMATCH("error_backup_photo_mismatch"),
    DOCUMENT_NOT_FOUND("error_document_not_found"),
    DOCUMENT_READ_FAILED("error_document_read_failed"),
    DOCUMENT_WRITE_FAILED("error_document_write_failed"),
    RESTORE_FAILED("error_restore_failed"),
    ERASE_FAILED("error_erase_failed"),
}

class LocalDataException(val key: LocalDataErrorKey, cause: Throwable? = null) :
    IllegalArgumentException(key.resourceKey, cause), com.sedsoftware.sproutpace.component.ScreenFailure {
    override val errorKey = com.sedsoftware.sproutpace.component.TextKey(key.resourceKey)
}

/** Opaque document identifiers are supplied by the platform picker; filesystem paths never enter an archive. */
interface ArchiveDocumentPort {
    suspend fun read(documentId: String): ByteArray?
    suspend fun write(documentId: String, bytes: ByteArray)
}

/** Replaces all app-owned future reminders, making both apply and compensation idempotent. */
fun interface ReminderReconciliationPort {
    suspend fun replaceFutureReminders(snapshot: ProductSnapshot)
}

/** Root consumes this output and resets navigation to first run after a successful erase. */
fun interface LocalDataIntegrationOutput {
    suspend fun emit(event: LocalDataIntegrationEvent)
}

sealed interface LocalDataIntegrationEvent {
    data object FirstRunNavigationRequired : LocalDataIntegrationEvent
}

data class LocalBackupArchive(
    val createdAt: Instant,
    val snapshot: ProductSnapshot,
    val photos: List<PrivatePhoto>,
    val preferences: ProductPreferences = ProductPreferences(),
    val formatVersion: Int = LocalBackupArchiveCodec.FORMAT_VERSION,
) {
    constructor(
        snapshot: ProductSnapshot,
        photos: List<PrivatePhoto>,
        preferences: ProductPreferences = ProductPreferences(),
    ) : this(Instant.fromEpochMilliseconds(0), snapshot, photos, preferences)
}

sealed interface BackupPreview {
    data class Valid(val archive: LocalBackupArchive) : BackupPreview
    data class Invalid(val reasons: List<LocalDataErrorKey>) : BackupPreview
}

/** Deterministic line-framed payload. Authored strings and JSON are UTF-8 hex, so records cannot escape framing. */
class LocalBackupArchiveCodec(private val domainCodec: DomainJsonCodec = DomainJsonCodec()) {
    fun encode(archive: LocalBackupArchive): ByteArray {
        val snapshot = archive.snapshot
        val lines = buildList {
            add(listOf(MAGIC, archive.formatVersion.toString(), archive.createdAt.toString()).joinToString(SEPARATOR))
            add(encodePreferences(archive.preferences))
            add(encodeManifest(snapshot.manifest))
            snapshot.zones.sortedBy { it.id.value }.forEach { zone ->
                add(listOf("Z", text(zone.id.value), text(zone.name), zone.type.name, zone.capacity.toString()).joinToString(SEPARATOR))
            }
            snapshot.trayPresets.sortedBy { it.id.value }.forEach { tray ->
                val shape = when (val value = tray.shape) {
                    is TrayShape.Rectangle -> listOf("RECTANGLE", value.widthCentimetres.toString(), value.lengthCentimetres.toString())
                    is TrayShape.Circle -> listOf("CIRCLE", value.diameterCentimetres.toString(), "")
                }
                add((listOf("T", text(tray.id.value), text(tray.name)) + shape).joinToString(SEPARATOR))
            }
            snapshot.recipes.sortedBy { it.id.value }.forEach { add("R$SEPARATOR${text(domainCodec.encodeRecipe(it))}") }
            snapshot.batches.sortedBy { it.id.value }.forEach { add("B$SEPARATOR${text(domainCodec.encodeBatch(it))}") }
            snapshot.plans.sortedBy { it.id.value }.forEach { add("L$SEPARATOR${text(domainCodec.encodePlan(it))}") }
            snapshot.observations.sortedBy { it.id.value }.forEach { add("O$SEPARATOR${text(domainCodec.encodeObservation(it))}") }
            archive.photos.sortedBy { it.id.value }.forEach { photo ->
                add(listOf("F", text(photo.id.value), photo.sha256, photo.bytes.toHex()).joinToString(SEPARATOR))
            }
        }
        return lines.joinToString("\n", postfix = "\n").encodeToByteArray()
    }

    fun decode(bytes: ByteArray): LocalBackupArchive = guardedDecode {
        val lines = bytes.decodeToString(throwOnInvalidSequence = true).split('\n').filter(String::isNotEmpty)
        val header = fields(lines.firstOrNull() ?: invalid())
        if (header.size != HEADER_FIELD_COUNT || header[RECORD_TYPE_INDEX] != MAGIC) invalid()
        val version = header[1].toIntOrNull() ?: invalid()
        if (version != FORMAT_VERSION) throw LocalDataException(LocalDataErrorKey.ARCHIVE_UNSUPPORTED)
        val createdAt = Instant.parse(header[2])
        val preferences = decodePreferences(lines.getOrNull(1) ?: invalid())
        val manifest = decodeManifest(lines.getOrNull(2) ?: invalid())
        val zones = mutableListOf<GrowZone>()
        val trays = mutableListOf<TraySizePreset>()
        val recipes = mutableListOf<UserGrowRecipe>()
        val batches = mutableListOf<GrowBatch>()
        val plans = mutableListOf<HarvestPlan>()
        val observations = mutableListOf<Observation>()
        val photos = mutableListOf<PrivatePhoto>()
        lines.drop(ARCHIVE_PREAMBLE_LINE_COUNT).forEach { line ->
            val value = fields(line)
            when (value.firstOrNull()) {
                "Z" -> zones += GrowZone(
                    GrowZoneId(plain(value[1])),
                    plain(value[2]),
                    GrowZoneType.valueOf(value[SHAPE_KIND_INDEX]),
                    value[PRIMARY_VALUE_INDEX].toInt(),
                )
                "T" -> trays += TraySizePreset(
                    TraySizePresetId(plain(value[1])), plain(value[2]),
                    if (value[SHAPE_KIND_INDEX] == "RECTANGLE") {
                        TrayShape.Rectangle(
                            value[PRIMARY_VALUE_INDEX].toDouble(),
                            value[SECONDARY_VALUE_INDEX].toDouble(),
                        )
                    } else if (value[SHAPE_KIND_INDEX] == "CIRCLE") {
                        TrayShape.Circle(value[PRIMARY_VALUE_INDEX].toDouble())
                    } else {
                        invalid()
                    },
                )
                "R" -> recipes += domainCodec.decodeRecipe(plain(value[1]))
                "B" -> batches += domainCodec.decodeBatch(plain(value[1]))
                "L" -> plans += domainCodec.decodePlan(plain(value[1]))
                "O" -> observations += domainCodec.decodeObservation(plain(value[1]))
                "F" -> photos += PrivatePhoto(
                    PhotoId(plain(value[1])),
                    value[PHOTO_BYTES_INDEX].hexToBytes(),
                    value[2],
                )
                else -> invalid()
            }
        }
        LocalBackupArchive(
            createdAt, ProductSnapshot(manifest, zones, trays, recipes, batches, plans, observations),
            photos, preferences, version,
        )
    }

    private fun encodePreferences(value: ProductPreferences): String {
        val privacy = value.privacyState
        return listOf(
            "P", value.theme.name, value.crashReportingEnabled.toString(), value.notificationsEnabled.toString(),
            privacy?.schemaVersion?.toString().orEmpty(), privacy?.policyVersion?.let(::text).orEmpty(),
            privacy?.expiresAtEpochMillis?.toString().orEmpty(), privacy?.choice?.name.orEmpty(),
            value.summaryTime.toString(), value.soakRemindersEnabled.toString(),
            privacy?.payloadVersion?.toString().orEmpty(), privacy?.endpoint?.let(::text).orEmpty(),
            privacy?.consentRequired?.toString().orEmpty(), privacy?.fetchedAtEpochMillis?.toString().orEmpty(),
        ).joinToString(SEPARATOR)
    }

    private fun decodePreferences(line: String): ProductPreferences {
        val value = fields(line)
        if (value.size !in SUPPORTED_PREFERENCE_FIELD_COUNTS || value[RECORD_TYPE_INDEX] != "P") invalid()
        val privacy = if (value[PRIVACY_SCHEMA_INDEX].isEmpty()) null else AdvertisingPrivacyState(
            schemaVersion = value[PRIVACY_SCHEMA_INDEX].toInt(),
            policyVersion = plain(value[POLICY_VERSION_INDEX]),
            expiresAtEpochMillis = value[EXPIRES_AT_INDEX].toLong(),
            choice = ConsentChoice.valueOf(value[CONSENT_CHOICE_INDEX]),
            payloadVersion = value.getOrNull(PAYLOAD_VERSION_INDEX)?.takeIf(String::isNotEmpty)?.toInt()
                ?: ADVERTISING_PRIVACY_PAYLOAD_VERSION,
            endpoint = value.getOrNull(PRIVACY_ENDPOINT_INDEX)?.takeIf(String::isNotEmpty)?.let(::plain)
                ?: ADVERTISING_PRIVACY_ENDPOINT,
            consentRequired = value.getOrNull(CONSENT_REQUIRED_INDEX)?.takeIf(String::isNotEmpty)?.toBooleanStrict()
                ?: true,
            fetchedAtEpochMillis = value.getOrNull(FETCHED_AT_INDEX)?.takeIf(String::isNotEmpty)?.toLong()
                ?: 0L,
        )
        return ProductPreferences(
            ThemePreference.valueOf(value[1]),
            value[2].toBooleanStrict(),
            value[NOTIFICATIONS_ENABLED_INDEX].toBooleanStrict(),
            privacy,
            summaryTime = SummaryLocalTime.parseOrDefault(value.getOrNull(SUMMARY_TIME_INDEX)),
            soakRemindersEnabled = value.getOrNull(SOAK_REMINDERS_INDEX)?.toBooleanStrictOrNull() ?: true,
        )
    }

    private fun encodeManifest(value: BackupManifest): String {
        val integrity = value.photoIntegrity.entries.sortedBy { it.key.value }
            .joinToString(",") { (id, digest) -> "${text(id.value)}:$digest" }
        return listOf(
            "M", value.schemaVersion.toString(), value.archiveVersion.toString(),
            value.recordCounts["zones"].orZero(), value.recordCounts["trayPresets"].orZero(),
            value.recordCounts["recipes"].orZero(), value.recordCounts["batches"].orZero(),
            value.recordCounts["plans"].orZero(), value.recordCounts["observations"].orZero(), integrity,
        ).joinToString(SEPARATOR)
    }

    private fun decodeManifest(line: String): BackupManifest {
        val value = fields(line)
        if (value.size != MANIFEST_FIELD_COUNT || value[RECORD_TYPE_INDEX] != "M") invalid()
        val counts = linkedMapOf(
            "zones" to value[MANIFEST_ZONES_INDEX].toInt(),
            "trayPresets" to value[MANIFEST_TRAYS_INDEX].toInt(),
            "recipes" to value[MANIFEST_RECIPES_INDEX].toInt(),
            "batches" to value[MANIFEST_BATCHES_INDEX].toInt(),
            "plans" to value[MANIFEST_PLANS_INDEX].toInt(),
            "observations" to value[MANIFEST_OBSERVATIONS_INDEX].toInt(),
        )
        val integrityValue = value[MANIFEST_INTEGRITY_INDEX]
        val integrity = if (integrityValue.isEmpty()) emptyMap() else integrityValue.split(',').associate { entry ->
            val parts = entry.split(':', limit = 2)
            PhotoId(plain(parts[0])) to parts[1]
        }
        return BackupManifest(value[1].toInt(), value[2].toInt(), counts, integrity)
    }

    private fun fields(line: String) = line.split(SEPARATOR)
    private fun text(value: String) = value.encodeToByteArray().toHex()
    private fun plain(value: String) = value.hexToBytes().decodeToString(throwOnInvalidSequence = true)
    private fun invalid(): Nothing = throw LocalDataException(LocalDataErrorKey.ARCHIVE_UNREADABLE)
    private inline fun <T> guardedDecode(block: () -> T): T = runCatching(block).getOrElse { failure ->
        if (failure is LocalDataException) throw failure
        throw LocalDataException(LocalDataErrorKey.ARCHIVE_UNREADABLE, failure)
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
        private const val MAGIC = "SPROUTPACE-LOCAL-ARCHIVE"
        private const val SEPARATOR = "\t"
        private const val RECORD_TYPE_INDEX = 0
        private const val HEADER_FIELD_COUNT = 3
        private const val ARCHIVE_PREAMBLE_LINE_COUNT = 3
        private const val MANIFEST_FIELD_COUNT = 10
        private const val SHAPE_KIND_INDEX = 3
        private const val NOTIFICATIONS_ENABLED_INDEX = 3
        private const val PRIMARY_VALUE_INDEX = 4
        private const val SECONDARY_VALUE_INDEX = 5
        private const val PHOTO_BYTES_INDEX = 3
        private const val PRIVACY_SCHEMA_INDEX = 4
        private const val POLICY_VERSION_INDEX = 5
        private const val EXPIRES_AT_INDEX = 6
        private const val CONSENT_CHOICE_INDEX = 7
        private const val SUMMARY_TIME_INDEX = 8
        private const val SOAK_REMINDERS_INDEX = 9
        private const val PAYLOAD_VERSION_INDEX = 10
        private const val PRIVACY_ENDPOINT_INDEX = 11
        private const val CONSENT_REQUIRED_INDEX = 12
        private const val FETCHED_AT_INDEX = 13
        private const val MANIFEST_ZONES_INDEX = 3
        private const val MANIFEST_TRAYS_INDEX = 4
        private const val MANIFEST_RECIPES_INDEX = 5
        private const val MANIFEST_BATCHES_INDEX = 6
        private const val MANIFEST_PLANS_INDEX = 7
        private const val MANIFEST_OBSERVATIONS_INDEX = 8
        private const val MANIFEST_INTEGRITY_INDEX = 9
        private val SUPPORTED_PREFERENCE_FIELD_COUNTS = setOf(8, 10, 14)
    }
}

private fun Int?.orZero(): String = (this ?: 0).toString()

class LocalBackupService(
    private val database: LocalDatabase,
    private val files: PrivateFileStore,
    private val settings: ProductSettings,
    private val ioDispatcher: CoroutineDispatcher,
    private val documents: ArchiveDocumentPort? = null,
    private val reminders: ReminderReconciliationPort = ReminderReconciliationPort { },
    private val output: LocalDataIntegrationOutput = LocalDataIntegrationOutput { },
    private val codec: LocalBackupArchiveCodec = LocalBackupArchiveCodec(),
    private val now: () -> Instant = { Clock.System.now() },
    private val csvHeaders: suspend () -> List<String> = { BatchHistoryCsv.HEADER.split(',') },
    private val flushPreferences: () -> Unit = { },
) : BackupManager {
    private val replacementMutex = Mutex()

    /** Must finish before exposing the workspace after process startup. A journal means no commit occurred. */
    suspend fun recoverPendingReplacement() = replacementMutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            val bytes = files.readRecoveryArchive()
            if (bytes != null) {
                val failure = LocalDataException(LocalDataErrorKey.RESTORE_FAILED)
                if (!compensate(codec.decode(bytes), failure)) throw failure
                files.writeRecoveryArchive(null)
            }
        }
    }
    override suspend fun createSnapshot(): Result<ProductSnapshot> = domainResult { createArchive().snapshot }

    suspend fun createArchive(): LocalBackupArchive = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val photos = files.exportAll().sortedBy { it.id.value }
        currentCoroutineContext().ensureActive()
        val current = database.snapshot()
        currentCoroutineContext().ensureActive()
        val integrity = photos.associate { it.id to it.sha256 }
        val manifest = SnapshotValidation.manifest(
            current.zones, current.trayPresets, current.recipes, current.batches,
            current.plans, current.observations, integrity,
        )
        LocalBackupArchive(now(), current.copy(manifest = manifest), photos, settings.snapshot())
    }

    suspend fun createArchiveBytes(): ByteArray = codec.encode(createArchive())
    suspend fun writeArchive(documentId: String) = documentWrite(documentId, createArchiveBytes())
    suspend fun readPreview(documentId: String): BackupPreview = preview(codec.decode(documentRead(documentId)))
    suspend fun replaceFromDocument(documentId: String) = replaceArchive(codec.decode(documentRead(documentId)))
    suspend fun writeCsv(documentId: String) = documentWrite(documentId, BatchHistoryCsv.encode(database.snapshot(), csvHeaders()))

    override suspend fun validate(snapshot: ProductSnapshot): Result<RestoreValidation> =
        domainResult { SnapshotValidation.validate(snapshot) }

    suspend fun preview(archive: LocalBackupArchive): BackupPreview = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val reasons = mutableListOf<LocalDataErrorKey>()
        if (archive.formatVersion != LocalBackupArchiveCodec.FORMAT_VERSION) reasons += LocalDataErrorKey.ARCHIVE_UNSUPPORTED
        if (SnapshotValidation.validate(archive.snapshot) is RestoreValidation.Invalid) reasons += LocalDataErrorKey.ARCHIVE_INVALID
        currentCoroutineContext().ensureActive()
        if (archive.photos.map { it.id }.distinct().size != archive.photos.size) reasons += LocalDataErrorKey.ARCHIVE_DUPLICATE_RECORD
        var corruptPhoto = false
        for (photo in archive.photos) {
            currentCoroutineContext().ensureActive()
            if (Sha256.digest(photo.bytes) != photo.sha256) {
                corruptPhoto = true
                break
            }
        }
        if (corruptPhoto) reasons += LocalDataErrorKey.ARCHIVE_CORRUPT_PHOTO
        val expected = archive.snapshot.manifest.photoIntegrity
        val actual = archive.photos.associate { it.id to it.sha256 }
        currentCoroutineContext().ensureActive()
        if (expected != actual) reasons += LocalDataErrorKey.ARCHIVE_PHOTO_MISMATCH
        if (reasons.isEmpty()) BackupPreview.Valid(archive) else BackupPreview.Invalid(reasons.distinct())
    }

    override suspend fun replace(snapshot: ProductSnapshot): Result<Unit> = domainResult {
        replaceArchive(LocalBackupArchive(now(), snapshot, emptyList()))
    }

    suspend fun replaceArchive(archive: LocalBackupArchive) = replacementMutex.withLock { withContext(ioDispatcher) {
        check(files.readRecoveryArchive() == null) { "Workspace recovery must finish before replacement" }
        val checked = preview(archive)
        if (checked is BackupPreview.Invalid) throw LocalDataException(checked.reasons.first())
        val previous = createArchive()
        files.writeRecoveryArchive(codec.encode(previous))
        val failure = runCatching {
            files.replaceAll(archive.photos)
            currentCoroutineContext().ensureActive()
            database.replace(archive.snapshot)
            currentCoroutineContext().ensureActive()
            settings.replace(archive.preferences)
            flushPreferences()
            reminders.replaceFutureReminders(archive.snapshot)
            currentCoroutineContext().ensureActive()
            files.writeRecoveryArchive(null)
        }.exceptionOrNull()
        if (failure != null) {
            withContext(NonCancellable) {
                if (compensate(previous, failure)) {
                    runCatching { files.writeRecoveryArchive(null) }.exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            if (failure is CancellationException) throw failure
            throw LocalDataException(LocalDataErrorKey.RESTORE_FAILED, failure)
        }
    } }

    suspend fun eraseAll() = replacementMutex.withLock { withContext(ioDispatcher) {
        check(files.readRecoveryArchive() == null) { "Workspace recovery must finish before erasure" }
        val previous = createArchive()
        files.writeRecoveryArchive(codec.encode(previous))
        runCatching {
            files.clear()
            database.erase()
            settings.clearProductState()
            flushPreferences()
            reminders.replaceFutureReminders(database.snapshot())
            currentCoroutineContext().ensureActive()
            files.writeRecoveryArchive(null)
        }.getOrElse { failure ->
            withContext(NonCancellable) {
                if (compensate(previous, failure)) {
                    runCatching { files.writeRecoveryArchive(null) }.exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            if (failure is CancellationException) throw failure
            throw LocalDataException(LocalDataErrorKey.ERASE_FAILED, failure)
        }
        output.emit(LocalDataIntegrationEvent.FirstRunNavigationRequired)
    } }

    private suspend fun compensate(previous: LocalBackupArchive, failure: Throwable): Boolean {
        var complete = true
        suspend fun restore(block: suspend () -> Unit) {
            runCatching { block() }.exceptionOrNull()?.let { error ->
                complete = false
                failure.addSuppressed(error)
            }
        }
        restore { files.replaceAll(previous.photos) }
        restore { database.replace(previous.snapshot) }
        restore { settings.replace(previous.preferences); flushPreferences() }
        restore { reminders.replaceFutureReminders(previous.snapshot) }
        return complete
    }

    private suspend fun documentRead(documentId: String): ByteArray {
        if (documentId.isBlank()) localDataFailure(LocalDataErrorKey.INVALID_COMMAND)
        val port = documents ?: localDataFailure(LocalDataErrorKey.DOCUMENT_READ_FAILED)
        val result = runCatching { port.read(documentId) }
        result.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            localDataFailure(LocalDataErrorKey.DOCUMENT_READ_FAILED, failure)
        }
        return result.getOrNull() ?: localDataFailure(LocalDataErrorKey.DOCUMENT_NOT_FOUND)
    }

    private suspend fun documentWrite(documentId: String, bytes: ByteArray) {
        if (documentId.isBlank()) localDataFailure(LocalDataErrorKey.INVALID_COMMAND)
        val port = documents ?: localDataFailure(LocalDataErrorKey.DOCUMENT_WRITE_FAILED)
        val failure = runCatching { port.write(documentId, bytes) }.exceptionOrNull()
        if (failure != null) {
            if (failure is CancellationException) throw failure
            localDataFailure(LocalDataErrorKey.DOCUMENT_WRITE_FAILED, failure)
        }
    }
}

class LocalWorkspaceRepository(
    private val database: LocalDatabase,
    private val files: PrivateFileStore,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun saveObservation(observation: Observation, photoBytes: ByteArray?): Observation = saveObservation(
        observation = observation,
        photoChange = photoBytes?.let { bytes ->
            ObservationPhotoChange.Replace(requireNotNull(observation.photoId), bytes)
        } ?: ObservationPhotoChange.Keep,
    )

    suspend fun saveObservation(
        observation: Observation,
        photoChange: ObservationPhotoChange = ObservationPhotoChange.Keep,
    ): Observation = withContext(ioDispatcher) {
        val previousObservation = database.snapshot().observations.firstOrNull { it.id == observation.id }
        val previousId = previousObservation?.photoId
        val previousBytes = previousId?.let { files.read(it) }
        val target = when (photoChange) {
            ObservationPhotoChange.Keep -> observation.copy(photoId = previousId ?: observation.photoId)
            ObservationPhotoChange.Remove -> observation.copy(photoId = null)
            is ObservationPhotoChange.Replace -> observation.copy(photoId = photoChange.id)
        }
        val replacement = photoChange as? ObservationPhotoChange.Replace
        val overwrittenReplacement = replacement?.id
            ?.takeIf { it != previousId }
            ?.let { id -> files.read(id)?.let { id to it } }
        return@withContext runCatching {
            replacement?.let { files.write(it.id, it.bytes) }
            database.saveObservation(target)
            if (previousId != null && previousId != target.photoId) files.delete(previousId)
            target
        }.getOrElse { failure ->
            runCatching {
                if (previousObservation == null) database.deleteObservation(observation.id)
                else database.saveObservation(previousObservation)
            }
            if (previousId != null && previousBytes != null) runCatching { files.write(previousId, previousBytes) }
            replacement?.takeIf { it.id != previousId }?.let { changed ->
                val overwritten = overwrittenReplacement?.takeIf { it.first == changed.id }
                if (overwritten == null) runCatching { files.delete(changed.id) }
                else runCatching { files.write(overwritten.first, overwritten.second) }
            }
            throw failure
        }
    }

    suspend fun readPhoto(id: PhotoId): ByteArray? = withContext(ioDispatcher) { files.read(id) }

    suspend fun deleteObservation(id: ObservationId) = withContext(ioDispatcher) {
        val observation = database.snapshot().observations.firstOrNull { it.id == id } ?: return@withContext
        val photo = observation.photoId?.let { it to files.read(it) }
        runCatching {
            observation.photoId?.let { files.delete(it) }
            database.deleteObservation(id)
        }.getOrElse { failure ->
            photo?.second?.let { files.write(photo.first, it) }
            throw failure
        }
    }

    suspend fun deleteBatch(id: BatchId) = withContext(ioDispatcher) {
        val photoIds = database.snapshot().observations.filter { it.batchId == id }.mapNotNull { it.photoId }
        val photos = photoIds.mapNotNull { photoId -> files.read(photoId)?.let { photoId to it } }
        runCatching {
            photoIds.forEach { files.delete(it) }
            database.deleteBatch(id)
        }.getOrElse { failure ->
            photos.forEach { (photoId, bytes) -> runCatching { files.write(photoId, bytes) } }
            throw failure
        }
    }
}

sealed interface ObservationPhotoChange {
    data object Keep : ObservationPhotoChange
    data object Remove : ObservationPhotoChange
    class Replace(val id: PhotoId, bytes: ByteArray) : ObservationPhotoChange {
        val bytes: ByteArray = bytes.copyOf()
    }
}

object BatchHistoryCsv {
    const val HEADER: String = "batch_id,culture_id,recipe_name,state,outcome_at,actual_mass_grams,note"

    fun encode(snapshot: ProductSnapshot, headers: List<String> = HEADER.split(',')): ByteArray {
        require(headers.size == HEADER.split(',').size && headers.all { it.isNotBlank() })
        val rows = snapshot.batches.map { batch ->
            val outcome = batch.outcome
            listOf(
                batch.id.value, batch.recipe.builtInCultureId?.value.orEmpty(), batch.recipe.nameSnapshot.orEmpty(),
                batch.state.name, outcome?.occurredAt?.toString().orEmpty(),
                (outcome as? BatchOutcome.Harvested)?.actualMass?.value?.toString().orEmpty(), outcome?.note.orEmpty(),
            ).joinToString(",", transform = ::escape)
        }
        return (listOf(headers.joinToString(",", transform = ::escape)) + rows)
            .joinToString("\r\n", postfix = "\r\n").encodeToByteArray()
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value
}

private const val HEX_RADIX = 16
private const val HEX_BYTE_WIDTH = 2

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    byte.toUByte().toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
}

private fun String.hexToBytes(): ByteArray {
    require(length % HEX_BYTE_WIDTH == 0)
    return ByteArray(length / HEX_BYTE_WIDTH) { index ->
        val start = index * HEX_BYTE_WIDTH
        substring(start, start + HEX_BYTE_WIDTH).toInt(HEX_RADIX).toByte()
    }
}

private fun localDataFailure(key: LocalDataErrorKey, cause: Throwable? = null): Nothing =
    throw LocalDataException(key, cause)
