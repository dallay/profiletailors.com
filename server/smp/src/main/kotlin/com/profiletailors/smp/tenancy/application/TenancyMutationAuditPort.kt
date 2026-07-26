package com.profiletailors.smp.tenancy.application

interface TenancyMutationAuditPort {
    suspend fun record(
        action: String,
        targetType: String,
        targetId: String,
        actorPrincipalId: String,
        workspaceId: String?,
        outcome: TenancyMutationAuditOutcome,
        details: Map<String, String> = emptyMap(),
    )
}

enum class TenancyMutationAuditOutcome {
    SUCCESS,
    REJECTED,
}
