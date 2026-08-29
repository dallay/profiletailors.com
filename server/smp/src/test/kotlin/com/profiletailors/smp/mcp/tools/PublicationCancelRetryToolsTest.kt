package com.profiletailors.smp.mcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.application.IdempotencyGuard
import com.profiletailors.smp.mcp.application.IdempotencyRecordRepository
import com.profiletailors.smp.mcp.infrastructure.McpAuditEmitter
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.mcp.infrastructure.McpIdempotencyConflictException
import com.profiletailors.smp.mcp.infrastructure.McpToolInvocationAuditFact
import com.profiletailors.smp.mcp.infrastructure.McpToolInvocationOutcome
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.PublicationNotFoundException
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("fast")
class PublicationCancelRetryToolsTest {

    private class CapturingAuditEmitter : McpAuditEmitter() {
        val captured: MutableList<McpToolInvocationAuditFact> = mutableListOf()
        override fun emit(fact: McpToolInvocationAuditFact) {
            captured.add(fact)
        }
    }

    private val workspaceId = "ws-1"
    private val principalId = "user-1"
    private val grantedScopes = setOf("mcp:publications:write")

    private fun stubIdempotencyMiss(repository: IdempotencyRecordRepository) {
        coEvery { repository.find(any(), any(), any(), any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0
    }

    private fun newAdapter(
        mediator: Mediator,
        idempotencyRepository: IdempotencyRecordRepository,
        auditEmitter: McpAuditEmitter,
    ): PublicationTools = PublicationTools(
        mediator = mediator,
        errorMapper = McpErrorMapper(),
        idempotencyGuard = IdempotencyGuard(idempotencyRepository),
        auditEmitter = auditEmitter,
    )

    private fun successResult(id: String = "pub-X"): PublicationResult = PublicationResult(
        publicationId = id,
        workspaceId = workspaceId,
        socialAccountId = "sa-1",
        status = PublicationStatus.CANCELLED,
        scheduleMode = ScheduleMode.NOW,
        priority = false,
        title = "hello",
        bodyText = "world",
        assetIds = emptyList(),
        scheduledFor = Instant.parse("2026-01-01T00:00:00Z"),
        nextSlotAfter = null,
    )

    @Test
    fun `cancel_publication delegates to mediator with publicationId`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CancelPublicationCommand>()) } returns successResult("pub-cancel")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).cancelPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-cancel",
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isTrue()
        coVerify { mediator.send(match<CancelPublicationCommand> { it.publicationId == "pub-cancel" }) }
    }

    @Test
    fun `cancel_publication maps state conflict to publication_state_conflict`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CancelPublicationCommand>()) } throws
            com.profiletailors.smp.publishing.domain.PublicationCancellationNotAllowedException("pub-cancel")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).cancelPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-cancel",
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("publication_state_conflict")
    }

    @Test
    fun `cancel_publication maps not-found to publication_not_found`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CancelPublicationCommand>()) } throws PublicationNotFoundException("pub-cancel")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).cancelPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-cancel",
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("publication_not_found")
    }

    @Test
    fun `cancel_publication replays cached result with idempotency key`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CancelPublicationCommand>()) } returns successResult("pub-cancel")

        val adapter = newAdapter(mediator, idempotencyRepository, auditEmitter)
        val first = adapter.cancelPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-cancel",
            idempotencyKey = "cancel-key",
        )

        assertThat(first.isSuccess).isTrue()
        coVerify(exactly = 1) { mediator.send(any<CancelPublicationCommand>()) }

        val cachedJson = PublicationCancelRetryToolsTest.defaultObjectMapper()
            .writeValueAsString(successResult("pub-cancel"))
        val secondMediator: Mediator = mockk()
        val secondRepo: IdempotencyRecordRepository = mockk()
        coEvery { secondRepo.find(any(), any(), any(), any()) } returns cachedJson
        val second = newAdapter(secondMediator, secondRepo, CapturingAuditEmitter()).cancelPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-cancel",
            idempotencyKey = "cancel-key",
        )

        assertThat(second.isSuccess).isTrue()
        coVerify(exactly = 0) { secondMediator.send(any<CancelPublicationCommand>()) }
    }

    @Test
    fun `retry_publication delegates to mediator with overrides`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<RetryPublicationCommand>()) } returns successResult("pub-retry")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).retryPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-retry",
            scheduleMode = "SCHEDULED_AT",
            scheduledFor = "2026-12-31T00:00:00Z",
            nextSlotAfter = null,
            priority = true,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isTrue()
        coVerify {
            mediator.send(
                match<RetryPublicationCommand> {
                    it.publicationId == "pub-retry" &&
                        it.scheduleMode == ScheduleMode.SCHEDULED_AT &&
                        it.scheduledFor == Instant.parse("2026-12-31T00:00:00Z") &&
                        it.priority == true
                },
            )
        }
    }

    @Test
    fun `retry_publication maps not-retryable to publication_state_conflict`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<RetryPublicationCommand>()) } throws
            com.profiletailors.smp.publishing.domain.PublicationRetryNotAllowedException("pub-retry")

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).retryPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-retry",
            scheduleMode = null,
            scheduledFor = null,
            nextSlotAfter = null,
            priority = null,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("publication_state_conflict")
    }

    @Test
    fun `retry_publication without write scope returns insufficient_scope`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).retryPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = setOf("mcp:channels:read"),
            publicationId = "pub-retry",
            scheduleMode = null,
            scheduledFor = null,
            nextSlotAfter = null,
            priority = null,
            idempotencyKey = null,
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("insufficient_scope")
        coVerify(exactly = 0) { mediator.send(any<RetryPublicationCommand>()) }
    }

    @Test
    fun `cancel_publication emits SUCCESS audit with publicationId`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<CancelPublicationCommand>()) } returns successResult("pub-cancel")

        newAdapter(mediator, idempotencyRepository, auditEmitter).cancelPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-cancel",
            idempotencyKey = null,
        )

        val fact = auditEmitter.captured.single()
        assertThat(fact.toolName).isEqualTo("cancel_publication")
        assertThat(fact.outcome).isEqualTo(McpToolInvocationOutcome.SUCCESS)
        assertThat(fact.publicationId).isEqualTo("pub-cancel")
    }

    @Test
    fun `retry_publication maps idempotency_conflict to idempotency_conflict`() = runTest {
        val mediator: Mediator = mockk()
        val idempotencyRepository: IdempotencyRecordRepository = mockk()
        val auditEmitter = CapturingAuditEmitter()
        stubIdempotencyMiss(idempotencyRepository)
        coEvery { mediator.send(any<RetryPublicationCommand>()) } throws
            McpIdempotencyConflictException("retry_publication", workspaceId)

        val result = newAdapter(mediator, idempotencyRepository, auditEmitter).retryPublication(
            workspaceId = workspaceId,
            principalId = principalId,
            grantedScopes = grantedScopes,
            publicationId = "pub-retry",
            scheduleMode = null,
            scheduledFor = null,
            nextSlotAfter = null,
            priority = null,
            idempotencyKey = "retry-key",
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("idempotency_conflict")
    }

    companion object {
        private fun defaultObjectMapper(): ObjectMapper =
            com.profiletailors.smp.mcp.application.IdempotencyGuard.defaultObjectMapper()
    }
}
