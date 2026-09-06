package com.profiletailors.smp.audit.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
data class AuthorizationDecisionAuditFact(
    val requestName: String,
    val requestPath: String,
    val permission: String,
    val principalId: String,
    val workspaceId: String?,
    val decision: String,
    val reasonCode: String,
    val roleKeys: List<String> = emptyList(),
) {
    init {
        require(permission.isNotBlank()) { "permission must not be blank." }
        require(principalId.isNotBlank()) { "principalId must not be blank." }
    }
}

@ValueObject
data class MutationAuditFact(
    val action: String,
    val targetType: String,
    val targetId: String,
    val actorPrincipalId: String,
    val workspaceId: String?,
    val outcome: MutationAuditOutcome,
    val details: Map<String, String> = emptyMap(),
) {
    init {
        require(action.isNotBlank()) { "action must not be blank." }
        require(actorPrincipalId.isNotBlank()) { "actorPrincipalId must not be blank." }
    }
}

@ValueObject
enum class MutationAuditOutcome {
    SUCCESS,
    REJECTED,
}
