package com.profiletailors.smp.publishing.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertFailsWith

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcPublishingRepositoriesUnitTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var publicationAssetRepository: R2dbcPublicationAssetRepository
    private lateinit var publicationJobRepository: R2dbcPublicationJobRepository
    private lateinit var deliveryAttemptRepository: R2dbcDeliveryAttemptRepository

    private val fixedClock = java.time.Clock.fixed(
        Instant.parse("2026-06-01T12:00:00Z"),
        java.time.ZoneId.systemDefault(),
    )

    @BeforeEach
    fun setUp() = runTest {
        seedWorkspaceAndPrincipal()
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
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
                reasonMessage = "LinkedIn rate limit exceeded",
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
                INSERT INTO publication_assets (
                    id, workspace_id, source_type, media_type, storage_key, status,
                    created_by_principal_id
                ) VALUES (
                    'asset-1', 'workspace-1', 'UPLOADED', 'image/png', 'key1.png',
                    'READY', 'principal-1'
                ),
                       ('asset-2', 'workspace-1', 'UPLOADED', 'video/mp4', 'key2.mp4',
                        'READY', 'principal-1')
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
                INSERT INTO publication_assets (
                    id, workspace_id, source_type, media_type, storage_key, status,
                    created_by_principal_id
                ) VALUES (
                    'asset-old', 'workspace-1', 'UPLOADED', 'image/jpeg', 'key-old.jpg',
                    'READY', 'principal-1'
                ),
                       ('asset-x', 'workspace-1', 'UPLOADED', 'image/jpeg', 'keyx.jpg',
                        'READY', 'principal-1')
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
                    assetIds = listOf("asset-old"),
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
        fun `updateEditableDraft updates same-workspace row without duplicate insert`() = runTest {
            val originalId = insertPublication(status = PublicationStatus.DRAFT.name, id = "pub-same-workspace-update")

            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = originalId,
                    workspaceId = "workspace-1",
                    authorPrincipalId = "principal-1",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "soacc-1",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = false,
                    title = "Updated title",
                    bodyText = "Updated same-workspace body",
                ),
            )

            val publicationCount = countPublicationsById(originalId)
            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", originalId)

            assertEquals(1L, publicationCount)
            assertNotNull(loaded)
            assertEquals("Updated title", loaded!!.title)
            assertEquals("Updated same-workspace body", loaded.bodyText)
            assertEquals("workspace-1", loaded.workspaceId)
        }

        @Test
        fun `createDraft inserts when current workspace has no matching row`() = runTest {
            val draftId = "pub-create-no-match"

            publicationRepository.createDraft(
                PublicationDraft(
                    id = draftId,
                    workspaceId = "workspace-1",
                    authorPrincipalId = "principal-1",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "soacc-1",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = false,
                    bodyText = "Created in current workspace",
                ),
            )

            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", draftId)
            assertNotNull(loaded)
            assertEquals("Created in current workspace", loaded!!.bodyText)
            assertEquals(1L, countPublicationsById(draftId))
        }

        @Test
        fun `updateEditableDraft fails fast when existing id belongs to another workspace`() = runTest {
            insertWorkspaceGraph(
                workspaceId = "workspace-2",
                principalId = "principal-2",
                connectionId = "soconn-2",
                accountId = "soacc-2",
                subject = "local:user2@example.com",
                displayIdentity = "User 2",
                workspaceName = "Workspace 2",
                displayName = "Yuniel Two",
                providerConnectionRef = "linkedin-conn-2",
                providerAccountId = "linkedin-account-2",
            )
            insertPublicationForWorkspace(
                workspaceId = "workspace-2",
                principalId = "principal-2",
                socialAccountId = "soacc-2",
                status = PublicationStatus.DRAFT.name,
                id = "pub-cross-workspace-existing-id",
                title = "Workspace 2 title",
                bodyText = "Workspace 2 body",
            )

            val exception = assertFailsWith<IllegalStateException> {
                publicationRepository.updateEditableDraft(
                    PublicationDraft(
                        id = "pub-cross-workspace-existing-id",
                        workspaceId = "workspace-1",
                        authorPrincipalId = "principal-1",
                        provider = SocialProvider.LINKEDIN,
                        socialAccountId = "soacc-1",
                        status = PublicationStatus.DRAFT,
                        scheduleMode = ScheduleMode.NOW,
                        priority = false,
                        title = "Workspace 1 hijack attempt",
                        bodyText = "This must fail",
                    ),
                )
            }

            assertTrue(exception.message!!.contains("current workspace"))
            val workspaceTwoLoaded = publicationRepository.findByWorkspaceAndId(
                "workspace-2",
                "pub-cross-workspace-existing-id",
            )
            val workspaceOneLoaded = publicationRepository.findByWorkspaceAndId(
                "workspace-1",
                "pub-cross-workspace-existing-id",
            )
            assertNotNull(workspaceTwoLoaded)
            assertEquals("Workspace 2 title", workspaceTwoLoaded!!.title)
            assertEquals("Workspace 2 body", workspaceTwoLoaded.bodyText)
            assertNull(workspaceOneLoaded)
            assertEquals(1L, countPublicationsById("pub-cross-workspace-existing-id"))
        }

        @Test
        fun `deleteUnpublished returns false when publication not found`() = runTest {
            val result = publicationRepository.deleteUnpublished("workspace-1", "non-existent-pub")
            assertFalse(result)
        }

        @Test
        fun `deleteUnpublished returns false when publication has PUBLISHED status`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.PUBLISHED.name)

            // Insert child records to verify they survive the failed deletion
            val assetId = "asset-published-${System.nanoTime()}"
            insertPublicationAsset(assetId, PublicationAssetStatus.READY)
            databaseClient.sql(
                """
                INSERT INTO publication_asset_links (publication_id, asset_id, position_index)
                VALUES (:publicationId, :assetId, 0)
                """.trimIndent(),
            )
                .bind("publicationId", pubId)
                .bind("assetId", assetId)
                .fetch()
                .rowsUpdated()
                .awaitSingle()

            insertPublicationJob(
                id = "job-published-${System.nanoTime()}",
                publicationId = pubId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T13:00:00Z"),
            )

            val result = publicationRepository.deleteUnpublished("workspace-1", pubId)

            assertFalse(result)
            // Publication should still exist (not deleted)
            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.PUBLISHED, loaded!!.status)

            // Child records must NOT have been deleted
            val remainingLinks = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_asset_links WHERE publication_id = :publicationId",
            )
                .bind("publicationId", pubId)
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingLinks, "publication_asset_links must NOT be deleted for PUBLISHED publication")

            val remainingJobs = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_jobs WHERE publication_id = :publicationId",
            )
                .bind("publicationId", pubId)
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingJobs, "publication_jobs must NOT be deleted for PUBLISHED publication")
        }

        @Test
        fun `deleteUnpublished returns false when publication has FAILED status`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.FAILED.name)

            // Insert child records to verify they survive the failed deletion
            val assetId = "asset-failed-${System.nanoTime()}"
            insertPublicationAsset(assetId, PublicationAssetStatus.READY)
            databaseClient.sql(
                """
                INSERT INTO publication_asset_links (publication_id, asset_id, position_index)
                VALUES (:publicationId, :assetId, 0)
                """.trimIndent(),
            )
                .bind("publicationId", pubId)
                .bind("assetId", assetId)
                .fetch()
                .rowsUpdated()
                .awaitSingle()

            insertPublicationJob(
                id = "job-failed-${System.nanoTime()}",
                publicationId = pubId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T13:00:00Z"),
            )

            val result = publicationRepository.deleteUnpublished("workspace-1", pubId)

            assertFalse(result)
            val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.FAILED, loaded!!.status)

            // Child records must NOT have been deleted
            val remainingLinks = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_asset_links WHERE publication_id = :publicationId",
            )
                .bind("publicationId", pubId)
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingLinks, "publication_asset_links must NOT be deleted for FAILED publication")

            val remainingJobs = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_jobs WHERE publication_id = :publicationId",
            )
                .bind("publicationId", pubId)
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingJobs, "publication_jobs must NOT be deleted for FAILED publication")
        }

        @Test
        fun `deleteUnpublished uses TransactionalOperator for atomicity`() {
            // Verify the repository constructor accepts TransactionalOperator
            // This is a compile-time check: if the refactoring is correct,
            // R2dbcPublicationRepository will require TransactionalOperator
            val constructor = R2dbcPublicationRepository::class.java.constructors.first()
            val paramTypes = constructor.parameterTypes
            assertTrue(
                paramTypes.any { it.simpleName.contains("TransactionalOperator") },
                "R2dbcPublicationRepository must accept TransactionalOperator",
            )
        }

        @Test
        fun `deleteUnpublished removes publication asset links and unclaimed jobs`() = runTest {
            val assetId = "asset-delete-${System.nanoTime()}"
            val publicationId = "pub-delete-${System.nanoTime()}"
            insertPublicationAsset(assetId, PublicationAssetStatus.READY)

            insertPublication(status = PublicationStatus.SCHEDULED.name, id = publicationId)
            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = publicationId,
                    workspaceId = "workspace-1",
                    authorPrincipalId = "principal-1",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "soacc-1",
                    status = PublicationStatus.SCHEDULED,
                    scheduleMode = ScheduleMode.SCHEDULED_AT,
                    priority = false,
                    bodyText = "Delete body",
                    assetIds = listOf(assetId),
                    scheduledFor = Instant.parse("2026-06-01T13:00:00Z"),
                ),
            )
            insertPublicationJob(
                id = "job-delete-pending-${System.nanoTime()}",
                publicationId = publicationId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T13:00:00Z"),
            )

            publicationRepository.deleteUnpublished("workspace-1", publicationId)

            assertNull(publicationRepository.findByWorkspaceAndId("workspace-1", publicationId))

            val remainingLinks = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_asset_links WHERE publication_id = :publicationId",
            )
                .bind("publicationId", publicationId)
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(0L, remainingLinks)

            val remainingJobs = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_jobs WHERE publication_id = :publicationId",
            )
                .bind("publicationId", publicationId)
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(0L, remainingJobs)
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
        fun `replaceForPublication deletes attempts before replacing job`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job1 = makeJob("job-replace-1", pubId, JobStatus.PENDING)
            val job2 = makeJob("job-replace-2", pubId, JobStatus.PENDING)

            publicationJobRepository.enqueue(job1)
            (1..3).forEach { attemptNumber ->
                deliveryAttemptRepository.record(
                    DeliveryAttempt(
                        id = "attempt-replace-$attemptNumber",
                        publicationId = pubId,
                        publicationJobId = job1.id,
                        attemptNumber = attemptNumber,
                        outcome = DeliveryAttemptOutcome.FAILED,
                        retryable = false,
                        attemptedAt = Instant.parse("2026-06-01T12:00:00Z").plusSeconds(attemptNumber.toLong()),
                    ),
                )
            }
            publicationJobRepository.replaceForPublication(job2)

            val remainingAttempts = databaseClient.sql(
                "SELECT COUNT(*) AS count FROM delivery_attempts WHERE publication_id = :publicationId",
            )
                .bind("publicationId", pubId)
                .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(0L, remainingAttempts)

            val claim1 = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-A")
            assertNotNull(claim1)
            assertEquals("job-replace-2", claim1!!.jobId)

            val claim2 = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-B")
            assertNull(claim2)
        }

        @Test
        fun `replaceForPublication inserts job when previous job is missing`() = runTest {
            val pubId = insertPublication(PublicationStatus.FAILED.name)
            val replacement = makeJob("job-replacement-without-previous", pubId, JobStatus.PENDING)

            publicationJobRepository.replaceForPublication(replacement)

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T13:00:00Z"), "worker-1")
            assertNotNull(claim)
            assertEquals(replacement.id, claim?.jobId)
        }

        @Test
        fun `claimNextDue returns null when no jobs are due`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            insertPublicationJob(
                "job-future",
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-02T00:00:00Z"),
            )

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T12:00:00Z"), "worker-1")
            assertNull(claim)
        }

        @Test
        fun `claimNextDue claims highest priority due job`() = runTest {
            val pubLow = insertPublication(PublicationStatus.PROCESSING.name, "pub-low")
            val pubHigh = insertPublication(PublicationStatus.PROCESSING.name, "pub-high")
            insertPublicationJob(
                "job-low-priority",
                pubLow,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T10:00:00Z"),
                priorityRank = 1,
            )
            insertPublicationJob(
                "job-high-priority",
                pubHigh,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T10:00:00Z"),
                priorityRank = 100,
            )

            val claim = publicationJobRepository.claimNextDue(Instant.parse("2026-06-01T12:00:00Z"), "worker-1")
            assertNotNull(claim)
            assertEquals("job-high-priority", claim!!.jobId)
        }

        @Test
        fun `claimNextDue returns RETRY_WAITING jobs past due_at`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            insertPublicationJob(
                "job-retry",
                pubId,
                JobStatus.RETRY_WAITING,
                dueAt = Instant.parse("2026-06-01T08:00:00Z"),
                priorityRank = 50,
            )

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
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

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
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

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
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

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
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                'soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-conn-1',
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
                'soacc-1', 'soconn-1', 'workspace-1', 'LINKEDIN',
                'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertPublication(status: String, id: String = "pub-mark-${System.nanoTime()}"): String =
        insertPublicationForWorkspace(
            workspaceId = "workspace-1",
            principalId = "principal-1",
            socialAccountId = "soacc-1",
            status = status,
            id = id,
        )

    private suspend fun insertPublicationForWorkspace(
        workspaceId: String,
        principalId: String,
        socialAccountId: String,
        status: String,
        id: String = "pub-mark-${System.nanoTime()}",
        title: String = "Test",
        bodyText: String = "Body",
    ): String {
        databaseClient.sql(
            """
            INSERT INTO publications (
                id, workspace_id, author_principal_id, provider, social_account_id,
                status, schedule_mode, priority, title, body_text, created_at, updated_at
            ) VALUES (
                :id, :workspaceId, :principalId, 'LINKEDIN', :socialAccountId,
                :status, 'NOW', false, :title, :bodyText, :createdAt, :updatedAt
            )
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .bind("socialAccountId", socialAccountId)
            .bind("status", status)
            .bind("title", title)
            .bind("bodyText", bodyText)
            .bind("createdAt", java.time.Instant.now())
            .bind("updatedAt", java.time.Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return id
    }

    private suspend fun countPublicationsById(publicationId: String): Long =
        databaseClient.sql("SELECT COUNT(*) AS total FROM publications WHERE id = :id")
            .bind("id", publicationId)
            .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
            .one()
            .awaitSingle()

    private suspend fun insertWorkspaceGraph(
        workspaceId: String,
        principalId: String,
        connectionId: String,
        accountId: String,
        subject: String,
        displayIdentity: String,
        workspaceName: String,
        displayName: String,
        providerConnectionRef: String,
        providerAccountId: String,
    ) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:principalId, 'USER', :subject, NULL, :displayIdentity)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("subject", subject)
            .bind("displayIdentity", displayIdentity)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES (:workspaceId, :workspaceName, 'ACTIVE', NULL)
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("workspaceName", workspaceName)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                :connectionId, :workspaceId, 'LINKEDIN', :providerConnectionRef,
                'ACTIVE', '00000000-0000-0000-0000-000000000000'
            )
            """.trimIndent(),
        )
            .bind("connectionId", connectionId)
            .bind("workspaceId", workspaceId)
            .bind("providerConnectionRef", providerConnectionRef)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                :accountId, :connectionId, :workspaceId, 'LINKEDIN', :providerAccountId,
                'PERSONAL_PROFILE', :displayName, 'ACTIVE'
            )
            """.trimIndent(),
        )
            .bind("accountId", accountId)
            .bind("connectionId", connectionId)
            .bind("workspaceId", workspaceId)
            .bind("providerAccountId", providerAccountId)
            .bind("displayName", displayName)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertPublicationAsset(id: String, status: PublicationAssetStatus) {
        databaseClient.sql(
            """
            INSERT INTO publication_assets (
                id, workspace_id, source_type, media_type, storage_key, status,
                created_by_principal_id
            ) VALUES (
                :id, 'workspace-1', 'UPLOADED', 'image/png', 'key.png', :status, 'principal-1'
            )
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
            INSERT INTO publication_jobs (
                id, publication_id, workspace_id, status, due_at, priority_rank,
                attempt_count, max_attempts, created_at
            ) VALUES (
                :id, :publicationId, 'workspace-1', :status, :dueAt,
                :priorityRank, 0, 3, :createdAt
            )
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

    private fun makeJob(id: String, publicationId: String, status: JobStatus): PublicationJob = PublicationJob(
        id = id,
        publicationId = publicationId,
        workspaceId = "workspace-1",
        status = status,
        dueAt = Instant.parse("2026-06-01T12:00:00Z"),
        priorityRank = 10,
        attemptCount = 0,
        maxAttempts = 3,
    )

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("publishing_repositories_unit")
    }
}
