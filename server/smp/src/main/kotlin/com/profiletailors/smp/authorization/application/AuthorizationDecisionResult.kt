package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.platform.application.AuthorizationReasonCode

data class AuthorizationDecisionResult(
    val decision: AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: Set<String> = emptySet(),
)
