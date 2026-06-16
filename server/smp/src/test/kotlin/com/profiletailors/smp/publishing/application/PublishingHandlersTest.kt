package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.ActivityDensity
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannel
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.DateCount
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
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
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `connects linkedin profile in active workspace`() = runTest {
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
        val handler = CreatePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = assetRepository,
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
    fun `rejects create publication when scheduledFor is now`() = runTest {
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
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreatePublicationCommand(
                        socialAccountId = "account-1",
                        bodyText = "Now post",
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-05-26T12:00:00Z"),
                    ),
                )
            }
        }
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
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
    fun `rejects unsupported provider content before queueing`() = runTest {
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
            providerCapabilityValidator = RejectingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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

        assertEquals("New text", result.bodyText)
        assertEquals(true, result.priority)
        assertNotNull(jobRepository.lastReplaced)
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
        val handler = CancelPublicationHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            clock = fixedClock,
        )

        val result = handler.handle(CancelPublicationCommand("pub-1"))

        assertEquals(PublicationStatus.CANCELLED, result.status)
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
        assertTrue(exception!!.message!!.contains("at least 5 minutes"))
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
    fun `retries failed publication and replaces job`() = runTest {
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
            schedulingPolicy = PublicationSchedulingPolicy(),
            clock = fixedClock,
        )

        val result = handler.handle(RetryPublicationCommand(publicationId = "pub-1", priority = true))

        assertEquals(PublicationStatus.QUEUED, result.status)
        assertEquals(true, result.priority)
        assertNotNull(jobRepository.lastReplaced)
    }

    @Test
    fun `reschedules queued publication with new timing`() = runTest {
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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

        assertEquals(ScheduleMode.SCHEDULED_AT, result.scheduleMode)
        assertEquals(Instant.parse("2026-06-15T10:00:00Z"), result.scheduledFor)
        assertNotNull(jobRepository.lastReplaced)
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z"),
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
        val handler = ReschedulePublicationHandler(
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(emptyList()),
            publicationJobRepository = jobRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            schedulingPolicy = PublicationSchedulingPolicy(),
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = publicationRepository,
            publicationJobRepository = jobRepository,
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
        val publicationRepository = InMemoryPublicationRepository(
            seedMany = listOf(
                calendarPublication("pub-1", "account-1", "2026-06-15T10:00:00Z"),
                calendarPublication("pub-2", "account-1", "2026-06-16T10:00:00Z"),
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
        scheduledFor = Instant.parse(scheduledFor),
    )

    private class FixedPrincipalContextProvider(
        private val principalContext: PrincipalContext,
    ) : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(
        private val resourceContext: ResourceContext,
    ) : ResourceContextProvider {
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

    private class CapturingOAuthStateSigner(
        private val payload: LinkedInOAuthStatePayload? = null,
    ) : OAuthStateSigner {
        var lastPayload: LinkedInOAuthStatePayload? = null

        override fun sign(payload: LinkedInOAuthStatePayload): String {
            lastPayload = payload
            return "state-1"
        }

        override fun verify(state: String): LinkedInOAuthStatePayload = payload ?: validStatePayload()
    }

    private class FakeAuthorizationUrlBuilder(
        private val configured: Boolean = true,
    ) : LinkedInAuthorizationUrlBuilder {
        override fun buildAuthorizationUrl(state: String, redirectUri: String): String =
            "https://linkedin.example/authorize?state=$state"

        override fun isConfigured(): Boolean = configured
    }

    private class InMemoryConnectedSocialChannelReadRepository(
        private val channels: List<ConnectedSocialChannel>,
    ) : ConnectedSocialChannelReadRepository {
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
        override fun validate(input: ProviderCapabilityValidationInput) {
            throw PublicationValidationException("Unsupported provider-content combination.")
        }
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
    ) : PublicationRepository {
        var lastFindStatuses: Set<PublicationStatus>? = null
        var lastFindSocialAccountIds: Set<String>? = null
        var lastCountWorkspaceId: String? = null
        var lastCountFrom: Instant? = null
        var lastCountTo: Instant? = null
        var lastCountStatuses: Set<PublicationStatus>? = null
        var lastCountTimezone: String? = null
        private val items = linkedMapOf<String, PublicationDraft>()

        init {
            if (seed != null) items[seed.id] = seed
            seedMany.forEach { items[it.id] = it }
        }

        override suspend fun createDraft(draft: PublicationDraft): PublicationDraft {
            items[draft.id] = draft
            return draft
        }

        override suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft {
            items[draft.id] = draft
            return draft
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft? =
            items[publicationId]?.takeIf { it.workspaceId == workspaceId }

        override suspend fun findInDateRange(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            socialAccountIds: Set<String>?,
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

        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) = Unit

        override suspend fun markFailed(publicationId: String, failedAt: Instant, reasonCode: String?, reasonMessage: String?) = Unit

        override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) = Unit

        override suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?) = Unit

        override suspend fun findBlockedForRecovery(
            maxRetries: Int,
        ): List<PublicationDraft> = emptyList()
    }

    private class InMemoryPublicationAssetRepository(
        private val assets: List<PublicationAsset> = emptyList(),
    ) : PublicationAssetRepository {
        private val items = linkedMapOf<String, PublicationAsset>()

        init {
            assets.forEach { items[it.id] = it }
        }

        override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: Collection<String>): List<PublicationAsset> =
            items.values.filter { it.workspaceId == workspaceId && it.id in assetIds }

        override suspend fun create(asset: PublicationAsset): PublicationAsset {
            items[asset.id] = asset
            return asset
        }

        override suspend fun updateStatus(assetId: String, status: PublicationAssetStatus) {
            items[assetId] = items[assetId]!!.copy(status = status)
        }

        override suspend fun updateProviderAssetRef(assetId: String, providerAssetRef: ProviderAssetRef) {
            items[assetId] = items[assetId]!!.copy(status = PublicationAssetStatus.READY, providerAssetRef = providerAssetRef)
        }
    }

    private class InMemoryPublicationJobRepository : PublicationJobRepository {
        var lastEnqueued: PublicationJob? = null
        var lastReplaced: PublicationJob? = null
        var lastCancelledPublicationId: String? = null

        override suspend fun enqueue(job: PublicationJob) {
            lastEnqueued = job
        }

        override suspend fun replaceForPublication(job: PublicationJob) {
            lastReplaced = job
        }

        override suspend fun claimNextDue(now: Instant, workerId: String): PublicationJobClaim? = null

        override suspend fun rescheduleRetry(jobId: String, nextAttemptAt: Instant, attemptNumber: Int) = Unit

        override suspend fun complete(jobId: String, completedAt: Instant) = Unit

        override suspend fun fail(jobId: String, failedAt: Instant) = Unit

        override suspend fun cancel(jobId: String, cancelledAt: Instant) {
            lastCancelledPublicationId = jobId
        }
    }
}
