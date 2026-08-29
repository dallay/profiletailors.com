package com.profiletailors.smp.mcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.application.IdempotencyGuard
import com.profiletailors.smp.mcp.application.IdempotencyRecordRepository
import com.profiletailors.smp.mcp.infrastructure.McpAuditEmitter
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.mcp.infrastructure.McpIdempotencyConflictException
import com.profiletailors.smp.mcp.infrastructure.McpToolInvocationAuditFact
import com.profiletailors.smp.mcp.infrastructure.McpToolInvocationOutcome
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.DeletePublicationCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.PublicationNotFoundException
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class PublicationWriteToolsTest {

    private class CapturingAuditEmitter : McpAuditEmitter(objectMapper = jacksonObjectMapper()) {
        val captured: MutableList<McpToolInvocationAuditFact> = mutableListOf()
        override fun emit(fact: McpToolInvocationAuditFact) {
            captured.add(fact)
        }
    }

    private val objectMapper: ObjectMapper =
        com.profiletailors.smp.mcp.application.IdempotencyGuard.defaultObjectMapper()

    private val workspaceId = "ws-1"
    private val principalId = "user-1"
    private val grantedScopes = setOf("mcp:publications:write")

    private fun newAdapter(
        mediator: Mediator,
        idempotencyRepository: IdempotencyRecordRepository,
        auditEmitter: McpAuditEmitter,
    ): PublicationTools = PublicationTools(
        mediator = mediator,
        errorMapper = McpErrorMapper(),
        idempotencyGuard = IdempotencyGuard(idempotencyRepository, objectMapper),
        auditEmitter = auditEmitter,
    )

    private fun stubIdempotencyMiss(repository: IdempotencyRecordRepository) {
        coEvery { repository.find(any(), any(), any(), any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0
    }

    private fun successResult(id: String = "pub-X"): PublicationResult = PublicationResult(
        publicationId = id,
        workspaceId = workspaceId,
        socialAccountId = "sa-1",
        status = PublicationStatus.QUEUED,
        scheduleMode = ScheduleMode.NOW,
        priority = false,
        title = "hello",
        bodyText = "world",
        assetIds = emptyList(),
        scheduledFor = Instant.parse("2026-01-01T00:00:00Z"),
        nextSlotAfter = null,
    )

    @Test
    fun `create_publication delegates to mediator and returns success envelope`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CreatePublicationCommand>()) } returns successResult()

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isTrue()
        coVerify {
            mediator.send(
                match<CreatePublicationCommand> {
                    it.socialAccountId == "sa-1" &&
                        it.title == "hello" &&
                        it.bodyText == "world" &&
                        it.scheduleMode == ScheduleMode.NOW
                },
            )
        }
    }

    @Test
    fun `create_publication rejects unknown schedule mode with publication_validation_failed`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = null,
            assetIds = emptyList(),
            scheduleMode = "WHEN_EVER",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("publication_validation_failed")
    }

    @Test
    fun `create_publication maps domain not-found errors to publication_not_found`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CreatePublicationCommand>()) } throws PublicationNotFoundException("pub-X")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("publication_not_found")
    }

    @Test
    fun `create_publication with idempotency key persists the response and replays cached result`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        coEvery { idempotencyRepository.find(any(), any(), any(), any()) } returns null
        coEvery { idempotencyRepository.save(any()) } returnsArgument 0
        coEvery { mediator.send(any<CreatePublicationCommand>()) } returns successResult("pub-cached")

        val adapter = newAdapter(mediator, idempotencyRepository, auditEmitter)

        val first = adapter.createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = "agent-retry-1",
        )

        assertThat(first.isSuccess).withFailMessage {
            "first call failed: code=${first.error?.code} message=${first.error?.message}"
        }.isTrue()
        coVerify(exactly = 1) { mediator.send(any<CreatePublicationCommand>()) }
        coVerify(exactly = 1) { idempotencyRepository.save(any()) }

        val cachedJson = objectMapper.writeValueAsString(successResult("pub-cached"))
        val secondMediator: Mediator = mockk()
        val secondRepo: IdempotencyRecordRepository = mockk()
        coEvery { secondRepo.find(any(), any(), any(), any()) } returns cachedJson
        val adapter2 = newAdapter(secondMediator, secondRepo, CapturingAuditEmitter())

        val second = adapter2.createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = "agent-retry-1",
        )

        assertThat(second.isSuccess).isTrue()
        coVerify(exactly = 0) { secondMediator.send(any<CreatePublicationCommand>()) }
    }

    @Test
    fun `edit_publication delegates to mediator with publicationId and parsed fields`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<EditPublicationCommand>()) } returns successResult("pub-edit")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).editPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-edit",
            title = "new",
            bodyText = "edited",
            assetIds = null,
            scheduleMode = "SCHEDULED_AT",
            scheduledFor = "2026-12-31T00:00:00Z",
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isTrue()
        coVerify {
            mediator.send(
                match<EditPublicationCommand> {
                    it.publicationId == "pub-edit" &&
                        it.title == "new" &&
                        it.scheduleMode == ScheduleMode.SCHEDULED_AT &&
                        it.scheduledFor == Instant.parse("2026-12-31T00:00:00Z")
                },
            )
        }
    }

    @Test
    fun `delete_publication delegates to mediator with publicationId`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<DeletePublicationCommand>()) } returns successResult("pub-del")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).deletePublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-del",
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isTrue()
        coVerify { mediator.send(match<DeletePublicationCommand> { it.publicationId == "pub-del" }) }
    }

    @Test
    fun `write tool without required scope returns insufficient_scope`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = setOf("mcp:channels:read"),
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("insufficient_scope")
    }

    @Test
    fun `idempotency conflict maps to idempotency_conflict`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CreatePublicationCommand>()) } throws
            McpIdempotencyConflictException("create_publication", workspaceId)

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = "k1",
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("idempotency_conflict")
    }

    @Test
    fun `successful create emits a SUCCESS audit fact carrying publicationId`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CreatePublicationCommand>()) } returns successResult("pub-audit")

        newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        val facts = auditEmitter.captured
        assertThat(facts).hasSize(1)
        val fact = facts.single()
        assertThat(fact.toolName).isEqualTo("create_publication")
        assertThat(fact.outcome).isEqualTo(McpToolInvocationOutcome.SUCCESS)
        assertThat(fact.publicationId).isEqualTo("pub-audit")
        assertThat(fact.workspaceId).isEqualTo(workspaceId)
    }

    @Test
    fun `failed create emits an ERROR audit fact without publicationId`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CreatePublicationCommand>()) } throws PublicationNotFoundException("pub-X")

        newAdapter(mediator, idempotencyRepository, auditEmitter).createPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            socialAccountId = "sa-1",
            title = "hello",
            bodyText = "world",
            assetIds = emptyList(),
            scheduleMode = "NOW",
            scheduledFor = null,
            nextSlotAfter = null,
            priority = false,
            idempotencyKey = null,
        )

        val fact = auditEmitter.captured.single()
        assertThat(fact.outcome).isEqualTo(McpToolInvocationOutcome.ERROR)
        assertThat(fact.publicationId).isNull()
    }
}
