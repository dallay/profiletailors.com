package com.profiletailors.smp.publishing.infrastructure.scheduling

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.publishing.domain.DateCount
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.NotificationEvent
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.ProviderUploadException
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.ReconnectReason
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SocialPublisher
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@Suppress("LargeClass")
class PublishingWorkerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `failure taxonomy defines canonical categories retryability and final behavior`() {
        val expected = mapOf(
            PublishingFailureCategory.MEDIA_NOT_FOUND to Pair(false, false),
            PublishingFailureCategory.MEDIA_UNAVAILABLE to Pair(true, false),
            PublishingFailureCategory.PROVIDER_VALIDATION_FAILED to Pair(false, false),
            PublishingFailureCategory.PROVIDER_RATE_LIMITED to Pair(true, false),
            PublishingFailureCategory.PROVIDER_UNAVAILABLE to Pair(true, false),
            PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED to Pair(false, true),
            PublishingFailureCategory.ACCOUNT_UNAVAILABLE to Pair(false, false),
            PublishingFailureCategory.PUBLISHING_FAILED to Pair(false, false),
        )

        PublishingFailureCategory.entries.map { it.code }.toSet() shouldBe expected.keys.map { it.code }.toSet()
        expected.forEach { (category, defaults) ->
            withClue(category.code) {
                category.retryable shouldBe defaults.first
                category.blocked shouldBe defaults.second
                PublishingFailure(category).retryable shouldBe category.retryable
            }
        }
    }

    @Test
    fun `worker emits claimed lifecycle event with required schema`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 2, fixedClock.instant()))
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )
        val logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            worker.pollOnce()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val message = appender.list.single { it.formattedMessage.contains("event=publishing_attempt_claimed") }
            .formattedMessage
        message shouldContain "publicationId=pub-1"
        message shouldContain "jobId=job-1"
        message shouldContain "workspaceId=workspace-1"
        message shouldContain "attemptNumber=2"
        message shouldContain "provider=LINKEDIN"
    }

    @Test
    fun `executor emits succeeded lifecycle event after persistence`() = runTest {
        val executor = PublishingJobExecutor(
            publicationJobRepository = InMemoryJobRepository(null),
            publicationRepository = InMemoryPublicationRepository(successPublication()),
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            executor.executeClaim(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val message = appender.list.single { it.formattedMessage.contains("event=publishing_attempt_succeeded") }
            .formattedMessage
        message shouldContain "publicationId=pub-1"
        message shouldContain "jobId=job-1"
        message shouldContain "attemptNumber=1"
        message shouldContain "provider=LINKEDIN"
        message shouldContain "outcome=SUCCEEDED"
        message shouldContain "durationMs=0"
    }

    @Test
    fun `executor does not emit success lifecycle event when transaction fails`() = runTest {
        val executor = PublishingJobExecutor(
            publicationJobRepository = InMemoryJobRepository(null),
            publicationRepository = InMemoryPublicationRepository(successPublication()),
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = FailingTransactionRunner(),
            clock = fixedClock,
        )
        val logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            runCatching {
                executor.executeClaim(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
            }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        appender.list.any { it.formattedMessage.contains("event=publishing_attempt_succeeded") } shouldBe false
    }

    @Test
    fun `worker completes successful publish`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        val claim = worker.pollOnce()

        claim.shouldNotBeNull()
        publicationRepository.publishedPublicationId shouldBe "pub-1"
        jobRepository.completedJobId shouldBe "job-1"
        attemptRepository.lastAttempt?.outcome shouldBe DeliveryAttemptOutcome.SUCCEEDED
    }

    @Test
    fun `executor emits retry scheduled lifecycle event after persistence`() = runTest {
        val executor = PublishingJobExecutor(
            publicationJobRepository = InMemoryJobRepository(null),
            publicationRepository = InMemoryPublicationRepository(successPublication()),
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RetryableFailingPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            executor.executeClaim(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val message = appender.list.single { it.formattedMessage.contains("event=publishing_retry_scheduled") }
            .formattedMessage
        message shouldContain "outcome=FAILED"
        message shouldContain "failureCategory=PROVIDER_UNAVAILABLE"
        message shouldContain "retryable=true"
        message shouldContain "durationMs=0"
    }

    @Test
    fun `worker reschedules retryable failure`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RetryableFailingPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        jobRepository.retriedJobId shouldBe "job-1"
        jobRepository.retryAt shouldBe Instant.parse("2026-05-26T12:05:00Z")
        attemptRepository.lastAttempt?.outcome shouldBe DeliveryAttemptOutcome.FAILED
    }

    @Test
    fun `executor emits terminal failure lifecycle event after persistence`() = runTest {
        val executor = PublishingJobExecutor(
            publicationJobRepository = InMemoryJobRepository(null),
            publicationRepository = InMemoryPublicationRepository(successPublication()),
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = ThrowingCapabilityValidator(
                PublicationValidationException("unsafe raw body"),
            ),
            socialPublisher = NeverPublishesPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            executor.executeClaim(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val message = appender.list.single { it.formattedMessage.contains("event=publishing_terminal_failure") }
            .formattedMessage
        message shouldContain "outcome=FAILED"
        message shouldContain "failureCategory=PROVIDER_VALIDATION_FAILED"
        message shouldContain "retryable=false"
        message shouldContain "durationMs=0"
        message.contains("unsafe raw body") shouldBe false
    }

    @Test
    fun `worker marks terminal failure when retry budget is exhausted`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 4, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RetryableFailingPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publicationRepository.failedPublicationId shouldBe "pub-1"
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `worker persists canonical code for retryable failure after exhaustion`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 4, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RateLimitedPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        attemptRepository.lastAttempt?.providerErrorCode shouldBe "PROVIDER_RATE_LIMITED"
        publicationRepository.failedReasonCode shouldBe "PROVIDER_RATE_LIMITED"
        publicationRepository.failedReasonMessage shouldBe null
    }

    @Test
    fun `worker maps unexpected exceptions to canonical publishing failed code without raw message`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = RawFailingPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        attemptRepository.lastAttempt?.providerErrorCode shouldBe "PUBLISHING_FAILED"
        publicationRepository.failedReasonCode shouldBe "PUBLISHING_FAILED"
        publicationRepository.failedReasonMessage shouldBe null
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `executor emits blocked lifecycle event after persistence`() = runTest {
        val executor = PublishingJobExecutor(
            publicationJobRepository = InMemoryJobRepository(null),
            publicationRepository = InMemoryPublicationRepository(successPublication()),
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = ReconnectPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            executor.executeClaim(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val message = appender.list.single { it.formattedMessage.contains("event=publishing_blocked") }
            .formattedMessage
        message shouldContain "outcome=BLOCKED"
        message shouldContain "failureCategory=ACCOUNT_RECONNECT_REQUIRED"
        message shouldContain "retryable=false"
        message shouldContain "durationMs=0"
    }

    @Test
    fun `worker stores reconnect blocked reason as canonical category`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = ReconnectPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publicationRepository.blockedReason shouldBe "ACCOUNT_RECONNECT_REQUIRED"
        publicationRepository.blockedPublicationId shouldBe "pub-1"
        jobRepository.completedJobId shouldBe "job-1"
    }

    @Test
    fun `worker records media resolution failure before provider dispatch and does not call provider`() = runTest {
        val publication = successPublication().copy(assetIds = listOf("asset-missing"))
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = FailingMediaAssetResolver(
                PublishingFailureException(PublishingFailure.mediaNotFound()),
            ),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publisher.called shouldBe false
        attemptRepository.lastAttempt?.outcome shouldBe DeliveryAttemptOutcome.FAILED
        attemptRepository.lastAttempt?.providerErrorCode shouldBe "MEDIA_NOT_FOUND"
        publicationRepository.failedReasonCode shouldBe "MEDIA_NOT_FOUND"
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `worker records unavailable media before provider dispatch and reschedules without calling provider`() =
        runTest {
            val publication = successPublication().copy(assetIds = listOf("asset-unavailable"))
            val publicationRepository = InMemoryPublicationRepository(publication)
            val jobRepository =
                InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
            val attemptRepository = InMemoryAttemptRepository()
            val publisher = NeverPublishesPublisher()
            val executor = PublishingJobExecutor(
                publicationJobRepository = jobRepository,
                publicationRepository = publicationRepository,
                socialAccountRepository = InMemoryAccountRepository(successAccount()),
                mediaAssetResolver = FailingMediaAssetResolver(
                    MediaServiceUnavailableException(
                        "GET https://storage.example.com/bucket/assets/workspace-1/raw.png " +
                            "Authorization: Bearer secret-token",
                    ),
                ),
                deliveryAttemptRepository = attemptRepository,
                notificationEventRepository = null,
                providerCapabilityValidator = AcceptingCapabilityValidator(),
                socialPublisher = publisher,
                retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
                transactionRunner = NoOpTransactionRunner(),
                clock = fixedClock,
            )
            val worker = PublishingWorker(
                publicationJobRepository = jobRepository,
                publicationRepository = publicationRepository,
                executor = executor,
                transactionRunner = NoOpTransactionRunner(),
                clock = fixedClock,
                workerId = "worker-1",
            )

            worker.pollOnce()

            publisher.called shouldBe false
            attemptRepository.lastAttempt?.outcome shouldBe DeliveryAttemptOutcome.FAILED
            attemptRepository.lastAttempt?.providerErrorCode shouldBe "MEDIA_UNAVAILABLE"
            attemptRepository.lastAttempt?.providerMessage shouldBe "MediaServiceUnavailableException"
            jobRepository.retriedJobId shouldBe "job-1"
        }

    @Test
    fun `worker maps media not ready exception to missing media before provider dispatch`() = runTest {
        val publication = successPublication().copy(assetIds = listOf("asset-missing"))
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = FailingMediaAssetResolver(
                AssetNotReadyException(
                    "asset-missing",
                    "bucket/assets/workspace-1/raw.png token=secret",
                ),
            ),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publisher.called shouldBe false
        attemptRepository.lastAttempt?.providerErrorCode shouldBe "MEDIA_NOT_FOUND"
        attemptRepository.lastAttempt?.providerMessage shouldBe "AssetNotReadyException"
        publicationRepository.failedReasonCode shouldBe "MEDIA_NOT_FOUND"
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `worker preserves linkedin upload error message when ProviderUploadException is thrown`() = runTest {
        val linkedInError =
            """LinkedIn binary upload failed: 403 {"status":403,"message":"Access denied due to insufficient permissions"}"""
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = ProviderUploadFailingPublisher(linkedInError),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        attemptRepository.lastAttempt?.outcome shouldBe DeliveryAttemptOutcome.FAILED
        attemptRepository.lastAttempt?.providerErrorCode shouldBe "PUBLISHING_FAILED"
        attemptRepository.lastAttempt?.providerMessage shouldBe linkedInError
        publicationRepository.failedReasonCode shouldBe "PUBLISHING_FAILED"
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `worker redacts unsafe diagnostics from publication attempts and notifications`() = runTest {
        val unsafeDiagnostic = """
            {"message":"provider body","access_token":"secret-token","authorization":"Bearer secret"}
            https://api.linkedin.com/rest/posts?token=secret-token
            at com.provider.Client.publish(Client.kt:42)
            workspace-550e8400-e29b-41d4-a716-446655440000 bucket/assets/workspace-1/raw.png
        """.trimIndent()
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 4, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val notificationRepository = InMemoryNotificationEventRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = notificationRepository,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = UnsafeDiagnosticPublisher(unsafeDiagnostic),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        attemptRepository.lastAttempt?.providerErrorCode shouldBe "PROVIDER_UNAVAILABLE"
        attemptRepository.lastAttempt?.providerMessage shouldBe null
        publicationRepository.failedReasonCode shouldBe "PROVIDER_UNAVAILABLE"
        publicationRepository.failedReasonMessage shouldBe null
        notificationRepository.lastEvent?.message shouldBe "PROVIDER_UNAVAILABLE"
        val persistedText = listOfNotNull(
            attemptRepository.lastAttempt?.providerMessage,
            publicationRepository.failedReasonCode,
            publicationRepository.failedReasonMessage,
            notificationRepository.lastEvent?.message,
            notificationRepository.lastEvent?.suggestedAction,
        ).joinToString(" ")
        listOf(
            "provider body",
            "secret-token",
            "Bearer secret",
            "https://api.linkedin.com",
            "com.provider.Client",
            "Client.kt:42",
            "550e8400-e29b-41d4-a716-446655440000",
            "bucket/assets/workspace-1/raw.png",
        ).forEach { unsafeValue ->
            withClue(unsafeValue) { persistedText.contains(unsafeValue) shouldBe false }
        }
    }

    @Test
    fun `worker maps publication validation exception to provider validation category`() = runTest {
        val publicationRepository = InMemoryPublicationRepository(successPublication())
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val attemptRepository = InMemoryAttemptRepository()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(successAccount()),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = attemptRepository,
            notificationEventRepository = null,
            providerCapabilityValidator = ThrowingCapabilityValidator(
                PublicationValidationException("body leaked-token https://provider.example"),
            ),
            socialPublisher = NeverPublishesPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        attemptRepository.lastAttempt?.providerErrorCode shouldBe "PROVIDER_VALIDATION_FAILED"
        attemptRepository.lastAttempt?.providerMessage shouldBe "PublicationValidationException"
        publicationRepository.failedReasonCode shouldBe "PROVIDER_VALIDATION_FAILED"
        publicationRepository.failedReasonMessage shouldBe null
        jobRepository.failedJobId shouldBe "job-1"
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
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = SuccessfulPublisher(),
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.scanBlockedForRecovery()

        publicationRepository.updatedDraft?.status shouldBe PublicationStatus.QUEUED
        publicationRepository.updatedDraft?.retryCount shouldBe 1
        publicationRepository.updatedDraft?.scheduledFor shouldBe Instant.parse("2026-05-26T12:01:00Z")
        jobRepository.replacedJob?.publicationId shouldBe "pub-1"
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

    private class NoOpTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class FailingTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            block()
            error("transaction failed")
        }
    }

    private class InMemoryJobRepository(private val claim: PublicationJobClaim?) : PublicationJobRepository {
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
        var failedReasonCode: String? = null
        var failedReasonMessage: String? = null
        var blockedPublicationId: String? = null
        var blockedReason: String? = null
        var updatedDraft: PublicationDraft? = null
        override suspend fun createDraft(draft: PublicationDraft): PublicationDraft = draft
        override suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft {
            updatedDraft = draft
            return draft
        }
        override suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft? =
            publication
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
        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) {
            publishedPublicationId = publicationId
        }
        override suspend fun markFailed(
            publicationId: String,
            failedAt: Instant,
            reasonCode: String?,
            reasonMessage: String?,
        ) {
            failedPublicationId = publicationId
            failedReasonCode = reasonCode
            failedReasonMessage = reasonMessage
        }
        override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) = Unit
        override suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?) {
            blockedPublicationId = publicationId
            blockedReason = reason
        }
        override suspend fun deleteUnpublished(workspaceId: String, publicationId: String): Boolean = false
        override suspend fun findBlockedForRecovery(maxRetries: Int): List<PublicationDraft> =
            blockedForRecovery.filter { it.retryCount < maxRetries }
    }

    private class InMemoryAccountRepository(private val account: SocialAccount) : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount = account
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? = account
    }

    private class InMemoryMediaAssetResolver(private val assets: List<ResolvedAssetSummary>) : MediaAssetResolver {
        override suspend fun resolveReadyAssets(
            workspaceId: String,
            assetIds: List<String>,
        ): List<ResolvedAssetSummary> = assets.filter { it.workspaceId == workspaceId && it.assetId in assetIds }
    }

    private class InMemoryAttemptRepository : DeliveryAttemptRepository {
        var lastAttempt: DeliveryAttempt? = null
        override suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt {
            lastAttempt = attempt
            return attempt
        }
    }

    private class InMemoryNotificationEventRepository : NotificationEventRepository {
        var lastEvent: NotificationEvent? = null
        override suspend fun record(event: NotificationEvent): NotificationEvent {
            lastEvent = event
            return event
        }

        override suspend fun findByWorkspace(
            workspaceId: String,
            socialAccountId: String?,
            publicationId: String?,
            categories: Set<com.profiletailors.smp.publishing.domain.NotificationCategory>?,
            limit: Int,
        ): List<NotificationEvent> = listOfNotNull(lastEvent)
    }

    private class AcceptingCapabilityValidator : ProviderCapabilityValidator {
        override fun validate(input: ProviderCapabilityValidationInput) = Unit
    }

    private class ThrowingCapabilityValidator(private val exception: RuntimeException) : ProviderCapabilityValidator {
        override fun validate(input: ProviderCapabilityValidationInput): Unit = throw exception
    }

    private class SuccessfulPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            ProviderPublishResult(externalPublicationId = "external-1")
    }

    private class RetryableFailingPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw RetryablePublishingException("transient provider error")
    }

    private class RateLimitedPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw PublishingFailureException(PublishingFailure.providerRateLimited())
    }

    private class RawFailingPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw IllegalStateException("com.example.ProviderClient token=secret bucket/key")
    }

    private class UnsafeDiagnosticPublisher(private val diagnostic: String) : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw PublishingFailureException(PublishingFailure.providerUnavailable(diagnostic))
    }

    private class ReconnectPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw ReconnectRequiredException(
                "Reconnect failed for token=secret https://provider.example/auth",
                ReconnectReason.INVALID_GRANT,
            )
    }

    private class ProviderUploadFailingPublisher(private val message: String) : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw ProviderUploadException(message)
    }

    private class FailingMediaAssetResolver(private val exception: RuntimeException) : MediaAssetResolver {
        override suspend fun resolveReadyAssets(
            workspaceId: String,
            assetIds: List<String>,
        ): List<ResolvedAssetSummary> = throw exception
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
    fun `preflight fails DISABLED account terminally without calling publisher`() = runTest {
        val disabledAccount = successAccount().copy(status = SocialConnectionStatus.DISABLED)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(disabledAccount),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publisher.called shouldBe false
        publicationRepository.failedPublicationId shouldBe "pub-1"
        publicationRepository.failedReasonCode shouldBe "ACCOUNT_UNAVAILABLE"
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `preflight blocks REQUIRES_RECONNECT account without calling publisher`() = runTest {
        val reconnectAccount = successAccount().copy(status = SocialConnectionStatus.REQUIRES_RECONNECT)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(reconnectAccount),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publisher.called shouldBe false
        publicationRepository.blockedPublicationId shouldBe "pub-1"
        jobRepository.completedJobId shouldBe "job-1"
    }

    @Test
    fun `preflight fails PENDING account terminally without calling publisher`() = runTest {
        val pendingAccount = successAccount().copy(status = SocialConnectionStatus.PENDING)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(pendingAccount),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        publisher.called shouldBe false
        publicationRepository.failedPublicationId shouldBe "pub-1"
        publicationRepository.failedReasonCode shouldBe "ACCOUNT_UNAVAILABLE"
        jobRepository.failedJobId shouldBe "job-1"
    }

    @Test
    fun `preflight fails DELETED account terminally without calling publisher`() = runTest {
        val deletedAccount = successAccount().copy(status = SocialConnectionStatus.DELETED)
        val publication = successPublication()
        val publicationRepository = InMemoryPublicationRepository(publication)
        val jobRepository =
            InMemoryJobRepository(PublicationJobClaim("job-1", "pub-1", "workspace-1", 1, fixedClock.instant()))
        val publisher = NeverPublishesPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            socialAccountRepository = InMemoryAccountRepository(deletedAccount),
            mediaAssetResolver = InMemoryMediaAssetResolver(emptyList()),
            deliveryAttemptRepository = InMemoryAttemptRepository(),
            notificationEventRepository = null,
            providerCapabilityValidator = AcceptingCapabilityValidator(),
            socialPublisher = publisher,
            retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5)),
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = executor,
            transactionRunner = NoOpTransactionRunner(),
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.pollOnce()

        // Publisher was never called
        publisher.called shouldBe false
        // Publication was failed terminally (not blocked — DELETED is terminal)
        publicationRepository.failedPublicationId shouldBe "pub-1"
        jobRepository.failedJobId shouldBe "job-1"
    }
}
