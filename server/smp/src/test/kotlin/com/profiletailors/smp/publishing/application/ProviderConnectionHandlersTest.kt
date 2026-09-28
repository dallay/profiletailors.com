package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.OAuthAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.OAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderAuthorizationRegistry
import com.profiletailors.smp.publishing.domain.ProviderCatalogItem
import com.profiletailors.smp.publishing.domain.ProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderConnectionRegistry
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProviderConnectionHandlersTest {
    private val principalContext = PrincipalContext(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "principal-1",
    )
    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `initiate signs state and returns authorization url for configured threads provider`() = runTest {
        val signer = RecordingOAuthStateSigner()
        val handler = InitiateProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(
                    configured = true,
                    allowedRedirectUri = "https://app.example.com/cb",
                    authorizationUrl = "https://threads.example/authorize",
                ),
            ),
            clock = fixedClock,
        )

        val result = handler.handle(
            InitiateProviderConnectionCommand(
                provider = SocialProvider.THREADS,
                redirectUri = "https://app.example.com/cb",
            ),
        )

        assertEquals("https://threads.example/authorize?state=${result.state}", result.authorizationUrl)
        assertNotNull(result.state)
        assertEquals("workspace-1", signer.lastPayload?.workspaceId)
        assertEquals("principal-1", signer.lastPayload?.principalId)
        assertEquals(SocialProvider.THREADS, signer.lastPayload?.provider)
        assertEquals("https://app.example.com/cb", signer.lastPayload?.redirectUri)
        assertEquals(
            Instant.parse("2026-09-27T10:10:00Z"),
            result.expiresAt,
        )
    }

    @Test
    fun `initiate rejects unregistered provider`() = runTest {
        val handler = InitiateProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = RecordingOAuthStateSigner(),
            authorizationRegistry = ProviderAuthorizationRegistry.from(),
            clock = fixedClock,
        )

        assertThrows(ProviderNotConfiguredException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    InitiateProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        redirectUri = "https://app.example.com/cb",
                    ),
                )
            }
        }
    }

    @Test
    fun `initiate rejects provider that is not configured`() = runTest {
        val handler = InitiateProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = RecordingOAuthStateSigner(),
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = false),
            ),
            clock = fixedClock,
        )

        assertThrows(ProviderNotConfiguredException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    InitiateProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        redirectUri = "https://app.example.com/cb",
                    ),
                )
            }
        }
    }

    @Test
    fun `initiate rejects redirect uri outside the allow list`() = runTest {
        val handler = InitiateProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = RecordingOAuthStateSigner(),
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(
                    configured = true,
                    allowedRedirectUri = "https://app.example.com/cb",
                ),
            ),
            clock = fixedClock,
        )

        assertThrows(InvalidOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    InitiateProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        redirectUri = "https://attacker.example/cb",
                    ),
                )
            }
        }
    }

    @Test
    fun `initiate respects restrictive catalog policy`() = runTest {
        val handler = InitiateProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = RecordingOAuthStateSigner(),
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = true),
            ),
            clock = fixedClock,
            providerCatalogPolicy = denyingCatalogPolicy(),
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    InitiateProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        redirectUri = "https://app.example.com/cb",
                    ),
                )
            }
        }
    }

    @Test
    fun `complete persists connection and account and publishes channel event`() = runTest {
        val connectionRepo = RecordingConnectionRepository()
        val accountRepo = RecordingAccountRepository()
        val signer = RecordingOAuthStateSigner(payload = validStatePayload())
        val state = signer.sign(validStatePayload())
        val publisher = RecordingChannelEventPublisher()
        val handler = CompleteProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            connectionRegistry = ProviderConnectionRegistry.from(
                SocialProvider.THREADS to FakeSocialConnectionProvider(),
            ),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = true),
            ),
            socialConnectionRepository = connectionRepo,
            socialAccountRepository = accountRepo,
            channelEventPublisher = publisher,
            clock = fixedClock,
            transactionRunner = NoOpTransactionRunner,
        )

        val result = handler.handle(
            CompleteProviderConnectionCommand(
                provider = SocialProvider.THREADS,
                authorizationCode = "auth-code-1",
                redirectUri = "https://app.example.com/cb",
                state = state,
            ),
        )

        assertEquals(SocialProvider.THREADS, result.provider)
        assertEquals(SocialConnectionStatus.ACTIVE, result.status)
        assertEquals("workspace-1", result.workspaceId)
        assertNotNull(connectionRepo.lastSaved)
        assertNotNull(accountRepo.lastSaved)
        assertEquals("threads-account-1", result.account.providerAccountId)
        assertEquals(1, publisher.events.size)
        assertEquals("workspace-1", publisher.events.first().workspaceId)
    }

    @Test
    fun `complete rejects expired state payload`() = runTest {
        val signer = RecordingOAuthStateSigner(
            payload = validStatePayload(expiresAt = Instant.parse("2026-09-27T09:00:00Z")),
        )
        val state = signer.sign(signer.payload!!)
        val handler = CompleteProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            connectionRegistry = ProviderConnectionRegistry.from(
                SocialProvider.THREADS to FakeSocialConnectionProvider(),
            ),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = true),
            ),
            socialConnectionRepository = RecordingConnectionRepository(),
            socialAccountRepository = RecordingAccountRepository(),
            channelEventPublisher = RecordingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = NoOpTransactionRunner,
        )

        assertThrows(ExpiredOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://app.example.com/cb",
                        state = state,
                    ),
                )
            }
        }
    }

    @Test
    fun `complete rejects state payload from a different workspace`() = runTest {
        val signer = RecordingOAuthStateSigner(
            payload = validStatePayload(workspaceId = "workspace-2"),
        )
        val state = signer.sign(signer.payload!!)
        val handler = CompleteProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            connectionRegistry = ProviderConnectionRegistry.from(
                SocialProvider.THREADS to FakeSocialConnectionProvider(),
            ),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = true),
            ),
            socialConnectionRepository = RecordingConnectionRepository(),
            socialAccountRepository = RecordingAccountRepository(),
            channelEventPublisher = RecordingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = NoOpTransactionRunner,
        )

        assertThrows(InvalidOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://app.example.com/cb",
                        state = state,
                    ),
                )
            }
        }
    }

    @Test
    fun `complete rejects redirect uri that does not match the state payload`() = runTest {
        val signer = RecordingOAuthStateSigner(
            payload = validStatePayload(redirectUri = "https://app.example.com/cb"),
        )
        val state = signer.sign(signer.payload!!)
        val handler = CompleteProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            connectionRegistry = ProviderConnectionRegistry.from(
                SocialProvider.THREADS to FakeSocialConnectionProvider(),
            ),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = true),
            ),
            socialConnectionRepository = RecordingConnectionRepository(),
            socialAccountRepository = RecordingAccountRepository(),
            channelEventPublisher = RecordingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = NoOpTransactionRunner,
        )

        assertThrows(InvalidOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://app.example.com/different-cb",
                        state = state,
                    ),
                )
            }
        }
    }

    @Test
    fun `complete rejects provider that is not registered for connection`() = runTest {
        val signer = RecordingOAuthStateSigner(payload = validStatePayload())
        val state = signer.sign(signer.payload!!)
        val handler = CompleteProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            connectionRegistry = ProviderConnectionRegistry.from(),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(configured = true),
            ),
            socialConnectionRepository = RecordingConnectionRepository(),
            socialAccountRepository = RecordingAccountRepository(),
            channelEventPublisher = RecordingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = NoOpTransactionRunner,
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://app.example.com/cb",
                        state = state,
                    ),
                )
            }
        }
    }

    @Test
    fun `complete rejects redirect uri outside the registered allow list`() = runTest {
        val signer = RecordingOAuthStateSigner(
            payload = validStatePayload(redirectUri = "https://app.example.com/cb"),
        )
        val state = signer.sign(signer.payload!!)
        val handler = CompleteProviderConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            connectionRegistry = ProviderConnectionRegistry.from(
                SocialProvider.THREADS to FakeSocialConnectionProvider(),
            ),
            oauthStateSigner = signer,
            authorizationRegistry = ProviderAuthorizationRegistry.from(
                SocialProvider.THREADS to ConfigurableAuthorizationUrlBuilder(
                    configured = true,
                    allowedRedirectUri = "https://app.example.com/cb",
                ),
            ),
            socialConnectionRepository = RecordingConnectionRepository(),
            socialAccountRepository = RecordingAccountRepository(),
            channelEventPublisher = RecordingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = NoOpTransactionRunner,
        )

        assertThrows(InvalidOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteProviderConnectionCommand(
                        provider = SocialProvider.THREADS,
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://attacker.example/cb",
                        state = state,
                    ),
                )
            }
        }
    }

    private fun validStatePayload(
        workspaceId: String = "workspace-1",
        principalId: String = "principal-1",
        redirectUri: String = "https://app.example.com/cb",
        expiresAt: Instant = Instant.parse("2026-09-27T10:10:00Z"),
    ): OAuthStatePayload = OAuthStatePayload(
        provider = SocialProvider.THREADS,
        workspaceId = workspaceId,
        principalId = principalId,
        redirectUri = redirectUri,
        nonce = "nonce-1",
        issuedAt = Instant.parse("2026-09-27T10:00:00Z"),
        expiresAt = expiresAt,
    )

    private fun denyingCatalogPolicy(): ProviderCatalogPolicy = ProviderCatalogPolicy { _, _ ->
        ProviderCatalogItem(
            provider = SocialProvider.THREADS,
            accountKinds = emptySet(),
            state = com.profiletailors.smp.publishing.domain.ProviderCatalogState.LOCKED,
            reason = com.profiletailors.smp.publishing.domain.ProviderLockReason.NOT_ENTITLED,
            channelLimit = null,
            connectedChannelCount = 0,
            canConnectMore = false,
        )
    }

    private class FixedPrincipalContextProvider(private val principalContext: PrincipalContext) :
        PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(private val resourceContext: ResourceContext) :
        ResourceContextProvider {
        override fun current(): ResourceContext = resourceContext
    }

    private class RecordingOAuthStateSigner(val payload: OAuthStatePayload? = null) : OAuthStateSigner {
        var lastPayload: OAuthStatePayload? = null

        override fun sign(payload: OAuthStatePayload): String {
            lastPayload = payload
            return "signed-state"
        }

        override fun verify(state: String): OAuthStatePayload = payload ?: error("payload is required for verify")
    }

    private class ConfigurableAuthorizationUrlBuilder(
        private val configured: Boolean = true,
        private val allowedRedirectUri: String? = null,
        private val authorizationUrl: String = "https://provider.example/authorize",
    ) : OAuthAuthorizationUrlBuilder {
        override fun buildAuthorizationUrl(state: String, redirectUri: String): String =
            "$authorizationUrl?state=$state"

        override fun isConfigured(): Boolean = configured

        override fun isAllowedRedirectUri(redirectUri: String): Boolean =
            allowedRedirectUri == null || allowedRedirectUri == redirectUri
    }

    private class FakeSocialConnectionProvider : SocialConnectionProvider {
        var callCount = 0

        override suspend fun completeConnection(
            command: com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand,
        ): ProviderConnectionResult {
            callCount += 1
            return ProviderConnectionResult(
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-connection-${UUID.randomUUID()}",
                credentialReference = "threads-cred-${UUID.randomUUID()}",
                account = ProviderAccountProfile(
                    providerAccountId = "threads-account-1",
                    displayName = "Threads Profile",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    profileUrn = null,
                ),
            )
        }
    }

    private class RecordingConnectionRepository : SocialConnectionRepository {
        var lastSaved: SocialConnection? = null

        override suspend fun upsert(connection: SocialConnection): SocialConnection {
            lastSaved = connection
            return connection
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? = null
        override suspend fun deleteByWorkspaceAndId(workspaceId: String, connectionId: String) = Unit
    }

    private class RecordingAccountRepository : SocialAccountRepository {
        var lastSaved: SocialAccount? = null

        override suspend fun upsert(account: SocialAccount): SocialAccount {
            lastSaved = account
            return account
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? = null
        override suspend fun findFirstActiveByWorkspace(workspaceId: String): SocialAccount? = null
        override suspend fun listActiveByWorkspace(workspaceId: String): List<SocialAccount> = emptyList()
        override suspend fun deleteByConnectionId(connectionId: String) = Unit
    }

    private class RecordingChannelEventPublisher : ChannelEventPublisher {
        val events = mutableListOf<ChannelEvent>()
        override fun publish(event: ChannelEvent) {
            events += event
        }
    }

    private object NoOpTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
}
