package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
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
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PostgreSQL-specific integration tests for publishing queue claim and update operations.
 *
 * These tests verify that the SQL operations in R2dbcPublicationJobRepository work correctly
 * against a real PostgreSQL database, beyond what a lightweight test database can validate.
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

    @Autowired
    private lateinit var transactionalOperator: TransactionalOperator

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var jobRepository: R2dbcPublicationJobRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        cleanupTestData()
        seedPrincipalWorkspaceAndAccount()
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
        jobRepository = R2dbcPublicationJobRepository(databaseClient)
    }

    private suspend fun cleanupTestData() {
        listOf(
            """
            DELETE FROM delivery_attempts
            WHERE publication_id IN (SELECT id FROM publications WHERE workspace_id = :workspaceId)
            """.trimIndent(),
            "DELETE FROM publication_jobs WHERE workspace_id = :workspaceId",
            """
            DELETE FROM publication_asset_links
            WHERE publication_id IN (SELECT id FROM publications WHERE workspace_id = :workspaceId)
            """.trimIndent(),
            "DELETE FROM publications WHERE workspace_id = :workspaceId",
            "DELETE FROM social_accounts WHERE workspace_id = :workspaceId",
            "DELETE FROM social_connections WHERE workspace_id = :workspaceId",
        ).forEach { sql ->
            databaseClient.sql(sql)
                .bind("workspaceId", UUID.fromString("00000000-0000-0000-0000-00000000049d"))
                .fetch().rowsUpdated().awaitSingle()
        }
    }

    @Test
    fun `claimNextDue with ORDER BY priority_rank DESC and due_at ASC works correctly in PostgreSQL`() = runTest {
        // Seed three jobs with different priorities and due times
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-000000000451",
            jobId = "00000000-0000-0000-0000-000000000418",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-00000000044f",
            jobId = "00000000-0000-0000-0000-000000000416",
            dueAt = Instant.parse("2026-05-27T09:00:00Z"),
            priority = true,
            priorityRank = 100,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-000000000452",
            jobId = "00000000-0000-0000-0000-000000000419",
            dueAt = Instant.parse("2026-05-27T08:30:00Z"),
            priority = false,
            priorityRank = 50,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // Claim at 09:01 — all three are due
        val claim = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T09:01:00Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )

        // Should claim the highest priority first (priority_rank DESC)
        assertNotNull(claim)
        assertEquals("00000000-0000-0000-0000-000000000416", claim?.jobId)
        assertEquals("00000000-0000-0000-0000-00000000044f", claim?.publicationId)
    }

    @Test
    fun `UPDATE with status transition from PENDING to CLAIMED updates attempt_count correctly`() = runTest {
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-00000000043c",
            jobId = "00000000-0000-0000-0000-000000000408",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // Claim the job
        val claim = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T08:01:00Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )

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
            .bind("id", UUID.fromString("00000000-0000-0000-0000-000000000408"))
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
            publicationId = "00000000-0000-0000-0000-000000000469",
            jobId = "00000000-0000-0000-0000-000000000422",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        // Claim and then reschedule
        val claim = requireNotNull(
            jobRepository.claimNextDue(
                Instant.parse("2026-05-27T08:01:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            ),
        )
        jobRepository.rescheduleRetry(
            jobId = "00000000-0000-0000-0000-000000000422",
            claimVersion = claim.claimVersion,
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
            .bind("id", UUID.fromString("00000000-0000-0000-0000-000000000422"))
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
            publicationId = "00000000-0000-0000-0000-00000000047a",
            jobId = "00000000-0000-0000-0000-00000000042a",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
        )

        // Claim before due time
        val claimTooEarly = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T07:59:59Z"),
            "worker-1",
            Duration.ofMinutes(2),
        )
        assertNull(claimTooEarly)

        // Claim exactly at due time
        val claimExact = jobRepository.claimNextDue(
            Instant.parse("2026-05-27T08:00:00Z"),
            "worker-2",
            Duration.ofMinutes(2),
        )
        assertNotNull(claimExact)
        assertEquals("00000000-0000-0000-0000-00000000042a", claimExact?.jobId)
    }

    @Test
    fun `should return one claim when two workers claim concurrently`() = runTest {
        seedPublicationAndJob(
            publicationId = "00000000-0000-0000-0000-00000000043e",
            jobId = "00000000-0000-0000-0000-00000000040b",
            dueAt = Instant.parse("2026-05-27T08:00:00Z"),
            priority = false,
            priorityRank = 0,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
        )

        val claims = coroutineScope {
            listOf("worker-1", "worker-2").map { workerId ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    R2dbcPublicationJobRepository(databaseClient).claimNextDue(
                        Instant.parse("2026-05-27T08:01:00Z"),
                        workerId,
                        Duration.ofMinutes(2),
                    )
                }
            }.awaitAll()
        }

        assertEquals(1, claims.count { it != null })
        assertEquals(1L, requireNotNull(claims.single { it != null }).claimVersion)
    }

    private suspend fun seedPrincipalWorkspaceAndAccount() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('00000000-0000-0000-0000-000000000430', 'USER', 'local:owner@example.com', NULL, 'owner')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('00000000-0000-0000-0000-00000000049d', 'Workspace 1', 'ACTIVE', NULL)
            ON CONFLICT (id) DO NOTHING
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
            ON CONFLICT (id) DO NOTHING
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
            PublicationJob(
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
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("publishing_queue_postgres")

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
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SET profiletailors.uuid_cutover_approved = 'on'")
                }
                val database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
                Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    ClassLoaderResourceAccessor(),
                    database,
                ).update(Contexts("uuid-native-identifiers"), LabelExpression())
            }
        }
    }
}
