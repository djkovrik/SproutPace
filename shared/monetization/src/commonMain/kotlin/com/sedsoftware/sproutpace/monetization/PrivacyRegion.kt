package com.sedsoftware.sproutpace.monetization

import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_ENDPOINT
import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_MAX_AGE_MILLIS
import com.sedsoftware.sproutpace.data.ADVERTISING_PRIVACY_SCHEMA_VERSION
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readRemaining
import kotlinx.datetime.Instant
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PRIVACY_REGION_ENDPOINT = ADVERTISING_PRIVACY_ENDPOINT
const val PRIVACY_REGION_SCHEMA_VERSION = ADVERTISING_PRIVACY_SCHEMA_VERSION
const val PRIVACY_CACHE_MAX_AGE_MILLIS = ADVERTISING_PRIVACY_MAX_AGE_MILLIS
const val PRIVACY_RESPONSE_MAX_BYTES = 4_096L
const val PRIVACY_REQUEST_TIMEOUT_MILLIS = 5_000L

data class PrivacyRegion(
    val schemaVersion: Int,
    val consentRequired: Boolean,
    val policyVersion: String,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

fun interface EpochMillisClock {
    fun now(): Long
}

fun interface PrivacyRegionApi {
    suspend fun fetch(): Result<PrivacyRegion>
}

@Serializable
private data class PrivacyRegionWireResponse(
    val schemaVersion: Int,
    val consentRequired: Boolean,
    val policyVersion: String,
    val expiresAt: String,
)

class KtorPrivacyRegionApi(
    private val client: HttpClient,
    private val clock: EpochMillisClock,
    private val privacyClientHeader: String? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    },
) : PrivacyRegionApi {
    override suspend fun fetch(): Result<PrivacyRegion> = cancellableResult {
        val now = clock.now().also { require(it >= 0L) { "Clock must return Unix epoch milliseconds" } }
        val response = client.get(PRIVACY_REGION_ENDPOINT) {
            accept(ContentType.Application.Json)
            privacyClientHeader?.takeIf(String::isNotBlank)?.let { header("X-Privacy-Client", it) }
        }
        require(response.status == HttpStatusCode.OK) { "Privacy endpoint returned ${response.status.value}" }
        require(response.headers[HttpHeaders.ContentType]?.trim()?.lowercase() == "application/json") {
            "Privacy endpoint must return application/json"
        }
        response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { length ->
            require(length in 0L..PRIVACY_RESPONSE_MAX_BYTES) { "Privacy response exceeds 4 KiB" }
        }
        val packet = response.bodyAsChannel().readRemaining(PRIVACY_RESPONSE_MAX_BYTES + 1L)
        val bytes = packet.readByteArray()
        require(bytes.size.toLong() <= PRIVACY_RESPONSE_MAX_BYTES) { "Privacy response exceeds 4 KiB" }
        val payload = bytes.decodeToString(throwOnInvalidSequence = true)
        val wire = json.decodeFromString(PrivacyRegionWireResponse.serializer(), payload)
        require(wire.schemaVersion == PRIVACY_REGION_SCHEMA_VERSION) { "Unsupported privacy schema" }
        require(POLICY_VERSION.matches(wire.policyVersion)) { "Invalid policyVersion" }
        val expiresAt = Instant.parse(wire.expiresAt).toEpochMilliseconds()
        require(expiresAt > now) { "Privacy response is expired" }
        require(expiresAt - now <= PRIVACY_CACHE_MAX_AGE_MILLIS) { "Privacy response exceeds 72 hours" }
        PrivacyRegion(
            schemaVersion = wire.schemaVersion,
            consentRequired = wire.consentRequired,
            policyVersion = wire.policyVersion,
            fetchedAtEpochMillis = now,
            expiresAtEpochMillis = expiresAt,
        )
    }

    private companion object {
        val POLICY_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

fun io.ktor.client.HttpClientConfig<*>.configurePrivacyRegionClient() {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = PRIVACY_REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = PRIVACY_REQUEST_TIMEOUT_MILLIS
        socketTimeoutMillis = PRIVACY_REQUEST_TIMEOUT_MILLIS
    }
}
