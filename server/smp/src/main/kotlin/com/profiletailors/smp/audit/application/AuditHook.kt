package com.profiletailors.smp.audit.application

import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.common.domain.observability.RequestOutcome

interface AuditHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)

    suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact)

    suspend fun onMutation(fact: MutationAuditFact)
}
