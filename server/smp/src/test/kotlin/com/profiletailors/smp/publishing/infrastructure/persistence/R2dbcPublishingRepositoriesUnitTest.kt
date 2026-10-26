package com.profiletailors.smp.publishing.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptPhase
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.RecurrenceFrequency
import com.profiletailors.smp.publishing.domain.RecurrenceRule
import com.profiletailors.smp.publishing.domain.RecurringSchedule
import com.profiletailors.smp.publishing.domain.RecurringScheduleStatus
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
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
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
    private lateinit var recurringScheduleRepository: R2dbcRecurringScheduleRepository

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
        recurringScheduleRepository = R2dbcRecurringScheduleRepository(databaseClient)
    }

    @Test
    fun `recurring schedule persists nullable integer fields through PostgreSQL`() = runTest {
        val templatePostId =
            insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000045d")
        val schedule = RecurringSchedule(
            id = "00000000-0000-0000-0000-000000000480",
            workspaceId = "00000000-0000-0000-0000-00000000049d",
            createdBy = "00000000-0000-0000-0000-000000000430",
            templatePostId = templatePostId,
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 2,
                dayOfMonth = 15,
                maxOccurrences = 12,
            ),
            timezone = "UTC",
            nextScheduledAt = Instant.parse("2026-07-15T10:00:00Z"),
            status = RecurringScheduleStatus.ACTIVE,
            createdAt = Instant.parse("2026-07-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T10:00:00Z"),
        )

        val created = recurringScheduleRepository.create(schedule)
        val found = requireNotNull(
            recurringScheduleRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", schedule.id),
        )

        assertEquals(schedule, created)
        assertEquals(schedule.recurrenceRule, found.recurrenceRule)
        assertEquals(schedule.nextScheduledAt, found.nextScheduledAt)
        assertEquals(schedule.status, found.status)
    }

    // -------------------------------------------------------------------------
    // R2dbcRecurringScheduleRepository — query and status operations
    // -------------------------------------------------------------------------

    @Nested
    inner class R2dbcRecurringScheduleRepositoryTests {

        @Test
        fun `findByWorkspace returns schedules ordered by nextScheduledAt then createdAt`() = runTest {
            val pub1 =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000046c")
            val pub2 =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000046d")
            val pub3 =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000046e")

            recurringScheduleRepository.create(
                RecurringSchedule(
                    id = "00000000-0000-0000-0000-000000000484",
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    createdBy = "00000000-0000-0000-0000-000000000430",
                    templatePostId = pub1,
                    recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1),
                    timezone = "UTC",
                    nextScheduledAt = Instant.parse("2026-07-15T10:00:00Z"),
                    status = RecurringScheduleStatus.ACTIVE,
                    createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T10:00:00Z"),
                ),
            )
            recurringScheduleRepository.create(
                RecurringSchedule(
                    id = "00000000-0000-0000-0000-000000000483",
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    createdBy = "00000000-0000-0000-0000-000000000430",
                    templatePostId = pub2,
                    recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1),
                    timezone = "UTC",
                    nextScheduledAt = Instant.parse("2026-07-10T10:00:00Z"),
                    status = RecurringScheduleStatus.ACTIVE,
                    createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T10:00:00Z"),
                ),
            )
            recurringScheduleRepository.create(
                RecurringSchedule(
                    id = "00000000-0000-0000-0000-000000000485",
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    createdBy = "00000000-0000-0000-0000-000000000430",
                    templatePostId = pub3,
                    recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1),
                    timezone = "UTC",
                    nextScheduledAt = null,
                    status = RecurringScheduleStatus.ACTIVE,
                    createdAt = Instant.parse("2026-07-02T10:00:00Z"),
                    updatedAt = Instant.parse("2026-07-02T10:00:00Z"),
                ),
            )

            val results = recurringScheduleRepository.findByWorkspace("00000000-0000-0000-0000-00000000049d")

            assertEquals(3, results.size)
            assertEquals("00000000-0000-0000-0000-000000000483", results[0].id)
            assertEquals("00000000-0000-0000-0000-000000000484", results[1].id)
            assertEquals("00000000-0000-0000-0000-000000000485", results[2].id)
        }

        @Test
        fun `delete sets status to CANCELLED and returns true`() = runTest {
            val pubId =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000046a")
            recurringScheduleRepository.create(
                RecurringSchedule(
                    id = "00000000-0000-0000-0000-000000000481",
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    createdBy = "00000000-0000-0000-0000-000000000430",
                    templatePostId = pubId,
                    recurrenceRule = RecurrenceRule(
                        frequency = RecurrenceFrequency.WEEKLY,
                        interval = 1,
                        daysOfWeek = setOf(1),
                    ),
                    timezone = "UTC",
                    nextScheduledAt = Instant.parse("2026-07-15T10:00:00Z"),
                    status = RecurringScheduleStatus.ACTIVE,
                    createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T10:00:00Z"),
                ),
            )

            val deleted = recurringScheduleRepository.delete(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-000000000481",
            )

            assertTrue(deleted)
            val found = recurringScheduleRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-000000000481",
            )
            assertEquals(RecurringScheduleStatus.CANCELLED, found!!.status)
        }

        @Test
        fun `delete returns false when schedule already CANCELLED`() = runTest {
            val pubId =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000046b")
            recurringScheduleRepository.create(
                RecurringSchedule(
                    id = "00000000-0000-0000-0000-000000000482",
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    createdBy = "00000000-0000-0000-0000-000000000430",
                    templatePostId = pubId,
                    recurrenceRule = RecurrenceRule(
                        frequency = RecurrenceFrequency.WEEKLY,
                        interval = 1,
                        daysOfWeek = setOf(1),
                    ),
                    timezone = "UTC",
                    nextScheduledAt = Instant.parse("2026-07-15T10:00:00Z"),
                    status = RecurringScheduleStatus.CANCELLED,
                    createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T10:00:00Z"),
                ),
            )

            val result = recurringScheduleRepository.delete(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-000000000482",
            )

            assertFalse(result)
        }

        @Test
        fun `pauses ACTIVE to PAUSED`() = runTest {
            val pubActive =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-00000000046f")
            val pubPaused =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-000000000471")
            val pubCancelled =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-000000000470")
            insertRecurringSchedule("00000000-0000-0000-0000-000000000486", pubActive, RecurringScheduleStatus.ACTIVE)
            insertRecurringSchedule("00000000-0000-0000-0000-000000000488", pubPaused, RecurringScheduleStatus.PAUSED)
            insertRecurringSchedule(
                "00000000-0000-0000-0000-000000000487",
                pubCancelled,
                RecurringScheduleStatus.CANCELLED,
            )

            recurringScheduleRepository.pauseByTemplatePost("00000000-0000-0000-0000-00000000049d", pubActive)

            assertEquals(
                RecurringScheduleStatus.PAUSED,
                requireNotNull(
                    recurringScheduleRepository.findByWorkspaceAndId(
                        "00000000-0000-0000-0000-00000000049d",
                        "00000000-0000-0000-0000-000000000486",
                    ),
                ).status,
            )
            assertEquals(
                RecurringScheduleStatus.PAUSED,
                requireNotNull(
                    recurringScheduleRepository.findByWorkspaceAndId(
                        "00000000-0000-0000-0000-00000000049d",
                        "00000000-0000-0000-0000-000000000488",
                    ),
                ).status,
            )
            assertEquals(
                RecurringScheduleStatus.CANCELLED,
                requireNotNull(
                    recurringScheduleRepository.findByWorkspaceAndId(
                        "00000000-0000-0000-0000-00000000049d",
                        "00000000-0000-0000-0000-000000000487",
                    ),
                ).status,
            )
        }
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

            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", pubId)
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

            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", pubId)
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

            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", pubId)
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
                    '00000000-0000-0000-0000-0000000003ed', '00000000-0000-0000-0000-00000000049d', 'UPLOADED', 'image/png', 'key1.png',
                    'READY', '00000000-0000-0000-0000-000000000430'
                ),
                       ('00000000-0000-0000-0000-0000000003ee', '00000000-0000-0000-0000-00000000049d', 'UPLOADED', 'video/mp4', 'key2.mp4',
                        'READY', '00000000-0000-0000-0000-000000000430')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()

            val draft = PublicationDraft(
                id = "00000000-0000-0000-0000-000000000453",
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-000000000489",
                status = PublicationStatus.DRAFT,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Multi-asset post",
                assetIds = listOf("00000000-0000-0000-0000-0000000003ed", "00000000-0000-0000-0000-0000000003ee"),
            )

            publicationRepository.createDraft(draft)

            val loaded = publicationRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-000000000453",
            )
            assertNotNull(loaded)
            assertEquals(
                listOf("00000000-0000-0000-0000-0000000003ed", "00000000-0000-0000-0000-0000000003ee"),
                requireNotNull(loaded).assetIds,
            )
        }

        @Test
        fun `findByWorkspaceAndId returns null when not found`() = runTest {
            val result = publicationRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-00000000042e",
            )
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
                    '00000000-0000-0000-0000-0000000003f1', '00000000-0000-0000-0000-00000000049d', 'UPLOADED', 'image/jpeg', 'key-old.jpg',
                    'READY', '00000000-0000-0000-0000-000000000430'
                ),
                       ('00000000-0000-0000-0000-0000000003f4', '00000000-0000-0000-0000-00000000049d', 'UPLOADED', 'image/jpeg', 'keyx.jpg',
                        'READY', '00000000-0000-0000-0000-000000000430')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()

            val originalId = "00000000-0000-0000-0000-00000000047d"
            publicationRepository.createDraft(
                PublicationDraft(
                    id = originalId,
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "00000000-0000-0000-0000-000000000489",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = true,
                    bodyText = "Original body",
                    assetIds = listOf("00000000-0000-0000-0000-0000000003f1"),
                ),
            )

            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = originalId,
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "00000000-0000-0000-0000-000000000489",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = true,
                    bodyText = "Updated body",
                    assetIds = listOf("00000000-0000-0000-0000-0000000003f4"),
                ),
            )

            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", originalId)
            assertNotNull(loaded)
            assertEquals("Updated body", loaded!!.bodyText)
            assertEquals(listOf("00000000-0000-0000-0000-0000000003f4"), loaded.assetIds)
        }

        @Test
        fun `updateEditableDraft updates same-workspace row without duplicate insert`() = runTest {
            val originalId =
                insertPublication(status = PublicationStatus.DRAFT.name, id = "00000000-0000-0000-0000-000000000472")

            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = originalId,
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "00000000-0000-0000-0000-000000000489",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = false,
                    title = "Updated title",
                    bodyText = "Updated same-workspace body",
                ),
            )

            val publicationCount = countPublicationsById(originalId)
            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", originalId)

            assertEquals(1L, publicationCount)
            assertNotNull(loaded)
            assertEquals("Updated title", loaded!!.title)
            assertEquals("Updated same-workspace body", loaded.bodyText)
            assertEquals("00000000-0000-0000-0000-00000000049d", loaded.workspaceId)
        }

        @Test
        fun `createDraft inserts when current workspace has no matching row`() = runTest {
            val draftId = "00000000-0000-0000-0000-000000000441"

            publicationRepository.createDraft(
                PublicationDraft(
                    id = draftId,
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "00000000-0000-0000-0000-000000000489",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = false,
                    bodyText = "Created in current workspace",
                ),
            )

            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", draftId)
            assertNotNull(loaded)
            assertEquals("Created in current workspace", loaded!!.bodyText)
            assertEquals(1L, countPublicationsById(draftId))
        }

        @Test
        fun `updateEditableDraft fails fast when existing id belongs to another workspace`() = runTest {
            insertWorkspaceGraph(
                workspaceId = "00000000-0000-0000-0000-00000000049e",
                principalId = "00000000-0000-0000-0000-000000000431",
                connectionId = "00000000-0000-0000-0000-000000000494",
                accountId = "00000000-0000-0000-0000-00000000048a",
                subject = "local:user2@example.com",
                displayIdentity = "User 2",
                workspaceName = "Workspace 2",
                displayName = "Yuniel Two",
                providerConnectionRef = "linkedin-conn-2",
                providerAccountId = "linkedin-account-2",
            )
            insertPublicationForWorkspace(
                workspaceId = "00000000-0000-0000-0000-00000000049e",
                principalId = "00000000-0000-0000-0000-000000000431",
                socialAccountId = "00000000-0000-0000-0000-00000000048a",
                status = PublicationStatus.DRAFT.name,
                id = "00000000-0000-0000-0000-000000000443",
                title = "Workspace 2 title",
                bodyText = "Workspace 2 body",
            )

            val exception = assertFailsWith<IllegalStateException> {
                publicationRepository.updateEditableDraft(
                    PublicationDraft(
                        id = "00000000-0000-0000-0000-000000000443",
                        workspaceId = "00000000-0000-0000-0000-00000000049d",
                        authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                        provider = SocialProvider.LINKEDIN,
                        socialAccountId = "00000000-0000-0000-0000-000000000489",
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
                "00000000-0000-0000-0000-00000000049e",
                "00000000-0000-0000-0000-000000000443",
            )
            val workspaceOneLoaded = publicationRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-000000000443",
            )
            assertNotNull(workspaceTwoLoaded)
            assertEquals("Workspace 2 title", workspaceTwoLoaded!!.title)
            assertEquals("Workspace 2 body", workspaceTwoLoaded.bodyText)
            assertNull(workspaceOneLoaded)
            assertEquals(1L, countPublicationsById("00000000-0000-0000-0000-000000000443"))
        }

        @Test
        fun `deleteUnpublished returns false when publication not found`() = runTest {
            val result = publicationRepository.deleteUnpublished(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-00000000042f",
            )
            assertFalse(result)
        }

        @Test
        fun `deleteUnpublished returns false when publication has PUBLISHED status`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.PUBLISHED.name)

            // Insert child records to verify they survive the failed deletion
            val assetId = UUID.randomUUID().toString()
            insertPublicationAsset(assetId, PublicationAssetStatus.READY)
            databaseClient.sql(
                """
                INSERT INTO publication_asset_links (publication_id, asset_id, position_index)
                VALUES (:publicationId, :assetId, 0)
                """.trimIndent(),
            )
                .bind("publicationId", UUID.fromString(pubId))
                .bind("assetId", UUID.fromString(assetId))
                .fetch()
                .rowsUpdated()
                .awaitSingle()

            insertPublicationJob(
                id = UUID.randomUUID().toString(),
                publicationId = pubId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T13:00:00Z"),
            )

            val result = publicationRepository.deleteUnpublished("00000000-0000-0000-0000-00000000049d", pubId)

            assertFalse(result)
            // Publication should still exist (not deleted)
            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.PUBLISHED, loaded!!.status)

            // Child records must NOT have been deleted
            val remainingLinks = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_asset_links WHERE publication_id = :publicationId",
            )
                .bind("publicationId", UUID.fromString(pubId))
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingLinks, "publication_asset_links must NOT be deleted for PUBLISHED publication")

            val remainingJobs = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_jobs WHERE publication_id = :publicationId",
            )
                .bind("publicationId", UUID.fromString(pubId))
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingJobs, "publication_jobs must NOT be deleted for PUBLISHED publication")
        }

        @Test
        fun `deleteUnpublished returns false when publication has FAILED status`() = runTest {
            val pubId = insertPublication(status = PublicationStatus.FAILED.name)

            // Insert child records to verify they survive the failed deletion
            val assetId = UUID.randomUUID().toString()
            insertPublicationAsset(assetId, PublicationAssetStatus.READY)
            databaseClient.sql(
                """
                INSERT INTO publication_asset_links (publication_id, asset_id, position_index)
                VALUES (:publicationId, :assetId, 0)
                """.trimIndent(),
            )
                .bind("publicationId", UUID.fromString(pubId))
                .bind("assetId", UUID.fromString(assetId))
                .fetch()
                .rowsUpdated()
                .awaitSingle()

            insertPublicationJob(
                id = UUID.randomUUID().toString(),
                publicationId = pubId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T13:00:00Z"),
            )

            val result = publicationRepository.deleteUnpublished("00000000-0000-0000-0000-00000000049d", pubId)

            assertFalse(result)
            val loaded = publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", pubId)
            assertNotNull(loaded)
            assertEquals(PublicationStatus.FAILED, loaded!!.status)

            // Child records must NOT have been deleted
            val remainingLinks = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_asset_links WHERE publication_id = :publicationId",
            )
                .bind("publicationId", UUID.fromString(pubId))
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(1L, remainingLinks, "publication_asset_links must NOT be deleted for FAILED publication")

            val remainingJobs = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_jobs WHERE publication_id = :publicationId",
            )
                .bind("publicationId", UUID.fromString(pubId))
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
            val assetId = UUID.randomUUID().toString()
            val publicationId = UUID.randomUUID().toString()
            insertPublicationAsset(assetId, PublicationAssetStatus.READY)

            insertPublication(status = PublicationStatus.SCHEDULED.name, id = publicationId)
            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = publicationId,
                    workspaceId = "00000000-0000-0000-0000-00000000049d",
                    authorPrincipalId = "00000000-0000-0000-0000-000000000430",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "00000000-0000-0000-0000-000000000489",
                    status = PublicationStatus.SCHEDULED,
                    scheduleMode = ScheduleMode.SCHEDULED_AT,
                    priority = false,
                    bodyText = "Delete body",
                    assetIds = listOf(assetId),
                    scheduledFor = Instant.parse("2026-06-01T13:00:00Z"),
                ),
            )
            insertPublicationJob(
                id = UUID.randomUUID().toString(),
                publicationId = publicationId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T13:00:00Z"),
            )

            publicationRepository.deleteUnpublished("00000000-0000-0000-0000-00000000049d", publicationId)

            assertNull(
                publicationRepository.findByWorkspaceAndId("00000000-0000-0000-0000-00000000049d", publicationId),
            )

            val remainingLinks = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_asset_links WHERE publication_id = :publicationId",
            )
                .bind("publicationId", UUID.fromString(publicationId))
                .map { row, _ -> requireNotNull(row.get("total", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(0L, remainingLinks)

            val remainingJobs = databaseClient.sql(
                "SELECT COUNT(*) AS total FROM publication_jobs WHERE publication_id = :publicationId",
            )
                .bind("publicationId", UUID.fromString(publicationId))
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
                id = "00000000-0000-0000-0000-0000000003ef",
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                sourceType = AssetSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "uploads/image.png",
                originalFilename = "screenshot.png",
                fileSizeBytes = 12345L,
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = "00000000-0000-0000-0000-000000000430",
            )

            val result = publicationAssetRepository.create(asset)

            assertEquals("00000000-0000-0000-0000-0000000003ef", result.id)
            assertEquals(PublicationAssetStatus.READY, result.status)

            // Verify persisted
            val loaded = publicationAssetRepository.findByWorkspaceAndIds(
                "00000000-0000-0000-0000-00000000049d",
                listOf("00000000-0000-0000-0000-0000000003ef"),
            )
            assertEquals(1, loaded.size)
            assertEquals("image/png", loaded[0].mediaType)
        }

        @Test
        fun `create with null optional fields succeeds`() = runTest {
            val asset = PublicationAsset(
                id = "00000000-0000-0000-0000-0000000003f0",
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                sourceType = AssetSourceType.EXTERNAL_URL,
                mediaType = "image/png",
                externalUrl = "https://example.com/image.png",
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = "00000000-0000-0000-0000-000000000430",
            )

            val result = publicationAssetRepository.create(asset)

            assertEquals("00000000-0000-0000-0000-0000000003f0", result.id)
            assertEquals("https://example.com/image.png", result.externalUrl)
        }

        @Test
        fun `updateStatus changes asset status`() = runTest {
            insertPublicationAsset("00000000-0000-0000-0000-0000000003f3", PublicationAssetStatus.READY)

            publicationAssetRepository.updateStatus(
                "00000000-0000-0000-0000-0000000003f3",
                PublicationAssetStatus.PROCESSING,
            )

            val loaded = publicationAssetRepository.findByWorkspaceAndIds(
                "00000000-0000-0000-0000-00000000049d",
                listOf("00000000-0000-0000-0000-0000000003f3"),
            )
            assertEquals(PublicationAssetStatus.PROCESSING, loaded[0].status)
        }

        @Test
        fun `updateProviderAssetRef sets READY status and serializes ref`() = runTest {
            insertPublicationAsset("00000000-0000-0000-0000-0000000003f2", PublicationAssetStatus.PROCESSING)
            val ref = ProviderAssetRef(
                providerAssetId = "urn:li:image:12345",
                mediaType = "image/png",
                accessUrl = "https://media.example.com/img.jpg",
            )

            publicationAssetRepository.updateProviderAssetRef("00000000-0000-0000-0000-0000000003f2", ref)

            val loaded = publicationAssetRepository.findByWorkspaceAndIds(
                "00000000-0000-0000-0000-00000000049d",
                listOf("00000000-0000-0000-0000-0000000003f2"),
            )
            assertTrue(loaded.isNotEmpty(), "Should find the updated asset")
            val updatedAsset = loaded.find { it.id == "00000000-0000-0000-0000-0000000003f2" }
            assertNotNull(updatedAsset, "Should find asset-ref-1")
            // Status should be READY after provider asset ref update
            assertEquals(PublicationAssetStatus.READY, updatedAsset!!.status)
        }

        @Test
        fun `findByWorkspaceAndIds returns empty list for empty input`() = runTest {
            val result = publicationAssetRepository.findByWorkspaceAndIds(
                "00000000-0000-0000-0000-00000000049d",
                emptyList(),
            )
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
            val job = makeJob("00000000-0000-0000-0000-000000000410", pubId, JobStatus.PENDING)

            publicationJobRepository.enqueue(job)

            val claim = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNotNull(claim)
            assertEquals("00000000-0000-0000-0000-000000000410", requireNotNull(claim).jobId)
            assertEquals(pubId, claim.publicationId)
        }

        @Test
        fun `replaceForPublication deletes attempts before replacing job`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job1 = makeJob("00000000-0000-0000-0000-00000000041e", pubId, JobStatus.PENDING)
            val job2 = makeJob("00000000-0000-0000-0000-00000000041f", pubId, JobStatus.PENDING)

            publicationJobRepository.enqueue(job1)
            (1..3).forEach { attemptNumber ->
                deliveryAttemptRepository.record(
                    DeliveryAttempt(
                        id = UUID.randomUUID().toString(),
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
                .bind("publicationId", UUID.fromString(pubId))
                .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
                .one()
                .awaitSingle()
            assertEquals(0L, remainingAttempts)

            val claim1 = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-A",
                Duration.ofMinutes(2),
            )
            assertNotNull(claim1)
            assertEquals("00000000-0000-0000-0000-00000000041f", requireNotNull(claim1).jobId)

            val claim2 = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-B",
                Duration.ofMinutes(2),
            )
            assertNull(claim2)
        }

        @Test
        fun `replaceForPublication inserts job when previous job is missing`() = runTest {
            val pubId = insertPublication(PublicationStatus.FAILED.name)
            val replacement = makeJob("00000000-0000-0000-0000-000000000420", pubId, JobStatus.PENDING)

            publicationJobRepository.replaceForPublication(replacement)

            val claim = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNotNull(claim)
            assertEquals(replacement.id, claim?.jobId)
        }

        @Test
        fun `claimNextDue returns null when no jobs are due`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            insertPublicationJob(
                "00000000-0000-0000-0000-000000000414",
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-02T00:00:00Z"),
            )

            val claim = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T12:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNull(claim)
        }

        @Test
        fun `claimNextDue claims highest priority due job`() = runTest {
            val pubLow = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000450")
            val pubHigh = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-00000000044e")
            insertPublicationJob(
                "00000000-0000-0000-0000-000000000417",
                pubLow,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T10:00:00Z"),
                priorityRank = 1,
            )
            insertPublicationJob(
                "00000000-0000-0000-0000-000000000415",
                pubHigh,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T10:00:00Z"),
                priorityRank = 100,
            )

            val claim = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T12:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNotNull(claim)
            assertEquals("00000000-0000-0000-0000-000000000415", requireNotNull(claim).jobId)
        }

        @Test
        fun `claimNextDue returns RETRY_WAITING jobs past due_at`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            insertPublicationJob(
                "00000000-0000-0000-0000-000000000421",
                pubId,
                JobStatus.RETRY_WAITING,
                dueAt = Instant.parse("2026-06-01T08:00:00Z"),
                priorityRank = 50,
            )

            val claim = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T12:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNotNull(claim)
            assertEquals("00000000-0000-0000-0000-000000000421", requireNotNull(claim).jobId)
        }

        @Test
        fun `rescheduleRetry updates job to RETRY_WAITING and clears claim`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("00000000-0000-0000-0000-000000000422", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            val firstClaim = requireNotNull(
                publicationJobRepository.claimNextDue(
                    Instant.parse("2026-06-01T12:00:00Z"),
                    "worker-1",
                    Duration.ofMinutes(2),
                ),
            )
            publicationJobRepository.rescheduleRetry(
                jobId = "00000000-0000-0000-0000-000000000422",
                claimVersion = firstClaim.claimVersion,
                nextAttemptAt = Instant.parse("2026-06-01T14:00:00Z"),
                attemptNumber = 2,
            )

            val claimAfterReschedule = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T14:00:00Z"),
                "worker-2",
                Duration.ofMinutes(2),
            )
            assertNotNull(claimAfterReschedule)
            // claimNextDue increments stored attempt_count, so after rescheduleRetry(2)
            // the stored count is 2, and the next claim returns 2 + 1 = 3
            assertEquals(3, claimAfterReschedule!!.attemptNumber)
        }

        @Test
        fun `complete sets job status to COMPLETED`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("00000000-0000-0000-0000-000000000409", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            val claim = requireNotNull(
                publicationJobRepository.claimNextDue(
                    Instant.parse("2026-06-01T12:00:00Z"),
                    "worker-1",
                    Duration.ofMinutes(2),
                ),
            )
            assertEquals(
                true,
                publicationJobRepository.complete(
                    "00000000-0000-0000-0000-000000000409",
                    claim.claimVersion,
                    Instant.parse("2026-06-01T12:30:00Z"),
                ),
            )

            val claimAfterCompletion = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNull(claimAfterCompletion)
        }

        @Test
        fun `fail sets job status to FAILED`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name)
            val job = makeJob("00000000-0000-0000-0000-000000000411", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            val claim = requireNotNull(
                publicationJobRepository.claimNextDue(
                    Instant.parse("2026-06-01T12:00:00Z"),
                    "worker-1",
                    Duration.ofMinutes(2),
                ),
            )
            assertEquals(
                true,
                publicationJobRepository.fail(
                    "00000000-0000-0000-0000-000000000411",
                    claim.claimVersion,
                    Instant.parse("2026-06-01T12:30:00Z"),
                ),
            )

            val claimAfterFailure = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNull(claimAfterFailure)
        }

        @Test
        fun `block sets job status to BLOCKED and clears the claim`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000437")
            insertPublicationJob(
                id = "00000000-0000-0000-0000-000000000405",
                publicationId = pubId,
                status = JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )
            val claim = requireNotNull(
                publicationJobRepository.claimNextDue(
                    Instant.parse("2026-06-01T12:00:00Z"),
                    "worker-1",
                    Duration.ofMinutes(2),
                ),
            )

            assertTrue(
                publicationJobRepository.block(
                    jobId = claim.jobId,
                    claimVersion = claim.claimVersion,
                    blockedAt = Instant.parse("2026-06-01T12:30:00Z"),
                ),
            )

            val row = loadJobRow("00000000-0000-0000-0000-000000000405")
            assertEquals("BLOCKED", row["status"])
            assertNotNull(row["blocked_at"])
            assertNull(row["claimed_at"])
            assertNull(row["lease_expires_at"])
        }

        @Test
        fun `cancel sets job status to CANCELLED by publication_id`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-00000000043a")
            val job = makeJob("00000000-0000-0000-0000-000000000406", pubId, JobStatus.PENDING)
            publicationJobRepository.enqueue(job)

            // cancel() uses WHERE publication_id = :publicationId — pass publication_id
            publicationJobRepository.cancel(pubId, Instant.parse("2026-06-01T12:30:00Z"))

            val claim = publicationJobRepository.claimNextDue(
                Instant.parse("2026-06-01T13:00:00Z"),
                "worker-1",
                Duration.ofMinutes(2),
            )
            assertNull(claim)
        }

        // -------------------------------------------------------------------------
        // R2dbcPublicationJobRepository — stale claim visibility / recovery
        // -------------------------------------------------------------------------

        @Test
        fun `should return only claimed jobs whose lease expired past the threshold`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubStaleOld =
                insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000474")
            val pubStaleRecent =
                insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000475")
            val pubFresh = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-00000000044d")
            val pubPending = insertPublication(PublicationStatus.QUEUED.name, "00000000-0000-0000-0000-000000000459")

            // Stale claim (lease expired long ago)
            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000427",
                publicationId = pubStaleOld,
                claimedByWorker = "worker-stuck",
                claimedAt = now.minus(Duration.ofMinutes(30)),
                leaseExpiresAt = now.minus(Duration.ofMinutes(25)),
            )

            // Stale claim (lease just expired past 5-min threshold)
            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000428",
                publicationId = pubStaleRecent,
                claimedByWorker = "worker-stuck",
                claimedAt = now.minus(Duration.ofMinutes(7)),
                leaseExpiresAt = now.minus(Duration.ofMinutes(6)),
            )

            // Fresh claim (still inside 5-min threshold)
            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000413",
                publicationId = pubFresh,
                claimedByWorker = "worker-1",
                claimedAt = now.minus(Duration.ofMinutes(1)),
                leaseExpiresAt = now.plus(Duration.ofMinutes(1)),
            )

            // Non-claimed (PENDING) with old due_at must NOT appear even if lease column would be null
            insertPublicationJob(
                id = "00000000-0000-0000-0000-00000000041b",
                publicationId = pubPending,
                status = JobStatus.PENDING,
                dueAt = now.minus(Duration.ofMinutes(30)),
            )

            val stale = publicationJobRepository.findStaleClaims(now, Duration.ofMinutes(5), limit = 1)

            assertEquals(2, stale.total)
            assertEquals(1, stale.jobs.size)
            // Ordered by lease_expires_at ASC; total still reports the unbounded match count.
            assertEquals("00000000-0000-0000-0000-000000000427", stale.jobs[0].jobId)
            assertEquals("00000000-0000-0000-0000-00000000049d", stale.jobs[0].workspaceId)
            assertEquals("worker-stuck", stale.jobs[0].claimedByWorker)
            assertEquals(pubStaleOld, stale.jobs[0].publicationId)
        }

        @Test
        fun `should return an empty page when no claims have expired past the threshold`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000455")

            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000412",
                publicationId = pubId,
                claimedByWorker = "worker-1",
                claimedAt = now.minus(Duration.ofMinutes(1)),
                leaseExpiresAt = now.plus(Duration.ofMinutes(1)),
            )

            val stale = publicationJobRepository.findStaleClaims(now, Duration.ofMinutes(5), limit = 10)

            assertTrue(stale.jobs.isEmpty(), "Expected no stale claims, got ${stale.jobs.size}")
            assertEquals(0, stale.total)
        }

        @Test
        fun `should respect the supplied stale threshold window`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000479")

            // Lease expired 2 minutes ago — stale only when threshold < 2 minutes
            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000404",
                publicationId = pubId,
                claimedByWorker = "worker-1",
                claimedAt = now.minus(Duration.ofMinutes(3)),
                leaseExpiresAt = now.minus(Duration.ofMinutes(2)),
            )

            val staleFor5Min = publicationJobRepository.findStaleClaims(now, Duration.ofMinutes(5), limit = 10)
            val staleFor1Min = publicationJobRepository.findStaleClaims(now, Duration.ofMinutes(1), limit = 10)

            assertTrue(
                staleFor5Min.jobs.isEmpty(),
                "5-min threshold should NOT flag a lease that expired only 2 minutes ago",
            )
            assertEquals(1, staleFor1Min.total)
            assertEquals(1, staleFor1Min.jobs.size)
        }

        @Test
        fun `should reset stale claimed jobs to pending and return the affected count`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubStale1 = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000460")
            val pubStale2 = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000461")

            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000425",
                publicationId = pubStale1,
                claimedByWorker = "worker-stuck",
                claimedAt = now.minus(Duration.ofMinutes(20)),
                leaseExpiresAt = now.minus(Duration.ofMinutes(15)),
            )
            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000426",
                publicationId = pubStale2,
                claimedByWorker = "worker-stuck",
                claimedAt = now.minus(Duration.ofMinutes(10)),
                leaseExpiresAt = now.minus(Duration.ofMinutes(8)),
            )

            val released = publicationJobRepository.releaseExpiredClaims(now, Duration.ofMinutes(5))

            assertEquals(2, released)

            // Both jobs must now be PENDING with cleared claim columns
            val reset1 = loadJobRow("00000000-0000-0000-0000-000000000425")
            assertEquals("PENDING", reset1["status"])
            assertNull(reset1["claimed_by_worker"])
            assertNull(reset1["claimed_at"])
            assertNull(reset1["lease_expires_at"])

            val reset2 = loadJobRow("00000000-0000-0000-0000-000000000426")
            assertEquals("PENDING", reset2["status"])
            assertNull(reset2["claimed_by_worker"])
        }

        @Test
        fun `reclaiming a stale job reuses the in-progress delivery attempt identity`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000476")
            val jobId = "00000000-0000-0000-0000-000000000429"
            val operationKey = "$jobId:1"

            insertPublicationJobWithClaim(
                id = jobId,
                publicationId = pubId,
                claimedByWorker = "worker-stuck",
                claimedAt = now.minus(Duration.ofMinutes(20)),
                leaseExpiresAt = now.minus(Duration.ofMinutes(15)),
            )
            deliveryAttemptRepository.record(
                DeliveryAttempt(
                    id = "00000000-0000-0000-0000-0000000003fe",
                    publicationId = pubId,
                    publicationJobId = jobId,
                    attemptNumber = 1,
                    outcome = DeliveryAttemptOutcome.IN_PROGRESS,
                    retryable = false,
                    attemptedAt = now.minus(Duration.ofMinutes(20)),
                    operationKey = operationKey,
                    phase = DeliveryAttemptPhase.PROVIDER_CREATE,
                ),
            )

            publicationJobRepository.releaseExpiredClaims(now, Duration.ofMinutes(5))
            val reclaimed = requireNotNull(
                publicationJobRepository.claimNextDue(
                    now = now,
                    workerId = "worker-reclaimer",
                    claimLease = Duration.ofMinutes(2),
                ),
            )

            assertEquals(1, reclaimed.attemptNumber)
            assertEquals(operationKey, reclaimed.operationKey)
        }

        @Test
        fun `should leave fresh claims alone`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-00000000045f")

            insertPublicationJobWithClaim(
                id = "00000000-0000-0000-0000-000000000412",
                publicationId = pubId,
                claimedByWorker = "worker-1",
                claimedAt = now.minus(Duration.ofMinutes(1)),
                leaseExpiresAt = now.plus(Duration.ofMinutes(1)),
            )

            val released = publicationJobRepository.releaseExpiredClaims(now, Duration.ofMinutes(5))

            assertEquals(0, released)
            val fresh = loadJobRow("00000000-0000-0000-0000-000000000412")
            assertEquals("CLAIMED", fresh["status"])
            assertEquals("worker-1", fresh["claimed_by_worker"])
        }

        @Test
        fun `should leave non-claimed statuses alone`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")
            val pubPending = insertPublication(PublicationStatus.QUEUED.name, "00000000-0000-0000-0000-000000000457")
            val pubRetry = insertPublication(PublicationStatus.SCHEDULED.name, "00000000-0000-0000-0000-000000000458")
            val pubCompleted =
                insertPublication(PublicationStatus.PUBLISHED.name, "00000000-0000-0000-0000-000000000456")

            insertPublicationJob(
                id = "00000000-0000-0000-0000-00000000041b",
                publicationId = pubPending,
                status = JobStatus.PENDING,
                dueAt = now.minus(Duration.ofMinutes(30)),
            )
            insertPublicationJob(
                id = "00000000-0000-0000-0000-000000000423",
                publicationId = pubRetry,
                status = JobStatus.RETRY_WAITING,
                dueAt = now.minus(Duration.ofMinutes(30)),
            )
            insertPublicationJob(
                id = "00000000-0000-0000-0000-00000000040a",
                publicationId = pubCompleted,
                status = JobStatus.COMPLETED,
                dueAt = now.minus(Duration.ofMinutes(30)),
            )

            val released = publicationJobRepository.releaseExpiredClaims(now, Duration.ofMinutes(5))

            assertEquals(0, released)
            assertEquals("PENDING", loadJobRow("00000000-0000-0000-0000-00000000041b")["status"])
            assertEquals("RETRY_WAITING", loadJobRow("00000000-0000-0000-0000-000000000423")["status"])
            assertEquals("COMPLETED", loadJobRow("00000000-0000-0000-0000-00000000040a")["status"])
        }

        @Test
        fun `should reject non-positive stale grace and limits`() = runTest {
            val now = Instant.parse("2026-06-01T12:00:00Z")

            assertFailsWith<IllegalArgumentException> {
                publicationJobRepository.findStaleClaims(now, Duration.ZERO, limit = 1)
            }
            assertFailsWith<IllegalArgumentException> {
                publicationJobRepository.findStaleClaims(now, Duration.ofMinutes(1), limit = 0)
            }
            assertFailsWith<IllegalArgumentException> {
                publicationJobRepository.releaseExpiredClaims(now, Duration.ZERO)
            }
        }

        @Test
        fun `should reject a terminal mutation from a stale claim version`() = runTest {
            val claimedAt = Instant.parse("2026-06-01T12:00:00Z")
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-00000000043b")
            insertPublicationJob(
                id = "00000000-0000-0000-0000-000000000407",
                publicationId = pubId,
                status = JobStatus.PENDING,
                dueAt = claimedAt,
            )

            val firstClaim = requireNotNull(
                publicationJobRepository.claimNextDue(
                    claimedAt,
                    "worker-1",
                    Duration.ofMinutes(2),
                ),
            )
            publicationJobRepository.releaseExpiredClaims(
                now = claimedAt.plus(Duration.ofMinutes(4)),
                staleGrace = Duration.ofMinutes(1),
            )
            val secondClaim = requireNotNull(
                publicationJobRepository.claimNextDue(
                    claimedAt.plus(Duration.ofMinutes(4)),
                    "worker-2",
                    Duration.ofMinutes(2),
                ),
            )

            assertTrue(secondClaim.claimVersion > firstClaim.claimVersion)
            assertEquals(
                false,
                publicationJobRepository.complete(
                    jobId = firstClaim.jobId,
                    claimVersion = firstClaim.claimVersion,
                    completedAt = claimedAt.plus(Duration.ofMinutes(4)),
                ),
            )
            assertEquals("CLAIMED", loadJobRow("00000000-0000-0000-0000-000000000407")["status"])
        }
    }

    // -------------------------------------------------------------------------
    // R2dbcDeliveryAttemptRepository
    // -------------------------------------------------------------------------

    @Nested
    inner class R2dbcDeliveryAttemptRepositoryTests {

        @Test
        fun `record stores attempt and returns it`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000445")
            val jobId = "00000000-0000-0000-0000-00000000040c"
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

            val attempt = DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003f5",
                publicationId = pubId,
                publicationJobId = jobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.SUCCEEDED,
                retryable = false,
                externalPublicationId = "linkedin-post-456",
                attemptedAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

            val result = deliveryAttemptRepository.record(attempt)

            assertEquals("00000000-0000-0000-0000-0000000003f5", result.id)
            assertEquals(DeliveryAttemptOutcome.SUCCEEDED, result.outcome)
        }

        @Test
        fun `record with null optional fields succeeds`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000446")
            val jobId = "00000000-0000-0000-0000-00000000040d"
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

            val attempt = DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003f6",
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

            assertEquals("00000000-0000-0000-0000-0000000003f6", result.id)
            assertEquals("Connection timeout", result.providerMessage)
            assertEquals("CONN_TIMEOUT", result.providerErrorCode)
        }

        @Test
        fun `record with all fields set succeeds`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000447")
            val jobId = "00000000-0000-0000-0000-00000000040e"
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
            )

            val attempt = DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003f7",
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

            assertEquals("00000000-0000-0000-0000-0000000003f7", result.id)
            assertEquals(3, result.attemptNumber)
            assertEquals("ext-id-3", result.externalPublicationId)
        }

        @Test
        fun `find and update roundtrip operation key phase and fenced outcome`() = runTest {
            val pubId = insertPublication(PublicationStatus.PROCESSING.name, "00000000-0000-0000-0000-000000000448")
            val jobId = "00000000-0000-0000-0000-00000000040f"
            insertPublicationJob(
                jobId,
                pubId,
                JobStatus.PENDING,
                dueAt = Instant.parse("2026-06-01T12:00:00Z"),
                claimVersion = 4,
            )

            val started = DeliveryAttempt(
                id = "00000000-0000-0000-0000-0000000003fd",
                publicationId = pubId,
                publicationJobId = jobId,
                attemptNumber = 1,
                outcome = DeliveryAttemptOutcome.IN_PROGRESS,
                retryable = false,
                attemptedAt = Instant.parse("2026-06-01T12:00:00Z"),
                createdAt = Instant.parse("2026-06-01T12:00:01Z"),
                operationKey = "$jobId:1",
                claimVersion = 4,
                phase = DeliveryAttemptPhase.PROVIDER_CREATE,
            )
            deliveryAttemptRepository.record(started)

            val loaded = requireNotNull(deliveryAttemptRepository.findByOperationKey("$jobId:1"))
            assertEquals(started.id, loaded.id)
            assertEquals(started.createdAt, loaded.createdAt)
            assertEquals(started.claimVersion, loaded.claimVersion)
            assertEquals(DeliveryAttemptPhase.PROVIDER_CREATE, loaded.phase)

            val finalized = loaded.copy(
                outcome = DeliveryAttemptOutcome.SUCCEEDED,
                externalPublicationId = "linkedin-post-roundtrip",
                attemptedAt = Instant.parse("2026-06-01T12:00:02Z"),
                phase = DeliveryAttemptPhase.FINALIZATION,
            )
            assertTrue(deliveryAttemptRepository.update(finalized))

            val persisted = requireNotNull(deliveryAttemptRepository.findByOperationKey("$jobId:1"))
            assertEquals(DeliveryAttemptOutcome.SUCCEEDED, persisted.outcome)
            assertEquals("linkedin-post-roundtrip", persisted.externalPublicationId)
            assertEquals(DeliveryAttemptPhase.FINALIZATION, persisted.phase)
            assertFalse(deliveryAttemptRepository.update(finalized.copy(claimVersion = 5)))
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private suspend fun seedWorkspaceAndPrincipal() {
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
                '00000000-0000-0000-0000-000000000493', '00000000-0000-0000-0000-00000000049d', 'LINKEDIN', 'linkedin-conn-1',
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
                '00000000-0000-0000-0000-000000000489', '00000000-0000-0000-0000-000000000493', '00000000-0000-0000-0000-00000000049d', 'LINKEDIN',
                'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertPublication(status: String, id: String = UUID.randomUUID().toString()): String =
        insertPublicationForWorkspace(
            workspaceId = "00000000-0000-0000-0000-00000000049d",
            principalId = "00000000-0000-0000-0000-000000000430",
            socialAccountId = "00000000-0000-0000-0000-000000000489",
            status = status,
            id = id,
        )

    private suspend fun insertPublicationForWorkspace(
        workspaceId: String,
        principalId: String,
        socialAccountId: String,
        status: String,
        id: String = UUID.randomUUID().toString(),
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
            .bind("id", UUID.fromString(id))
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("principalId", UUID.fromString(principalId))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
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
            .bind("id", UUID.fromString(publicationId))
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
            .bind("principalId", UUID.fromString(principalId))
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
            .bind("workspaceId", UUID.fromString(workspaceId))
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
            .bind("connectionId", UUID.fromString(connectionId))
            .bind("workspaceId", UUID.fromString(workspaceId))
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
            .bind("accountId", UUID.fromString(accountId))
            .bind("connectionId", UUID.fromString(connectionId))
            .bind("workspaceId", UUID.fromString(workspaceId))
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
                :id, '00000000-0000-0000-0000-00000000049d', 'UPLOADED', 'image/png', 'key.png', :status, '00000000-0000-0000-0000-000000000430'
            )
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(id))
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
        claimVersion: Long = 0,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publication_jobs (
                id, publication_id, workspace_id, status, due_at, priority_rank,
                attempt_count, max_attempts, created_at, claim_version
            ) VALUES (
                :id, :publicationId, '00000000-0000-0000-0000-00000000049d', :status, :dueAt,
                :priorityRank, 0, 3, :createdAt, :claimVersion
            )
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(id))
            .bind("publicationId", UUID.fromString(publicationId))
            .bind("status", status.name)
            .bind("dueAt", dueAt)
            .bind("priorityRank", priorityRank)
            .bind("createdAt", java.time.Instant.now())
            .bind("claimVersion", claimVersion)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertRecurringSchedule(id: String, templatePostId: String, status: RecurringScheduleStatus) {
        recurringScheduleRepository.create(
            RecurringSchedule(
                id = id,
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                createdBy = "00000000-0000-0000-0000-000000000430",
                templatePostId = templatePostId,
                recurrenceRule = RecurrenceRule(
                    frequency = RecurrenceFrequency.MONTHLY,
                    interval = 1,
                    dayOfMonth = 15,
                ),
                timezone = "UTC",
                nextScheduledAt = Instant.parse("2026-08-01T10:00:00Z"),
                status = status,
                createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-07-01T10:00:00Z"),
            ),
        )
    }

    private suspend fun insertPublicationJobWithClaim(
        id: String,
        publicationId: String,
        claimedByWorker: String,
        claimedAt: Instant,
        leaseExpiresAt: Instant,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publication_jobs (
                id, publication_id, workspace_id, status, due_at, priority_rank,
                attempt_count, max_attempts, claimed_by_worker, claimed_at,
                lease_expires_at, created_at
            ) VALUES (
                :id, :publicationId, '00000000-0000-0000-0000-00000000049d', 'CLAIMED', :dueAt,
                10, 1, 3, :claimedByWorker, :claimedAt,
                :leaseExpiresAt, :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(id))
            .bind("publicationId", UUID.fromString(publicationId))
            .bind("dueAt", claimedAt)
            .bind("claimedByWorker", claimedByWorker)
            .bind("claimedAt", claimedAt)
            .bind("leaseExpiresAt", leaseExpiresAt)
            .bind("createdAt", java.time.Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun loadJobRow(jobId: String): Map<String, Any?> {
        val row = databaseClient.sql(
            """
            SELECT status, claimed_by_worker, claimed_at, lease_expires_at, blocked_at
            FROM publication_jobs
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(jobId))
            .map { resultRow, _ ->
                mapOf(
                    "status" to resultRow.get("status", String::class.java),
                    "claimed_by_worker" to resultRow.get("claimed_by_worker", String::class.java),
                    "claimed_at" to resultRow.get("claimed_at", OffsetDateTime::class.java),
                    "lease_expires_at" to resultRow.get("lease_expires_at", OffsetDateTime::class.java),
                    "blocked_at" to resultRow.get("blocked_at", OffsetDateTime::class.java),
                )
            }
            .one()
            .awaitSingle()
        return row
    }

    private fun makeJob(id: String, publicationId: String, status: JobStatus): PublicationJob = PublicationJob(
        id = id,
        publicationId = publicationId,
        workspaceId = "00000000-0000-0000-0000-00000000049d",
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
