package com.profiletailors.smp.publishing.integration

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionHandler
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcDeliveryAttemptRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialConnectionRepository
import com.profiletailors.smp.test.TestStorageConfiguration
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

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
class PublishingHandlersTransactionPostgresIntegrationTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var transactionManager: R2dbcTransactionManager

    @Autowired
    private lateinit var transactionalOperator: TransactionalOperator

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var jobRepository: R2dbcPublicationJobRepository
    private lateinit var deliveryAttemptRepository: R2dbcDeliveryAttemptRepository
    private lateinit var socialConnectionRepository: R2dbcSocialConnectionRepository
    private lateinit var socialAccountRepository: R2dbcSocialAccountRepository
    private lateinit var transactionRunner: AtomicTransactionRunner
    private val schedulingPolicy = PublicationSchedulingPolicy()
    private val now: Instant = Instant.parse("2026-05-26T12:00:00Z")

    @BeforeEach
    fun setUpRepositories() = runTest {
        cleanupTestData()
        seedPrincipalWorkspaceAndAccount()
        seedAssets()
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
        jobRepository = R2dbcPublicationJobRepository(databaseClient)
        deliveryAttemptRepository = R2dbcDeliveryAttemptRepository(databaseClient)
        socialConnectionRepository = R2dbcSocialConnectionRepository(databaseClient)
        socialAccountRepository = R2dbcSocialAccountRepository(databaseClient, SimpleMeterRegistry())
        transactionRunner = R2dbcAtomicTransactionRunner(TransactionalOperator.create(transactionManager))
    }

    @Test
    fun `linkedin completion rolls back social connection when account upsert fails`() = runTest {
        val eventPublisher = CapturingChannelEventPublisher()
        val handler = CompleteLinkedInConnectionHandler(
            principalContextProvider = FixedPrincipalContextProvider(),
            resourceContextProvider = FixedResourceContextProvider(),
            socialConnectionProvider = FakeSocialConnectionProvider(),
            oauthStateSigner = FixedOAuthStateSigner(),
            socialConnectionRepository = socialConnectionRepository,
            socialAccountRepository = FailingSocialAccountRepository(socialAccountRepository),
            channelEventPublisher = eventPublisher,
            clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
            transactionRunner = transactionRunner,
        )

        assertThrows(InjectedSocialAccountFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CompleteLinkedInConnectionCommand(
                        authorizationCode = "oauth-code-193",
                        redirectUri = "https://app.example.com/callback",
                        state = "state-193",
                    ),
                )
            }
        }

        assertNull(socialConnectionByProviderRef("linkedin-connection-193"))
        assertNull(socialAccountByProviderAccountId("linkedin-account-193"))
        assertEquals(emptyList<ChannelEvent>(), eventPublisher.events)
    }

    @Test
    fun `create commits publication asset links and job together`() = runTest {
        createPublicationAndJob("pub-create-commit", jobRepository)

        assertPublication("pub-create-commit", PublicationStatus.QUEUED, "create", listOf("asset-1", "asset-2"))
        assertJob("pub-create-commit", status = JobStatus.PENDING, priorityRank = 100)
    }

    @Test
    fun `create rolls back publication asset links and job when enqueue fails`() = runTest {
        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                createPublicationAndJob("pub-create-rollback", FailingJobRepository(jobRepository, failEnqueue = true))
            }
        }

        assertNull(publicationRepository.findByWorkspaceAndId("workspace-1", "pub-create-rollback"))
        assertEquals(emptyList<String>(), assetLinks("pub-create-rollback"))
        assertNull(jobRow("pub-create-rollback"))
    }

    @Test
    fun `edit commits persisted publication asset links and replacement job`() = runTest {
        seedPublicationAndJob("pub-edit-commit", "old", listOf("asset-1"))

        updatePublicationAndReplaceJob(
            publicationId = "pub-edit-commit",
            draft = draft("pub-edit-commit", "edited", listOf("asset-2"), priority = true),
            repository = jobRepository,
        )

        assertPublication("pub-edit-commit", PublicationStatus.QUEUED, "edited", listOf("asset-2"))
        assertJob("pub-edit-commit", status = JobStatus.PENDING, priorityRank = 100)
    }

    @Test
    fun `edit rolls back publication asset links and preserves job when replacement fails`() = runTest {
        val originalJobId = seedPublicationAndJob("pub-edit-rollback", "old", listOf("asset-1"))

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = "pub-edit-rollback",
                    draft = draft("pub-edit-rollback", "edited", listOf("asset-2"), priority = true),
                    repository = FailingJobRepository(jobRepository, failReplace = true),
                )
            }
        }

        assertPublication("pub-edit-rollback", PublicationStatus.QUEUED, "old", listOf("asset-1"))
        assertEquals(originalJobId, requireNotNull(jobRow("pub-edit-rollback"))["id"])
    }

    @Test
    fun `cancel commits publication and job cancellation together`() = runTest {
        seedPublicationAndJob("pub-cancel-commit", "cancel me", listOf("asset-1"))

        transactionRunner.runAtomically {
            publicationRepository.markCancelled("pub-cancel-commit", now)
            jobRepository.cancel("pub-cancel-commit", now)
        }

        assertPublication("pub-cancel-commit", PublicationStatus.CANCELLED, "cancel me", listOf("asset-1"))
        assertJob("pub-cancel-commit", status = JobStatus.CANCELLED)
    }

    @Test
    fun `cancel rolls back publication status and preserves job when cancel fails`() = runTest {
        val originalJobId = seedPublicationAndJob("pub-cancel-rollback", "cancel me", listOf("asset-1"))

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                transactionRunner.runAtomically {
                    publicationRepository.markCancelled("pub-cancel-rollback", now)
                    FailingJobRepository(jobRepository, failCancel = true).cancel("pub-cancel-rollback", now)
                }
            }
        }

        assertPublication("pub-cancel-rollback", PublicationStatus.QUEUED, "cancel me", listOf("asset-1"))
        val job = requireNotNull(jobRow("pub-cancel-rollback"))
        assertEquals(originalJobId, job["id"])
        assertEquals(JobStatus.PENDING.name, job["status"])
    }

    @Test
    fun `retry commits publication asset links and replacement job`() = runTest {
        val originalJobId =
            seedPublicationAndJob("pub-retry-commit", "failed", listOf("asset-1"), status = PublicationStatus.FAILED)
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-retry-commit",
                publicationId = "pub-retry-commit",
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        updatePublicationAndReplaceJob(
            publicationId = "pub-retry-commit",
            draft = draft(
                "pub-retry-commit",
                "retry",
                listOf("asset-2"),
                priority = true,
                status = PublicationStatus.QUEUED,
            ),
            repository = jobRepository,
        )

        assertPublication("pub-retry-commit", PublicationStatus.QUEUED, "retry", listOf("asset-2"))
        assertJob("pub-retry-commit", status = JobStatus.PENDING, priorityRank = 100)
        assertNull(deliveryAttemptRow("pub-retry-commit"))
    }

    @Test
    fun `retry rolls back publication asset links and preserves job when replacement fails`() = runTest {
        val originalJobId =
            seedPublicationAndJob("pub-retry-rollback", "failed", listOf("asset-1"), status = PublicationStatus.FAILED)
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-retry-rollback",
                publicationId = "pub-retry-rollback",
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = "pub-retry-rollback",
                    draft = draft(
                        "pub-retry-rollback",
                        "retry",
                        listOf("asset-2"),
                        priority = true,
                        status = PublicationStatus.QUEUED,
                    ),
                    repository = FailingJobRepository(jobRepository, failReplace = true),
                )
            }
        }

        assertPublication("pub-retry-rollback", PublicationStatus.FAILED, "failed", listOf("asset-1"))
        assertEquals(originalJobId, requireNotNull(jobRow("pub-retry-rollback"))["id"])
        assertEquals("attempt-retry-rollback", requireNotNull(deliveryAttemptRow("pub-retry-rollback"))["id"])
    }

    @Test
    fun `retry rolls back deleted attempts and job when replacement insert fails`() = runTest {
        val publicationId = "pub-retry-sql-rollback"
        val originalJobId =
            seedPublicationAndJob(publicationId, "failed", listOf("asset-1"), status = PublicationStatus.FAILED)
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-retry-sql-rollback",
                publicationId = publicationId,
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )
        val collisionHolder = publicationRepository.createDraft(
            draft("pub-retry-collision-holder", "collision", emptyList()),
        )
        jobRepository.enqueue(
            jobFor(collisionHolder).copy(id = "pjob-$publicationId"),
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = publicationId,
                    draft = draft(
                        publicationId,
                        "retry",
                        listOf("asset-2"),
                        priority = true,
                        status = PublicationStatus.QUEUED,
                    ),
                    repository = jobRepository,
                )
            }
        }

        assertPublication(publicationId, PublicationStatus.FAILED, "failed", listOf("asset-1"))
        assertEquals(originalJobId, requireNotNull(jobRow(publicationId))["id"])
        assertEquals(
            "attempt-retry-sql-rollback",
            requireNotNull(deliveryAttemptRow(publicationId))["id"],
        )
    }

    @Test
    fun `concurrent retries leave one replacement job without delivery attempts`() = runTest {
        val publicationId = "pub-retry-concurrent"
        val originalJobId =
            seedPublicationAndJob(publicationId, "failed", listOf("asset-1"), status = PublicationStatus.FAILED)
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-retry-concurrent",
                publicationId = publicationId,
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        val results = coroutineScope {
            listOf(false, true).map { priority ->
                async {
                    runCatching {
                        updatePublicationAndReplaceJob(
                            publicationId = publicationId,
                            draft = draft(
                                publicationId,
                                "retry",
                                listOf("asset-2"),
                                priority = priority,
                                status = PublicationStatus.QUEUED,
                            ),
                            repository = jobRepository,
                        )
                    }
                }
            }.awaitAll()
        }

        assertTrue(results.all { it.isSuccess })
        assertEquals(1L, countJobs(publicationId))
        assertNull(deliveryAttemptRow(publicationId))
    }

    @Test
    fun `reschedule commits publication asset links and replacement job`() = runTest {
        val originalJobId = seedPublicationAndJob("pub-reschedule-commit", "old time", listOf("asset-1"))
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-reschedule-commit",
                publicationId = "pub-reschedule-commit",
                publicationJobId = originalJobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = false,
                attemptedAt = now,
            ),
        )

        updatePublicationAndReplaceJob(
            publicationId = "pub-reschedule-commit",
            draft = draft(
                "pub-reschedule-commit",
                "new time",
                listOf("asset-2"),
                status = PublicationStatus.SCHEDULED,
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-06-15T10:00:00Z"),
            ),
            repository = jobRepository,
        )

        assertPublication("pub-reschedule-commit", PublicationStatus.SCHEDULED, "new time", listOf("asset-2"))
        assertJob("pub-reschedule-commit", status = JobStatus.PENDING)
        assertNull(deliveryAttemptRow("pub-reschedule-commit"))
    }

    @Test
    fun `reschedule rolls back publication timing assets and preserves job when replacement fails`() = runTest {
        val originalJobId = seedPublicationAndJob("pub-reschedule-rollback", "old time", listOf("asset-1"))

        assertThrows(InjectedJobFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                updatePublicationAndReplaceJob(
                    publicationId = "pub-reschedule-rollback",
                    draft = draft(
                        "pub-reschedule-rollback",
                        "new time",
                        listOf("asset-2"),
                        scheduleMode = ScheduleMode.SCHEDULED_AT,
                        scheduledFor = Instant.parse("2026-06-15T10:00:00Z"),
                    ),
                    repository = FailingJobRepository(jobRepository, failReplace = true),
                )
            }
        }

        assertPublication("pub-reschedule-rollback", PublicationStatus.QUEUED, "old time", listOf("asset-1"))
        assertEquals(originalJobId, requireNotNull(jobRow("pub-reschedule-rollback"))["id"])
    }

    private suspend fun createPublicationAndJob(publicationId: String, repository: PublicationJobRepository) {
        transactionRunner.runAtomically {
            val created = publicationRepository.createDraft(
                draft(publicationId, "create", listOf("asset-1", "asset-2"), priority = true),
            )
            repository.enqueue(jobFor(created))
            created
        }
    }

    private suspend fun updatePublicationAndReplaceJob(
        publicationId: String,
        draft: PublicationDraft,
        repository: PublicationJobRepository,
    ) {
        transactionRunner.runAtomically {
            val persisted = publicationRepository.updateEditableDraft(draft)
            repository.replaceForPublication(jobFor(persisted))
            publicationId
        }
    }

    private fun jobFor(publication: PublicationDraft): PublicationJob = PublicationJob(
        id = "pjob-${publication.id}",
        publicationId = publication.id,
        workspaceId = publication.workspaceId,
        status = JobStatus.PENDING,
        dueAt = schedulingPolicy.resolveDueAt(publication, now),
        priorityRank = schedulingPolicy.priorityRank(publication),
        attemptCount = 0,
        maxAttempts = 1,
    )

    private suspend fun seedPublicationAndJob(
        publicationId: String,
        bodyText: String,
        assetIds: List<String>,
        status: PublicationStatus = PublicationStatus.QUEUED,
    ): String {
        publicationRepository.createDraft(draft(publicationId, bodyText, assetIds, status = status))
        val jobId = "pjob-original-$publicationId"
        jobRepository.enqueue(
            PublicationJob(
                id = jobId,
                publicationId = publicationId,
                workspaceId = "workspace-1",
                status = JobStatus.PENDING,
                dueAt = now,
                priorityRank = 0,
                attemptCount = 0,
                maxAttempts = 3,
            ),
        )
        return jobId
    }

    private fun draft(
        publicationId: String,
        bodyText: String,
        assetIds: List<String>,
        priority: Boolean = false,
        status: PublicationStatus = PublicationStatus.QUEUED,
        scheduleMode: ScheduleMode = ScheduleMode.NOW,
        scheduledFor: Instant? = null,
    ): PublicationDraft = PublicationDraft(
        id = publicationId,
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        status = status,
        scheduleMode = scheduleMode,
        priority = priority,
        bodyText = bodyText,
        assetIds = assetIds,
        scheduledFor = scheduledFor,
    )

    private suspend fun assertPublication(
        publicationId: String,
        status: PublicationStatus,
        bodyText: String,
        assetIds: List<String>,
    ) {
        val publication = requireNotNull(publicationRepository.findByWorkspaceAndId("workspace-1", publicationId))
        assertEquals(status, publication.status)
        assertEquals(bodyText, publication.bodyText)
        assertEquals(assetIds, publication.assetIds)
    }

    private suspend fun assertJob(publicationId: String, status: JobStatus, priorityRank: Int? = null) {
        val job = requireNotNull(jobRow(publicationId))
        assertEquals(status.name, job["status"])
        priorityRank?.let { assertEquals(it, (job["priority_rank"] as Number).toInt()) }
    }

    private suspend fun jobRow(publicationId: String): Map<String, Any>? = databaseClient.sql(
        "SELECT id, status, priority_rank FROM publication_jobs WHERE publication_id = :publicationId",
    )
        .bind("publicationId", publicationId)
        .fetch()
        .one()
        .awaitSingleOrNull()

    private suspend fun countJobs(publicationId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS count FROM publication_jobs WHERE publication_id = :publicationId",
    )
        .bind("publicationId", publicationId)
        .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
        .one()
        .awaitSingle()

    private suspend fun deliveryAttemptRow(publicationId: String): Map<String, Any>? = databaseClient.sql(
        "SELECT id FROM delivery_attempts WHERE publication_id = :publicationId",
    )
        .bind("publicationId", publicationId)
        .fetch()
        .one()
        .awaitSingleOrNull()

    private suspend fun socialConnectionByProviderRef(providerConnectionRef: String): Map<String, Any>? =
        databaseClient.sql(
            "SELECT id FROM social_connections WHERE provider_connection_ref = :providerConnectionRef",
        )
            .bind("providerConnectionRef", providerConnectionRef)
            .fetch()
            .one()
            .awaitSingleOrNull()

    private suspend fun socialAccountByProviderAccountId(providerAccountId: String): Map<String, Any>? =
        databaseClient.sql(
            "SELECT id FROM social_accounts WHERE provider_account_id = :providerAccountId",
        )
            .bind("providerAccountId", providerAccountId)
            .fetch()
            .one()
            .awaitSingleOrNull()

    private suspend fun assetLinks(publicationId: String): List<String> = databaseClient.sql(
        """
        SELECT asset_id
        FROM publication_asset_links
        WHERE publication_id = :publicationId
        ORDER BY position_index ASC
        """.trimIndent(),
    )
        .bind("publicationId", publicationId)
        .map { row, _ -> requireNotNull(row.get("asset_id", String::class.java)) }
        .all()
        .collectList()
        .awaitSingle()

    private suspend fun cleanupTestData() {
        databaseClient.sql(
            "DELETE FROM delivery_attempts WHERE publication_id LIKE 'pub-%'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "DELETE FROM publication_jobs WHERE publication_id LIKE 'pub-%'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "DELETE FROM publication_asset_links WHERE publication_id LIKE 'pub-%'",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publications WHERE id LIKE 'pub-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_assets WHERE id LIKE 'asset-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM social_accounts WHERE id LIKE 'account-%'").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "DELETE FROM social_connections WHERE id LIKE 'connection-%'",
        ).fetch().rowsUpdated().awaitSingle()
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

    private suspend fun seedAssets() {
        listOf("asset-1", "asset-2").forEach { assetId ->
            databaseClient.sql(
                """
                INSERT INTO publication_assets (id, workspace_id, source_type, media_type, storage_key, status, created_by_principal_id)
                VALUES (:id, 'workspace-1', 'UPLOADED', 'image/jpeg', :storageKey, 'READY', 'principal-1')
                """.trimIndent(),
            )
                .bind("id", assetId)
                .bind("storageKey", "assets/workspace-1/$assetId")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private class InjectedSocialAccountFailure : RuntimeException("Injected social account repository failure")

    private class InjectedJobFailure : RuntimeException("Injected job repository failure")

    private class FixedPrincipalContextProvider : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:owner@example.com",
        )
    }

    private class FixedResourceContextProvider : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
    }

    private class FixedOAuthStateSigner : OAuthStateSigner {
        override fun sign(payload: LinkedInOAuthStatePayload): String = "state-193"

        override fun verify(state: String): LinkedInOAuthStatePayload = LinkedInOAuthStatePayload(
            provider = SocialProvider.LINKEDIN,
            workspaceId = "workspace-1",
            principalId = "principal-1",
            redirectUri = "https://app.example.com/callback",
            nonce = "nonce-193",
            issuedAt = Instant.parse("2026-05-26T12:00:00Z"),
            expiresAt = Instant.parse("2026-05-26T12:10:00Z"),
        )
    }

    private class FakeSocialConnectionProvider : SocialConnectionProvider {
        override suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult =
            ProviderConnectionResult(
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-connection-193",
                credentialReference = "secret-ref-193",
                account = ProviderAccountProfile(
                    providerAccountId = "linkedin-account-193",
                    displayName = "Issue 193",
                    kind = SocialAccountKind.PERSONAL_PROFILE,
                    profileUrn = "urn:li:person:193",
                ),
            )
    }

    private class FailingSocialAccountRepository(private val delegate: SocialAccountRepository) :
        SocialAccountRepository by delegate {
        override suspend fun upsert(account: SocialAccount): SocialAccount = throw InjectedSocialAccountFailure()
    }

    private class CapturingChannelEventPublisher : ChannelEventPublisher {
        val events = mutableListOf<ChannelEvent>()

        override fun publish(event: ChannelEvent) {
            events += event
        }
    }

    private class FailingJobRepository(
        private val delegate: PublicationJobRepository,
        private val failEnqueue: Boolean = false,
        private val failReplace: Boolean = false,
        private val failCancel: Boolean = false,
    ) : PublicationJobRepository by delegate {
        override suspend fun enqueue(job: PublicationJob) {
            if (failEnqueue) throw InjectedJobFailure()
            delegate.enqueue(job)
        }

        override suspend fun replaceForPublication(job: PublicationJob) {
            if (failReplace) throw InjectedJobFailure()
            delegate.replaceForPublication(job)
        }

        override suspend fun cancel(jobId: String, cancelledAt: Instant) {
            if (failCancel) throw InjectedJobFailure()
            delegate.cancel(jobId, cancelledAt)
        }

        override suspend fun claimNextDue(now: Instant, workerId: String): PublicationJobClaim? =
            delegate.claimNextDue(now, workerId)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("publishing_transactions_postgres")
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
