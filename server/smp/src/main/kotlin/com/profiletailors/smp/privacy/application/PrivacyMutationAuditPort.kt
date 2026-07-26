package com.profiletailors.smp.privacy.application

enum class PrivacyMutationAuditOutcome {
    SUCCESS,
    REJECTED,
}

data class PrivacyMutationAuditFact(
    val action: String,
    val targetType: String,
    val targetId: String,
    val actorPrincipalId: String,
    val workspaceId: String,
    val outcome: PrivacyMutationAuditOutcome,
    val details: Map<String, String> = emptyMap(),
)

fun interface PrivacyMutationAuditPort {
    suspend fun onMutation(fact: PrivacyMutationAuditFact)
}
