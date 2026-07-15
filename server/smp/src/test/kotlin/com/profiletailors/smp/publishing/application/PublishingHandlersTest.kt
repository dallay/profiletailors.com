package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.emailVerificationPolicyOf
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.publishing.domain.ActivityDensity
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannel
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.DateCount
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.ScheduleMode
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PublishingHandlersTest {

    private val principalContext = PrincipalContext(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "local:owner@example.com",
    )
    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC)

    /** Returns emailStatus = VERIFIED for all lookups so the email-verification gate passes. */
    private val verifiedPrincipalIdentityLookup = VerifiedPrincipalIdentityLookup()

    /** Always returns false — email verification gate is disabled in unit tests. */
    private val noOpEmailVerificationPolicy = permissiveEmailVerificationPolicy

    /** Always returns true — email verification gate is enforced. */
    private val strictEmailVerificationPolicy: EmailVerificationPolicy = emailVerificationPolicyOf()

    private fun recordingTransactionRunner(): RecordingAtomicTransactionRunner =
        RecordingAtomicTransactionRunner(mutableListOf())

    private class RecordingAtomicTransactionRunner(private val order: MutableList<String>) : AtomicTransactionRunner {
        var invocations: Int = 0

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            invocations += 1
            order += "tx:start"
            val result = block()
            order += "tx:commit"
            return result
        }
    }

    private fun assertPersistedResultAndReplacementJob(
        persisted: PublicationDraft,
        result: PublicationResult,
        jobRepository: InMemoryPublicationJobRepository,
    ) {
        val replacement = requireNotNull(jobRepository.lastReplaced)
        assertEquals(persisted.id, result.publicationId)
        assertEquals(persisted.workspaceId, result.workspaceId)
        assertEquals(persisted.status, result.status)
        assertEquals(persisted.scheduleMode, result.scheduleMode)
        assertEquals(persisted.priority, result.priority)
        assertEquals(persisted.scheduledFor, result.scheduledFor)
        assertEquals(persisted.id, replacement.publicationId)
        assertEquals(persisted.workspaceId, replacement.workspaceId)
        assertEquals(PublicationSchedulingPolicy().resolveDueAt(persisted, fixedClock.instant()), replacement.dueAt)
        assertEquals(PublicationSchedulingPolicy().priorityRank(persisted), replacement.priorityRank)
    }

    private fun assertNoDurableWrites(
        transactionRunner: RecordingAtomicTransactionRunner,
        publicationRepository: InMemoryPublicationRepository,
        jobRepository: InMemoryPublicationJobRepository,
    ) {
        assertEquals(0, transactionRunner.invocations)
        assertEquals(0, publicationRepository.writeCount)
        assertEquals(0, jobRepository.writeCount)
    }

    private suspend fun activeLinkedInAccounts(): InMemorySocialAccountRepository =
        InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }

    private suspend fun editPublicationHandler(
        publicationRepository: InMemoryPublicationRepository,
        jobRepository: InMemoryPublicationJobRepository = InMemoryPublicationJobRepository(),
        mediaResolver: FakeMediaAssetResolver = FakeMediaAssetResolver(),
    ): EditPublicationHandler = EditPublicationHandler(
        principalContextProvider = FixedPrincipalContextProvider(principalContext),
        resourceContextProvider = FixedResourceContextProvider(workspaceContext),
        socialAccountRepository = activeLinkedInAccounts(),
        publicationRepository = publicationRepository,
        publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
        publicationJobRepository = jobRepository,
        transactionRunner = recordingTransactionRunner(),
        providerCapabilityValidator = AcceptingCapabilityValidator(),
        schedulingPolicy = PublicationSchedulingPolicy(),
        mediaAssetResolver = mediaResolver,
        mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
        clock = fixedClock,
    )

    private fun editablePublication(assetIds: List<String> = emptyList()): PublicationDraft = PublicationDraft(
        id = "pub-1",
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        status = PublicationStatus.QUEUED,
        scheduleMode = ScheduleMode.NOW,
        priority = false,
        bodyText = "Old text",
        assetIds = assetIds,
    )

    private class VerifiedPrincipalIdentityLookup : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = principalType,
            subject = subject,
            provider = provider,
            displayIdentity = "Test User",
            email = "owner@example.com",
            username = "testuser",
            emailStatus = EmailStatus.VERIFIED,
        )

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:$email",
            provider = null,
            displayIdentity = "Test User",
            email = email,
            username = "testuser",
            emailStatus = EmailStatus.VERIFIED,
        )

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "local:owner@example.com",
            provider = null,
            displayIdentity = "Test User",
            email = "owner@example.com",
            username = "testuser",
            emailStatus = EmailStatus.VERIFIED,
        )
    }

    /** Returns emailStatus = PENDING so strict gate blocks the call. */
    private class PendingEmailIdentityLookup : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = principalType,
            subject = subject,
            provider = provider,
            displayIdentity = "Test User",
            email = "owner@example.com",
            username = "testuser",
            emailStatus = EmailStatus.PENDING,
        )

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:$email",
            provider = null,
            displayIdentity = "Test User",
            email = email,
            username = "testuser",
            emailStatus = EmailStatus.PENDING,
        )

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "local:owner@example.com",
            provider = null,
            displayIdentity = "Test User",
            email = "owner@example.com",
            username = "testuser",
            emailStatus = EmailStatus.PENDING,
        )
    }

    @Test
    fun `connects linkedin profile in active workspace`() = runTest {
        val connectionRepository = InMemorySocialConnectionRepository()
        val accountRepository = InMemorySocialAccountRepository()
        val stateSigner = CapturingOAuthStateSigner()
        val state = stateSigner.sign(validStatePayload())
        val transactionRunner = recordingTransactionRunner()
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            oauthStateSigner = stateSigner,
            socialConnectionRepository = connectionRepository,
            socialAccountRepository = accountRepository,
            channelEventPublisher = CapturingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = transactionRunner,
        )

        val result = handler.handle(
            CompleteLinkedInConnectionCommand(
                authorizationCode = "oauth-code-123",
                redirectUri = "https://app.example.com/callback",
                state = state,
            ),
        )

        assertEquals("workspace-1", result.workspaceId)
        assertEquals(SocialProvider.LINKEDIN, result.provider)
        assertEquals("linkedin-account-1", result.account.providerAccountId)
        assertNotNull(connectionRepository.lastSaved)
        assertNotNull(accountRepository.lastSaved)
    }

    @Test
    fun `initiates linkedin connection with signed state and authorization url`() = runTest {
        val stateSigner = CapturingOAuthStateSigner()
        val handler = InitiateLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = stateSigner,
            authorizationUrlBuilder = FakeAuthorizationUrlBuilder(),
            clock = fixedClock,
        )

        val result = handler.handle(InitiateLinkedInConnectionCommand("https://app.example.com/callback"))

        assertEquals("state-1", result.state)
        assertEquals("https://linkedin.example/authorize?state=state-1", result.authorizationUrl)
        assertEquals("workspace-1", stateSigner.lastPayload?.workspaceId)
        assertEquals("principal-1", stateSigner.lastPayload?.principalId)
        assertEquals(Instant.parse("2026-05-26T12:10:00Z"), result.expiresAt)
    }

    @Test
    fun `rejects linkedin initiation when provider is not configured`() = runTest {
        val handler = InitiateLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            oauthStateSigner = CapturingOAuthStateSigner(),
            authorizationUrlBuilder = FakeAuthorizationUrlBuilder(configured = false),
            clock = fixedClock,
        )

        assertThrows(ProviderNotConfiguredException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(InitiateLinkedInConnectionCommand("https://app.example.com/callback"))
            }
        }
    }

    @Test
    fun `lists active connected channels for active workspace`() = runTest {
        val repository = InMemoryConnectedSocialChannelReadRepository(
            listOf(
                ConnectedSocialChannel(
                    socialAccountId = "account-1",
                    connectionId = "connection-1",
                    provider = SocialProvider.LINKEDIN,
                    accountKind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                    profileUrn = "urn:li:person:123",
                    avatarUrl = "https://media.licdn.com/photo.jpg",
                    connectedAt = fixedClock.instant(),
                    lastSyncedAt = null,
                ),
            ),
        )
        val handler = ListConnectedChannelsHandler(FixedResourceContextProvider(workspaceContext), repository)

        val result = handler.handle(ListConnectedChannelsQuery())

        assertEquals(1, result.channels.size)
        assertEquals("account-1", result.channels.single().socialAccountId)
        assertEquals("https://media.licdn.com/photo.jpg", result.channels.single().avatarUrl)
        assertEquals(SocialConnectionStatus.entries.toSet(), repository.lastStatuses)
    }

    @Test
    fun `toSummary maps avatarUrl as null when channel has no avatar`() = runTest {
        val repository = InMemoryConnectedSocialChannelReadRepository(
            listOf(
                ConnectedSocialChannel(
                    socialAccountId = "account-no-avatar",
                    connectionId = "connection-2",
                    provider = SocialProvider.LINKEDIN,
                    accountKind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "No Avatar",
                    status = SocialConnectionStatus.ACTIVE,
                    profileUrn = "urn:li:person:456",
                    avatarUrl = null,
                    connectedAt = fixedClock.instant(),
                    lastSyncedAt = null,
                ),
            ),
        )
        val handler = ListConnectedChannelsHandler(FixedResourceContextProvider(workspaceContext), repository)

        val result = handler.handle(ListConnectedChannelsQuery())

        assertEquals(1, result.channels.size)
        assertNull(result.channels.single().avatarUrl)
    }

    @Test
    fun `rejects completion with mismatched oauth state before provider exchange`() = runTest {
        val provider = FakeSocialConnectionProvider()
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = provider,
            oauthStateSigner = CapturingOAuthStateSigner(
                payload = validStatePayload(workspaceId = "other-workspace"),
            ),
            socialConnectionRepository = InMemorySocialConnectionRepository(),
            socialAccountRepository = InMemorySocialAccountRepository(),
            channelEventPublisher = CapturingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = recordingTransactionRunner(),
        )

        assertThrows(InvalidOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteLinkedInConnectionCommand(
                        authorizationCode = "oauth-code-123",
                        redirectUri = "https://app.example.com/callback",
                        state = "state-1",
                    ),
                )
            }
        }
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `rejects completion with expired oauth state before provider exchange`() = runTest {
        val provider = FakeSocialConnectionProvider()
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = provider,
            oauthStateSigner = CapturingOAuthStateSigner(
                payload = validStatePayload(expiresAt = Instant.parse("2026-05-26T11:59:59Z")),
            ),
            socialConnectionRepository = InMemorySocialConnectionRepository(),
            socialAccountRepository = InMemorySocialAccountRepository(),
            channelEventPublisher = CapturingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = recordingTransactionRunner(),
        )

        assertThrows(ExpiredOAuthStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteLinkedInConnectionCommand(
                        authorizationCode = "oauth-code-123",
                        redirectUri = "https://app.example.com/callback",
                        state = "state-1",
                    ),
                )
            }
        }
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `creates queued publication and job for now schedule`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val transactionRunner = recordingTransactionRunner()
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val result = handler.handle(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                bodyText = "Ship it now",
                scheduleMode = ScheduleMode.NOW,
                priority = true,
            ),
        )

        assertEquals(PublicationStatus.QUEUED, result.status)
        assertEquals(ScheduleMode.NOW, result.scheduleMode)
        assertEquals(true, result.priority)
        assertEquals(1, transactionRunner.invocations)
        assertNotNull(jobRepository.lastEnqueued)
        assertEquals(100, jobRepository.lastEnqueued?.priorityRank)
    }

    @Test
    fun `rejects create publication with past scheduledFor`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Past post",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-05-26T11:55:00Z"),
                    ),
                )
            }
        }
    }

    @Test
    fun `allows create publication when scheduledFor is just after now`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val result = handler.handle(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                bodyText = "Now post",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-05-26T12:00:01Z"),
            ),
        )

        assertEquals(PublicationStatus.SCHEDULED, result.status)
    }

    @Test
    fun `rejects create publication when scheduledFor is within offset of now`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Past post",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-05-26T12:00:00Z"),
                    ),
                )
            }
        }
        assertTrue(exception.message!!.contains("Scheduled time must be in the future"))
    }

    @Test
    fun `rejects create publication when SCHEDULED_AT and scheduledFor is null`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Null scheduledFor post",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = null,
                    ),
                )
            }
        }
    }

    @Test
    fun `reschedule publication throws when SCHEDULED_AT and scheduledFor is null`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Reschedule me",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = ReschedulePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    ReschedulePublicationCommand(
                        publicationId = "pub-1",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = null,
                    ),
                )
            }
        }
    }

    @Test
    fun `rejects edit publication with past scheduledFor`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Old text",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    EditPublicationCommand(
                        publicationId = "pub-1",
                        bodyText = "New text",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-05-26T11:55:00Z"),
                    ),
                )
            }
        }
    }

    @Test
    fun `edit publication preserves existing assets when assetIds is absent`() = runTest {
        val mediaResolver = FakeMediaAssetResolver()
        val publicationRepository =
            InMemoryPublicationRepository(editablePublication(assetIds = listOf("asset-a", "asset-b")))
        val handler = editPublicationHandler(publicationRepository, mediaResolver = mediaResolver)

        val result = handler.handle(
            EditPublicationCommand(
                publicationId = "pub-1",
                bodyText = "Updated text",
                assetIds = null,
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals(listOf("asset-a", "asset-b"), result.assetIds)
        assertEquals(listOf("asset-a", "asset-b"), publicationRepository.lastUpdatedDraft?.assetIds)
        assertEquals(listOf("workspace-1" to listOf("asset-a", "asset-b")), mediaResolver.requestedCalls)
    }

    @Test
    fun `edit publication clears existing assets when assetIds is empty`() = runTest {
        val mediaResolver = FakeMediaAssetResolver()
        val publicationRepository =
            InMemoryPublicationRepository(editablePublication(assetIds = listOf("asset-a", "asset-b")))
        val handler = editPublicationHandler(publicationRepository, mediaResolver = mediaResolver)

        val result = handler.handle(
            EditPublicationCommand(
                publicationId = "pub-1",
                bodyText = "Updated text",
                assetIds = emptyList(),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals(emptyList<String>(), result.assetIds)
        assertEquals(emptyList<String>(), publicationRepository.lastUpdatedDraft?.assetIds)
        assertTrue(mediaResolver.requestedCalls.isEmpty())
    }

    @Test
    fun `edit publication replaces assets exactly in request order`() = runTest {
        val mediaResolver = FakeMediaAssetResolver().apply {
            resolvedAssets = listOf(
                ResolvedAssetSummary("asset-c", "workspace-1", "assets/workspace-1/asset-c", "image/png"),
                ResolvedAssetSummary("asset-a", "workspace-1", "assets/workspace-1/asset-a", "image/png"),
            )
        }
        val publicationRepository =
            InMemoryPublicationRepository(editablePublication(assetIds = listOf("asset-a", "asset-b")))
        val handler = editPublicationHandler(publicationRepository, mediaResolver = mediaResolver)

        val result = handler.handle(
            EditPublicationCommand(
                publicationId = "pub-1",
                bodyText = "Updated text",
                assetIds = listOf("asset-c", "asset-a"),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals(listOf("asset-c", "asset-a"), result.assetIds)
        assertEquals(listOf("asset-c", "asset-a"), publicationRepository.lastUpdatedDraft?.assetIds)
        assertEquals(listOf("workspace-1" to listOf("asset-c", "asset-a")), mediaResolver.requestedCalls)
    }

    @Test
    fun `rejects unsupported provider content before queueing`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val transactionRunner = recordingTransactionRunner()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(
            listOf(
                PublicationAsset(
                    id = "asset-1",
                    workspaceId = "workspace-1",
                    sourceType = AssetSourceType.EXTERNAL_URL,
                    mediaType = "application/pdf",
                    externalUrl = "https://cdn.example.com/doc.pdf",
                    status = PublicationAssetStatus.READY,
                    createdByPrincipalId = "principal-1",
                ),
            ),
        )
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            providerCapabilityValidator = RejectingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        assertThrows(PublicationValidationException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Unsupported asset",
                        assetIds = listOf("asset-1"),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }
        assertNoDurableWrites(transactionRunner, publicationRepository, jobRepository)
    }

    @Test
    fun `edits queued publication before claim`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Old text",
        )
        val persistedPublication = publication.copy(id = "pub-persisted", workspaceId = "workspace-persisted")
        val publicationRepository = InMemoryPublicationRepository(
            seed = publication,
            updateResultOverride = persistedPublication,
        )
        val jobRepository = InMemoryPublicationJobRepository()
        val transactionRunner = recordingTransactionRunner()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val result = handler.handle(
            EditPublicationCommand(
                publicationId = "pub-1",
                bodyText = "New text",
                scheduleMode = ScheduleMode.NOW,
                priority = true,
            ),
        )

        assertEquals("Old text", result.bodyText)
        assertEquals(1, transactionRunner.invocations)
        assertPersistedResultAndReplacementJob(persistedPublication, result, jobRepository)
    }

    @Test
    fun `cancels queued publication before processing`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Cancelable",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val transactionRunner = recordingTransactionRunner()
        val handler = CancelPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            clock = fixedClock,
        )

        val result = handler.handle(CancelPublicationCommand("pub-1"))

        assertEquals(PublicationStatus.CANCELLED, result.status)
        assertEquals(1, transactionRunner.invocations)
        assertEquals("pub-1", jobRepository.lastCancelledPublicationId)
    }

    @Test
    fun `retry with SCHEDULED_AT mode and past scheduledFor throws`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.FAILED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Retry me",
            failedAt = Instant.parse("2026-05-26T11:00:00Z"),
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = RetryPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val exception = try {
            handler.handle(
                RetryPublicationCommand(
                    publicationId = "pub-1",
                    scheduleMode = ScheduleMode.SCHEDULED_AT,
                    scheduledFor = Instant.parse("2026-05-26T11:00:00Z"),
                ),
            )
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("Scheduled time must be in the future"))
    }

    @Test
    fun `retry with SCHEDULED_AT mode and future scheduledFor succeeds`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.FAILED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Retry me",
            failedAt = Instant.parse("2026-05-26T11:00:00Z"),
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = RetryPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val future = fixedClock.instant().plus(java.time.Duration.ofMinutes(10))
        val result = handler.handle(
            RetryPublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = future,
            ),
        )

        assertEquals(PublicationStatus.SCHEDULED, result.status)
        assertEquals(ScheduleMode.SCHEDULED_AT, result.scheduleMode)
        assertEquals(future, result.scheduledFor)
    }

    @Test
    fun `retries failed publication using normalized persisted result and replacement job`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.FAILED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Retry me",
            failedAt = Instant.parse("2026-05-26T11:00:00Z"),
        )
        val persistedPublication = publication.copy(
            id = "pub-retry-persisted",
            workspaceId = "workspace-retry-persisted",
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            priority = true,
            scheduledFor = fixedClock.instant().plusSeconds(1_800),
            failedAt = null,
        )
        val publicationRepository = InMemoryPublicationRepository(
            seed = publication,
            updateResultOverride = persistedPublication,
        )
        val jobRepository = InMemoryPublicationJobRepository()
        val transactionRunner = recordingTransactionRunner()
        val handler = RetryPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val result = handler.handle(RetryPublicationCommand(publicationId = "pub-1", priority = true))

        assertEquals(1, transactionRunner.invocations)
        assertPersistedResultAndReplacementJob(persistedPublication, result, jobRepository)
    }

    @Test
    fun `reschedules queued publication using normalized persisted result and replacement job`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Reschedule me",
        )
        val persistedPublication = publication.copy(
            id = "pub-reschedule-persisted",
            workspaceId = "workspace-reschedule-persisted",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = true,
            scheduledFor = null,
        )
        val publicationRepository = InMemoryPublicationRepository(
            seed = publication,
            updateResultOverride = persistedPublication,
        )
        val jobRepository = InMemoryPublicationJobRepository()
        val transactionRunner = recordingTransactionRunner()
        val handler = ReschedulePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val result = handler.handle(
            ReschedulePublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-06-15T10:00:00Z"),
            ),
        )

        assertEquals(1, transactionRunner.invocations)
        assertPersistedResultAndReplacementJob(persistedPublication, result, jobRepository)
    }

    @Test
    fun `reschedule publication rejects past time`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Reschedule me",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = ReschedulePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    ReschedulePublicationCommand(
                        publicationId = "pub-1",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-05-26T11:55:00Z"),
                    ),
                )
            }
        }
    }

    @Test
    fun `gets calendar publications with conflicts and activity`() = runTest {
        val publicationAssetRepository = InMemoryPublicationAssetRepository(
            assets = listOf(
                PublicationAsset(
                    id = "asset-1",
                    workspaceId = "workspace-1",
                    sourceType = AssetSourceType.UPLOADED,
                    mediaType = "image/jpeg",
                    storageKey = "assets/workspace-1/asset-1",
                    status = PublicationAssetStatus.READY,
                    createdByPrincipalId = "principal-1",
                ),
            ),
        )
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z", assetIds = listOf("asset-1")),
                calendarPublication("pub-2", "account-1", "2026-06-15T10:10:00Z"),
                calendarPublication("pub-3", "account-2", "2026-06-16T10:00:00Z"),
            ),
            dateCounts = listOf(
                DateCount(date = LocalDate.parse("2026-06-15"), count = 2),
                DateCount(date = LocalDate.parse("2026-06-16"), count = 6),
            ),
        )
        val handler = GetCalendarPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            mediaAssetResolver = FakeMediaAssetResolver().apply {
                resolvedAssets = listOf(
                    ResolvedAssetSummary(
                        assetId = "asset-1",
                        workspaceId = "workspace-1",
                        storageKey = "assets/workspace-1/asset-1",
                        mediaType = "image/png",
                    ),
                )
            },
            assetPreviewUrlResolver = FakeAssetPreviewUrlResolver(),
        )

        val result = handler.handle(
            GetCalendarPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
                timezone = "Europe/Madrid",
            ),
        )

        assertEquals(listOf("pub-1", "pub-2", "pub-3"), result.publications.map { it.id })
        assertEquals(setOf("pub-1", "pub-2"), result.conflicts.map { it.publicationId }.toSet())
        assertEquals(ActivityDensity.LIGHT, result.activity.first { it.date == LocalDate.parse("2026-06-15") }.density)
        assertEquals(ActivityDensity.HIGH, result.activity.first { it.date == LocalDate.parse("2026-06-16") }.density)
        assertEquals("Europe/Madrid", publicationRepository.lastCountTimezone)
        assertEquals("https://preview.local/assets/workspace-1/asset-1", result.publications.first().previewUrl)
        assertEquals(listOf("asset-1"), result.publications.first().assetIds)
    }

    @Test
    fun `calendar exposes only opaque failure codes without technical messages`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-blocked", "account-1", "2026-06-15T10:00:00Z", PublicationStatus.BLOCKED)
                    .copy(blockedReason = "ACCOUNT_RECONNECT_REQUIRED"),
                calendarPublication("pub-failed", "account-1", "2026-06-15T11:00:00Z", PublicationStatus.FAILED)
                    .copy(
                        lastErrorCode = "PROVIDER_VALIDATION_FAILED",
                        lastErrorMessage = "com.linkedin.Client token=secret " +
                            "https://api.linkedin.com/rest/posts bucket/key",
                    ),
            ),
        )
        val handler = GetCalendarPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            mediaAssetResolver = FakeMediaAssetResolver(),
            assetPreviewUrlResolver = FakeAssetPreviewUrlResolver(),
        )

        val result = handler.handle(
            GetCalendarPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
            ),
        )

        assertEquals("ACCOUNT_RECONNECT_REQUIRED", result.publications[0].blockedReason)
        assertEquals("PROVIDER_VALIDATION_FAILED", result.publications[1].errorCode)
        val serializedBoundaryText = result.publications.joinToString(" ") { publication ->
            listOfNotNull(publication.blockedReason, publication.errorCode).joinToString(" ")
        }
        listOf("com.linkedin.Client", "token=secret", "https://api.linkedin.com", "bucket/key").forEach { unsafe ->
            assertEquals(false, serializedBoundaryText.contains(unsafe), unsafe)
        }
    }

    @Test
    fun `gets calendar publications with status and account filters`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z"),
                calendarPublication("pub-2", "account-2", "2026-06-15T10:00:00Z", PublicationStatus.QUEUED),
                calendarPublication("pub-3", "account-1", "2026-06-15T10:00:00Z", PublicationStatus.FAILED),
            ),
        )
        val handler = GetCalendarPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            mediaAssetResolver = FakeMediaAssetResolver(),
            assetPreviewUrlResolver = FakeAssetPreviewUrlResolver(),
        )

        val result = handler.handle(
            GetCalendarPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
                status = PublicationStatus.SCHEDULED,
                socialAccountId = "account-1",
            ),
        )

        assertEquals(listOf("pub-1"), result.publications.map { it.id })
        assertEquals(setOf(PublicationStatus.SCHEDULED), publicationRepository.lastFindStatuses)
        assertEquals(setOf("account-1"), publicationRepository.lastFindSocialAccountIds)
    }

    @Test
    fun `gets empty calendar for empty range`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val handler = GetCalendarPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            mediaAssetResolver = FakeMediaAssetResolver(),
            assetPreviewUrlResolver = FakeAssetPreviewUrlResolver(),
        )

        val result = handler.handle(
            GetCalendarPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
            ),
        )

        assertEquals(emptyList<CalendarPublicationResult>(), result.publications)
        assertEquals(emptyList<ConflictEntry>(), result.conflicts)
        assertEquals(emptyList<ActivityEntry>(), result.activity)
    }

    @Test
    fun `calendar ignores non-conflicting accounts`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z"),
                calendarPublication("pub-2", "account-2", "2026-06-15T10:10:00Z"),
            ),
        )
        val handler = GetCalendarPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            mediaAssetResolver = FakeMediaAssetResolver(),
            assetPreviewUrlResolver = FakeAssetPreviewUrlResolver(),
        )

        val result = handler.handle(
            GetCalendarPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
            ),
        )

        assertEquals(emptyList<ConflictEntry>(), result.conflicts)
    }

    @Test
    fun `calendar converts zero activity density`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            dateCounts = listOf(DateCount(date = LocalDate.parse("2026-06-15"), count = 0)),
        )
        val handler = GetCalendarPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            mediaAssetResolver = FakeMediaAssetResolver(),
            assetPreviewUrlResolver = FakeAssetPreviewUrlResolver(),
        )

        val result = handler.handle(
            GetCalendarPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
            ),
        )

        assertEquals(ActivityDensity.NONE, result.activity.single().density)
    }

    @Test
    fun `reschedule publication rejects terminal status`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.PUBLISHED,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            priority = false,
            bodyText = "Already published",
            scheduledFor = Instant.parse("2026-06-15T10:00:00Z"),
            publishedAt = Instant.parse("2026-06-15T10:01:00Z"),
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val transactionRunner = recordingTransactionRunner()
        val handler = ReschedulePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        assertThrows(com.profiletailors.smp.publishing.domain.PublicationEditNotAllowedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    ReschedulePublicationCommand(
                        publicationId = "pub-1",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-06-16T10:00:00Z"),
                    ),
                )
            }
        }
        assertNoDurableWrites(transactionRunner, publicationRepository, jobRepository)
    }

    @Test
    fun `create asset for uploaded type generates storage key`() = runTest {
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreateAssetHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationAssetRepository = assetRepository,
            clock = fixedClock,
        )

        val result = handler.handle(
            CreateAssetCommand(
                mediaType = "image/jpeg",
                sourceType = AssetSourceType.UPLOADED,
                originalFilename = "photo.jpg",
            ),
        )

        assertEquals("workspace-1", result.workspaceId)
        assertEquals(AssetSourceType.UPLOADED, result.sourceType)
        assertEquals("IMAGE/JPEG", result.mediaType)
        assertNotNull(result.assetId)
    }

    @Test
    fun `create asset for external url type does not generate storage key`() = runTest {
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreateAssetHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationAssetRepository = assetRepository,
            clock = fixedClock,
        )

        val result = handler.handle(
            CreateAssetCommand(
                mediaType = "image/png",
                sourceType = AssetSourceType.EXTERNAL_URL,
                externalUrl = "https://cdn.example.com/image.png",
                originalFilename = "remote-image.png",
            ),
        )

        assertEquals(AssetSourceType.EXTERNAL_URL, result.sourceType)
        assertEquals("IMAGE/PNG", result.mediaType)
        assertNotNull(result.assetId)
    }

    @Test
    fun `create publication throws when social account not found`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository()
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(SocialAccountNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "non-existent-account",
                        bodyText = "Test",
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("non-existent-account"))
    }

    @Test
    fun `edit publication throws when publication not found`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository()
        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(PublicationNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    EditPublicationCommand(
                        publicationId = "non-existent-pub",
                        bodyText = "Test",
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("non-existent-pub"))
    }

    @Test
    fun `cancel publication throws when publication not found`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = CancelPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            clock = fixedClock,
        )

        val error = assertThrows(PublicationNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(CancelPublicationCommand("non-existent-pub"))
            }
        }

        assertTrue(error.message!!.contains("non-existent-pub"))
    }

    @Test
    fun `retry publication throws when publication not found`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = RetryPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val error = assertThrows(PublicationNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(RetryPublicationCommand(publicationId = "non-existent-pub"))
            }
        }

        assertTrue(error.message!!.contains("non-existent-pub"))
    }

    @Test
    fun `reschedule publication throws when publication not found`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = ReschedulePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val error = assertThrows(PublicationNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    ReschedulePublicationCommand(
                        publicationId = "non-existent-pub",
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("non-existent-pub"))
    }

    // --- ListPublicationsHandler tests ---

    @Test
    fun `list publications with both from and to returns matching items`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-10T10:00:00Z"),
                calendarPublication("pub-2", "account-1", "2026-06-15T10:00:00Z"),
                calendarPublication("pub-3", "account-1", "2026-06-20T10:00:00Z"),
            ),
        )
        val handler = ListPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
        )

        val result = handler.handle(
            ListPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-06-18T00:00:00Z"),
            ),
        )

        assertEquals(2, result.total)
        assertEquals(listOf("pub-1", "pub-2"), result.publications.map { it.id })
    }

    @Test
    fun `list publications open-ended uses broad date range`() = runTest {
        // Use runtime-relative dates so the test never becomes flaky as time progresses.
        // The handler derives its window from Clock.systemUTC(), so dates must fall
        // within [now-90d, now+30d).
        val now = java.time.Clock.systemUTC().instant()
        val oneHourAgo = now.minus(java.time.Duration.ofHours(1))
        val twoHoursAgo = now.minus(java.time.Duration.ofHours(2))
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", oneHourAgo.toString()),
                calendarPublication("pub-2", "account-1", twoHoursAgo.toString()),
            ),
        )
        val handler = ListPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
        )

        val result = handler.handle(ListPublicationsQuery())

        assertEquals(2, result.total)
        assertEquals(2, result.publications.size)
    }

    @Test
    fun `list publications with status filter`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z", PublicationStatus.SCHEDULED),
                calendarPublication("pub-2", "account-1", "2026-06-15T11:00:00Z", PublicationStatus.QUEUED),
                calendarPublication("pub-3", "account-1", "2026-06-15T12:00:00Z", PublicationStatus.SCHEDULED),
            ),
        )
        val handler = ListPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
        )

        val result = handler.handle(
            ListPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
                status = PublicationStatus.SCHEDULED,
            ),
        )

        assertEquals(2, result.total)
        assertEquals(setOf(PublicationStatus.SCHEDULED), publicationRepository.lastFindStatuses)
    }

    @Test
    fun `list publications with socialAccountId filter`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z"),
                calendarPublication("pub-2", "account-2", "2026-06-15T11:00:00Z"),
                calendarPublication("pub-3", "account-1", "2026-06-15T12:00:00Z"),
            ),
        )
        val handler = ListPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
        )

        val result = handler.handle(
            ListPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
                socialAccountId = "account-1",
            ),
        )

        assertEquals(2, result.total)
        assertEquals(setOf("account-1"), publicationRepository.lastFindSocialAccountIds)
    }

    @Test
    fun `list publications respects offset and limit`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-10T10:00:00Z"),
                calendarPublication("pub-2", "account-1", "2026-06-11T10:00:00Z"),
                calendarPublication("pub-3", "account-1", "2026-06-12T10:00:00Z"),
                calendarPublication("pub-4", "account-1", "2026-06-13T10:00:00Z"),
                calendarPublication("pub-5", "account-1", "2026-06-14T10:00:00Z"),
            ),
        )
        val handler = ListPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
        )

        val result = handler.handle(
            ListPublicationsQuery(
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-07-01T00:00:00Z"),
                offset = 1,
                limit = 2,
            ),
        )

        assertEquals(5, result.total)
        assertEquals(2, result.publications.size)
        assertEquals("pub-2", result.publications[0].id)
        assertEquals("pub-3", result.publications[1].id)
    }

    @Test
    fun `list publications returns empty for unknown workspace`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val handler = ListPublicationsHandler(
            resourceContextProvider = FixedResourceContextProvider(
                ResourceContext(
                    type = ResourceContextType.WORKSPACE,
                    workspaceId = "unknown-workspace",
                ),
            ),
            publicationRepository = publicationRepository,
        )

        val result = handler.handle(ListPublicationsQuery())

        assertEquals(0, result.total)
        assertEquals(emptyList<ListPublicationItem>(), result.publications)
    }

    private fun calendarPublication(
        id: String,
        socialAccountId: String,
        scheduledFor: String,
        status: PublicationStatus = PublicationStatus.SCHEDULED,
        assetIds: List<String> = emptyList(),
    ): PublicationDraft = PublicationDraft(
        id = id,
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = socialAccountId,
        status = status,
        scheduleMode = ScheduleMode.SCHEDULED_AT,
        priority = false,
        title = id,
        bodyText = "Calendar publication $id",
        assetIds = assetIds,
        scheduledFor = Instant.parse(scheduledFor),
    )

    private class FixedPrincipalContextProvider(private val principalContext: PrincipalContext) :
        PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(private val resourceContext: ResourceContext) :
        ResourceContextProvider {
        override fun current(): ResourceContext = resourceContext
    }

    private class FakeSocialConnectionProvider : SocialConnectionProvider {
        var callCount = 0

        override suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult {
            callCount += 1
            return ProviderConnectionResult(
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-connection-1",
                credentialReference = "secret-ref-1",
                account = ProviderAccountProfile(
                    providerAccountId = "linkedin-account-1",
                    displayName = "Yuniel",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    profileUrn = "urn:li:person:123",
                ),
            )
        }
    }

    private class CapturingOAuthStateSigner(private val payload: LinkedInOAuthStatePayload? = null) :
        OAuthStateSigner {
        var lastPayload: LinkedInOAuthStatePayload? = null

        override fun sign(payload: LinkedInOAuthStatePayload): String {
            lastPayload = payload
            return "state-1"
        }

        override fun verify(state: String): LinkedInOAuthStatePayload = payload ?: validStatePayload()
    }

    private class FakeAuthorizationUrlBuilder(private val configured: Boolean = true) :
        LinkedInAuthorizationUrlBuilder {
        override fun buildAuthorizationUrl(state: String, redirectUri: String): String =
            "https://linkedin.example/authorize?state=$state"

        override fun isConfigured(): Boolean = configured
    }

    private class InMemoryConnectedSocialChannelReadRepository(private val channels: List<ConnectedSocialChannel>) :
        ConnectedSocialChannelReadRepository {
        var lastStatuses: Set<SocialConnectionStatus>? = null

        override suspend fun listByWorkspace(
            workspaceId: String,
            statuses: Set<SocialConnectionStatus>,
        ): List<ConnectedSocialChannel> {
            lastStatuses = statuses
            return channels
        }
    }

    private companion object {
        fun validStatePayload(
            workspaceId: String = "workspace-1",
            principalId: String = "principal-1",
            redirectUri: String = "https://app.example.com/callback",
            expiresAt: Instant = Instant.parse("2026-05-26T12:10:00Z"),
        ): LinkedInOAuthStatePayload = LinkedInOAuthStatePayload(
            provider = SocialProvider.LINKEDIN,
            workspaceId = workspaceId,
            principalId = principalId,
            redirectUri = redirectUri,
            nonce = "nonce-1",
            issuedAt = Instant.parse("2026-05-26T12:00:00Z"),
            expiresAt = expiresAt,
        )
    }

    private class AcceptingCapabilityValidator : ProviderCapabilityValidator {
        override fun validate(input: ProviderCapabilityValidationInput) = Unit
    }

    private class RejectingCapabilityValidator : ProviderCapabilityValidator {
        override fun validate(input: ProviderCapabilityValidationInput): Unit =
            throw PublicationValidationException("Unsupported provider-content combination.")
    }

    private class InMemorySocialConnectionRepository : SocialConnectionRepository {
        var lastSaved: SocialConnection? = null
        private val items = linkedMapOf<String, SocialConnection>()

        override suspend fun upsert(connection: SocialConnection): SocialConnection {
            items[connection.id] = connection
            lastSaved = connection
            return connection
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            items[connectionId]?.takeIf { it.workspaceId == workspaceId }
    }

    private class InMemorySocialAccountRepository : SocialAccountRepository {
        var lastSaved: SocialAccount? = null
        private val items = linkedMapOf<String, SocialAccount>()

        override suspend fun upsert(account: SocialAccount): SocialAccount {
            items[account.id] = account
            lastSaved = account
            return account
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? =
            items[accountId]?.takeIf { it.workspaceId == workspaceId }
    }

    private class ThrowingSocialAccountRepository : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount =
            throw IllegalStateException("account upsert failed")

        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? = null
    }

    private class CapturingChannelEventPublisher : ChannelEventPublisher {
        val events = mutableListOf<ChannelEvent>()

        override fun publish(event: ChannelEvent) {
            events += event
        }
    }

    private class InMemoryPublicationRepository(
        seed: PublicationDraft? = null,
        seedMany: List<PublicationDraft> = emptyList(),
        private val dateCounts: List<DateCount> = emptyList(),
        private val jobRepository: InMemoryPublicationJobRepository? = null,
        private val updateResultOverride: PublicationDraft? = null,
    ) : PublicationRepository {
        var deletedPublication: Pair<String, String>? = null
        var lastUpdatedDraft: PublicationDraft? = null
        var lastFindStatuses: Set<PublicationStatus>? = null
        var lastFindSocialAccountIds: Set<String>? = null
        var lastCountWorkspaceId: String? = null
        var lastCountFrom: Instant? = null
        var lastCountTo: Instant? = null
        var lastCountStatuses: Set<PublicationStatus>? = null
        var lastCountTimezone: String? = null
        var writeCount: Int = 0
        private val items = linkedMapOf<String, PublicationDraft>()

        init {
            if (seed != null) items[seed.id] = seed
            seedMany.forEach { items[it.id] = it }
        }

        override suspend fun createDraft(draft: PublicationDraft): PublicationDraft {
            writeCount += 1
            items[draft.id] = draft
            return draft
        }

        override suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft {
            writeCount += 1
            lastUpdatedDraft = draft
            val result = updateResultOverride ?: draft
            items[result.id] = result
            return result
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft? =
            items[publicationId]?.takeIf { it.workspaceId == workspaceId }

        override suspend fun findInDateRange(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            socialAccountIds: Set<String>?,
            hydrateAssets: Boolean,
        ): List<PublicationDraft> {
            lastFindStatuses = statuses
            lastFindSocialAccountIds = socialAccountIds
            return items.values.filter { pub ->
                pub.workspaceId == workspaceId &&
                    pub.scheduledFor != null &&
                    pub.scheduledFor >= from &&
                    pub.scheduledFor < to &&
                    (statuses == null || pub.status in statuses) &&
                    (socialAccountIds == null || pub.socialAccountId in socialAccountIds)
            }
        }

        override suspend fun countByDate(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            timezone: String,
        ): List<DateCount> {
            lastCountWorkspaceId = workspaceId
            lastCountFrom = from
            lastCountTo = to
            lastCountStatuses = statuses
            lastCountTimezone = timezone
            return dateCounts
        }

        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) =
            Unit

        override suspend fun markFailed(
            publicationId: String,
            failedAt: Instant,
            reasonCode: String?,
            reasonMessage: String?,
        ) = Unit

        override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) {
            writeCount += 1
        }

        override suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?) = Unit

        override suspend fun deleteUnpublished(workspaceId: String, publicationId: String): Boolean {
            val item = items[publicationId]
            if (item != null && item.workspaceId == workspaceId) {
                val deletableStatuses =
                    setOf(PublicationStatus.DRAFT, PublicationStatus.QUEUED, PublicationStatus.SCHEDULED)
                if (item.status !in deletableStatuses) return false
                deletedPublication = workspaceId to publicationId
                items.remove(publicationId)
                jobRepository?.removeUnclaimedForPublication(publicationId)
                return true
            }
            return false
        }

        override suspend fun findBlockedForRecovery(maxRetries: Int): List<PublicationDraft> = emptyList()
    }

    private class InMemoryPublicationAssetRepository(private val assets: List<PublicationAsset> = emptyList()) :
        PublicationAssetRepository {
        private val items = linkedMapOf<String, PublicationAsset>()

        init {
            assets.forEach { items[it.id] = it }
        }

        override suspend fun findByWorkspaceAndIds(
            workspaceId: String,
            assetIds: Collection<String>,
        ): List<PublicationAsset> = items.values.filter { it.workspaceId == workspaceId && it.id in assetIds }

        override suspend fun create(asset: PublicationAsset): PublicationAsset {
            items[asset.id] = asset
            return asset
        }

        override suspend fun updateStatus(assetId: String, status: PublicationAssetStatus) {
            items[assetId] = items[assetId]!!.copy(status = status)
        }

        override suspend fun updateProviderAssetRef(assetId: String, providerAssetRef: ProviderAssetRef) {
            items[assetId] =
                items[assetId]!!.copy(status = PublicationAssetStatus.READY, providerAssetRef = providerAssetRef)
        }
    }

    private class FakeAssetPreviewUrlResolver : AssetPreviewUrlResolver {
        override suspend fun resolvePreviewUrl(
            assetId: String,
            workspaceId: String,
            mediaType: String,
            storageKey: String?,
            externalUrl: String?,
        ): String? = if (mediaType.startsWith("image/")) {
            externalUrl ?: storageKey?.let { "https://preview.local/$it" }
        } else {
            null
        }
    }

    private class InMemoryPublicationJobRepository : PublicationJobRepository {
        var lastEnqueued: PublicationJob? = null
        var lastReplaced: PublicationJob? = null
        var lastCancelledPublicationId: String? = null
        var writeCount: Int = 0
        var jobsByPublicationId: MutableMap<String, MutableList<PublicationJob>> = linkedMapOf()

        override suspend fun enqueue(job: PublicationJob) {
            writeCount += 1
            lastEnqueued = job
            jobsByPublicationId.getOrPut(job.publicationId) { mutableListOf() }.add(job)
        }

        override suspend fun replaceForPublication(job: PublicationJob) {
            writeCount += 1
            lastReplaced = job
            jobsByPublicationId[job.publicationId] = mutableListOf(job)
        }

        override suspend fun claimNextDue(now: Instant, workerId: String): PublicationJobClaim? = null

        override suspend fun rescheduleRetry(jobId: String, nextAttemptAt: Instant, attemptNumber: Int) = Unit

        override suspend fun complete(jobId: String, completedAt: Instant) = Unit

        override suspend fun fail(jobId: String, failedAt: Instant) = Unit

        override suspend fun cancel(jobId: String, cancelledAt: Instant) {
            writeCount += 1
            lastCancelledPublicationId = jobId
        }

        fun removeUnclaimedForPublication(publicationId: String) {
            jobsByPublicationId[publicationId] = jobsByPublicationId[publicationId]
                ?.filterNot {
                    it.status == com.profiletailors.smp.publishing.domain.JobStatus.PENDING ||
                        it.status == com.profiletailors.smp.publishing.domain.JobStatus.RETRY_WAITING
                }
                ?.toMutableList()
                ?: mutableListOf()
        }
    }

    // --- MediaAssetResolver test doubles ---

    /**
     * Fake MediaAssetResolver that can be configured to throw specific exceptions.
     */
    private class FakeMediaAssetResolver : MediaAssetResolver {
        var shouldThrowMissing = false
        var shouldThrowNotReady = false
        var shouldThrowUnavailable = false
        val requestedCalls = mutableListOf<Pair<String, List<String>>>()
        var resolvedAssets: List<ResolvedAssetSummary> = emptyList()

        override suspend fun resolveReadyAssets(
            workspaceId: String,
            assetIds: List<String>,
        ): List<ResolvedAssetSummary> {
            requestedCalls.add(workspaceId to assetIds)
            when {
                shouldThrowUnavailable -> throw MediaServiceUnavailableException(
                    "Media context unavailable",
                )

                shouldThrowMissing -> throw AssetNotReadyException(
                    assetIds.first(),
                    "asset not found",
                )

                shouldThrowNotReady -> throw AssetNotReadyException(
                    assetIds.first(),
                    "asset not READY",
                )
            }
            return resolvedAssets
        }
    }

    // --- Media-context-aware publication handler tests ---

    @Test
    fun `create publication rejects missing asset through media resolver`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver().apply { shouldThrowMissing = true }

        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Post with missing asset",
                        assetIds = listOf("missing-asset-1"),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("missing-asset-1"))
    }

    @Test
    fun `create publication rejects non-READY asset through media resolver`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver().apply { shouldThrowNotReady = true }

        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Post with processing asset",
                        assetIds = listOf("processing-asset-1"),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("processing-asset-1"))
    }

    @Test
    fun `create publication succeeds when all assets are READY in media resolver`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver().apply {
            resolvedAssets = listOf(
                ResolvedAssetSummary(
                    assetId = "ready-asset-1",
                    workspaceId = "workspace-1",
                    storageKey = "assets/workspace-1/ready-asset-1",
                    mediaType = "image/jpeg",
                ),
            )
        }

        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val result = handler.handle(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                bodyText = "Post with ready asset",
                assetIds = listOf("ready-asset-1"),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals(PublicationStatus.QUEUED, result.status)
        assertEquals(listOf("ready-asset-1"), mediaResolver.requestedCalls.last().second)
    }

    @Test
    fun `create publication throws MediaServiceUnavailableException when media context is unavailable`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver().apply { shouldThrowUnavailable = true }
        val transactionRunner = recordingTransactionRunner()

        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(MediaServiceUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Post with asset",
                        assetIds = listOf("asset-1"),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("unavailable") || error.message!!.contains("timeout"))
        assertNoDurableWrites(transactionRunner, publicationRepository, jobRepository)
    }

    @Test
    fun `create publication falls back to legacy asset when media context integration disabled`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(
            listOf(
                PublicationAsset(
                    id = "legacy-asset-1",
                    workspaceId = "workspace-1",
                    sourceType = AssetSourceType.UPLOADED,
                    mediaType = "IMAGE/JPEG",
                    storageKey = "assets/workspace-1/legacy-asset-1",
                    status = PublicationAssetStatus.READY,
                    createdByPrincipalId = "principal-1",
                ),
            ),
        )
        val mediaResolver = FakeMediaAssetResolver().apply { shouldThrowUnavailable = true }

        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = false), // legacy fallback
            clock = fixedClock,
        )

        // When integration is disabled, the handler should use legacy lookup and succeed
        val result = handler.handle(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                bodyText = "Post with legacy asset",
                assetIds = listOf("legacy-asset-1"),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals(PublicationStatus.QUEUED, result.status)
        // Media resolver should NOT have been called (integration disabled)
        assertTrue(mediaResolver.requestedCalls.isEmpty())
    }

    @Test
    fun `create publication skips media validation when assetIds is empty`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver()

        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val result = handler.handle(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                bodyText = "Post with no assets",
                assetIds = emptyList(),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals(PublicationStatus.QUEUED, result.status)
        // Media resolver should NOT be called when assetIds is empty
        assertTrue(mediaResolver.requestedCalls.isEmpty())
    }

    @Test
    fun `edit publication rejects non-READY asset through media resolver`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Old text",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver().apply { shouldThrowNotReady = true }

        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    EditPublicationCommand(
                        publicationId = "pub-1",
                        bodyText = "Updated text",
                        assetIds = listOf("processing-asset-1"),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("processing-asset-1"))
    }

    @Test
    fun `edit publication throws MediaServiceUnavailableException when media context is unavailable`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Old text",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver().apply { shouldThrowUnavailable = true }

        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val error = assertThrows(MediaServiceUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    EditPublicationCommand(
                        publicationId = "pub-1",
                        bodyText = "Updated text",
                        assetIds = listOf("asset-1"),
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("unavailable") || error.message!!.contains("timeout"))
    }

    @Test
    fun `edit publication skips media validation when assetIds is empty`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Old text",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val assetRepository = InMemoryPublicationAssetRepository(emptyList())
        val mediaResolver = FakeMediaAssetResolver()

        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository, transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
        )

        val result = handler.handle(
            EditPublicationCommand(
                publicationId = "pub-1",
                bodyText = "Updated text",
                assetIds = emptyList(),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        assertEquals("Updated text", result.bodyText)
        // Media resolver should NOT be called when assetIds is empty
        assertTrue(mediaResolver.requestedCalls.isEmpty())
    }

    @Test
    fun `complete linkedin connection requires verified email when policy is strict`() = runTest {
        val connectionRepository = InMemorySocialConnectionRepository()
        val accountRepository = InMemorySocialAccountRepository()
        val stateSigner = CapturingOAuthStateSigner()
        val state = stateSigner.sign(validStatePayload())
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            oauthStateSigner = stateSigner,
            socialConnectionRepository = connectionRepository,
            socialAccountRepository = accountRepository,
            channelEventPublisher = CapturingChannelEventPublisher(),
            clock = fixedClock,
            transactionRunner = recordingTransactionRunner(),
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteLinkedInConnectionCommand(
                        authorizationCode = "oauth-code-123",
                        redirectUri = "https://app.example.com/callback",
                        state = state,
                    ),
                )
            }
        }
    }

    @Test
    fun `connects linkedin profile commits transaction and publishes event`() = runTest {
        val connectionRepository = InMemorySocialConnectionRepository()
        val accountRepository = InMemorySocialAccountRepository()
        val eventPublisher = CapturingChannelEventPublisher()
        val stateSigner = CapturingOAuthStateSigner()
        val state = stateSigner.sign(validStatePayload())
        val order = mutableListOf<String>()
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            oauthStateSigner = stateSigner,
            socialConnectionRepository = connectionRepository,
            socialAccountRepository = accountRepository,
            channelEventPublisher = eventPublisher,
            clock = fixedClock,
            transactionRunner = transactionRunner,
        )

        val result = handler.handle(
            CompleteLinkedInConnectionCommand(
                authorizationCode = "oauth-code-123",
                redirectUri = "https://app.example.com/callback",
                state = state,
            ),
        )

        assertEquals("workspace-1", result.workspaceId)
        assertEquals(SocialProvider.LINKEDIN, result.provider)
        assertNotNull(connectionRepository.lastSaved)
        assertNotNull(accountRepository.lastSaved)
        assertEquals(1, transactionRunner.invocations)
        assertTrue(order.contains("tx:start"), "Expected tx:start in order: $order")
        assertTrue(order.contains("tx:commit"), "Expected tx:commit in order: $order")
        assertEquals(1, eventPublisher.events.size)
        assertEquals(ChannelEventType.CONNECTED_CHANNEL_UPDATED, eventPublisher.events[0].type)
    }

    @Test
    fun `linkedin connection rollback when account upsert fails`() = runTest {
        val connectionRepository = InMemorySocialConnectionRepository()
        val accountRepository = ThrowingSocialAccountRepository()
        val eventPublisher = CapturingChannelEventPublisher()
        val stateSigner = CapturingOAuthStateSigner()
        val state = stateSigner.sign(validStatePayload())
        val order = mutableListOf<String>()
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            oauthStateSigner = stateSigner,
            socialConnectionRepository = connectionRepository,
            socialAccountRepository = accountRepository,
            channelEventPublisher = eventPublisher,
            clock = fixedClock,
            transactionRunner = transactionRunner,
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteLinkedInConnectionCommand(
                        authorizationCode = "oauth-code-123",
                        redirectUri = "https://app.example.com/callback",
                        state = state,
                    ),
                )
            }
        }
        // Transaction was rolled back (no tx:commit recorded)
        assertFalse(order.contains("tx:commit"), "Expected NO tx:commit in order (rollback): $order")
        assertEquals(0, eventPublisher.events.size)
    }

    @Test
    fun `create publication requires verified email when policy is strict`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val transactionRunner = recordingTransactionRunner()
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository,
            transactionRunner = transactionRunner,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Schedule me",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = fixedClock.instant().plusSeconds(600),
                    ),
                )
            }
        }
        assertNoDurableWrites(transactionRunner, publicationRepository, jobRepository)
    }

    @Test
    fun `edit publication requires verified email when policy is strict`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Old text",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val socialAccountRepository = InMemorySocialAccountRepository().apply {
            upsert(
                SocialAccount(
                    id = "account-1",
                    socialConnectionId = "connection-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    providerAccountId = "linkedin-account-1",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    displayName = "Yuniel",
                    status = SocialConnectionStatus.ACTIVE,
                ),
            )
        }
        val handler = EditPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = InMemoryPublicationJobRepository(),
            transactionRunner = recordingTransactionRunner(),
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = FakeMediaAssetResolver(),
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = true),
            clock = fixedClock,
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    EditPublicationCommand(
                        publicationId = "pub-1",
                        bodyText = "Updated text",
                        scheduleMode = ScheduleMode.NOW,
                    ),
                )
            }
        }
    }

    @Test
    fun `deletes unpublished publication in editable status and removes unclaimed jobs`() = runTest {
        val publication = PublicationDraft(
            id = "pub-delete-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            priority = false,
            bodyText = "Delete me",
            scheduledFor = fixedClock.instant().plusSeconds(600),
        )
        val jobRepository = InMemoryPublicationJobRepository()
        val pendingJob = PublicationJob(
            id = "pjob-pending-1",
            publicationId = publication.id,
            workspaceId = "workspace-1",
            status = JobStatus.PENDING,
            dueAt = fixedClock.instant(),
            priorityRank = 0,
            attemptCount = 0,
            maxAttempts = 1,
        )
        val retryJob = PublicationJob(
            id = "pjob-retry-1",
            publicationId = publication.id,
            workspaceId = "workspace-1",
            status = JobStatus.RETRY_WAITING,
            dueAt = fixedClock.instant(),
            priorityRank = 0,
            attemptCount = 1,
            maxAttempts = 3,
        )
        jobRepository.enqueue(pendingJob)
        jobRepository.enqueue(retryJob)
        val publicationRepository = InMemoryPublicationRepository(publication, jobRepository = jobRepository)
        val handler = DeletePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            clock = fixedClock,
        )

        val result = handler.handle(DeletePublicationCommand(publication.id))

        assertEquals(publication.id, result.publicationId)
        assertEquals(PublicationStatus.SCHEDULED, result.status)
        assertEquals("workspace-1" to publication.id, publicationRepository.deletedPublication)
        val remainingJobs = jobRepository.jobsByPublicationId[publication.id].orEmpty()
        assertTrue(remainingJobs.none { it.status == JobStatus.PENDING || it.status == JobStatus.RETRY_WAITING }) {
            "Expected all PENDING and RETRY_WAITING jobs to be removed, but found: ${remainingJobs.map { it.id }}"
        }
    }

    @Test
    fun `delete publication throws when publication not found`() = runTest {
        val publicationRepository = InMemoryPublicationRepository()
        val jobRepository = InMemoryPublicationJobRepository()
        val handler = DeletePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            clock = fixedClock,
        )

        val error = assertThrows(PublicationNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(DeletePublicationCommand("non-existent-pub"))
            }
        }

        assertTrue(error.message!!.contains("non-existent-pub"))
    }

    @Test
    fun `delete publication rejects non editable statuses`() = runTest {
        val publication = PublicationDraft(
            id = "pub-delete-2",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.PUBLISHED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Already published",
        )
        val publicationRepository = InMemoryPublicationRepository(publication)
        val handler = DeletePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = InMemoryPublicationJobRepository(),
            clock = fixedClock,
        )

        assertThrows(PublicationDeletionNotAllowedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(DeletePublicationCommand(publication.id))
            }
        }
    }

    @Test
    fun `delete publication requires verified email when policy is strict`() = runTest {
        val publication = PublicationDraft(
            id = "pub-delete-3",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Need verification",
        )
        val handler = DeletePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = InMemoryPublicationRepository(publication),
            publicationJobRepository = InMemoryPublicationJobRepository(),
            clock = fixedClock,
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(DeletePublicationCommand(publication.id))
            }
        }
    }

    @Test
    fun `cancel publication requires verified email when policy is strict`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Cancelable",
        )
        val handler = CancelPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = InMemoryPublicationRepository(publication),
            publicationJobRepository = InMemoryPublicationJobRepository(),
            transactionRunner = recordingTransactionRunner(),
            clock = fixedClock,
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(CancelPublicationCommand("pub-1"))
            }
        }
    }

    @Test
    fun `retry publication requires verified email when policy is strict`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.FAILED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Retry me",
            failedAt = Instant.parse("2026-05-26T11:00:00Z"),
        )
        val handler = RetryPublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = InMemoryPublicationRepository(publication),
            publicationJobRepository = InMemoryPublicationJobRepository(),
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(RetryPublicationCommand(publicationId = "pub-1", priority = true))
            }
        }
    }

    @Test
    fun `reschedule publication requires verified email when policy is strict`() = runTest {
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Reschedule me",
        )
        val handler = ReschedulePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = InMemoryPublicationRepository(publication),
            publicationJobRepository = InMemoryPublicationJobRepository(),
            transactionRunner = recordingTransactionRunner(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
            principalIdentityLookup = PendingEmailIdentityLookup(),
            emailVerificationPolicy = strictEmailVerificationPolicy,
        )

        assertThrows(FeatureEmailVerificationRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    ReschedulePublicationCommand(
                        publicationId = "pub-1",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = fixedClock.instant().plusSeconds(600),
                    ),
                )
            }
        }
    }
}
