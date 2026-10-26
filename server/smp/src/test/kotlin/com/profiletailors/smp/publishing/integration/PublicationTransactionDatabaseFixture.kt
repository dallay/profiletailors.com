package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcDeliveryAttemptRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationJobRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialConnectionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.UUID

abstract class PublicationTransactionDatabaseFixture : PostgresDatabaseTestBase() {
    protected val publicationRepository by lazy { R2dbcPublicationRepository(databaseClient, transactionalOperator) }
    protected val jobRepository by lazy { R2dbcPublicationJobRepository(databaseClient) }
    protected val deliveryAttemptRepository by lazy { R2dbcDeliveryAttemptRepository(databaseClient) }
    protected val socialConnectionRepository by lazy { R2dbcSocialConnectionRepository(databaseClient) }
    protected val socialAccountRepository by lazy {
        R2dbcSocialAccountRepository(databaseClient, SimpleMeterRegistry())
    }
    protected val transactionRunner by lazy { R2dbcAtomicTransactionRunner(transactionalOperator) }
    protected val schedulingPolicy = PublicationSchedulingPolicy()
    protected val now: Instant = Instant.parse("2026-05-26T12:00:00Z")

    @BeforeEach
    fun seedTransactionDatabase() = runTest {
        seedPrincipalWorkspaceAndAccount()
        seedAssets()
    }

    protected fun jobFor(publication: PublicationDraft): PublicationJob = PublicationJob(
        id = publication.id,
        publicationId = publication.id,
        workspaceId = publication.workspaceId,
        status = JobStatus.PENDING,
        dueAt = schedulingPolicy.resolveDueAt(publication, now),
        priorityRank = schedulingPolicy.priorityRank(publication),
        attemptCount = 0,
        maxAttempts = 1,
    )

    protected suspend fun seedPublicationAndJob(
        publicationId: String,
        bodyText: String,
        assetIds: List<String>,
        status: PublicationStatus = PublicationStatus.QUEUED,
    ): String {
        publicationRepository.createDraft(draft(publicationId, bodyText, assetIds, status = status))
        val jobId = UUID.randomUUID().toString()
        jobRepository.enqueue(
            PublicationJob(
                id = jobId,
                publicationId = publicationId,
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                status = JobStatus.PENDING,
                dueAt = now,
                priorityRank = 0,
                attemptCount = 0,
                maxAttempts = 3,
            ),
        )
        return jobId
    }

    protected fun draft(
        publicationId: String,
        bodyText: String,
        assetIds: List<String>,
        priority: Boolean = false,
        status: PublicationStatus = PublicationStatus.QUEUED,
        scheduleMode: ScheduleMode = ScheduleMode.NOW,
        scheduledFor: Instant? = null,
    ): PublicationDraft = PublicationDraft(
        id = publicationId,
        workspaceId = "00000000-0000-0000-0000-00000000049d",
        authorPrincipalId = "00000000-0000-0000-0000-000000000430",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "00000000-0000-0000-0000-0000000003e8",
        status = status,
        scheduleMode = scheduleMode,
        priority = priority,
        bodyText = bodyText,
        assetIds = assetIds,
        scheduledFor = scheduledFor,
    )

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
                '00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-00000000049d',
                'LINKEDIN', 'linkedin-connection-1', 'ACTIVE', '00000000-0000-0000-0000-000000000000'
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
                '00000000-0000-0000-0000-0000000003e8', '00000000-0000-0000-0000-000000000403',
                '00000000-0000-0000-0000-00000000049d', 'LINKEDIN', 'linkedin-account-1',
                'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE'
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedAssets() {
        listOf("00000000-0000-0000-0000-0000000003ed", "00000000-0000-0000-0000-0000000003ee").forEach { assetId ->
            databaseClient.sql(
                """
                INSERT INTO publication_assets (
                    id, workspace_id, source_type, media_type, storage_key, status, created_by_principal_id
                ) VALUES (
                    :id, '00000000-0000-0000-0000-00000000049d', 'UPLOADED', 'image/jpeg', :storageKey,
                    'READY', '00000000-0000-0000-0000-000000000430'
                )
                """.trimIndent(),
            )
                .bind("id", UUID.fromString(assetId))
                .bind("storageKey", "assets/workspace-1/$assetId")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }
}
