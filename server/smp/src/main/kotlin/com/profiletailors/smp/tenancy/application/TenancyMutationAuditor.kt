package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.common.domain.context.PrincipalContextProvider

internal class TenancyMutationAuditor(
    private val principalContextProvider: PrincipalContextProvider,
    private val auditHook: AuditHook,
) {
    suspend fun recordSuccess(
        action: String,
        targetType: String,
        targetId: String,
        workspaceId: String?,
        details: Map<String, String> = emptyMap(),
    ) {
        val actor = principalContextProvider.require()
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorPrincipalId = actor.principalId,
                workspaceId = workspaceId,
                outcome = MutationAuditOutcome.SUCCESS,
                details = details,
            ),
        )
    }

    suspend fun recordRejected(
        action: String,
        targetType: String,
        targetId: String,
        workspaceId: String?,
        reason: String,
        details: Map<String, String> = emptyMap(),
    ) {
        val actor = principalContextProvider.require()
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorPrincipalId = actor.principalId,
                workspaceId = workspaceId,
                outcome = MutationAuditOutcome.REJECTED,
                details = details + ("reason" to reason),
            ),
        )
    }
}
