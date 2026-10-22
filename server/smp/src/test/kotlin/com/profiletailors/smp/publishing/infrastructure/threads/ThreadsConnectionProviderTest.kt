package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentials
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ThreadsConnectionProviderTest {
    private val properties = ThreadsPublishingProperties(
        enabled = true,
        clientId = "client",
        clientSecret = "secret",
        redirectUri = "https://app.example.com/callback",
    )
    private val clock = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `exchanges short token then long token and stores mapped profile`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(
                200,
                HttpHeaders.of(emptyMap()) { _, _ ->
                    true
                },
                """{"access_token":"short","user_id":"user-1"}""",
            ),
            LinkedInHttpResponse(
                200,
                HttpHeaders.of(emptyMap()) { _, _ ->
                    true
                },
                """{"access_token":"long","expires_in":86400}""",
            ),
            LinkedInHttpResponse(
                200,
                HttpHeaders.of(emptyMap()) { _, _ ->
                    true
                },
                """{"id":"user-1","username":"threads-user","name":"Threads User"}""",
            ),
        )
        val credentials = RecordingCredentialGateway()
        val result = ThreadsConnectionProvider(
            properties,
            ObjectMapper().findAndRegisterModules(),
            transport,
            credentials,
            clock,
        )
            .completeConnection(
                CompleteProviderConnectionCommand(
                    "workspace-1",
                    "principal-1",
                    "authorization-code",
                    properties.redirectUri,
                ),
            )

        transport.requests[0].uri().host shouldBe "graph.threads.com"
        transport.requests[0].uri().path shouldBe "/oauth/access_token"
        transport.requests[1].uri().host shouldBe "graph.threads.net"
        transport.requests[1].uri().path shouldBe "/access_token"
        result.provider shouldBe SocialProvider.THREADS
        result.account.providerAccountId shouldBe "user-1"
        result.account.displayName shouldBe "Threads User"
        credentials.saved.accessToken shouldBe "long"
        credentials.saved.refreshToken shouldBe "long"
        transport.requests.size shouldBe 3
        transport.requests[0].uri().toString() shouldBe "https://graph.threads.com/oauth/access_token"
    }

    @Test
    fun `sanitizes provider failure without returning raw response`() = runTest {
        val body = "token=super-secret&error=denied"
        val transport = RecordingTransport(
            LinkedInHttpResponse(400, HttpHeaders.of(emptyMap()) { _, _ -> true }, body),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                ThreadsConnectionProvider(
                    properties,
                    ObjectMapper().findAndRegisterModules(),
                    transport,
                    RecordingCredentialGateway(),
                    clock,
                )
                    .completeConnection(
                        CompleteProviderConnectionCommand(
                            "workspace-1",
                            "principal-1",
                            "authorization-code",
                            properties.redirectUri,
                        ),
                    )
            }
        }

        exception.message shouldBe "Threads token exchange failed."
        exception.message?.contains("super-secret") shouldBe false
    }

    private class RecordingTransport(private vararg val responses: LinkedInHttpResponse) : LinkedInHttpTransport {
        val requests = mutableListOf<HttpRequest>()
        private var index = 0
        override suspend fun send(request: HttpRequest): LinkedInHttpResponse {
            requests += request
            return responses[index++]
        }
    }

    private class RecordingCredentialGateway : ProviderCredentialGateway {
        lateinit var saved: ProviderCredentials
        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID {
            saved = credentials
            return UUID.randomUUID()
        }
        override suspend fun resolveCredential(id: UUID): ProviderCredentials = saved
        override suspend fun invalidateCredential(id: UUID) = Unit
    }
}
