package com.profiletailors.smp.audit.domain

data class AuthorizationDecisionAuditFact(
    val requestName: String,
    val requestPath: String,
    val permission: String,
    val principalId: String,
    val workspaceId: String?,
    val decision: String,
    val reasonCode: String,
    val roleKeys: List<String> = emptyList(),
)

data class MutationAuditFact(
    val action: String,
    val targetType: String,
    val targetId: String,
    val actorPrincipalId: String,
    val workspaceId: String?,
    val outcome: MutationAuditOutcome,
    val details: Map<String, String> = emptyMap(),
)

enum class MutationAuditOutcome {
    SUCCESS,
    REJECTED,
}
