package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
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
import org.junit.jupiter.api.Test
import java.time.Instant

class PublishingQueueIntegrationTest : DatabaseUnitTestBase() {

    override fun databaseName(): String = "publishing_queue"

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var jobRepository: R2dbcPublicationJobRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        seedPrincipalWorkspaceAndAccount()
        publicationRepository = R2dbcPublicationRepository(databaseClient)
        jobRepository = R2dbcPublicationJobRepository(databaseClient)
    }

    @Test
    fun `scheduled publication waits until due time and is ignored before due`() = runTest {
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-scheduled",
                workspaceId = "workspace-1",
                authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-1",
                status = PublicationStatus.SCHEDULED,
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                priority = false,
                bodyText = "scheduled",
                scheduledFor = Instant.parse("2026-05-27T08:00:00Z"),
            ),
        )
        jobRepository.enqueue(
            com.profiletailors.smp.publishing.domain.PublicationJob(
                id = "job-scheduled",
                publicationId = "pub-scheduled",
                workspaceId = "workspace-1",
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-05-27T08:00:00Z"),
                priorityRank = 0,
                attemptCount = 0,
                maxAttempts = 3,
            ),
        )

        val claim = jobRepository.claimNextDue(Instant.parse("2026-05-27T07:59:00Z"), "worker-1")

        assertEquals(null, claim)
    }

    @Test
    fun `priority publication is claimed ahead of regular due work`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-regular",
            jobId = "job-regular",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )
        seedPublicationAndJob(
            publicationId = "pub-priority",
            jobId = "job-priority",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = true,
            priorityRank = 100,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        val claim = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:01:00Z"), "worker-1")

        assertNotNull(claim)
        assertEquals("pub-priority", claim?.publicationId)
        assertEquals("job-priority", claim?.jobId)
    }

    @Test
    fun `next slot publication can be manually rescheduled for later retry execution`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-next-slot",
            jobId = "job-next-slot",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.NEXT_SLOT,
            nextSlotAfter = Instant.parse("2026-05-27T08:00:00Z"),
        )

        jobRepository.rescheduleRetry(
            jobId = "job-next-slot",
            nextAttemptAt = Instant.parse("2026-05-27T09:30:00Z"),
            attemptNumber = 2,
        )

        val claimTooEarly = jobRepository.claimNextDue(Instant.parse("2026-05-27T09:00:00Z"), "worker-1")
        val claimLater = jobRepository.claimNextDue(Instant.parse("2026-05-27T09:31:00Z"), "worker-1")

        assertEquals(null, claimTooEarly)
        assertEquals("job-next-slot", claimLater?.jobId)
        assertEquals(3, claimLater?.attemptNumber)
    }

    @Test
    fun `completed publication job is not claimable again`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-completed",
            jobId = "job-completed",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        val firstClaim = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:01:00Z"), "worker-1")
        jobRepository.complete("job-completed", Instant.parse("2026-05-27T08:02:00Z"))
        val secondClaim = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:03:00Z"), "worker-2")

        assertEquals("job-completed", firstClaim?.jobId)
        assertEquals(null, secondClaim)
    }

    private suspend fun seedPrincipalWorkspaceAndAccount() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'local:owner@example.com', NULL, 'owner')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Workspace 1', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                'connection-1', 'workspace-1', 'LINKEDIN', 'linkedin-connection-1',
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
                'account-1', 'connection-1', 'workspace-1', 'LINKEDIN',
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
                workspaceId = "workspace-1",
                authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-1",
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
                workspaceId = "workspace-1",
                status = JobStatus.PENDING,
                dueAt = dueAt,
                priorityRank = priorityRank,
                attemptCount = 0,
                maxAttempts = 3,
            ),
        )
    }
}
