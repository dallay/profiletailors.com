package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import com.profiletailors.smp.governance.domain.event.TakedownReported
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class ReportTakedownHandlerTest {

    private val repository: TakedownReportRepository = mockk()
    private val resourceContextProvider: ResourceContextProvider = mockk()
    private val principalContextProvider: com.profiletailors.common.domain.context.PrincipalContextProvider = mockk()
    private val authorizationDecider: WorkspaceAuthorizationDecider = mockk()
    private val auditHook: AuditHook = mockk()
    private val eventPublisher: EventPublisher<DomainEvent> = mockk()

    private val authorizationService = GovernanceAuthorizationService(authorizationDecider)
    private val handler = ReportTakedownHandler(
        repository = repository,
        resourceContextProvider = resourceContextProvider,
        principalContextProvider = principalContextProvider,
        authorizationService = authorizationService,
        auditHook = auditHook,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `creates takedown report when authorized`() = runTest {
        coEvery { authorizationDecider.decideDetailed(any()) } returns AuthorizationDecisionResult(
            decision = AuthorizationDecision.ALLOW,
            reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
        )
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(
                principalId = "user-001",
                principalType = PrincipalType.USER,
                subject = "reporter@example.com",
            )
        coEvery { repository.findExisting("ws-001", "asset-001", "user-001") } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { auditHook.onMutation(any()) } returns Unit
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

        val command = ReportTakedownCommand(
            assetId = "asset-001",
            reason = "Copyright infringement",
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.com/original",
        )

        val result = handler.handle(command)

        result.status shouldBe TakedownReportStatus.REPORTED
        result.workspaceId shouldBe "ws-001"
        result.assetId shouldBe "asset-001"
        result.reportedById shouldBe "user-001"
        result.reason shouldBe "Copyright infringement"
        result.reporterEmail shouldBe "reporter@example.com"
        result.mediaReferenceUrl shouldBe "https://example.com/original"

        coVerify {
            auditHook.onMutation(
                match { fact: MutationAuditFact ->
                    fact.action == "MEDIA_TAKEDOWN_REPORTED" &&
                        fact.targetType == "takedown_report" &&
                        fact.outcome == MutationAuditOutcome.SUCCESS
                },
            )
            eventPublisher.publish(
                match<TakedownReported> { event ->
                    event.workspaceId == "ws-001" &&
                        event.assetId == "asset-001" &&
                        event.reporterEmail == "reporter@example.com" &&
                        event.reason == "Copyright infringement"
                },
            )
        }
    }

    @Test
    fun `throws AuthorizationDeniedException when not authorized`() = runTest {
        coEvery { authorizationDecider.decideDetailed(any()) } returns AuthorizationDecisionResult(
            decision = AuthorizationDecision.DENY,
            reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
        )

        val command = ReportTakedownCommand(
            assetId = "asset-001",
            reason = "Copyright infringement",
            reporterEmail = "reporter@example.com",
        )

        shouldThrow<AuthorizationDeniedException> { handler.handle(command) }
    }
}
