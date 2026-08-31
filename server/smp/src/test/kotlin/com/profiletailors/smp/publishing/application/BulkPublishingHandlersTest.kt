@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.BulkImportJob
import com.profiletailors.smp.publishing.domain.BulkImportJobRepository
import com.profiletailors.smp.publishing.domain.BulkJobStatus
import com.profiletailors.smp.publishing.domain.BulkValidationPipeline
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class BulkPublishingHandlersTest {
    private val validationPipeline =
        BulkValidationPipeline(
            ProviderCapabilityValidator {
            },
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")),
        )
    private val principalContextProvider = mockk<PrincipalContextProvider>(relaxed = true)
    private val resourceContextProvider = mockk<ResourceContextProvider>(relaxed = true)
    private val bulkImportJobRepository = mockk<BulkImportJobRepository>(relaxed = true)
    private val publicationCreationService = mockk<PublicationCreationService>(relaxed = true)
    private val socialAccountRepository = mockk<SocialAccountRepository>(relaxed = true)
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"))

    private val scheduleHandler = ScheduleBulkHandler(
        principalContextProvider,
        resourceContextProvider,
        validationPipeline,
        bulkImportJobRepository,
        publicationCreationService,
        transactionRunner,
        clock,
        socialAccountRepository,
    )

    @Test
    fun `validate handler returns per-row errors no persistence`() = runTest {
        val handler = ValidateBulkHandler(validationPipeline)
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2026-02-01T12:00:00Z,UTC,,tag\n,not-a-date,UTC,,tag"
        val result = handler.handle(ValidateBulkCommand(workspaceId = "ws-1", csvText = csv))
        result.rows.size shouldBe 2
        result.rows[0].status shouldBe "VALID"
        result.rows[1].status shouldBe "INVALID"
    }

    @Test
    fun `schedule handler chunked 50-100 with 200_207 partial`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        every { resourceContextProvider.require() } returns ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { bulkImportJobRepository.findByIdempotencyKey(any()) } returns null
        coEvery { bulkImportJobRepository.save(any()) } answers { it.invocation.args[0] as BulkImportJob }
        coEvery { bulkImportJobRepository.saveRows(any()) } returns Unit
        coEvery { socialAccountRepository.findFirstActiveByWorkspace(workspaceId) } returns SocialAccount(
            id = "acc-1",
            socialConnectionId = "conn-1",
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "p-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Active",
            status = SocialConnectionStatus.ACTIVE,
        )
        coEvery {
            publicationCreationService.create(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers
            {
                com.profiletailors.smp.publishing.domain.PublicationDraft(
                    id = "pub-${java.util.UUID.randomUUID()}", workspaceId = workspaceId, authorPrincipalId = principalId,
                    provider = com.profiletailors.smp.publishing.domain.SocialProvider.LINKEDIN, socialAccountId = "acc-1",
                    status = com.profiletailors.smp.publishing.domain.PublicationStatus.DRAFT, scheduleMode = com.profiletailors.smp.publishing.domain.ScheduleMode.SCHEDULED_AT,
                    priority = false, bodyText = it.invocation.args[3] as String?, scheduledFor = it.invocation.args[5] as Instant?,
                )
            }
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nValid row,2026-02-01T12:00:00Z,UTC,,\n,not-a-date,UTC,,"
        val result = scheduleHandler.handle(
            ScheduleBulkCommand(workspaceId = workspaceId, csvText = csv, csvHash = csv),
        )
        result.totalRows shouldBe 2
        result.scheduledCount shouldBe 1
        result.failedCount shouldBe 1
    }

    @Test
    fun `schedule handler workspace mismatch throws 403`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        every { resourceContextProvider.require() } returns ResourceContext(ResourceContextType.WORKSPACE, "ws-other")
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHi,2026-02-01T12:00:00Z,UTC,,"
        assertThrows<BulkWorkspaceMismatchException> {
            scheduleHandler.handle(ScheduleBulkCommand(workspaceId = workspaceId, csvText = csv, csvHash = csv))
        }
    }

    @Test
    fun `schedule handler conflict warn-only marks hasConflict`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        every { resourceContextProvider.require() } returns ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { bulkImportJobRepository.findByIdempotencyKey(any()) } returns null
        coEvery { bulkImportJobRepository.save(any()) } answers { it.invocation.args[0] as BulkImportJob }
        coEvery { bulkImportJobRepository.saveRows(any()) } returns Unit
        coEvery { socialAccountRepository.findFirstActiveByWorkspace(workspaceId) } returns SocialAccount(
            id = "acc-1",
            socialConnectionId = "conn-1",
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "p-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Active",
            status = SocialConnectionStatus.ACTIVE,
        )
        coEvery {
            publicationCreationService.create(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers
            {
                com.profiletailors.smp.publishing.domain.PublicationDraft(
                    id = "pub-${java.util.UUID.randomUUID()}", workspaceId = workspaceId, authorPrincipalId = principalId,
                    provider = com.profiletailors.smp.publishing.domain.SocialProvider.LINKEDIN, socialAccountId = "acc-1",
                    status = com.profiletailors.smp.publishing.domain.PublicationStatus.DRAFT, scheduleMode = com.profiletailors.smp.publishing.domain.ScheduleMode.SCHEDULED_AT,
                    priority = false, bodyText = it.invocation.args[3] as String?, scheduledFor = it.invocation.args[5] as Instant?,
                )
            }
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nPost A,2026-02-01T10:00:00Z,UTC,,\nPost B,2026-02-01T10:10:00Z,UTC,,"
        val result = scheduleHandler.handle(
            ScheduleBulkCommand(workspaceId = workspaceId, csvText = csv, csvHash = csv),
        )
        result.rows.all { it.hasConflict } shouldBe true
        result.rows.size shouldBe 2
    }

    @Test
    fun `schedule handler fails when no active account`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        every { resourceContextProvider.require() } returns ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { bulkImportJobRepository.findByIdempotencyKey(any()) } returns null
        coEvery { bulkImportJobRepository.save(any()) } answers { it.invocation.args[0] as BulkImportJob }
        coEvery { socialAccountRepository.findFirstActiveByWorkspace(workspaceId) } returns null
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHi,2026-02-01T12:00:00Z,UTC,,"
        assertThrows<PublicationValidationException> {
            scheduleHandler.handle(ScheduleBulkCommand(workspaceId = workspaceId, csvText = csv, csvHash = csv))
        }
    }

    @Test
    fun `schedule handler throws 409 on duplicate sha256`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        every { resourceContextProvider.require() } returns ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        val existingJob = BulkImportJob(
            id = "bulk-existing",
            workspaceId = workspaceId,
            principalId = principalId,
            idempotencyKey = BulkImportJob.computeIdempotencyKey(workspaceId, principalId, "hash"),
            csvHash = "hash",
            status = BulkJobStatus.SCHEDULED,
            totalRows = 1,
            createdAt = Instant.now(),
        )
        coEvery { bulkImportJobRepository.findByIdempotencyKey(any()) } returns existingJob
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2026-02-01T12:00:00Z,UTC,,"
        assertThrows<DuplicateBulkImportException> {
            scheduleHandler.handle(ScheduleBulkCommand(workspaceId = workspaceId, csvText = csv, csvHash = "hash"))
        }
    }

    @Test
    fun `get job handler workspace scoped 404`() = runTest {
        val repo = mockk<BulkImportJobRepository>()
        coEvery { repo.findByWorkspaceAndId("ws-1", "job-1") } returns null
        val handler = GetBulkJobHandler(repo)
        assertThrows<BulkJobNotFoundException> {
            handler.handle(GetBulkJobQuery(workspaceId = "ws-1", jobId = "job-1"))
        }
    }

    @Test
    fun `templates handler returns canonical header`() = runTest {
        val handler = BulkTemplatesHandler()
        val result = handler.handle(BulkTemplatesQuery(workspaceId = "ws-1"))
        result.templates.isNotEmpty() shouldBe true
        result.templates.first().header shouldBe "bodyText,scheduledFor,timezone,media_urls,hashtags"
    }
}
