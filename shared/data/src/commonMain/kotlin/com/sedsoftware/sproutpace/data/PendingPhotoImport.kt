package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.PhotoId

/** App-private, short-lived picker bytes. Tokens are opaque file names and are never persisted. */
interface PendingPhotoImportStore : AutoCloseable {
    suspend fun read(token: String): ByteArray?
    suspend fun delete(token: String)
    suspend fun clear()
    override fun close()
}

class InMemoryPendingPhotoImportStore : PendingPhotoImportStore {
    private val values = linkedMapOf<String, ByteArray>()

    fun stage(token: String, bytes: ByteArray) {
        requireSafePendingPhotoToken(token)
        values[token] = bytes.copyOf()
    }

    override suspend fun read(token: String): ByteArray? = values[requireSafePendingPhotoToken(token)]?.copyOf()
    override suspend fun delete(token: String) { values.remove(requireSafePendingPhotoToken(token)) }
    override suspend fun clear() = values.clear()
    override fun close() = values.clear()
}

internal fun managedPhotoId(
    observationId: ObservationId,
    bytes: ByteArray,
    checkActive: () -> Unit = {},
): PhotoId {
    checkActive()
    val identity = observationId.value.encodeToByteArray()
    checkActive()
    val digestInput = ByteArray(identity.size + 1 + bytes.size)
    checkActive()
    identity.copyInto(digestInput)
    digestInput[identity.size] = 0
    bytes.copyInto(digestInput, identity.size + 1)
    checkActive()
    return PhotoId("photo-${Sha256.digest(digestInput, checkActive)}")
}

fun requireSafePendingPhotoToken(token: String): String {
    require(token.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "Invalid pending photo token" }
    require(token != "." && token != "..") { "Invalid pending photo token" }
    return token
}
