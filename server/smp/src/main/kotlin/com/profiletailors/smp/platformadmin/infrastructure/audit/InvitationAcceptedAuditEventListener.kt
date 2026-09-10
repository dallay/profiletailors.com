package com.profiletailors.smp.platformadmin.infrastructure.audit

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.domain.InvitationAccepted
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class InvitationAcceptedAuditEventListener(
    private val auditHook: AuditHook,
    private val telemetry: InvitationTelemetry = InvitationTelemetry.noop(),
) {
    @EventListener
    suspend fun onInvitationAccepted(event: InvitationAccepted) {
        telemetry.recordInvitationAccepted(event.target)
        auditHook.onMutation(
            MutationAuditFact(
                action = "INVITATION_ACCEPTED",
                targetType = "INVITATION",
                targetId = event.invitationId.toString(),
                actorPrincipalId = event.principalId,
                workspaceId = event.workspaceId,
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf(
                    "target" to event.target.name,
                    "outcome" to event.outcome.name,
                    "occurredAt" to event.occurredAt.toString(),
                ),
            ),
        )
    }
}
