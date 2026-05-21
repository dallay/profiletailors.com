package com.profiletailors.smp.integration.support

import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.audit.application.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact

class CapturingAuditHook : AuditHook {
    private val capturedFacts = mutableListOf<AuthorizationDecisionAuditFact>()
    private val capturedMutations = mutableListOf<MutationAuditFact>()

    val facts: List<AuthorizationDecisionAuditFact>
        get() = capturedFacts.toList()

    val mutations: List<MutationAuditFact>
        get() = capturedMutations.toList()

    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

    override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) {
        capturedFacts += fact
    }

    override suspend fun onMutation(fact: MutationAuditFact) {
        capturedMutations += fact
    }

    fun reset() {
        capturedFacts.clear()
        capturedMutations.clear()
    }

    fun addFact(fact: AuthorizationDecisionAuditFact) {
        capturedFacts += fact
    }
}
