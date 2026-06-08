package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentials
import com.profiletailors.smp.publishing.infrastructure.scheduling.RetryablePublishingException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.util.UUID

class LinkedInPublishingAdaptersTest {

    private val properties = LinkedInPublishingProperties(
        mode = "real",
        clientId = "client-id",
        clientSecret = "client-secret",
        redirectUri = "https://app.example.com/callback",
        scopes = "openid profile email w_member_social",
        apiBaseUrl = "https://api.linkedin.com",
        authorizationBaseUrl = "https://www.linkedin.com/oauth/v2/authorization",
        tokenBaseUrl = "https://www.linkedin.com/oauth/v2/accessToken",
        apiVersion = "202601",
    )
    private val objectMapper = ObjectMapper()

    @Test
    fun `real connection provider exchanges token and resolves profile`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000,"scope":"openid profile email w_member_social"}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"abcd1234","name":"Yuniel Acosta","email":"yuniel@example.com"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val provider = RealLinkedInConnectionProvider(properties, objectMapper, transport, credentialGateway)

        val result = provider.completeConnection(
            CompleteProviderConnectionCommand(
                workspaceId = "workspace-1",
                actorPrincipalId = "principal-1",
                authorizationCode = "auth-code-1",
                redirectUri = "https://app.example.com/callback",
            ),
        )

        assertEquals(SocialProvider.LINKEDIN, result.provider)
        assertEquals("linkedin-member-abcd1234", result.providerConnectionRef)
        assertEquals("abcd1234", result.account.providerAccountId)
        assertEquals("urn:li:person:abcd1234", result.account.profileUrn)
        // Verify credentials were stored with derived UUID
        val expectedUuid = UUID.nameUUIDFromBytes("linkedin:abcd1234".toByteArray())
        assertEquals(expectedUuid.toString(), result.credentialReference)
    }

    @Test
    fun `real publisher builds article post and publishes with resolved token`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    201,
                    headersOf("x-restli-id" to "post-123"),
                    """{"id":"post-123"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        // Use derived UUID for LinkedIn account "abcd1234"
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, null))
        val publisher = RealLinkedInPublisher(properties, objectMapper, transport, credentialGateway)
        val account = SocialAccount(
            id = "account-1",
            socialConnectionId = "connection-1",
            workspaceId = "workspace-1",
            provider = SocialProvider.LINKEDIN,
            providerAccountId = accountId,
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Yuniel",
            profileUrn = "urn:li:person:$accountId",
            status = SocialConnectionStatus.ACTIVE,
        )
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Check this out https://example.com/article",
            title = "Article title",
        )

        val result = publisher.publish(
            ProviderPublishCommand(
                publicationId = "pub-1",
                workspaceId = "workspace-1",
                socialAccount = account,
                publication = publication,
                assets = emptyList(),
            ),
        )

        assertEquals("post-123", result.externalPublicationId)
    }

    @Test
    fun `real connection provider maps token exchange failure`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    400,
                    emptyHeaders(),
                    "invalid_request",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val provider = RealLinkedInConnectionProvider(properties, objectMapper, transport, credentialGateway)

        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                provider.completeConnection(
                    CompleteProviderConnectionCommand(
                        workspaceId = "workspace-1",
                        actorPrincipalId = "principal-1",
                        authorizationCode = "bad-code",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            }
        }

        assertEquals(true, error.message!!.contains("token exchange failed"))
    }

    private class StubTransport(
        private val responses: List<LinkedInHttpResponse>,
    ) : LinkedInHttpTransport {
        private var index = 0
        override suspend fun send(request: java.net.http.HttpRequest): LinkedInHttpResponse =
            responses.getOrElse(index++) {
                throw RetryablePublishingException("No stub response configured")
            }
    }

    private class FakeCredentialGateway : LinkedInCredentialGateway {
        private val store = mutableMapOf<UUID, LinkedInCredentials>()

        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: LinkedInCredentials): UUID {
            store[ownerId] = credentials
            return ownerId
        }

        override suspend fun resolveCredential(id: UUID): LinkedInCredentials {
            return store[id] ?: throw IllegalStateException("No credentials found for $id")
        }

        fun store(id: UUID, credentials: LinkedInCredentials) {
            store[id] = credentials
        }
    }

    private fun emptyHeaders(): HttpHeaders =
        HttpHeaders.of(emptyMap()) { _, _ -> true }

    private fun headersOf(vararg pairs: Pair<String, String>): HttpHeaders =
        HttpHeaders.of(pairs.groupBy({ it.first }, { it.second })) { _, _ -> true }
}
