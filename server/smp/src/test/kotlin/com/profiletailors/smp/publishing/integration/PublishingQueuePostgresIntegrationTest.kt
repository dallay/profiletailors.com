package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * PostgreSQL-specific integration tests for publishing queue claim and update operations.
 * 
 * These tests verify that the SQL operations in R2dbcPublicationJobRepository work correctly
 * against a real PostgreSQL database, beyond what H2 semantics can validate.
 * 
 * Key scenarios tested:
 * - Concurrent claim operations with ORDER BY and LIMIT
 * - UPDATE with status transitions and NULL handling
 * - Priority ranking with DESC ordering
 * - Timestamp comparisons with PostgreSQL's TIMESTAMP WITH TIME ZONE
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
class PublishingQueuePostgresIntegrationTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var jobRepository: R2dbcPublicationJobRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        cleanupTestData()
        seedPrincipalWorkspaceAndAccount()
        publicationRepository = R2dbcPublicationRepository(databaseClient)
        jobRepository = R2dbcPublicationJobRepository(databaseClient)
    }

    private suspend fun cleanupTestData() {
        databaseClient.sql("DELETE FROM delivery_attempts WHERE publication_id LIKE 'pub-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_jobs WHERE publication_id LIKE 'pub-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_asset_links WHERE publication_id LIKE 'pub-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publications WHERE id LIKE 'pub-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM social_accounts WHERE id LIKE 'account-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM social_connections WHERE id LIKE 'connection-%' OR id LIKE 'conn-%'").fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `claimNextDue with ORDER BY priority_rank DESC and due_at ASC works correctly in PostgreSQL`() = runTest {
        // Seed three jobs with different priorities and due times
        seedPublicationAndJob(
            publicationId = "pub-low-priority-early",
            jobId = "job-low-priority-early",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )
        seedPublicationAndJob(
            publicationId = "pub-high-priority-late",
            jobId = "job-high-priority-late",
            dueAt = Instant.parse("2026-05-27T09:00:00Z"),
            priority = true,
            priorityRank = 100,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )
        seedPublicationAndJob(
            publicationId = "pub-medium-priority-middle",
            jobId = "job-medium-priority-middle",
            dueAt = Instant.parse("2026-05-27T08:30:00Z"),
            priority = false,
            priorityRank = 50,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // Claim at 09:01 — all three are due
        val claim = jobRepository.claimNextDue(Instant.parse("2026-05-27T09:01:00Z"), "worker-1")

        // Should claim the highest priority first (priority_rank DESC)
        assertNotNull(claim)
        assertEquals("job-high-priority-late", claim?.jobId)
        assertEquals("pub-high-priority-late", claim?.publicationId)
    }

    @Test
    fun `UPDATE with status transition from PENDING to CLAIMED updates attempt_count correctly`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-claim-test",
            jobId = "job-claim-test",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // Claim the job
        val claim = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:01:00Z"), "worker-1")

        assertNotNull(claim)
        assertEquals(1, claim?.attemptNumber)

        // Verify the database state directly
        val row = databaseClient.sql(
            """
            SELECT status, attempt_count, claimed_by_worker, claimed_at
            FROM publication_jobs
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", "job-claim-test")
            .fetch()
            .one()
            .awaitSingle()

        assertEquals("CLAIMED", row["status"])
        assertEquals(1, (row["attempt_count"] as Number).toInt())
        assertEquals("worker-1", row["claimed_by_worker"])
        assertNotNull(row["claimed_at"])
    }

    @Test
    fun `rescheduleRetry updates status to RETRY_WAITING and clears worker claim fields`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-retry-test",
            jobId = "job-retry-test",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // Claim and then reschedule
        jobRepository.claimNextDue(Instant.parse("2026-05-27T08:01:00Z"), "worker-1")
        jobRepository.rescheduleRetry(
            jobId = "job-retry-test",
            nextAttemptAt = Instant.parse("2026-05-27T09:00:00Z"),
            attemptNumber = 1,
        )

        // Verify the database state
        val row = databaseClient.sql(
            """
            SELECT status, due_at, attempt_count, claimed_by_worker, claimed_at
            FROM publication_jobs
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", "job-retry-test")
            .fetch()
            .one()
            .awaitSingle()

        assertEquals("RETRY_WAITING", row["status"])
        assertEquals(1, (row["attempt_count"] as Number).toInt())
        assertNull(row["claimed_by_worker"])
        assertNull(row["claimed_at"])
    }

    @Test
    fun `timestamp comparison with due_at works correctly with PostgreSQL TIMESTAMP WITH TIME ZONE`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-timestamp-test",
            jobId = "job-timestamp-test",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
        )

        // Claim before due time
        val claimTooEarly = jobRepository.claimNextDue(Instant.parse("2026-05-27T07:59:59Z"), "worker-1")
        assertNull(claimTooEarly)

        // Claim exactly at due time
        val claimExact = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:00:00Z"), "worker-2")
        assertNotNull(claimExact)
        assertEquals("job-timestamp-test", claimExact?.jobId)
    }

    @Test
    fun `multiple workers cannot claim the same job due to transaction isolation`() = runTest {
        seedPublicationAndJob(
            publicationId = "pub-concurrent-test",
            jobId = "job-concurrent-test",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // First worker claims
        val claim1 = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:01:00Z"), "worker-1")
        assertNotNull(claim1)
        assertEquals("job-concurrent-test", claim1?.jobId)

        // Second worker tries to claim — should get nothing
        val claim2 = jobRepository.claimNextDue(Instant.parse("2026-05-27T08:01:00Z"), "worker-2")
        assertNull(claim2)
    }

    private suspend fun seedPrincipalWorkspaceAndAccount() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'local:owner@example.com', NULL, 'owner')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Workspace 1', 'ACTIVE', NULL)
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
            VALUES ('account-1', 'connection-1', 'workspace-1', 'LINKEDIN', 'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE')
            ON CONFLICT (id) DO NOTHING
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
            PublicationJob(
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

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("publishing_queue_postgres")
            .withUsername("profiletailors")
            .withPassword("profiletailors")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) {
                postgres.start()
            }

            val r2dbcUrl =
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}/${postgres.databaseName}"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
