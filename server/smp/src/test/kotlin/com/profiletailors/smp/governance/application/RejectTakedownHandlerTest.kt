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
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import com.profiletailors.smp.governance.domain.event.TakedownRejected
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class RejectTakedownHandlerTest {

    private val repository: TakedownReportRepository = mockk()
    private val resourceContextProvider: ResourceContextProvider = mockk()
    private val principalContextProvider: com.profiletailors.common.domain.context.PrincipalContextProvider = mockk()
    private val authorizationDecider: WorkspaceAuthorizationDecider = mockk()
    private val auditHook: AuditHook = mockk()
    private val eventPublisher: EventPublisher<DomainEvent> = mockk()

    private val authorizationService = GovernanceAuthorizationService(authorizationDecider)
    private val handler = RejectTakedownHandler(
        repository = repository,
        resourceContextProvider = resourceContextProvider,
        principalContextProvider = principalContextProvider,
        authorizationService = authorizationService,
        auditHook = auditHook,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `rejects pending report when authorized`() = runTest {
        val report = TakedownReport(
            reportId = "report-001",
            workspaceId = "ws-001",
            assetId = "asset-001",
            reportedById = "user-001",
            reason = "Copyright infringement",
            status = TakedownReportStatus.REPORTED,
            reporterEmail = "reporter@example.com",
            createdAt = Instant.parse("2026-07-21T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
        )

        coEvery { authorizationDecider.decideDetailed(any()) } returns AuthorizationDecisionResult(
            decision = AuthorizationDecision.ALLOW,
            reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
        )
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(
                principalId = "reviewer-001",
                principalType = PrincipalType.USER,
                subject = "reviewer@example.com",
            )
        coEvery { repository.findById("ws-001", "report-001") } returns report
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { auditHook.onMutation(any()) } returns Unit
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

        val result = handler.handle(RejectTakedownCommand("report-001", "Insufficient evidence"))

        result.status shouldBe TakedownReportStatus.DISMISSED
        result.reviewedById shouldBe "reviewer-001"
        result.rejectionReason shouldBe "Insufficient evidence"

        coVerify {
            auditHook.onMutation(
                match { fact: MutationAuditFact ->
                    fact.action == "MEDIA_TAKEDOWN_REJECTED" &&
                        fact.targetType == "takedown_report" &&
                        fact.outcome == MutationAuditOutcome.SUCCESS
                },
            )
            eventPublisher.publish(
                match<TakedownRejected> { event ->
                    event.workspaceId == "ws-001" &&
                        event.assetId == "asset-001" &&
                        event.reporterEmail == "reporter@example.com" &&
                        event.rejectionReason == "Insufficient evidence"
                },
            )
        }
    }

    @Test
    fun `rejects without reason`() = runTest {
        val report = TakedownReport(
            reportId = "report-001",
            workspaceId = "ws-001",
            assetId = "asset-001",
            reportedById = "user-001",
            reason = "Copyright infringement",
            status = TakedownReportStatus.REPORTED,
            reporterEmail = "reporter@example.com",
            createdAt = Instant.parse("2026-07-21T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
        )

        coEvery { authorizationDecider.decideDetailed(any()) } returns AuthorizationDecisionResult(
            decision = AuthorizationDecision.ALLOW,
            reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
        )
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(
                principalId = "reviewer-001",
                principalType = PrincipalType.USER,
                subject = "reviewer@example.com",
            )
        coEvery { repository.findById("ws-001", "report-001") } returns report
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { auditHook.onMutation(any()) } returns Unit
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

        val result = handler.handle(RejectTakedownCommand("report-001"))

        result.status shouldBe TakedownReportStatus.DISMISSED
        result.rejectionReason shouldBe null
    }

    @Test
    fun `throws AuthorizationDeniedException when not authorized`() = runTest {
        coEvery { authorizationDecider.decideDetailed(any()) } returns AuthorizationDecisionResult(
            decision = AuthorizationDecision.DENY,
            reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
        )

        shouldThrow<AuthorizationDeniedException> {
            handler.handle(RejectTakedownCommand("report-001"))
        }
    }
}
