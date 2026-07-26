package com.profiletailors.smp.privacy.infrastructure.audit

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditFact
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditOutcome
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditPort
import org.springframework.stereotype.Component

@Component
class AuditHookPrivacyMutationAuditPort(private val auditHook: AuditHook) : PrivacyMutationAuditPort {
    override suspend fun onMutation(fact: PrivacyMutationAuditFact) {
        auditHook.onMutation(
            MutationAuditFact(
                action = fact.action,
                targetType = fact.targetType,
                targetId = fact.targetId,
                actorPrincipalId = fact.actorPrincipalId,
                workspaceId = fact.workspaceId,
                outcome = when (fact.outcome) {
                    PrivacyMutationAuditOutcome.SUCCESS -> MutationAuditOutcome.SUCCESS
                    PrivacyMutationAuditOutcome.REJECTED -> MutationAuditOutcome.REJECTED
                },
                details = fact.details,
            ),
        )
    }
}
