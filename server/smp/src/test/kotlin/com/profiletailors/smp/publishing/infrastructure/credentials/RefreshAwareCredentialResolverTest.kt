package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.ReconnectReason
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingProperties
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RefreshAwareCredentialResolverTest {

    private val fakeCredentialGateway = FakeCredentialGateway()
    private val fakeConnectionRepository = FakeConnectionRepository()
    private val properties = LinkedInPublishingProperties(
        clientId = "test-client-id",
        clientSecret = "test-client-secret",
        redirectUri = "https://example.com/callback",
        scopes = "w_member_social",
        apiBaseUrl = "https://api.linkedin.com",
        authorizationBaseUrl = "https://www.linkedin.com/oauth/v2/authorization",
        tokenBaseUrl = "https://www.linkedin.com/oauth/v2/accessToken",
        apiVersion = "202601",
    )
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()

    private val fixedNow = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private lateinit var resolver: RefreshAwareCredentialResolverImpl

    @BeforeEach
    fun setup() {
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = StubLinkedInHttpTransport(),
            objectMapper = objectMapper,
            clock = clock,
        )
    }

    private fun testAccount(
        workspaceId: String = "ws-1",
        socialConnectionId: String = "conn-1",
        id: String = "acc-1",
    ) = SocialAccount(
        id = id,
        socialConnectionId = socialConnectionId,
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "linkedin-123",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Test User",
        profileUrn = "urn:li:person:123",
        status = SocialConnectionStatus.ACTIVE,
    )

    @Test
    fun `returns access token when token is not expired`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val futureExpiry = fixedNow.epochSecond + 3600 // 1 hour from now
        val credentials = LinkedInCredentials(
            accessToken = "valid-token",
            refreshToken = "refresh-token",
            expiresAtEpochSeconds = futureExpiry,
            scope = "w_member_social",
        )

        fakeConnectionRepository.addConnection(
            SocialConnection(
                id = "conn-1",
                workspaceId = "ws-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-member-123",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = connectionId.toString(),
            ),
        )
        fakeCredentialGateway.store("linkedin:user", connectionId, credentials)

        val result = resolver.resolve(account)

        assertEquals("valid-token", result)
    }

    @Test
    fun `throws ReconnectRequiredException when no refresh token available`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "expired-token",
            refreshToken = null,
            expiresAtEpochSeconds = fixedNow.epochSecond - 100,
            scope = "w_member_social",
        )

        fakeConnectionRepository.addConnection(
            SocialConnection(
                id = "conn-1",
                workspaceId = "ws-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-member-123",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = connectionId.toString(),
            ),
        )
        fakeCredentialGateway.store("linkedin:user", connectionId, expiredCredentials)

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        assertEquals(ReconnectReason.REFRESH_UNAVAILABLE, exception.reason)
    }

    @Test
    fun `throws ReconnectRequiredException when refresh token expired`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredRefreshCredentials = LinkedInCredentials(
            accessToken = "expired-token",
            refreshToken = "expired-refresh",
            expiresAtEpochSeconds = fixedNow.epochSecond - 100,
            refreshTokenExpiresAtEpochSeconds = fixedNow.epochSecond - 50,
            scope = "w_member_social",
        )

        fakeConnectionRepository.addConnection(
            SocialConnection(
                id = "conn-1",
                workspaceId = "ws-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-member-123",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = connectionId.toString(),
            ),
        )
        fakeCredentialGateway.store("linkedin:user", connectionId, expiredRefreshCredentials)

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        assertEquals(ReconnectReason.REFRESH_TOKEN_EXPIRED, exception.reason)
    }

    @Test
    fun `returns token when access token is valid and refresh ahead window not reached`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val validCredentials = LinkedInCredentials(
            accessToken = "still-valid-token",
            refreshToken = "refresh-token",
            expiresAtEpochSeconds = fixedNow.epochSecond + 600, // 10 min from now
            scope = "w_member_social",
        )

        fakeConnectionRepository.addConnection(
            SocialConnection(
                id = "conn-1",
                workspaceId = "ws-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-member-123",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = connectionId.toString(),
            ),
        )
        fakeCredentialGateway.store("linkedin:user", connectionId, validCredentials)

        val result = resolver.resolve(account)

        assertEquals("still-valid-token", result)
    }

    // --- Test doubles ---

    private class FakeCredentialGateway : LinkedInCredentialGateway {
        private val store = mutableMapOf<UUID, LinkedInCredentials>()

        fun store(ownerType: String, ownerId: UUID, credentials: LinkedInCredentials) {
            store[ownerId] = credentials
        }

        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: LinkedInCredentials): UUID {
            store[ownerId] = credentials
            return ownerId
        }

        override suspend fun resolveCredential(id: UUID): LinkedInCredentials {
            return store[id] ?: throw IllegalStateException("No credentials for $id")
        }
    }

    private class FakeConnectionRepository : SocialConnectionRepository {
        private val connections = mutableMapOf<String, SocialConnection>()

        fun addConnection(connection: SocialConnection) {
            connections["${connection.workspaceId}:${connection.id}"] = connection
        }

        override suspend fun upsert(connection: SocialConnection): SocialConnection {
            connections["${connection.workspaceId}:${connection.id}"] = connection
            return connection
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? {
            return connections["$workspaceId:$connectionId"]
        }
    }

    private class StubLinkedInHttpTransport : com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport {
        override suspend fun send(request: java.net.http.HttpRequest): com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse {
            throw UnsupportedOperationException("Stub transport — refresh not tested in this suite")
        }
    }
}
