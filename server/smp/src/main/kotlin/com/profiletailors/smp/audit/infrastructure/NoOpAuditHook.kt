package com.profiletailors.smp.audit.infrastructure

import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact

class NoOpAuditHook : AuditHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

    override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit

    override suspend fun onMutation(fact: MutationAuditFact) = Unit
}
