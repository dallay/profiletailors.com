package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.publishing.domain.DateCount
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.NotificationCategory
import com.profiletailors.smp.publishing.domain.NotificationEvent
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SocialPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PublishingSchedulingConfigurationTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC)

    private val publicationJobRepository = NoOpPublicationJobRepository()
    private val publicationRepository = NoOpPublicationRepository()
    private val socialAccountRepository = NoOpSocialAccountRepository()
    private val mediaAssetResolver = NoOpMediaAssetResolver()
    private val deliveryAttemptRepository = NoOpDeliveryAttemptRepository()
    private val providerCapabilityValidator = ProviderCapabilityValidator { _: ProviderCapabilityValidationInput -> }
    private val socialPublisher = SocialPublisher {
        ProviderPublishResult(externalPublicationId = "external-1")
    }

    private val configuration = PublishingSchedulingConfiguration(
        publicationJobRepository = publicationJobRepository,
        publicationRepository = publicationRepository,
        socialAccountRepository = socialAccountRepository,
        mediaAssetResolver = mediaAssetResolver,
        deliveryAttemptRepository = deliveryAttemptRepository,
        providerCapabilityValidator = providerCapabilityValidator,
        socialPublisher = socialPublisher,
        clock = fixedClock,
    )

    @Test
    fun `publishingTaskScheduler creates thread pool scheduler with expected prefix`() {
        val scheduler = configuration.publishingTaskScheduler()

        assertInstanceOf(ThreadPoolTaskScheduler::class.java, scheduler)
        val threadPoolScheduler = scheduler as ThreadPoolTaskScheduler
        assertEquals("publishing-worker-", threadPoolScheduler.threadNamePrefix)
    }

    @Test
    fun `publishingRetryPolicy uses configured values`() {
        val retryPolicy = configuration.publishingRetryPolicy(
            properties = PublishingWorkerProperties(
                maxRetries = 5,
                retryBackoff = Duration.ofMinutes(7),
            ),
        )

        assertEquals(true, retryPolicy.shouldRetry(currentAttemptNumber = 5, retryable = true))
        assertEquals(false, retryPolicy.shouldRetry(currentAttemptNumber = 6, retryable = true))
        assertEquals(Instant.parse("2026-05-26T12:07:00Z"), retryPolicy.nextRetryAt(fixedClock.instant()))
        assertEquals(6, retryPolicy.maxAttempts())
    }

    @Test
    fun `publishingJobExecutor and worker beans are created`() {
        val retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5))
        val notificationEventRepository = NoOpNotificationEventRepository()

        val executor = configuration.publishingJobExecutor(
            notificationEventRepository = notificationEventRepository,
            publishingRetryPolicy = retryPolicy,
        )
        val worker = configuration.publishingWorker(
            publicationJobRepository = publicationJobRepository,
            publicationRepository = publicationRepository,
            publishingJobExecutor = executor,
        )

        assertNotNull(executor)
        assertNotNull(worker)
    }

    @Test
    fun `publishingWorkerLifecycle schedules polling and recovery tasks when enabled`() {
        val retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5))
        val executor = configuration.publishingJobExecutor(
            notificationEventRepository = NoOpNotificationEventRepository(),
            publishingRetryPolicy = retryPolicy,
        )
        val worker = configuration.publishingWorker(
            publicationJobRepository = publicationJobRepository,
            publicationRepository = publicationRepository,
            publishingJobExecutor = executor,
        )
        val scheduler = RecordingTaskScheduler()

        configuration.publishingWorkerLifecycle(
            properties = PublishingWorkerProperties(
                enabled = true,
                pollInterval = Duration.ofSeconds(30),
                blockedRecoveryInterval = Duration.ofMinutes(5),
            ),
            publishingTaskScheduler = scheduler,
            publishingWorker = worker,
        )

        assertEquals(2, scheduler.fixedRateSchedules.size)
        assertEquals(Duration.ofSeconds(30), scheduler.fixedRateSchedules[0].period)
        assertEquals(Duration.ofMinutes(5), scheduler.fixedRateSchedules[1].period)
    }

    @Test
    fun `publishingWorkerLifecycle does not schedule tasks when disabled`() {
        val retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5))
        val executor = configuration.publishingJobExecutor(
            notificationEventRepository = NoOpNotificationEventRepository(),
            publishingRetryPolicy = retryPolicy,
        )
        val worker = configuration.publishingWorker(
            publicationJobRepository = publicationJobRepository,
            publicationRepository = publicationRepository,
            publishingJobExecutor = executor,
        )
        val scheduler = RecordingTaskScheduler()

        configuration.publishingWorkerLifecycle(
            properties = PublishingWorkerProperties(
                enabled = false,
                pollInterval = Duration.ofSeconds(30),
                blockedRecoveryInterval = Duration.ofMinutes(5),
            ),
            publishingTaskScheduler = scheduler,
            publishingWorker = worker,
        )

        assertEquals(0, scheduler.fixedRateSchedules.size)
    }

    private class RecordingTaskScheduler : TaskScheduler {
        data class FixedRateSchedule(
            val startTime: Instant,
            val period: Duration,
        )

        val fixedRateSchedules = mutableListOf<FixedRateSchedule>()

        override fun schedule(task: Runnable, trigger: Trigger): ScheduledFuture<*> = CompletedScheduledFuture

        override fun schedule(task: Runnable, startTime: Instant): ScheduledFuture<*> = CompletedScheduledFuture

        override fun scheduleAtFixedRate(
            task: Runnable,
            startTime: Instant,
            period: Duration,
        ): ScheduledFuture<*> {
            fixedRateSchedules += FixedRateSchedule(startTime, period)
            return CompletedScheduledFuture
        }

        override fun scheduleAtFixedRate(task: Runnable, period: Duration): ScheduledFuture<*> {
            fixedRateSchedules += FixedRateSchedule(Instant.EPOCH, period)
            return CompletedScheduledFuture
        }

        override fun scheduleWithFixedDelay(
            task: Runnable,
            startTime: Instant,
            delay: Duration,
        ): ScheduledFuture<*> = CompletedScheduledFuture

        override fun scheduleWithFixedDelay(task: Runnable, delay: Duration): ScheduledFuture<*> = CompletedScheduledFuture
    }

    private object CompletedScheduledFuture : ScheduledFuture<Unit> {
        override fun getDelay(unit: TimeUnit): Long = 0
        override fun compareTo(other: Delayed): Int = 0
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): Unit = Unit
        override fun get(timeout: Long, unit: TimeUnit): Unit = Unit
    }

    private class NoOpPublicationJobRepository : PublicationJobRepository {
        override suspend fun enqueue(job: PublicationJob) = Unit
        override suspend fun replaceForPublication(job: PublicationJob) = Unit
        override suspend fun claimNextDue(now: Instant, workerId: String): PublicationJobClaim? = null
        override suspend fun rescheduleRetry(jobId: String, nextAttemptAt: Instant, attemptNumber: Int) = Unit
        override suspend fun complete(jobId: String, completedAt: Instant) = Unit
        override suspend fun fail(jobId: String, failedAt: Instant) = Unit
        override suspend fun cancel(jobId: String, cancelledAt: Instant) = Unit
    }

    private class NoOpPublicationRepository : PublicationRepository {
        override suspend fun createDraft(draft: PublicationDraft): PublicationDraft = draft
        override suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft = draft
        override suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft? = publication()
        override suspend fun findInDateRange(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            socialAccountIds: Set<String>?,
            hydrateAssets: Boolean,
        ): List<PublicationDraft> = emptyList()
        override suspend fun countByDate(
            workspaceId: String,
            from: Instant,
            to: Instant,
            statuses: Set<PublicationStatus>?,
            timezone: String,
        ): List<DateCount> = emptyList()
        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) = Unit
        override suspend fun markFailed(publicationId: String, failedAt: Instant, reasonCode: String?, reasonMessage: String?) = Unit
        override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) = Unit
        override suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?) = Unit
        override suspend fun findBlockedForRecovery(maxRetries: Int): List<PublicationDraft> = emptyList()
        override suspend fun deleteById(workspaceId: String, publicationId: String) = Unit
    }

    private class NoOpSocialAccountRepository : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount = account
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount = account()
    }

    private class NoOpMediaAssetResolver : MediaAssetResolver {
        override suspend fun resolveReadyAssets(workspaceId: String, assetIds: List<String>): List<ResolvedAssetSummary> = emptyList()
    }

    private class NoOpDeliveryAttemptRepository : DeliveryAttemptRepository {
        override suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt = attempt
    }

    private class NoOpNotificationEventRepository : NotificationEventRepository {
        override suspend fun record(event: NotificationEvent): NotificationEvent = event
        override suspend fun findByWorkspace(
            workspaceId: String,
            socialAccountId: String?,
            publicationId: String?,
            categories: Set<NotificationCategory>?,
            limit: Int,
        ): List<NotificationEvent> = emptyList()
    }

    private companion object {
        fun publication() = PublicationDraft(
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

        fun account() = SocialAccount(
            id = "account-1",
            socialConnectionId = "connection-1",
            workspaceId = "workspace-1",
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "linkedin-account-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Yuniel",
            status = SocialConnectionStatus.ACTIVE,
        )
    }
}
