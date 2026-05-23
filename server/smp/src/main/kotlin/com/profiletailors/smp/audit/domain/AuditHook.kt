package com.profiletailors.smp.audit.domain

import com.profiletailors.common.domain.observability.RequestOutcome

interface AuditHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)

    suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact)

    suspend fun onMutation(fact: MutationAuditFact)
}
