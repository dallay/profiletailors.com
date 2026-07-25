package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.governance.application.GovernanceMutationAuditPort
import org.springframework.stereotype.Component

@Component
internal class AuditHookGovernanceMutationAuditPort(
    private val auditHook: AuditHook,
) : GovernanceMutationAuditPort {

    override suspend fun recordSuccess(
        action: String,
        targetType: String,
        targetId: String,
        actorPrincipalId: String,
        workspaceId: String,
        details: Map<String, Any?>,
    ) {
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorPrincipalId = actorPrincipalId,
                workspaceId = workspaceId,
                outcome = MutationAuditOutcome.SUCCESS,
                details = details,
            ),
        )
    }
}