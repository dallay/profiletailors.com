package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.tenancy.application.TenancyMutationAuditOutcome
import com.profiletailors.smp.tenancy.application.TenancyMutationAuditPort
import org.springframework.stereotype.Component

@Component
internal class AuditHookTenancyMutationAuditPort(
    private val auditHook: AuditHook,
) : TenancyMutationAuditPort {

    override suspend fun record(
        action: String,
        targetType: String,
        targetId: String,
        actorPrincipalId: String,
        workspaceId: String?,
        outcome: TenancyMutationAuditOutcome,
        details: Map<String, String>,
    ) {
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorPrincipalId = actorPrincipalId,
                workspaceId = workspaceId,
                outcome = outcome.toAuditOutcome(),
                details = details,
            ),
        )
    }

    private fun TenancyMutationAuditOutcome.toAuditOutcome(): MutationAuditOutcome = when (this) {
        TenancyMutationAuditOutcome.SUCCESS -> MutationAuditOutcome.SUCCESS
        TenancyMutationAuditOutcome.REJECTED -> MutationAuditOutcome.REJECTED
    }
}