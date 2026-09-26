package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpResponse
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.http.HttpRequest
import java.time.Clock
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
        override suspend fun upsert(connection: SocialConnection): SocialConnection = connection

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            connection.takeIf { it.workspaceId == workspaceId && it.id == connectionId }

        override suspend fun deleteByWorkspaceAndId(workspaceId: String, connectionId: String) = Unit
    }

    private object NoopHttpTransport : LinkedInHttpTransport {
        override suspend fun send(request: HttpRequest): LinkedInHttpResponse = error("No refresh request expected")
    }
}
