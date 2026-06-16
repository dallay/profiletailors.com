package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.DateCount
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SocialPublisher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PublishingWorkerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `worker completes successful publish`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository = InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        val claim = worker.pollOnce()

        assertNotNull(claim)
        assertEquals("pub-1", publicationRepository.publishedPublicationId)
        assertEquals("job-1", jobRepository.completedJobId)
        assertEquals(DeliveryAttemptOutcome.SUCCEEDED, attemptRepository.lastAttempt?.outcome)
    }

    @Test
    fun `worker reschedules retryable failure`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository = InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RetryableFailingPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        assertEquals("job-1", jobRepository.retriedJobId)
        assertEquals(Instant.parse("2026-05-26T12:05:00Z"), jobRepository.retryAt)
        assertEquals(DeliveryAttemptOutcome.FAILED, attemptRepository.lastAttempt?.outcome)
    }

    @Test
    fun `worker marks terminal failure when retry budget is exhausted`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository = InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 4, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RetryableFailingPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        assertEquals("pub-1", publicationRepository.failedPublicationId)
        assertEquals("job-1", jobRepository.failedJobId)
    }

    @Test
    fun `worker requeues blocked publications when scan finds recovered accounts`() = runTest {
        val blockedPublication = blockedPublication()
        val publicationRepository = InMemoryPublicationRepository(
            publication = blockedPublication,
            blockedForRecovery = listOf(blockedPublication),
        )
        val jobRepository = InMemoryJobRepository(null)
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.scanBlockedForRecovery()

        assertEquals(PublicationStatus.QUEUED, publicationRepository.updatedDraft?.status)
        assertEquals(1, publicationRepository.updatedDraft?.retryCount)
        assertEquals(Instant.parse("2026-05-26T12:01:00Z"), publicationRepository.updatedDraft?.scheduledFor)
        assertEquals("pub-1", jobRepository.replacedJob?.publicationId)
    }

    private fun successPublication() = PublicationDraft(
        id = "pub-1",
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        status = PublicationStatus.QUEUED,
        scheduleMode = ScheduleMode.NOW,
        priority = false,
        bodyText = "hello",
    )

    private fun blockedPublication() = successPublication().copy(
        status = PublicationStatus.BLOCKED,
        blockedAt = fixedClock.instant().minus(Duration.ofMinutes(10)),
        blockedReason = "Account requires reconnect.",
    )

    private fun successAccount() = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "linkedin-account-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Yuniel",
        status = SocialConnectionStatus.ACTIVE,
    )

    private class InMemoryJobRepository(
        private val claim: PublicationJobClaim?,
    ) : PublicationJobRepository {
        var completedJobId: String? = null
        var retriedJobId: String? = null
        var retryAt: Instant? = null
        var failedJobId: String? = null
        var replacedJob: com.profiletailors.smp.publishing.domain.PublicationJob? = null

        override suspend fun enqueue(job: com.profiletailors.smp.publishing.domain.PublicationJob) = Unit
        override suspend fun replaceForPublication(job: com.profiletailors.smp.publishing.domain.PublicationJob) {
            replacedJob = job
        }
        override suspend fun claimNextDue(now: Instant, workerId: String): PublicationJobClaim? = claim
        override suspend fun rescheduleRetry(jobId: String, nextAttemptAt: Instant, attemptNumber: Int) {
            retriedJobId = jobId
            retryAt = nextAttemptAt
        }
        override suspend fun complete(jobId: String, completedAt: Instant) {
            completedJobId = jobId
        }
        override suspend fun fail(jobId: String, failedAt: Instant) {
            failedJobId = jobId
        }
        override suspend fun cancel(jobId: String, cancelledAt: Instant) = Unit
    }

    private class InMemoryPublicationRepository(
        private val publication: PublicationDraft,
        private val blockedForRecovery: List<PublicationDraft> = emptyList(),
    ) : PublicationRepository {
        var publishedPublicationId: String? = null
        var failedPublicationId: String? = null
        var blockedPublicationId: String? = null
        var updatedDraft: PublicationDraft? = null
        override suspend fun createDraft(draft: PublicationDraft): PublicationDraft = draft
        override suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft {
            updatedDraft = draft
            return draft
        }
        override suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft? = publication
        override suspend fun findInDateRange(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            socialAccountIds: Set<String>?,
        ): List<PublicationDraft> = emptyList()
        override suspend fun countByDate(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            timezone: String,
        ): List<DateCount> = emptyList()
        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) {
            publishedPublicationId = publicationId
        }
        override suspend fun markFailed(publicationId: String, failedAt: Instant, reasonCode: String?, reasonMessage: String?) {
            failedPublicationId = publicationId
        }
        override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) = Unit
        override suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?) {
            blockedPublicationId = publicationId
        }
        override suspend fun findBlockedForRecovery(
            maxRetries: Int,
        ): List<PublicationDraft> = blockedForRecovery.filter { it.retryCount < maxRetries }
    }

    private class InMemoryAccountRepository(
        private val account: SocialAccount,
    ) : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount = account
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? = account
    }

    private class InMemoryAssetRepository(
        private val assets: List<PublicationAsset>,
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

    private class InMemoryAttemptRepository : DeliveryAttemptRepository {
        var lastAttempt: DeliveryAttempt? = null
        override suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt {
            lastAttempt = attempt
            return attempt
        }
    }

    private class AcceptingCapabilityValidator : ProviderCapabilityValidator {
        override fun validate(input: ProviderCapabilityValidationInput) = Unit
    }

    private class SuccessfulPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            ProviderPublishResult(externalPublicationId = "external-1")
    }

    private class RetryableFailingPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult {
            throw RetryablePublishingException("transient provider error")
        }
    }

    private class NeverPublishesPublisher : SocialPublisher {
        var called = false
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult {
            called = true
            throw AssertionError("Should not be called — preflight should block")
        }
    }

    // ===== Preflight gate tests =====

    @Test
    fun `preflight blocks DISABLED account without calling publisher`() = runTest {
        val disabledAccount = successAccount().copy(status = SocialConnectionStatus.DISABLED)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(disabledAccount),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        // Publisher was never called
        assertEquals(false, publisher.called)
        // Publication was marked blocked
        assertEquals("pub-1", publicationRepository.blockedPublicationId)
        // Job was completed (not left hanging)
        assertEquals("job-1", jobRepository.completedJobId)
    }

    @Test
    fun `preflight blocks REQUIRES_RECONNECT account without calling publisher`() = runTest {
        val reconnectAccount = successAccount().copy(status = SocialConnectionStatus.REQUIRES_RECONNECT)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(reconnectAccount),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        assertEquals(false, publisher.called)
        assertEquals("pub-1", publicationRepository.blockedPublicationId)
        assertEquals("job-1", jobRepository.completedJobId)
    }

    @Test
    fun `preflight fails DELETED account terminally without calling publisher`() = runTest {
        val deletedAccount = successAccount().copy(status = SocialConnectionStatus.DELETED)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository = InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(deletedAccount),
            publicationAssetRepository = InMemoryAssetRepository(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        // Publisher was never called
        assertEquals(false, publisher.called)
        // Publication was failed terminally (not blocked — DELETED is terminal)
        assertEquals("pub-1", publicationRepository.failedPublicationId)
        assertEquals("job-1", jobRepository.failedJobId)
    }
}
