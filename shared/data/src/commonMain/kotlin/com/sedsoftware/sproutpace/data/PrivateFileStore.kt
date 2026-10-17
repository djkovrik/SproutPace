package com.sedsoftware.sproutpace.data

import com.sedsoftware.sproutpace.domain.model.PhotoId

/** Photo IDs are portable basenames, never archive-supplied filesystem paths. */
fun isSafePhotoId(id: PhotoId): Boolean = id.value.let { value ->
    value.isNotBlank() && !value.startsWith('.') &&
        value.none { it == '/' || it == '\\' || it == ':' || it.code < FIRST_PRINTABLE_CODE || it.code == DELETE_CODE } &&
        !value.endsWith('.') && !value.endsWith(' ')
}

private const val FIRST_PRINTABLE_CODE = 32
private const val DELETE_CODE = 127

fun requireSafePhotoId(id: PhotoId): String {
    require(isSafePhotoId(id)) { "Photo ID must be a portable basename" }
    return id.value
}

data class PrivatePhoto(val id: PhotoId, val bytes: ByteArray, val sha256: String) {
    override fun equals(other: Any?): Boolean = other is PrivatePhoto && id == other.id &&
        sha256 == other.sha256 && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = 31 * (31 * id.hashCode() + bytes.contentHashCode()) + sha256.hashCode()
}

interface PrivateFileStore : AutoCloseable {
    suspend fun read(id: PhotoId): ByteArray?
    suspend fun write(id: PhotoId, bytes: ByteArray)
    suspend fun delete(id: PhotoId)
    suspend fun exportAll(): List<PrivatePhoto>
    /** Replaces the complete private photo set. Implementations must stage and atomically swap. */
    suspend fun replaceAll(photos: List<PrivatePhoto>)
    suspend fun clear()
    /** Durable rollback archive outside the replaceable photo directory; null commits the replacement. */
    suspend fun readRecoveryArchive(): ByteArray?
    suspend fun writeRecoveryArchive(bytes: ByteArray?)
    override fun close()
}

class InMemoryPrivateFileStore : PrivateFileStore {
    private val values = linkedMapOf<PhotoId, PrivatePhoto>()
    var failNextReplace: Throwable? = null
    private var recoveryArchive: ByteArray? = null
    override suspend fun readRecoveryArchive() = recoveryArchive?.copyOf()
    override suspend fun writeRecoveryArchive(bytes: ByteArray?) { recoveryArchive = bytes?.copyOf() }

    override suspend fun read(id: PhotoId) = values[id]?.bytes?.copyOf()
    override suspend fun write(id: PhotoId, bytes: ByteArray) {
        requireSafePhotoId(id)
        values[id] = PrivatePhoto(id, bytes.copyOf(), Sha256.digest(bytes))
    }
    override suspend fun delete(id: PhotoId) { values.remove(id) }
    override suspend fun exportAll() = values.values.map { it.copy(bytes = it.bytes.copyOf()) }
    override suspend fun replaceAll(photos: List<PrivatePhoto>) {
        failNextReplace?.let { failure -> failNextReplace = null; throw failure }
        require(photos.map { it.id }.distinct().size == photos.size)
        photos.forEach { requireSafePhotoId(it.id) }
        require(photos.all { Sha256.digest(it.bytes) == it.sha256 })
        values.clear()
        photos.forEach { values[it.id] = it.copy(bytes = it.bytes.copyOf()) }
    }
    override suspend fun clear() = values.clear()
    override fun close() = values.clear()
}

internal object Sha256 {
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xff
    private const val WORD_BYTES = 4
    private const val LAST_WORD_BYTE_OFFSET = 3
    private const val LENGTH_FIELD_BYTES = 8
    private const val BLOCK_BYTES = 64
    private const val INITIAL_PADDING_BYTES = 9
    private const val MESSAGE_WORDS = 16
    private const val SCHEDULE_WORDS = 64
    private const val HEX_RADIX = 16
    private const val HEX_WORD_WIDTH = 8
    private const val FIRST_HASH_INDEX = 0
    private const val SECOND_HASH_INDEX = 1
    private const val THIRD_HASH_INDEX = 2
    private const val FOURTH_HASH_INDEX = 3
    private const val FIFTH_HASH_INDEX = 4
    private const val SIXTH_HASH_INDEX = 5
    private const val SEVENTH_HASH_INDEX = 6
    private const val EIGHTH_HASH_INDEX = 7
    private const val WORD_HIGH_SHIFT = 24
    private const val WORD_MIDDLE_SHIFT = 16
    private const val SCHEDULE_OFFSET_2 = 2
    private const val SCHEDULE_OFFSET_7 = 7
    private const val SCHEDULE_OFFSET_15 = 15
    private const val SCHEDULE_OFFSET_16 = 16
    private const val SMALL_SIGMA_0_ROTATION_A = 7
    private const val SMALL_SIGMA_0_ROTATION_B = 18
    private const val SMALL_SIGMA_0_SHIFT = 3
    private const val SMALL_SIGMA_1_ROTATION_A = 17
    private const val SMALL_SIGMA_1_ROTATION_B = 19
    private const val SMALL_SIGMA_1_SHIFT = 10
    private const val LARGE_SIGMA_0_ROTATION_A = 2
    private const val LARGE_SIGMA_0_ROTATION_B = 13
    private const val LARGE_SIGMA_0_ROTATION_C = 22
    private const val LARGE_SIGMA_1_ROTATION_A = 6
    private const val LARGE_SIGMA_1_ROTATION_B = 11
    private const val LARGE_SIGMA_1_ROTATION_C = 25
    private const val INITIAL_HASHES_HEX =
        "6a09e667,bb67ae85,3c6ef372,a54ff53a,510e527f,9b05688c,1f83d9ab,5be0cd19"
    private const val ROUND_CONSTANTS_HEX =
        "428a2f98,71374491,b5c0fbcf,e9b5dba5,3956c25b,59f111f1,923f82a4,ab1c5ed5," +
            "d807aa98,12835b01,243185be,550c7dc3,72be5d74,80deb1fe,9bdc06a7,c19bf174," +
            "e49b69c1,efbe4786,0fc19dc6,240ca1cc,2de92c6f,4a7484aa,5cb0a9dc,76f988da," +
            "983e5152,a831c66d,b00327c8,bf597fc7,c6e00bf3,d5a79147,06ca6351,14292967," +
            "27b70a85,2e1b2138,4d2c6dfc,53380d13,650a7354,766a0abb,81c2c92e,92722c85," +
            "a2bfe8a1,a81a664b,c24b8b70,c76c51a3,d192e819,d6990624,f40e3585,106aa070," +
            "19a4c116,1e376c08,2748774c,34b0bcb5,391c0cb3,4ed8aa4a,5b9cca4f,682e6ff3," +
            "748f82ee,78a5636f,84c87814,8cc70208,90befffa,a4506ceb,bef9a3f7,c67178f2"

    private val initialHashes = INITIAL_HASHES_HEX.hexWords()
    private val roundConstants = ROUND_CONSTANTS_HEX.hexWords()

    // Pure Kotlin SHA-256 keeps archive integrity deterministic on every KMP target.
    fun digest(input: ByteArray, checkActive: () -> Unit = {}): String {
        checkActive()
        val bitLength = input.size.toLong() * BYTE_BITS
        val paddedSize = ((input.size + INITIAL_PADDING_BYTES + BLOCK_BYTES - 1) / BLOCK_BYTES) * BLOCK_BYTES
        val data = ByteArray(paddedSize)
        input.copyInto(data); data[input.size] = 0x80.toByte()
        for (i in 0 until LENGTH_FIELD_BYTES) {
            data[paddedSize - 1 - i] = (bitLength ushr (BYTE_BITS * i)).toByte()
        }
        val h = initialHashes.copyOf()
        val w = IntArray(SCHEDULE_WORDS)
        for (offset in data.indices step BLOCK_BYTES) {
            if (offset % HASH_CANCELLATION_INTERVAL == 0) checkActive()
            for (i in 0 until MESSAGE_WORDS) {
                val p = offset + i * WORD_BYTES
                w[i] = ((data[p].toInt() and BYTE_MASK) shl WORD_HIGH_SHIFT) or
                    ((data[p + 1].toInt() and BYTE_MASK) shl WORD_MIDDLE_SHIFT) or
                    ((data[p + 2].toInt() and BYTE_MASK) shl BYTE_BITS) or
                    (data[p + LAST_WORD_BYTE_OFFSET].toInt() and BYTE_MASK)
            }
            for (i in MESSAGE_WORDS until SCHEDULE_WORDS) {
                val s0 = w[i - SCHEDULE_OFFSET_15].rotateRight(SMALL_SIGMA_0_ROTATION_A) xor
                    w[i - SCHEDULE_OFFSET_15].rotateRight(SMALL_SIGMA_0_ROTATION_B) xor
                    (w[i - SCHEDULE_OFFSET_15] ushr SMALL_SIGMA_0_SHIFT)
                val s1 = w[i - SCHEDULE_OFFSET_2].rotateRight(SMALL_SIGMA_1_ROTATION_A) xor
                    w[i - SCHEDULE_OFFSET_2].rotateRight(SMALL_SIGMA_1_ROTATION_B) xor
                    (w[i - SCHEDULE_OFFSET_2] ushr SMALL_SIGMA_1_SHIFT)
                w[i] = w[i - SCHEDULE_OFFSET_16] + s0 + w[i - SCHEDULE_OFFSET_7] + s1
            }
            var a = h[FIRST_HASH_INDEX]; var b = h[SECOND_HASH_INDEX]
            var c = h[THIRD_HASH_INDEX]; var d = h[FOURTH_HASH_INDEX]
            var e = h[FIFTH_HASH_INDEX]; var f = h[SIXTH_HASH_INDEX]
            var g = h[SEVENTH_HASH_INDEX]; var z = h[EIGHTH_HASH_INDEX]
            for (i in 0 until SCHEDULE_WORDS) {
                val s1 = e.rotateRight(LARGE_SIGMA_1_ROTATION_A) xor
                    e.rotateRight(LARGE_SIGMA_1_ROTATION_B) xor
                    e.rotateRight(LARGE_SIGMA_1_ROTATION_C)
                val choose = (e and f) xor (e.inv() and g)
                val t1 = z + s1 + choose + roundConstants[i] + w[i]
                val s0 = a.rotateRight(LARGE_SIGMA_0_ROTATION_A) xor
                    a.rotateRight(LARGE_SIGMA_0_ROTATION_B) xor
                    a.rotateRight(LARGE_SIGMA_0_ROTATION_C)
                val majority = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + majority
                z = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[FIRST_HASH_INDEX] += a; h[SECOND_HASH_INDEX] += b
            h[THIRD_HASH_INDEX] += c; h[FOURTH_HASH_INDEX] += d
            h[FIFTH_HASH_INDEX] += e; h[SIXTH_HASH_INDEX] += f
            h[SEVENTH_HASH_INDEX] += g; h[EIGHTH_HASH_INDEX] += z
        }
        return h.joinToString("") { it.toUInt().toString(HEX_RADIX).padStart(HEX_WORD_WIDTH, '0') }
    }

    private fun String.hexWords(): IntArray = split(',')
        .map { it.toUInt(HEX_RADIX).toInt() }
        .toIntArray()

    private const val HASH_CANCELLATION_INTERVAL = 4096
}
