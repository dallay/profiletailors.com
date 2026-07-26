package com.profiletailors.smp.governance.application

interface GovernanceMutationAuditPort {
    suspend fun recordSuccess(
        action: String,
        targetType: String,
        targetId: String,
        actorPrincipalId: String,
        workspaceId: String,
        details: Map<String, Any?> = emptyMap(),
    )
}
