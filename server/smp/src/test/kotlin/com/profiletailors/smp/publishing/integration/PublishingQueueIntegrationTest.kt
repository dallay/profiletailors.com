package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishingQueueIntegrationTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var jobRepository: R2dbcPublicationJobRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        seedPrincipalWorkspaceAndAccount()
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
        jobRepository = R2dbcPublicationJobRepository(databaseClient)
    }

    @Test
    fun `scheduled publication waits until due time and is ignored before due`() = runTest {
        publicationRepository.createDraft(
            PublicationDraft(
                id = "00000000-0000-0000-0000-000000000473",
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003e8",
                status = PublicationStatus.SCHEDULED,
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                priority = false,
                bodyText = "scheduled",
                scheduledFor = Instant.parse("2026-05-27T08:00:00Z"),
            ),
        )
        jobRepository.enqueue(
            com.profiletailors.smp.publishing.domain.PublicationJob(
                id = "00000000-0000-0000-0000-000000000424",
                publicationId = "00000000-0000-0000-0000-000000000473",
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-05-27T08:00:00Z"),
                priorityRank = 0,
                attemptCount = 0,
                maxAttempts = 3,
            ),
        )

        val claim = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T07:59:00Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )

        assertEquals(null, claim)
    }

    @Test
    fun `priority publication is claimed ahead of regular due work`() = runTest {
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-00000000045e",
            jobId = "00000000-0000-0000-0000-00000000041d",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-00000000045a",
            jobId = "00000000-0000-0000-0000-00000000041c",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = true,
            priorityRank = 100,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        val claim = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T08:01:00Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )

        assertNotNull(claim)
        assertEquals("00000000-0000-0000-0000-00000000045a", claim?.publicationId)
        assertEquals("00000000-0000-0000-0000-00000000041c", claim?.jobId)
    }

    @Test
    fun `next slot publication can be manually rescheduled for later retry execution`() = runTest {
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-000000000454",
            jobId = "00000000-0000-0000-0000-00000000041a",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.NEXT_SLOT,
            nextSlotAfter = Instant.parse("2026-05-27T08:00:00Z"),
        )

        val initialClaim = requireNotNull(
            jobRepository.claimNextDue(
                now = Instant.parse("2026-05-27T08:01:00Z"),
                workerId = "worker-1",
                claimLease = Duration.ofMinutes(2),
            ),
        )
        jobRepository.rescheduleRetry(
            jobId = "00000000-0000-0000-0000-00000000041a",
            claimVersion = initialClaim.claimVersion,
            nextAttemptAt = Instant.parse("2026-05-27T09:30:00Z"),
            attemptNumber = 2,
        )

        val claimTooEarly = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T09:00:00Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )
        val claimLater = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T09:31:00Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )

        assertEquals(null, claimTooEarly)
        assertEquals("00000000-0000-0000-0000-00000000041a", claimLater?.jobId)
        assertEquals(3, claimLater?.attemptNumber)
    }

    @Test
    fun `completed publication job is not claimable again`() = runTest {
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-00000000043d",
            jobId = "00000000-0000-0000-0000-00000000040a",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        val firstClaim = requireNotNull(
            jobRepository.claimNextDue(
                Instant.parse("2026-05-27T08:01:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            ),
        )
        jobRepository.complete(
            "00000000-0000-0000-0000-00000000040a",
            firstClaim.claimVersion,
            Instant.parse("2026-05-27T08:02:00Z"),
        )
        val secondClaim = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T08:03:00Z"),
            "worker-2",
            Duration.ofMinutes(2),
        )

        assertEquals("00000000-0000-0000-0000-00000000040a", firstClaim.jobId)
        assertEquals(null, secondClaim)
    }

    private suspend fun seedPrincipalWorkspaceAndAccount() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('00000000-0000-0000-0000-000000000430', 'USER', 'local:owner@example.com', NULL, 'owner')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('00000000-0000-0000-0000-00000000049d', 'Workspace 1', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                '00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-00000000049d', 'LINKEDIN', 'linkedin-connection-1',
                'ACTIVE', '00000000-0000-0000-0000-000000000000'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                '00000000-0000-0000-0000-0000000003e8', '00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-00000000049d', 'LINKEDIN',
                'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPublicationAndJob(
        publicationId: String,
        jobId: String,
        dueAt: Instant,
        priority: Boolean,
        priorityRank: Int,
        status: PublicationStatus,
        scheduleMode: ScheduleMode,
        nextSlotAfter: Instant? = null,
    ) {
        publicationRepository.createDraft(
            PublicationDraft(
                id = publicationId,
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003e8",
                status = status,
                scheduleMode = scheduleMode,
                priority = priority,
                bodyText = publicationId,
                nextSlotAfter = nextSlotAfter,
                scheduledFor = if (scheduleMode == ScheduleMode.SCHEDULED_AT) dueAt else null,
            ),
        )
        jobRepository.enqueue(
            com.profiletailors.smp.publishing.domain.PublicationJob(
                id = jobId,
                publicationId = publicationId,
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                status = JobStatus.PENDING,
                dueAt = dueAt,
                priorityRank = priorityRank,
                attemptCount = 0,
                maxAttempts = 3,
            ),
        )
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("publishing_queue")
    }
}
