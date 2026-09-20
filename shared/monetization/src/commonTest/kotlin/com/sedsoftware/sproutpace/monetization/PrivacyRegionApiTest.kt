package com.sedsoftware.sproutpace.monetization

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyRegionApiTest {
    private val now = 1_800_000_000_000L

    @Test
    fun acceptsOnlyStrictFreshSchemaOneResponse() = runTest {
        var requests = 0
        val client = client { request ->
            requests += 1
            assertEquals(PRIVACY_REGION_ENDPOINT, request.url.toString())
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            assertEquals("com.sedsoftware.sproutpace/1.0.0", request.headers["X-Privacy-Client"])
            respond(
                content = ByteReadChannel(response(expiresAt = "2027-01-17T09:00:00Z")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = KtorPrivacyRegionApi(
            client,
            EpochMillisClock { now },
            "com.sedsoftware.sproutpace/1.0.0",
        ).fetch().getOrThrow()

        assertEquals(1, requests)
        assertTrue(result.consentRequired)
        assertEquals("2026-08-privacy-region-v1", result.policyVersion)
        assertEquals(now, result.fetchedAtEpochMillis)
    }

    @Test
    fun rejectsNon200WrongContentTypeUnknownFieldsAndUnknownSchema() = runTest {
        val cases = listOf(
            client { respondError(HttpStatusCode.TooManyRequests) },
            client { respond(ByteReadChannel(response()), headers = headersOf(HttpHeaders.ContentType, "text/plain")) },
            client {
                respond(
                    ByteReadChannel(response().dropLast(1) + ",\"country\":\"RU\"}"),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            client {
                respond(
                    ByteReadChannel(response(schemaVersion = 2)),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        cases.forEach { httpClient ->
            assertTrue(KtorPrivacyRegionApi(httpClient, EpochMillisClock { now }).fetch().isFailure)
        }
    }

    @Test
    fun rejectsMalformedOversizedExpiredAndOverlongResponses() = runTest {
        val cases = listOf(
            "{not-json",
            " ".repeat(PRIVACY_RESPONSE_MAX_BYTES.toInt() + 1),
            response(expiresAt = "2026-01-01T00:00:00Z"),
            response(expiresAt = "2027-01-20T10:00:00Z"),
        )
        cases.forEach { body ->
            val api = KtorPrivacyRegionApi(
                client {
                    respond(
                        ByteReadChannel(body),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                EpochMillisClock { now },
            )
            assertTrue(api.fetch().isFailure)
        }
    }

    @Test
    fun redirectIsNotFollowedAndFailsClosed() = runTest {
        val api = KtorPrivacyRegionApi(
            client {
                respond(
                    "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://example.invalid/redirect"),
                )
            },
            EpochMillisClock { now },
        )
        assertFalse(api.fetch().isSuccess)
    }

    @Test
    fun invalidUtf8AndTransportExceptionsFailClosed() = runTest {
        val invalidUtf8 = KtorPrivacyRegionApi(
            client {
                respond(
                    byteArrayOf(0xC3.toByte(), 0x28),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            EpochMillisClock { now },
        )
        val transportFailure = KtorPrivacyRegionApi(
            client { throw IllegalStateException("offline") },
            EpochMillisClock { now },
        )

        assertTrue(invalidUtf8.fetch().isFailure)
        assertTrue(transportFailure.fetch().isFailure)
    }

    private fun client(handler: MockRequestHandler) =
        HttpClient(MockEngine(handler)) { configurePrivacyRegionClient() }

    private fun response(
        schemaVersion: Int = 1,
        expiresAt: String = "2027-01-17T09:00:00Z",
    ) = """{"schemaVersion":$schemaVersion,"consentRequired":true,"policyVersion":"2026-08-privacy-region-v1","expiresAt":"$expiresAt"}"""
}
