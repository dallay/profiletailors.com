package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.ProviderTransportUncertaintyException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.net.http.HttpRequest

class LinkedInAvatarFetcherImplTest {

    private val properties = LinkedInPublishingProperties(
        clientId = "client-id",
        clientSecret = "client-secret",
        redirectUri = "https://app.example.com/callback",
        scopes = "openid profile email",
        apiBaseUrl = "https://api.linkedin.com",
        authorizationBaseUrl = "https://www.linkedin.com/oauth/v2/authorization",
        tokenBaseUrl = "https://www.linkedin.com/oauth/v2/accessToken",
        apiVersion = "202601",
    )
    private val objectMapper = ObjectMapper()

    @Test
    fun `returns picture url from userinfo response`() = runTest {
        val transport = StubTransport(
            listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"abcd1234","picture":"https://media.licdn.com/fresh.jpg"}""",
                ),
            ),
        )
        val fetcher = LinkedInAvatarFetcherImpl(properties, objectMapper, transport)

        assertEquals("https://media.licdn.com/fresh.jpg", fetcher.fetchAvatarUrl("access-123"))
    }

    @Test
    fun `returns null when userinfo has no picture`() = runTest {
        val transport = StubTransport(
            listOf(
                LinkedInHttpResponse(200, emptyHeaders(), """{"sub":"abcd1234","name":"Yuniel"}"""),
            ),
        )
        val fetcher = LinkedInAvatarFetcherImpl(properties, objectMapper, transport)

        assertNull(fetcher.fetchAvatarUrl("access-123"))
    }

    @Test
    fun `returns null when userinfo lookup fails`() = runTest {
        val transport = StubTransport(
            listOf(LinkedInHttpResponse(401, emptyHeaders(), """{"message":"unauthorized"}""")),
        )
        val fetcher = LinkedInAvatarFetcherImpl(properties, objectMapper, transport)

        assertNull(fetcher.fetchAvatarUrl("stale-token"))
    }

    @Test
    fun `returns null when transport is uncertain`() = runTest {
        val transport = LinkedInHttpTransport {
            throw ProviderTransportUncertaintyException(java.io.IOException("timeout"))
        }
        val fetcher = LinkedInAvatarFetcherImpl(properties, objectMapper, transport)

        assertNull(fetcher.fetchAvatarUrl("access-123"))
    }

    @Test
    fun `requests userinfo with bearer token`() = runTest {
        val transport = CapturingTransport(
            LinkedInHttpResponse(
                200,
                emptyHeaders(),
                """{"sub":"abcd1234","picture":"https://media.licdn.com/fresh.jpg"}""",
            ),
        )
        val fetcher = LinkedInAvatarFetcherImpl(properties, objectMapper, transport)

        fetcher.fetchAvatarUrl("access-123")

        assertEquals("https://api.linkedin.com/v2/userinfo", transport.lastRequest?.uri().toString())
        assertEquals(
            "Bearer access-123",
            transport.lastRequest?.headers()?.firstValue("Authorization")?.orElse(null),
        )
    }

    private class StubTransport(private val responses: List<LinkedInHttpResponse>) : LinkedInHttpTransport {
        private var index = 0
        override suspend fun send(request: HttpRequest): LinkedInHttpResponse = responses.getOrElse(index++) {
            throw IllegalStateException("No stub response configured")
        }
    }

    private class CapturingTransport(private val response: LinkedInHttpResponse) : LinkedInHttpTransport {
        var lastRequest: HttpRequest? = null
        override suspend fun send(request: HttpRequest): LinkedInHttpResponse {
            lastRequest = request
            return response
        }
    }

    private fun emptyHeaders(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }
}
