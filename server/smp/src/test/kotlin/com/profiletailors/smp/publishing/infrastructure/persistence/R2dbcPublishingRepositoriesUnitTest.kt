package com.profiletailors.smp.publishing.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class R2dbcPublishingRepositoriesUnitTest : DatabaseUnitTestBase() {

    override fun databaseName(): String = "publishing_repos_unit"

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var publicationAssetRepository: R2dbcPublicationAssetRepository
    private lateinit var publicationJobRepository: R2dbcPublicationJobRepository
    private lateinit var deliveryAttemptRepository: R2dbcDeliveryAttemptRepository

    private val fixedClock = java.time.Clock.fixed(
        Instant.parse("2026-06-01T12:00:00Z"),
        java.time.ZoneId.systemDefault()
    )

    @BeforeEach
    fun setUp() = runTest {
        seedWorkspaceAndPrincipal()
        publicationRepository = R2dbcPublicationRepository(databaseClient)
        publicationAssetRepository = R2dbcPublicationAssetRepository(databaseClient, ObjectMapper())
        publicationJobRepository = R2dbcPublicationJobRepository(databaseClient)
        deliveryAttemptRepository = R2dbcDeliveryAttemptRepository(databaseClient)
    }

    // -------------------------------------------------------------------------
    // R2dbcPublicationRepository — mark* operations
    // -------------------------------------------------------------------------

    @Nested
    inner class R2dbcPublicationRepositoryTests {

        @Test
        fun `markPublished updates status and external id`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.QUEUED.name)

            publicationRepository.markPublished(pubId, "linkedin-post-123", Instant.parse("2026-06-01T12:00:00Z"))

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.PUBLISHED, loaded!!.status)
            assertEquals("linkedin-post-123", loaded.externalPublicationId)
            assertNotNull(loaded.publishedAt)
            assertNull(loaded.failedAt)
            assertNull(loaded.lastErrorCode)
        }

        @Test
        fun `markFailed stores error code and message`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.PROCESSING.name)

            publicationRepository.markFailed(
                pubId,
                Instant.parse("2026-06-01T12:00:00Z"),
                reasonCode = "RATE_LIMITED",
                reasonMessage = "LinkedIn rate limit exceeded"
            )

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.FAILED, loaded!!.status)
            assertEquals("RATE_LIMITED", loaded.lastErrorCode)
            assertEquals("LinkedIn rate limit exceeded", loaded.lastErrorMessage)
            assertNotNull(loaded.failedAt)
        }

        @Test
        fun `markCancelled sets CANCELLED status`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.QUEUED.name)

            publicationRepository.markCancelled(pubId, Instant.parse("2026-06-01T12:00:00Z"))

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.CANCELLED, loaded!!.status)
            assertNotNull(loaded.failedAt)
        }

        @Test
        fun `createDraft with multiple assets replaces asset links`() = runTest {
            databaseClient.sql(
                """
                INSERT INTO publication_assets (id, workspace_id, source_type, media_type, storage_key, status, created_by_principal_id)
                VALUES ('asset-1', 'workspace-1', 'UPLOADED', 'image/png', 'key1.png', 'READY', 'principal-1'),
                       ('asset-2', 'workspace-1', 'UPLOADED', 'video/mp4', 'key2.mp4', 'READY', 'principal-1')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()

            val draft = PublicationDraft(
                id = "pub-multi-asset",
                workspaceId = "workspace-1",
                authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "soacc-1",
                status = PublicationStatus.DRAFT,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Multi-asset post",
                assetIds = listOf("asset-1", "asset-2"),
            )

            publicationRepository.createDraft(draft)

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", "pub-multi-asset")
            assertNotNull(loaded)
            assertEquals(listOf("asset-1", "asset-2"), loaded!!.assetIds)
        }

        @Test
        fun `findByWorkspaceAndId returns null when not found`() = runTest {
            val result = publicationRepository.findByWorkspaceAndId("workspace-1", "non-existent-id")
            assertNull(result)
        }

        @Test
        fun `updateEditableDraft replaces publication and asset links`() = runTest {
            databaseClient.sql(
                """
                INSERT INTO publication_assets (id, workspace_id, source_type, media_type, storage_key, status, created_by_principal_id)
                VALUES ('asset-x', 'workspace-1', 'UPLOADED', 'image/jpeg', 'keyx.jpg', 'READY', 'principal-1')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()

            val originalId = "pub-update-test"
            publicationRepository.createDraft(
                PublicationDraft(
                    id = originalId,
                    workspaceId = "workspace-1",
                    authorPrincipalId = "principal-1",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "soacc-1",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = true,
                    bodyText = "Original body",
                    assetIds = emptyList(),
                ),
            )

            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = originalId,
                    workspaceId = "workspace-1",
                    authorPrincipalId = "principal-1",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "soacc-1",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = true,
                    bodyText = "Updated body",
                    assetIds = listOf("asset-x"),
                ),
            )

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", originalId)
            assertNotNull(loaded)
            assertEquals("Updated body", loaded!!.bodyText)
            assertEquals(listOf("asset-x"), loaded.assetIds)
        }

        @Test
        fun `deleteById cascades delivery attempts jobs asset links and publication`() = runTest {
            val pubId = insertPublication(PublicationStatus.DRAFT.name, "pub-delete-1")
            insertPublicationAsset("asset-delete-1", PublicationAssetStatus.READY)
            databaseClient.sql(
                """
                INSERT INTO publication_asset_links (publication_id, asset_id, position_index)
                VALUES (:publicationId, 'asset-delete-1', 0)
                """.trimIndent(),
            )
                .bind("publicationId", pubId)
                .fetch().rowsUpdated().awaitSingle()
            insertPublicationJob("job-delete-1", pubId, JobStatus.PENDING, Instant.parse("2026-06-01T12:00:00Z"))
            deliveryAttemptRepository.record(
                DeliveryAttempt(
                    id = "attempt-delete-1",
                    publicationId = pubId,
                    publicationJobId = "job-delete-1",
                    attemptNumber = 1,
                    outcome = DeliveryAttemptOutcome.FAILED,
                    retryable = true,
                    attemptedAt = Instant.parse("2026-06-01T12:01:00Z"),
                ),
            )

            publicationRepository.deleteById("workspace-1", pubId)

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", pubId)
            assertNull(loaded)

            val assetLinkCount = databaseClient.sql("SELECT COUNT(*) AS c FROM publication_asset_links WHERE publication_id = :id")
                .bind("id", pubId)
                .map { row, _ -> requireNotNull(row.get("c", java.lang.Long::class.java)).toLong() }
                .one().awaitSingle()
            assertEquals(0L, assetLinkCount)

            val jobCount = databaseClient.sql("SELECT COUNT(*) AS c FROM publication_jobs WHERE publication_id = :id")
                .bind("id", pubId)
                .map { row, _ -> requireNotNull(row.get("c", java.lang.Long::class.java)).toLong() }
                .one().awaitSingle()
            assertEquals(0L, jobCount)

            val attemptCount = databaseClient.sql("SELECT COUNT(*) AS c FROM delivery_attempts WHERE publication_id = :id")
                .bind("id", pubId)
                .map { row, _ -> requireNotNull(row.get("c", java.lang.Long::class.java)).toLong() }
                .one().awaitSingle()
            assertEquals(0L, attemptCount)
        }

        @Test
        fun `deleteById only deletes within the correct workspace`() = runTest {
            databaseClient.sql(
                """
                INSERT INTO workspaces (id, name, status, icon)
                VALUES ('workspace-2', 'Workspace 2', 'ACTIVE', NULL)
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
            databaseClient.sql(
                """
                INSERT INTO social_connections (id, workspace_id, provider, provider_connection_ref, status, credential_reference)
                VALUES ('soconn-2', 'workspace-2', 'LINKEDIN', 'linkedin-conn-2', 'ACTIVE', '00000000-0000-0000-0000-000000000000')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
            databaseClient.sql(
                """
                INSERT INTO social_accounts (id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, status)
                VALUES ('soacc-2', 'soconn-2', 'workspace-2', 'LINKEDIN', 'linkedin-account-2', 'PERSONAL_PROFILE', 'Other', 'ACTIVE')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
            databaseClient.sql(
                """
                INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id, status, schedule_mode, priority, title, body_text, created_at, updated_at)
                VALUES ('pub-delete-ws', 'workspace-2', 'principal-1', 'LINKEDIN', 'soacc-2', 'DRAFT', 'NOW', false, 'Other', 'Other body', :createdAt, :updatedAt)
                """.trimIndent(),
            )
                .bind("createdAt", Instant.now())
                .bind("updatedAt", Instant.now())
                .fetch().rowsUpdated().awaitSingle()

            publicationRepository.deleteById("workspace-1", "pub-delete-ws")

            val workspace2Count = databaseClient.sql("SELECT COUNT(*) AS c FROM publications WHERE workspace_id = 'workspace-2' AND id = 'pub-delete-ws'")
                .map { row, _ -> requireNotNull(row.get("c", java.lang.Long::class.java)).toLong() }
                .one().awaitSingle()
            assertEquals(1L, workspace2Count)
        }
    }

    // -------------------------------------------------------------------------
    // R2dbcPublicationAssetRepository — create / update operations
    // -------------------------------------------------------------------------

    @Nested
    inner class R2dbcPublicationAssetRepositoryTests {

        @Test
        fun `create inserts asset and returns it`() = runTest {
            val asset = PublicationAsset(
                id = "asset-create-1",
                workspaceId = "workspace-1",
                sourceType = AssetSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "uploads/image.png",
                originalFilename = "screenshot.png",
                fileSizeBytes = 12345L,
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = "principal-1",
            )

            val result = publicationAssetRepository.create(asset)

            assertEquals("asset-create-1", result.id)
            assertEquals(PublicationAssetStatus.READY, result.status)

            // Verify persisted
            val loaded = publicationAssetRepository.findByWorkspaceAndIds("workspace-1", listOf("asset-create-1"))
            assertEquals(1, loaded.size)
            assertEquals("image/png", loaded[0].mediaType)
        }

        @Test
        fun `create with null optional fields succeeds`() = runTest {
            val asset = PublicationAsset(
                id = "asset-create-nulls",
                workspaceId = "workspace-1",
                sourceType = AssetSourceType.EXTERNAL_URL,
                mediaType = "image/png",
                externalUrl = "https://example.com/image.png",
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = "principal-1",
            )

            val result = publicationAssetRepository.create(asset)

            assertEquals("asset-create-nulls", result.id)
            assertEquals("https://example.com/image.png", result.externalUrl)
        }

        @Test
        fun `updateStatus changes asset status`() = runTest {
            insertPublicationAsset("asset-status-1", PublicationAssetStatus.READY)

            publicationAssetRepository.updateStatus("asset-status-1", PublicationAssetStatus.PROCESSING)

            val loaded = publicationAssetRepository.findByWorkspaceAndIds("workspace-1", listOf("asset-status-1"))
            assertEquals(PublicationAssetStatus.PROCESSING, loaded[0].status)
        }

        @Test
        fun `updateProviderAssetRef sets READY status and serializes ref`() = runTest {
            insertPublicationAsset("asset-ref-1", PublicationAssetStatus.PROCESSING)
            val ref = ProviderAssetRef(
                providerAssetId = "urn:li:image:12345",
                mediaType = "image/png",
                accessUrl = "https://media.example.com/img.jpg",
            )

            publicationAssetRepository.updateProviderAssetRef("asset-ref-1", ref)

            val loaded = publicationAssetRepository.findByWorkspaceAndIds("workspace-1", listOf("asset-ref-1"))
            assertTrue(loaded.isNotEmpty(), "Should find the updated asset")
            val updatedAsset = loaded.find { it.id == "asset-ref-1" }
            assertNotNull(updatedAsset, "Should find asset-ref-1")
            // Status should be READY after provider asset ref update
            assertEquals(PublicationAssetStatus.READY, updatedAsset!!.status)
        }

        @Test
        fun `findByWorkspaceAndIds returns empty list for empty input`() = runTest {
            val result = publicationAssetRepository.findByWorkspaceAndIds("workspace-1", emptyList())
            assertTrue(result.isEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // R2dbcPublicationJobRepository — all job operations
    // -------------------------------------------------------------------------

    @Nested
    inner class R2dbcPublicationJobRepositoryTests {

        @Test
        fun `enqueue inserts job`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("job-enqueue-1", pubId, JobStatus.PENDING)

            publicationJobRepository.enqueue(job)

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-1")
            assertNotNull(claim)
            assertEquals("job-enqueue-1", claim!!.jobId)
            assertEquals(pubId, claim.publicationId)
        }

        @Test
        fun `replaceForPublication deletes and re-inserts`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job1 = makeJob("job-replace-1", pubId, JobStatus.PENDING)
            val job2 = makeJob("job-replace-2", pubId, JobStatus.PENDING)

            publicationJobRepository.enqueue(job1)
            publicationJobRepository.replaceForPublication(job2)

            val claim1 = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-A")
            assertNotNull(claim1)
            assertEquals("job-replace-2", claim1!!.jobId)

            val claim2 = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-B")
            assertNull(claim2)
        }

        @Test
        fun `claimNextDue returns null when no jobs are due`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            insertPublicationJob("job-future", pubId, JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-02T00:00:00Z"))

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T12:00:00Z"), "worker-1")
            assertNull(claim)
        }

        @Test
        fun `claimNextDue claims highest priority due job`() = runTest {
            val pubLow = insertPublication(PublicationStatus.PROCESSING.name, "pub-low")
            val pubHigh = insertPublication(PublicationStatus.PROCESSING.name, "pub-high")
            insertPublicationJob("job-low-priority", pubLow, JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T10:00:00Z"), priorityRank = 1)
            insertPublicationJob("job-high-priority", pubHigh, JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T10:00:00Z"), priorityRank = 100)

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T12:00:00Z"), "worker-1")
            assertNotNull(claim)
            assertEquals("job-high-priority", claim!!.jobId)
        }

        @Test
        fun `claimNextDue returns RETRY_WAITING jobs past due_at`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            insertPublicationJob("job-retry", pubId, JobStatus.RETRY_WAITING,
                dueAt = Instant.parse("2026-06-01T08:00:00Z"), priorityRank = 50)

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T12:00:00Z"), "worker-1")
            assertNotNull(claim)
            assertEquals("job-retry", claim!!.jobId)
        }

        @Test
        fun `rescheduleRetry updates job to RETRY_WAITING and clears claim`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("job-retry-test", pubId, JobStatus.CLAIMED)
            publicationJobRepository.enqueue(job)
            publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T12:00:00Z"), "worker-1")

            publicationJobRepository.rescheduleRetry(
                jobId = "job-retry-test",
                nextAttemptAt = Instant.parse("2026-06-01T14:00:00Z"),
                attemptNumber = 2,
            )

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T14:00:00Z"), "worker-2")
            assertNotNull(claim)
            // claimNextDue increments stored attempt_count, so after rescheduleRetry(2)
            // the stored count is 2, and the next claim returns 2 + 1 = 3
            assertEquals(3, claim!!.attemptNumber)
        }

        @Test
        fun `complete sets job status to COMPLETED`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("job-complete-test", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            publicationJobRepository.complete("job-complete-test", Instant.parse("2026-06-01T12:30:00Z"))

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-1")
            assertNull(claim)
        }

        @Test
        fun `fail sets job status to FAILED`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("job-fail-test", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            publicationJobRepository.fail("job-fail-test", Instant.parse("2026-06-01T12:30:00Z"))

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-1")
            assertNull(claim)
        }

        @Test
        fun `cancel sets job status to CANCELLED by publication_id`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "pub-cancel-test")
            val job = makeJob("job-cancel-test", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            // cancel() uses WHERE publication_id = :publicationId — pass publication_id
            publicationJobRepository.cancel(pubId, Instant.parse("2026-06-01T12:30:00Z"))

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-1")
            assertNull(claim)
        }
    }

    // -------------------------------------------------------------------------
    // R2dbcDeliveryAttemptRepository
    // -------------------------------------------------------------------------

    @Nested
    inner class R2dbcDeliveryAttemptRepositoryTests {

        @Test
        fun `record stores attempt and returns it`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "pub-da-1")
            val jobId = "job-da-1"
            insertPublicationJob(jobId, pubId, JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"))

            val attempt = DeliveryAttempt(
                id = "attempt-1",
                publicationId = pubId,
                publicationJobId = jobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.SUCCEEDED,
                retryable = false,
                externalPublicationId = "linkedin-post-456",
                attemptedAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

            val result = deliveryAttemptRepository.record(attempt)

            assertEquals("attempt-1", result.id)
            assertEquals(DeliveryAttemptOutcome.SUCCEEDED, result.outcome)
        }

        @Test
        fun `record with null optional fields succeeds`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "pub-da-2")
            val jobId = "job-da-2"
            insertPublicationJob(jobId, pubId, JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"))

            val attempt = DeliveryAttempt(
                id = "attempt-2",
                publicationId = pubId,
                publicationJobId = jobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = true,
                providerMessage = "Connection timeout",
                providerErrorCode = "CONN_TIMEOUT",
                attemptedAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

            val result = deliveryAttemptRepository.record(attempt)

            assertEquals("attempt-2", result.id)
            assertEquals("Connection timeout", result.providerMessage)
            assertEquals("CONN_TIMEOUT", result.providerErrorCode)
        }

        @Test
        fun `record with all fields set succeeds`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "pub-da-3")
            val jobId = "job-da-3"
            insertPublicationJob(jobId, pubId, JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"))

            val attempt = DeliveryAttempt(
                id = "attempt-3",
                publicationId = pubId,
                publicationJobId = jobId,
                attemptNumber = 3,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = true,
                providerMessage = "Rate limit",
                providerErrorCode = "RATE_LIMIT",
                externalPublicationId = "ext-id-3",
                attemptedAt = Instant.parse("2026-06-01T12:00:00Z"),
                createdAt = Instant.parse("2026-06-01T12:00:01Z"),
            )

            val result = deliveryAttemptRepository.record(attempt)

            assertEquals("attempt-3", result.id)
            assertEquals(3, result.attemptNumber)
            assertEquals("ext-id-3", result.externalPublicationId)
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private suspend fun seedWorkspaceAndPrincipal() {
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
            INSERT INTO social_connections (id, workspace_id, provider, provider_connection_ref, status, credential_reference)
            VALUES ('soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-conn-1', 'ACTIVE', '00000000-0000-0000-0000-000000000000')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, status)
            VALUES ('soacc-1', 'soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertPublication(status: String, id: String = "pub-mark-${System.nanoTime()}"): String {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id, status, schedule_mode, priority, title, body_text, created_at, updated_at)
            VALUES (:id, 'workspace-1', 'principal-1', 'LINKEDIN', 'soacc-1', :status, 'NOW', false, 'Test', 'Body', :createdAt, :updatedAt)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("status", status)
            .bind("createdAt", java.time.Instant.now())
            .bind("updatedAt", java.time.Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return id
    }

    private suspend fun insertPublicationAsset(id: String, status: PublicationAssetStatus) {
        databaseClient.sql(
            """
            INSERT INTO publication_assets (id, workspace_id, source_type, media_type, storage_key, status, created_by_principal_id)
            VALUES (:id, 'workspace-1', 'UPLOADED', 'image/png', 'key.png', :status, 'principal-1')
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("status", status.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertPublicationJob(
        id: String,
        publicationId: String,
        status: JobStatus,
        dueAt: Instant,
        priorityRank: Int = 10,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publication_jobs (id, publication_id, workspace_id, status, due_at, priority_rank, attempt_count, max_attempts, created_at)
            VALUES (:id, :publicationId, 'workspace-1', :status, :dueAt, :priorityRank, 0, 3, :createdAt)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("publicationId", publicationId)
            .bind("status", status.name)
            .bind("dueAt", dueAt)
            .bind("priorityRank", priorityRank)
            .bind("createdAt", java.time.Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun makeJob(id: String, publicationId: String, status: JobStatus): PublicationJob {
        return PublicationJob(
            id = id,
            publicationId = publicationId,
            workspaceId = "workspace-1",
            status = status,
            dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            priorityRank = 10,
            attemptCount = 0,
            maxAttempts = 3,
        )
    }
}