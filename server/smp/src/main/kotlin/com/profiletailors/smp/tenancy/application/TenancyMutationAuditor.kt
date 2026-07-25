package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider

@Service
internal class TenancyMutationAuditor(
    private val principalContextProvider: PrincipalContextProvider,
    private val tenancyMutationAuditPort: TenancyMutationAuditPort,
) {
    suspend fun recordSuccess(
        action: String,
        targetType: String,
        targetId: String,
        workspaceId: String?,
        details: Map<String, String> = emptyMap(),
    ) {
        val actor = principalContextProvider.require()
        tenancyMutationAuditPort.record(
            action = action,
            targetType = targetType,
            targetId = targetId,
            actorPrincipalId = actor.principalId,
            workspaceId = workspaceId,
            outcome = TenancyMutationAuditOutcome.SUCCESS,
            details = details,
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
        tenancyMutationAuditPort.record(
            action = action,
            targetType = targetType,
            targetId = targetId,
            actorPrincipalId = actor.principalId,
            workspaceId = workspaceId,
            outcome = TenancyMutationAuditOutcome.REJECTED,
            details = details + ("reason" to reason),
        )
    }
}
