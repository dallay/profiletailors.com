package com.profiletailors.smp.authorization.domain

data class AuthorizationDecisionResult(
    val decision: AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: Set<String> = emptySet(),
)
