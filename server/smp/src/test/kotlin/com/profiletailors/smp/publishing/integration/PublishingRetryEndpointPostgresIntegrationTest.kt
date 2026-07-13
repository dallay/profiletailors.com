package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcDeliveryAttemptRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishingRetryEndpointPostgresIntegrationTest : PostgresIntegrationTestBase() {

    @Autowired
    private lateinit var publicationRepository: R2dbcPublicationRepository

    @Autowired
    private lateinit var jobRepository: R2dbcPublicationJobRepository

    @Autowired
    private lateinit var deliveryAttemptRepository: R2dbcDeliveryAttemptRepository

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    override suspend fun seedScenario() {
        seedIdentityAndWorkspace()
        seedSocialAccount()
        seedFailedPublication()
    }

    private suspend fun seedIdentityAndWorkspace() {
        seedPrincipal("owner-1")
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES ('owner-1', 'owner@example.com', 'owner', 'VERIFIED')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        seedWorkspace("workspace-1", "Publishing Workspace")
        seedWorkspaceMembership(
            id = "membership-owner-1",
            workspaceId = "workspace-1",
            principalId = "owner-1",
        )
    }

    private suspend fun seedSocialAccount() {
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
                'account-1', 'connection-1', 'workspace-1', 'LINKEDIN', 'linkedin-account-1',
                'PERSONAL_PROFILE', 'Profile Tailors', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedFailedPublication() {
        publicationRepository.createDraft(
            PublicationDraft(
                id = PUBLICATION_ID,
                workspaceId = "workspace-1",
                authorPrincipalId = "owner-1",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-1",
                status = PublicationStatus.FAILED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Retry through HTTP",
                failedAt = Instant.parse("2026-07-13T08:00:00Z"),
            ),
        )
        jobRepository.enqueue(
            PublicationJob(
                id = ORIGINAL_JOB_ID,
                publicationId = PUBLICATION_ID,
                workspaceId = "workspace-1",
                status = JobStatus.FAILED,
                dueAt = Instant.parse("2026-07-13T08:00:00Z"),
                priorityRank = 0,
                attemptCount = 1,
                maxAttempts = 3,
                failedAt = Instant.parse("2026-07-13T08:00:00Z"),
            ),
        )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-http-retry",
                publicationId = PUBLICATION_ID,
                publicationJobId = ORIGINAL_JOB_ID,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = Instant.parse("2026-07-13T08:00:00Z"),
            ),
        )
    }

    @Test
    fun `POST retry removes previous attempts and creates one replacement job`() = runTest {
        webTestClient.post()
            .uri("/api/publishing/publications/$PUBLICATION_ID/retry")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, IntegrationTestBase.API_V1_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"scheduleMode":"NOW","priority":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.publicationId").isEqualTo(PUBLICATION_ID)
            .jsonPath("$.status").isEqualTo(PublicationStatus.QUEUED.name)
            .jsonPath("$.priority").isEqualTo(true)

        assertEquals(0L, countDeliveryAttempts())
        assertEquals(1L, countPublicationJobs())
        assertEquals(
            JobStatus.PENDING.name,
            databaseClient.sql("SELECT status FROM publication_jobs WHERE publication_id = :publicationId")
                .bind("publicationId", PUBLICATION_ID)
                .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
                .one()
                .awaitSingle(),
        )
    }

    private suspend fun countDeliveryAttempts(): Long = databaseClient.sql(
        "SELECT COUNT(*) AS count FROM delivery_attempts WHERE publication_id = :publicationId",
    )
        .bind("publicationId", PUBLICATION_ID)
        .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
        .one()
        .awaitSingle()

    private suspend fun countPublicationJobs(): Long = databaseClient.sql(
        "SELECT COUNT(*) AS count FROM publication_jobs WHERE publication_id = :publicationId",
    )
        .bind("publicationId", PUBLICATION_ID)
        .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
        .one()
        .awaitSingle()

    companion object {
        private const val PUBLICATION_ID = "pub-http-retry"
        private const val ORIGINAL_JOB_ID = "job-http-retry-original"

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer(
            databaseName = "publishing_retry_endpoint",
        )

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
