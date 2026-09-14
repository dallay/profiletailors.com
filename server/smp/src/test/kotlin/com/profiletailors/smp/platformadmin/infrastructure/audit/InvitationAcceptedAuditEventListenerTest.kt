package com.profiletailors.smp.platformadmin.infrastructure.audit

import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.platformadmin.domain.InvitationAccepted
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InvitationAcceptedAuditEventListenerTest {

    @Test
    fun `maps accepted event to a redacted mutation audit fact`() = runTest {
        val auditHook = CapturingAuditHook()
        val listener = InvitationAcceptedAuditEventListener(auditHook)
        val occurredAt = Instant.parse("2026-08-15T12:00:00Z")
        val event = InvitationAccepted(
            invitationId = UUID.fromString("00000000-0000-0000-0000-000000000567"),
            principalId = "principal-567",
            workspaceId = "workspace-567",
            target = InvitationTarget.EXISTING_WORKSPACE,
            occurredAt = occurredAt,
        )

        listener.onInvitationAccepted(event)

        val fact = auditHook.mutationFacts.single()
        fact.action shouldBe "INVITATION_ACCEPTED"
        fact.targetType shouldBe "INVITATION"
        fact.targetId shouldBe event.invitationId.toString()
        fact.actorPrincipalId shouldBe event.principalId
        fact.workspaceId shouldBe event.workspaceId
        fact.outcome.name shouldBe "SUCCESS"
        fact.details shouldContainExactly mapOf(
            "target" to InvitationTarget.EXISTING_WORKSPACE.name,
            "outcome" to "ACCEPTED",
            "occurredAt" to occurredAt.toString(),
        )
        fact.toString().contains("raw-token") shouldBe false
        fact.toString().contains("invitee@example.com") shouldBe false
    }

    private class CapturingAuditHook : AuditHook {
        val mutationFacts = mutableListOf<MutationAuditFact>()

        override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

        override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit

        override suspend fun onMutation(fact: MutationAuditFact) {
            mutationFacts += fact
        }
    }
}
