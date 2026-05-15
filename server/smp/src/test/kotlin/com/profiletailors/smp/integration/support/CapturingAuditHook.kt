package com.profiletailors.smp.integration.support

import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.RequestOutcome

class CapturingAuditHook : AuditHook {
    private val capturedFacts = mutableListOf<AuthorizationDecisionAuditFact>()

    val facts: List<AuthorizationDecisionAuditFact>
        get() = capturedFacts.toList()

    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

    override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) {
        capturedFacts += fact
    }

    fun reset() {
        capturedFacts.clear()
    }
}
