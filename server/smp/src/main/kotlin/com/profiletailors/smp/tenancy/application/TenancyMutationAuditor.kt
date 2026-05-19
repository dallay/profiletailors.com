package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.MutationAuditFact
import com.profiletailors.smp.platform.application.MutationAuditOutcome
import com.profiletailors.smp.platform.application.PrincipalContextProvider

class TenancyMutationAuditor(
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
