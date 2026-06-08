package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
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
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            socialConnectionRepository = connectionRepository,
            socialAccountRepository = accountRepository,
            clock = fixedClock,
        )

        val result = handler.handle(
            CompleteLinkedInConnectionCommand(
                authorizationCode = "oauth-code-123",
                redirectUri = "https://app.example.com/callback",
            ),
        )

        assertEquals("workspace-1", result.workspaceId)
        assertEquals(SocialProvider.LINKEDIN, result.provider)
        assertEquals("linkedin-account-1", result.account.providerAccountId)
        assertNotNull(connectionRepository.lastSaved)
        assertNotNull(accountRepository.lastSaved)
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
        override suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult =
            ProviderConnectionResult(
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

    private class InMemoryPublicationRepository(
        seed: PublicationDraft? = null,
    ) : PublicationRepository {
        private val items = linkedMapOf<String, PublicationDraft>()

        init {
            if (seed != null) items[seed.id] = seed
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

        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) = Unit

        override suspend fun markFailed(publicationId: String, failedAt: Instant, reasonCode: String?, reasonMessage: String?) = Unit

        override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) = Unit
    }

    private class InMemoryPublicationAssetRepository(
        private val assets: List<PublicationAsset>,
    ) : PublicationAssetRepository {
        override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: Collection<String>): List<PublicationAsset> =
            assets.filter { it.workspaceId == workspaceId && it.id in assetIds }
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
