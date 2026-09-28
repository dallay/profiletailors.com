package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.ReconnectReason
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
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

    @Test
    fun `refreshes Threads credentials through the th_refresh_token grant`() = runTest {
        val fixture = buildThreadsRefreshFixture(responseBody = SUCCESS_REFRESH_BODY)
        val token = fixture.resolver.resolve(fixture.account)

        assertEquals("new-access-token", token)
        assertEquals(
            "https://graph.threads.net/v1.0/refresh_access_token",
            fixture.transport.lastRequest?.uri()?.toString(),
        )
        val body = fixture.transport.lastRequestBody
        check(body != null)
        check(body.contains("grant_type=th_refresh_token"))
        check(body.contains("access_token=old-refresh-token"))
        check(!body.contains("client_id"))
        check(!body.contains("client_secret"))
        val stored = fixture.gateway.lastStored
        check(stored != null)
        assertEquals("new-access-token", stored.accessToken)
        assertEquals("new-refresh-token", stored.refreshToken)
    }

    @Test
    fun `threads refresh falls back to INVALID_GRANT on non-2xx response`() = runTest {
        val fixture = buildThreadsRefreshFixture(
            responseBody = """{"error":"invalid_grant"}""",
            statusCode = 401,
        )

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { fixture.resolver.resolve(fixture.account) }
        }
        assertEquals(ReconnectReason.INVALID_GRANT, exception.reason)
    }

    @Test
    fun `threads refresh falls back to INVALID_GRANT on malformed json response`() = runTest {
        val fixture = buildThreadsRefreshFixture(responseBody = "not-json")

        val exception = assertThrows(ReconnectRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { fixture.resolver.resolve(fixture.account) }
        }
        assertEquals(ReconnectReason.INVALID_GRANT, exception.reason)
    }

    private data class ThreadsRefreshFixture(
        val resolver: RefreshAwareCredentialResolverImpl,
        val gateway: FakeProviderCredentialGateway,
        val transport: RecordingHttpTransport,
        val account: SocialAccount,
    )

    private fun buildThreadsRefreshFixture(responseBody: String, statusCode: Int = 200): ThreadsRefreshFixture {
        val credentialId = UUID.randomUUID()
        val fixedNow = Instant.parse("2026-09-27T10:00:00Z")
        val gateway = FakeProviderCredentialGateway(
            credentialId,
            ProviderCredentials(
                provider = SocialProvider.THREADS,
                accessToken = "expired-access-token",
                refreshToken = "old-refresh-token",
                expiresAtEpochSeconds = fixedNow.epochSecond - 100,
                scope = "threads_basic threads_content_publish",
            ),
        )
        val account = threadsAccount()
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
        val transport = RecordingHttpTransport(
            response = LinkedInHttpResponse(
                statusCode = statusCode,
                headers = HttpHeaders.of(emptyMap()) { _, _ -> true },
                body = responseBody,
            ),
        )
        val resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = gateway,
            socialConnectionRepository = repository,
            httpTransport = transport,
            objectMapper = com.fasterxml.jackson.databind.ObjectMapper(),
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        )
        return ThreadsRefreshFixture(resolver, gateway, transport, account)
    }

    @Test
    fun `throws when social connection is not found`() = runTest {
        val resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = FakeProviderCredentialGateway(
                UUID.randomUUID(),
                ProviderCredentials(
                    provider = SocialProvider.THREADS,
                    accessToken = "token",
                    refreshToken = null,
                    expiresAtEpochSeconds = null,
                    scope = null,
                ),
            ),
            socialConnectionRepository = FakeSocialConnectionRepository(
                SocialConnection(
                    id = "missing",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.THREADS,
                    providerConnectionRef = "x",
                    status = SocialConnectionStatus.ACTIVE,
                    credentialReference = UUID.randomUUID().toString(),
                ),
            ),
            httpTransport = NoopHttpTransport,
            objectMapper = com.fasterxml.jackson.databind.ObjectMapper(),
            clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC),
        )

        val account = threadsAccount().copy(socialConnectionId = "does-not-exist")
        val exception = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(account) }
        }
        check(exception.message!!.contains("THREADS"))
    }

    @Test
    fun `throws when social connection is missing credentials`() = runTest {
        val resolver = RefreshAwareCredentialResolverImpl(
            credentialGateway = FakeProviderCredentialGateway(
                UUID.randomUUID(),
                ProviderCredentials(
                    provider = SocialProvider.THREADS,
                    accessToken = "token",
                    refreshToken = null,
                    expiresAtEpochSeconds = null,
                    scope = null,
                ),
            ),
            socialConnectionRepository = FakeSocialConnectionRepository(
                SocialConnection(
                    id = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.THREADS,
                    providerConnectionRef = "threads-user-1",
                    status = SocialConnectionStatus.ACTIVE,
                    credentialReference = null,
                ),
            ),
            httpTransport = NoopHttpTransport,
            objectMapper = com.fasterxml.jackson.databind.ObjectMapper(),
            clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(threadsAccount()) }
        }
        check(exception.message!!.contains("missing credentials"))
    }

    private fun threadsAccount() = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.THREADS,
        providerAccountId = "threads-account-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Threads User",
        status = SocialConnectionStatus.ACTIVE,
    )

    companion object {
        private const val SUCCESS_REFRESH_BODY =
            """{"access_token":"new-access-token","expires_in":3600,"refresh_token":"new-refresh-token"}"""
    }

    private class FakeProviderCredentialGateway(private val id: UUID, private var credentials: ProviderCredentials) :
        ProviderCredentialGateway {
        var lastStored: ProviderCredentials? = null
            private set

        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID {
            this.credentials = credentials
            lastStored = credentials
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

    private class RecordingHttpTransport(private val response: LinkedInHttpResponse) : LinkedInHttpTransport {
        var lastRequest: HttpRequest? = null
            private set
        var lastRequestBody: String? = null
            private set

        override suspend fun send(request: HttpRequest): LinkedInHttpResponse {
            lastRequest = request
            request.bodyPublisher().ifPresent { publisher ->
                val sink = java.io.ByteArrayOutputStream()
                val latch = java.util.concurrent.CountDownLatch(1)
                publisher.subscribe(object : java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
                    override fun onSubscribe(subscription: java.util.concurrent.Flow.Subscription) =
                        subscription.request(Long.MAX_VALUE)
                    override fun onNext(item: java.nio.ByteBuffer) {
                        val copy = item.duplicate()
                        val data = ByteArray(copy.remaining())
                        copy.get(data)
                        sink.write(data)
                    }
                    override fun onError(throwable: Throwable) = latch.countDown()
                    override fun onComplete() = latch.countDown()
                })
                check(latch.await(1, java.util.concurrent.TimeUnit.SECONDS))
                lastRequestBody = sink.toString(Charsets.UTF_8)
            }
            return response
        }
    }
}
