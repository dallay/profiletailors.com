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
            httpTransport = ControllableFakeLinkedInHttpTransport(
                throwException = UnsupportedOperationException("Stub transport — no refresh response configured"),
            ),
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

    // --- executeRefresh tests ---

    @Test
    fun `executeRefresh returns new access token on success`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "old-token",
            refreshToken = "refresh-token-123",
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

        val fakeTransport = ControllableFakeLinkedInHttpTransport(
            response = com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse(
                statusCode = 200,
                headers = java.net.http.HttpHeaders.of(emptyMap()) { _, _ -> true },
                body = """{"access_token":"new-access-token","expires_in":3600,"refresh_token":"new-refresh-token"}""",
            ),
        )
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = fakeTransport,
            objectMapper = objectMapper,
            clock = clock,
        )

        val result = resolver.resolve(account)

        assertEquals("new-access-token", result)
        val stored = fakeCredentialGateway.resolveCredential(connectionId)
        assertEquals("new-access-token", stored.accessToken)
        assertEquals("new-refresh-token", stored.refreshToken)
        assertEquals("SUCCESS", stored.lastRefreshStatus)
    }

    @Test
    fun `executeRefresh returns null on non-2xx causing ReconnectRequiredException`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "old-token",
            refreshToken = "refresh-token-123",
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

        val fakeTransport = ControllableFakeLinkedInHttpTransport(
            response = com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse(
                statusCode = 401,
                headers = java.net.http.HttpHeaders.of(emptyMap()) { _, _ -> true },
                body = """{"error":"invalid_client"}""",
            ),
        )
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = fakeTransport,
            objectMapper = objectMapper,
            clock = clock,
        )

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        assertEquals(ReconnectReason.INVALID_GRANT, exception.reason)
    }

    @Test
    fun `executeRefresh treats HTTP timeout as null response causing INVALID_GRANT`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "old-token",
            refreshToken = "refresh-token-123",
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

        val fakeTransport = ControllableFakeLinkedInHttpTransport(
            throwException = java.net.http.HttpTimeoutException("Request timed out"),
        )
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = fakeTransport,
            objectMapper = objectMapper,
            clock = clock,
        )

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        assertEquals(ReconnectReason.INVALID_GRANT, exception.reason)
    }

    @Test
    fun `executeRefresh treats IOException as null response causing INVALID_GRANT`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "old-token",
            refreshToken = "refresh-token-123",
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

        val fakeTransport = ControllableFakeLinkedInHttpTransport(
            throwException = java.io.IOException("Connection refused"),
        )
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = fakeTransport,
            objectMapper = objectMapper,
            clock = clock,
        )

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        assertEquals(ReconnectReason.INVALID_GRANT, exception.reason)
    }

    @Test
    fun `executeRefresh treats unexpected exception as null response causing INVALID_GRANT`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "old-token",
            refreshToken = "refresh-token-123",
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

        val fakeTransport = ControllableFakeLinkedInHttpTransport(
            throwException = IllegalStateException("Unexpected failure"),
        )
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = fakeTransport,
            objectMapper = objectMapper,
            clock = clock,
        )

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        assertEquals(ReconnectReason.INVALID_GRANT, exception.reason)
    }

    @Test
    fun `blank refresh token treated as null and throws REFRESH_UNAVAILABLE`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "expired-token",
            refreshToken = "   ",
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
    fun `executeRefresh persists credentials with old refresh token when response omits new refresh_token`() = runTest {
        val account = testAccount()
        val connectionId = UUID.randomUUID()
        val expiredCredentials = LinkedInCredentials(
            accessToken = "old-token",
            refreshToken = "original-refresh-token",
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

        val fakeTransport = ControllableFakeLinkedInHttpTransport(
            response = com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse(
                statusCode = 200,
                headers = java.net.http.HttpHeaders.of(emptyMap()) { _, _ -> true },
                body = """{"access_token":"refreshed-token","expires_in":7200}""",
            ),
        )
        resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = fakeCredentialGateway,
            socialConnectionRepository = fakeConnectionRepository,
            properties = properties,
            httpTransport = fakeTransport,
            objectMapper = objectMapper,
            clock = clock,
        )

        val result = resolver.resolve(account)

        assertEquals("refreshed-token", result)
        val stored = fakeCredentialGateway.resolveCredential(connectionId)
        assertEquals("original-refresh-token", stored.refreshToken)
        assertEquals("SUCCESS", stored.lastRefreshStatus)
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

        override suspend fun resolveCredential(id: UUID): LinkedInCredentials =
            store[id] ?: throw IllegalStateException("No credentials for $id")
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

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            connections["$workspaceId:$connectionId"]
    }

    private class ControllableFakeLinkedInHttpTransport(
        private val response: com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse? = null,
        private val throwException: Exception? = null,
    ) : com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport {
        var lastRequest: java.net.http.HttpRequest? = null
            private set

        override suspend fun send(
            request: java.net.http.HttpRequest,
        ): com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse {
            lastRequest = request
            if (throwException != null) throw throwException
            return response!!
        }
    }
}
