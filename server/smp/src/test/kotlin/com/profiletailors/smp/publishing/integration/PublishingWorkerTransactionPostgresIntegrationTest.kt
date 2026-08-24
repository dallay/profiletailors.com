package com.profiletailors.smp.publishing.integration

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.integration.support.countPublicationJobs
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptPhase
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.NotificationEvent
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
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
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcDeliveryAttemptRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcNotificationEventRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingFailure
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingFailureException
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingJobExecutor
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingWorker
import com.profiletailors.smp.publishing.infrastructure.scheduling.RetryablePublishingException
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * PostgreSQL integration tests proving that PublishingJobExecutor worker methods execute
 * DB writes atomically. Each test decorates a repository method to throw mid-sequence,
 * then queries tables directly to confirm rollback prevented partial state.
 *
 * Issue #192: Without transaction boundaries, a crash between sequential repository writes
 * leaves publications, publication_jobs, delivery_attempts, and notification_events out of sync.
 */
@Tag("postgres")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestStorageConfiguration::class)
class PublishingWorkerTransactionPostgresIntegrationTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var transactionManager: R2dbcTransactionManager

    @Autowired
    private lateinit var transactionalOperator: TransactionalOperator

    private lateinit var publicationRepository: PublicationRepository
    private lateinit var jobRepository: PublicationJobRepository
    private lateinit var deliveryAttemptRepository: DeliveryAttemptRepository
    private lateinit var notificationEventRepository: NotificationEventRepository
    private lateinit var transactionRunner: AtomicTransactionRunner
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-28T12:00:00Z"), ZoneOffset.UTC)
    private val retryPolicy = DeliveryRetryPolicy(3, Duration.ofMinutes(5))

    @BeforeEach
    fun setUpRepositories() = runTest {
        cleanupTestData()
        seedPrincipalWorkspaceAndAccount()
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
        jobRepository = R2dbcPublicationJobRepository(databaseClient)
        deliveryAttemptRepository = R2dbcDeliveryAttemptRepository(databaseClient)
        notificationEventRepository = R2dbcNotificationEventRepository(databaseClient, fixedClock)
        transactionRunner = R2dbcAtomicTransactionRunner(TransactionalOperator.create(transactionManager))
    }

    // ===== validateAndPublish tests =====

    @Test
    fun `validateAndPublish rolls back when markPublished fails after recording delivery attempt`() = runTest {
        val claim = seedPublicationAndJob("pub-validate-rollback")
        val executor = executorWithFailingPublicationRepository(failMarkPublished = true)

        executor.executeClaim(claim)

        // Rollback should prevent the SUCCEEDED attempt from surviving; the fallback failure handler
        // may mark the publication FAILED, but duplicate SUCCEEDED+FAILED attempt state is forbidden.
        assertPublication("pub-validate-rollback", PublicationStatus.FAILED)
        assertNoDeliveryAttemptWithOutcome("pub-validate-rollback", "SUCCEEDED")
        assertDeliveryAttemptWithOutcome("pub-validate-rollback", "FAILED")
    }

    @Test
    fun `validateAndPublish rolls back when complete job fails after markPublished`() = runTest {
        val claim = seedPublicationAndJob("pub-validate-job-rollback")
        val executor = executorWithFailingJobRepository(failComplete = true)

        executor.executeClaim(claim)

        // Rollback should revert the PUBLISHED state and SUCCEEDED attempt; fallback marks failure atomically.
        assertPublication("pub-validate-job-rollback", PublicationStatus.FAILED)
        assertNoDeliveryAttemptWithOutcome("pub-validate-job-rollback", "SUCCEEDED")
        assertDeliveryAttemptWithOutcome("pub-validate-job-rollback", "FAILED")
    }

    @Test
    fun `stale reclaim reconciles in-progress attempt without replaying provider create`() = runTest {
        val initialClaim = seedPublicationAndJob("pub-stale-recovery")
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-stale-recovery",
                publicationId = initialClaim.publicationId,
                publicationJobId = initialClaim.jobId,
                attemptNumber = initialClaim.attemptNumber,
                outcome = DeliveryAttemptOutcome.IN_PROGRESS,
                retryable = false,
                attemptedAt = fixedClock.instant(),
                operationKey = initialClaim.operationKey,
                claimVersion = initialClaim.claimVersion,
                phase = DeliveryAttemptPhase.PROVIDER_CREATE,
            ),
        )

        jobRepository.releaseExpiredClaims(
            now = fixedClock.instant().plus(Duration.ofMinutes(10)),
            staleGrace = Duration.ofMinutes(5),
        )
        val reclaimedClaim = requireNotNull(
            jobRepository.claimNextDue(
                now = fixedClock.instant().plus(Duration.ofMinutes(10)),
                workerId = "worker-reclaimer",
                claimLease = Duration.ofMinutes(2),
            ),
        )
        assertEquals(initialClaim.attemptNumber, reclaimedClaim.attemptNumber)
        assertEquals(initialClaim.operationKey, reclaimedClaim.operationKey)

        val publisher = CountingPublisher()
        createExecutor(socialPublisher = publisher).executeClaim(reclaimedClaim)

        assertEquals(0, publisher.calls)
        assertPublication("pub-stale-recovery", PublicationStatus.BLOCKED)
        assertDeliveryAttemptWithOutcome("pub-stale-recovery", "AMBIGUOUS")
        assertJobBlocked("pub-stale-recovery")
    }

    @Test
    fun `disabled account fails publication and job terminally`() = runTest {
        val claim = seedPublicationAndJob("pub-disabled-account")
        val executor = executorWithDisabledAccount()

        executor.executeClaim(claim)

        assertPublication("pub-disabled-account", PublicationStatus.FAILED)
        assertJobFailed("pub-disabled-account")
    }

    // ===== handlePublishFailure tests =====

    @Test
    fun `handlePublishFailure rolls back when markFailed fails after recording delivery attempt`() = runTest {
        val claim = seedPublicationAndJob("pub-failure-rollback", attemptNumber = 4) // exhausted retries
        val executor = executorWithFailingPublisherAndRepository(failMarkFailed = true)

        assertThrows(InjectedPublicationFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.executeClaim(claim)
            }
        }

        // The pre-provider attempt is retained for reconciliation when finalization fails.
        assertPublication("pub-failure-rollback", PublicationStatus.QUEUED)
        assertDeliveryAttemptWithOutcome("pub-failure-rollback", "IN_PROGRESS")
        assertJobClaimed("pub-failure-rollback")
    }

    @Test
    fun `handlePublishFailure with retry rolls back when reschedule fails after recording attempt`() = runTest {
        val claim = seedPublicationAndJob("pub-retry-rollback", attemptNumber = 1)
        val executor = executorWithRetryableFailingPublisherAndJobRepository(failRescheduleRetry = true)

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.executeClaim(claim)
            }
        }

        // The pre-provider attempt is intentionally retained for reconciliation when finalization fails.
        assertDeliveryAttemptWithOutcome("pub-retry-rollback", "IN_PROGRESS")
        assertJobClaimed("pub-retry-rollback")
    }

    // ===== requeueBlockedPublication tests =====

    @Test
    fun `requeueBlockedPublication removes previous delivery attempts before replacing job`() = runTest {
        val claim = seedPublicationAndJob("pub-requeue-attempts", status = PublicationStatus.BLOCKED)
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-requeue-blocked",
                publicationId = claim.publicationId,
                publicationJobId = claim.jobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = fixedClock.instant(),
            ),
        )
        val worker = PublishingWorker(
            publicationJobRepository = jobRepository,
            publicationRepository = publicationRepository,
            executor = createExecutor(),
            transactionRunner = transactionRunner,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.scanBlockedForRecovery()

        assertPublication("pub-requeue-attempts", PublicationStatus.QUEUED)
        assertNoDeliveryAttempt("pub-requeue-attempts")
        assertEquals(1L, databaseClient.countPublicationJobs("pub-requeue-attempts"))
    }

    @Test
    fun `requeueBlockedPublication rolls back when replaceJob fails after updateEditableDraft`() = runTest {
        seedPublicationAndJob("pub-requeue-rollback", status = PublicationStatus.BLOCKED)
        val failingJobRepository = FailingJobRepository(jobRepository, failReplace = true)
        val worker = PublishingWorker(
            publicationJobRepository = failingJobRepository,
            publicationRepository = publicationRepository,
            executor = createExecutor(jobRepository = failingJobRepository),
            transactionRunner = transactionRunner,
            clock = fixedClock,
            workerId = "worker-1",
        )

        worker.scanBlockedForRecovery()

        // Rollback should keep publication BLOCKED, not QUEUED
        assertPublication("pub-requeue-rollback", PublicationStatus.BLOCKED)
    }

    // ===== notification event rollback tests =====

    @Test
    fun `failPublicationTerminal rolls back when notification event record fails`() = runTest {
        val claim = seedPublicationAndJob("pub-notif-rollback")
        val failingNotificationRepo = FailingNotificationEventRepository(
            delegate = notificationEventRepository,
            failRecord = true,
        )
        val executor = createExecutor(
            socialAccount = testAccount().copy(status = SocialConnectionStatus.DELETED),
            notificationEventRepository = failingNotificationRepo,
        )

        assertThrows(InjectedNotificationEventFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.executeClaim(claim)
            }
        }

        // Rollback keeps the publication queued, claim active, and event absent.
        assertPublication("pub-notif-rollback", PublicationStatus.QUEUED)
        assertJobClaimed("pub-notif-rollback")
        assertNoNotificationEvent("pub-notif-rollback")
    }

    // ===== Helper methods =====

    private fun executorWithFailingPublicationRepository(
        failMarkPublished: Boolean = false,
        failMarkFailed: Boolean = false,
    ): PublishingJobExecutor {
        val decoratedRepo = FailingPublicationRepository(
            publicationRepository,
            failMarkPublished = failMarkPublished,
            failMarkFailed = failMarkFailed,
        )
        return createExecutor(publicationRepository = decoratedRepo)
    }

    private fun executorWithFailingJobRepository(
        failComplete: Boolean = false,
        failReplace: Boolean = false,
    ): PublishingJobExecutor {
        val decoratedRepo = FailingJobRepository(
            jobRepository,
            failComplete = failComplete,
            failReplace = failReplace,
        )
        return createExecutor(jobRepository = decoratedRepo)
    }

    private fun executorWithFailingPublisherAndRepository(failMarkFailed: Boolean = false): PublishingJobExecutor {
        val decoratedRepo = FailingPublicationRepository(
            publicationRepository,
            failMarkFailed = failMarkFailed,
        )
        return createExecutor(
            publicationRepository = decoratedRepo,
            socialPublisher = FailingPublisher(),
        )
    }

    private fun executorWithFailingPublisherAndJobRepository(
        failRescheduleRetry: Boolean = false,
    ): PublishingJobExecutor {
        val decoratedRepo = FailingJobRepository(
            jobRepository,
            failRescheduleRetry = failRescheduleRetry,
        )
        return createExecutor(
            jobRepository = decoratedRepo,
            socialPublisher = FailingPublisher(),
        )
    }

    private fun executorWithRetryableFailingPublisherAndJobRepository(
        failRescheduleRetry: Boolean = false,
    ): PublishingJobExecutor {
        val decoratedRepo = FailingJobRepository(
            jobRepository,
            failRescheduleRetry = failRescheduleRetry,
        )
        return createExecutor(
            jobRepository = decoratedRepo,
            socialPublisher = RetryableFailingPublisher(),
        )
    }

    private fun executorWithDisabledAccount(): PublishingJobExecutor = createExecutor(
        socialAccount = testAccount().copy(status = SocialConnectionStatus.DISABLED),
    )

    private fun createExecutor(
        publicationRepository: PublicationRepository = this.publicationRepository,
        jobRepository: PublicationJobRepository = this.jobRepository,
        socialAccount: SocialAccount = testAccount(),
        socialPublisher: SocialPublisher = SuccessfulPublisher(),
        notificationEventRepository: NotificationEventRepository? = null,
    ): PublishingJobExecutor = PublishingJobExecutor(
        publicationJobRepository = jobRepository,
        publicationRepository = publicationRepository,
        socialAccountRepository = InMemorySocialAccountRepository(socialAccount),
        mediaAssetResolver = InMemoryMediaAssetResolver(),
        deliveryAttemptRepository = deliveryAttemptRepository,
        notificationEventRepository = notificationEventRepository,
        providerCapabilityValidator = AcceptingCapabilityValidator(),
        socialPublisher = socialPublisher,
        retryPolicy = retryPolicy,
        transactionRunner = transactionRunner,
        clock = fixedClock,
    )

    private suspend fun seedPublicationAndJob(
        publicationId: String,
        status: PublicationStatus = PublicationStatus.QUEUED,
        attemptNumber: Int = 1,
    ): PublicationJobClaim {
        val draft = PublicationDraft(
            id = publicationId,
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = status,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "test publication",
            assetIds = emptyList(),
            scheduledFor = null,
            nextSlotAfter = null,
            createdAt = fixedClock.instant(),
        )
        publicationRepository.createDraft(draft)

        val jobId = "job-$publicationId"
        jobRepository.enqueue(
            com.profiletailors.smp.publishing.domain.PublicationJob(
                id = jobId,
                publicationId = publicationId,
                workspaceId = "workspace-1",
                status = JobStatus.PENDING,
                dueAt = fixedClock.instant(),
                priorityRank = 0,
                attemptCount = attemptNumber - 1,
                maxAttempts = 3,
            ),
        )

        return requireNotNull(
            jobRepository.claimNextDue(
                now = fixedClock.instant(),
                workerId = "worker-seed",
                claimLease = Duration.ofMinutes(2),
            ),
        )
    }

    private suspend fun assertPublication(publicationId: String, expectedStatus: PublicationStatus) {
        val row = databaseClient.sql("SELECT status FROM publications WHERE id = :id")
            .bind("id", publicationId)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertEquals(expectedStatus.name, row?.get("status"), "Publication status mismatch")
    }

    private suspend fun assertNoDeliveryAttempt(publicationId: String) {
        val row = databaseClient.sql("SELECT id FROM delivery_attempts WHERE publication_id = :pub_id")
            .bind("pub_id", publicationId)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertNull(row, "Delivery attempt should not exist after rollback")
    }

    private suspend fun assertNoDeliveryAttemptWithOutcome(publicationId: String, outcome: String) {
        val row = databaseClient.sql(
            "SELECT id FROM delivery_attempts WHERE publication_id = :pub_id AND outcome = :outcome",
        )
            .bind("pub_id", publicationId)
            .bind("outcome", outcome)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertNull(row, "Delivery attempt with outcome $outcome should not exist after rollback")
    }

    private suspend fun assertDeliveryAttemptWithOutcome(publicationId: String, outcome: String) {
        val row = databaseClient.sql(
            "SELECT id FROM delivery_attempts WHERE publication_id = :pub_id AND outcome = :outcome",
        )
            .bind("pub_id", publicationId)
            .bind("outcome", outcome)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertEquals(true, row != null, "Delivery attempt with outcome $outcome should exist")
    }

    private suspend fun assertNoNotificationEvent(publicationId: String) {
        val row = databaseClient.sql(
            "SELECT id FROM notification_events WHERE publication_id = :pub_id",
        )
            .bind("pub_id", publicationId)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertNull(row, "Notification event should not exist after rollback")
    }

    private suspend fun assertJobClaimed(publicationId: String) {
        val row = databaseClient.sql("SELECT status FROM publication_jobs WHERE publication_id = :pub_id")
            .bind("pub_id", publicationId)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertEquals("CLAIMED", row?.get("status"), "Job should retain its claim after rollback")
    }

    private suspend fun assertJobFailed(publicationId: String) {
        val row = databaseClient.sql("SELECT status FROM publication_jobs WHERE publication_id = :pub_id")
            .bind("pub_id", publicationId)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertEquals("FAILED", row?.get("status"), "Job should be FAILED")
    }

    private suspend fun assertJobBlocked(publicationId: String) {
        val row = databaseClient.sql("SELECT status FROM publication_jobs WHERE publication_id = :pub_id")
            .bind("pub_id", publicationId)
            .fetch()
            .one()
            .awaitSingleOrNull()
        assertEquals("BLOCKED", row?.get("status"), "Job should be BLOCKED")
    }

    private suspend fun cleanupTestData() {
        databaseClient.sql(
            "DELETE FROM notification_events WHERE publication_id LIKE 'pub-%'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "DELETE FROM delivery_attempts WHERE publication_id LIKE 'pub-%'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "DELETE FROM publication_jobs WHERE publication_id LIKE 'pub-%'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_assets WHERE id LIKE 'asset-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publications WHERE id LIKE 'pub-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM social_accounts WHERE id = 'account-1'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "DELETE FROM social_connections WHERE id = 'connection-1'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspaces WHERE id = 'workspace-1'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM principals WHERE id = 'principal-1'").fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPrincipalWorkspaceAndAccount() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'local:test@example.com', NULL, 'principal-1')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Test Workspace', 'ACTIVE', NULL)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO social_connections (id, workspace_id, provider, provider_connection_ref, status, credential_reference)
            VALUES ('connection-1', 'workspace-1', 'LINKEDIN', 'linkedin-connection-1', 'ACTIVE', '00000000-0000-0000-0000-000000000000')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO social_accounts (id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, status)
            VALUES ('account-1', 'connection-1', 'workspace-1', 'LINKEDIN', 'linkedin-account-1', 'PERSONAL_PROFILE', 'Test Account', 'ACTIVE')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private fun testAccount() = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "linkedin-account-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Test Account",
        status = SocialConnectionStatus.ACTIVE,
    )

    // ===== Failing repository decorators =====

    private class FailingPublicationRepository(
        private val delegate: PublicationRepository,
        private val failMarkPublished: Boolean = false,
        private val failMarkFailed: Boolean = false,
    ) : PublicationRepository by delegate {
        override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) {
            if (failMarkPublished) throw InjectedPublicationFailure("Injected failure in markPublished")
            delegate.markPublished(publicationId, externalPublicationId, publishedAt)
        }

        override suspend fun markFailed(
            publicationId: String,
            failedAt: Instant,
            reasonCode: String?,
            reasonMessage: String?,
        ) {
            if (failMarkFailed) throw InjectedPublicationFailure("Injected failure in markFailed")
            delegate.markFailed(publicationId, failedAt, reasonCode, reasonMessage)
        }
    }

    private class FailingJobRepository(
        private val delegate: PublicationJobRepository,
        private val failComplete: Boolean = false,
        private val failReplace: Boolean = false,
        private val failRescheduleRetry: Boolean = false,
    ) : PublicationJobRepository by delegate {
        override suspend fun complete(jobId: String, claimVersion: Long, completedAt: Instant): Boolean {
            if (failComplete) throw InjectedJobFailure("Injected failure in complete")
            return delegate.complete(jobId, claimVersion, completedAt)
        }

        override suspend fun replaceForPublication(job: com.profiletailors.smp.publishing.domain.PublicationJob) {
            if (failReplace) throw InjectedJobFailure("Injected failure in replaceForPublication")
            delegate.replaceForPublication(job)
        }

        override suspend fun rescheduleRetry(
            jobId: String,
            claimVersion: Long,
            nextAttemptAt: Instant,
            attemptNumber: Int,
        ): Boolean {
            if (failRescheduleRetry) throw InjectedJobFailure("Injected failure in rescheduleRetry")
            return delegate.rescheduleRetry(jobId, claimVersion, nextAttemptAt, attemptNumber)
        }
    }

    private class InjectedPublicationFailure(message: String) : RuntimeException(message)
    private class InjectedJobFailure(message: String) : RuntimeException(message)
    private class InjectedNotificationEventFailure(message: String) : RuntimeException(message)
    private class FailingNotificationEventRepository(
        private val delegate: NotificationEventRepository,
        private val failRecord: Boolean = false,
    ) : NotificationEventRepository by delegate {
        override suspend fun record(event: NotificationEvent): NotificationEvent {
            if (failRecord) throw InjectedNotificationEventFailure("Injected failure in notification record")
            return delegate.record(event)
        }
    }

    // ===== Test doubles =====

    private class InMemorySocialAccountRepository(private val account: SocialAccount) : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount = account
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount = account
    }

    private class InMemoryMediaAssetResolver : MediaAssetResolver {
        override suspend fun resolveReadyAssets(
            workspaceId: String,
            assetIds: List<String>,
        ): List<ResolvedAssetSummary> = emptyList()
    }

    private class AcceptingCapabilityValidator : ProviderCapabilityValidator {
        override fun validate(input: ProviderCapabilityValidationInput) = Unit
    }

    private class SuccessfulPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            ProviderPublishResult(externalPublicationId = "external-pub-${command.publicationId}")
    }

    private class CountingPublisher : SocialPublisher {
        var calls: Int = 0

        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult {
            calls += 1
            return ProviderPublishResult(externalPublicationId = "external-pub-${command.publicationId}")
        }
    }

    private class FailingPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw PublishingFailureException(PublishingFailure.publishingFailed("provider failure"))
    }

    private class RetryableFailingPublisher : SocialPublisher {
        override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
            throw RetryablePublishingException("Provider unavailable")
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("publishing_worker_postgres")
            .withUsername("profiletailors")
            .withPassword("profiletailors")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) {
                postgres.start()
            }

            val r2dbcUrl =
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(
                    PostgreSQLContainer.POSTGRESQL_PORT,
                )}/${postgres.databaseName}"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
