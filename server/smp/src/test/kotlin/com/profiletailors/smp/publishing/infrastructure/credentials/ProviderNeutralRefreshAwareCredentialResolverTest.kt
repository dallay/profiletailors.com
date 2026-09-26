package com.profiletailors.smp.publishing.infrastructure.credentials

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.http.ProviderHttpResponse
import com.profiletailors.smp.publishing.infrastructure.http.ProviderHttpTransport
import com.profiletailors.smp.publishing.infrastructure.threads.ThreadsPublishingProperties
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProviderNeutralRefreshAwareCredentialResolverTest {
    @Test
    fun `resolves Threads credentials through the provider gateway`() = runTest {
        val credentialId = UUID.randomUUID()
        val gateway = FakeProviderCredentialGateway(
            credentialId,
            ProviderCredentials(
                provider = SocialProvider.THREADS,
                accessToken = "threads-access-token",
                refreshToken = null,
                expiresAtEpochSeconds = Instant.parse("2030-01-01T00:00:00Z").epochSecond,
                scope = "threads_basic threads_content_publish",
            ),
        )
        val account = SocialAccount(
            id = "account-1",
            socialConnectionId = "connection-1",
            workspaceId = "workspace-1",
            provider = SocialProvider.THREADS,
            providerAccountId = "threads-account-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Threads User",
            status = SocialConnectionStatus.ACTIVE,
        )
        val repository = FakeSocialConnectionRepository(
            SocialConnection(
                id = account.socialConnectionId,
                workspaceId = account.workspaceId,
                provider = account.provider,
                providerConnectionRef = "threads-user-1",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = credentialId.toString(),
            ),
        )
        val resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = gateway,
            socialConnectionRepository = repository,
            httpTransport = NoopHttpTransport,
            objectMapper = com.fasterxml.jackson.databind.ObjectMapper(),
            clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals("threads-access-token", resolver.resolve(account))
    }

    @Test
    fun `should refresh Threads with its access token when inside the configured refresh window`() = runTest {
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val id = UUID.randomUUID()
        val gateway = FakeProviderCredentialGateway(
            id,
            ProviderCredentials(
                SocialProvider.THREADS,
                "long-lived-token",
                null,
                now.plusSeconds(1800).epochSecond,
                scope = "threads_basic",
            ),
        )
        val account = SocialAccount(
            "account", "connection", "workspace", SocialProvider.THREADS, "threads-user",
            SocialAccountKind.PERSONAL_PROFILE, "User", status = SocialConnectionStatus.ACTIVE,
        )
        val repository = mockk<SocialConnectionRepository>()
        coEvery { repository.findByWorkspaceAndId("workspace", "connection") } returns SocialConnection(
            "connection", "workspace", SocialProvider.THREADS, "threads-user", SocialConnectionStatus.ACTIVE,
            id.toString(),
        )
        val requests = mutableListOf<HttpRequest>()
        val resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = gateway,
            socialConnectionRepository = repository,
            httpTransport = ProviderHttpTransport { request ->
                requests += request
                ProviderHttpResponse(200, HttpHeaders.of(emptyMap()) { _, _ -> true },
                    """{"access_token":"renewed-token","expires_in":5184000}""")
            },
            objectMapper = ObjectMapper(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            threadsProperties = ThreadsPublishingProperties(
                apiBaseUrl = "https://threads.example.test",
                refreshAhead = Duration.ofHours(1),
            ),
        )

        resolver.resolve(account) shouldBe "renewed-token"
        requests.single().method() shouldBe "GET"
        requests.single().uri().toString() shouldBe
            "https://threads.example.test/refresh_access_token?grant_type=th_refresh_token&access_token=long-lived-token"
        gateway.resolveCredential(id).refreshToken shouldBe null
        resolver.resolve(account) shouldBe "renewed-token"
        requests.size shouldBe 1
    }

    private class FakeProviderCredentialGateway(private val id: UUID, private var credentials: ProviderCredentials) :
        ProviderCredentialGateway {
        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID {
            this.credentials = credentials
            return id
        }

        override suspend fun resolveCredential(id: UUID): ProviderCredentials {
            check(id == this.id)
            return credentials
        }

        override suspend fun invalidateCredential(id: UUID) = Unit
    }

    private class FakeSocialConnectionRepository(private val connection: SocialConnection) :
        SocialConnectionRepository {
        override suspend fun existsByCredentialReference(credentialReference: String): Boolean = false

        override suspend fun upsert(connection: SocialConnection): SocialConnection = connection

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            connection.takeIf { it.workspaceId == workspaceId && it.id == connectionId }

        override suspend fun deleteByWorkspaceAndId(workspaceId: String, connectionId: String) = Unit
    }

    private object NoopHttpTransport : ProviderHttpTransport {
        override suspend fun send(request: HttpRequest): ProviderHttpResponse = error("No refresh request expected")
    }
}
