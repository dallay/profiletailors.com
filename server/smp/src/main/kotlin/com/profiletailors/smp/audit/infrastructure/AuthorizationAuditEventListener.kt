package com.profiletailors.smp.audit.infrastructure

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationDecisionHandledEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class AuthorizationAuditEventListener(private val auditHook: AuditHook) {

    private val logger = LoggerFactory.getLogger(AuthorizationAuditEventListener::class.java)

    @Suppress("TooGenericExceptionCaught")
    @EventListener
    suspend fun onAuthorizationDecision(event: AuthorizationDecisionHandledEvent) {
        try {
            auditHook.onAuthorizationDecision(
                AuthorizationDecisionAuditFact(
                    requestName = event.requestName,
                    requestPath = event.requestPath,
                    permission = event.permission,
                    principalId = event.principalId,
                    workspaceId = event.workspaceId,
                    decision = event.decision,
                    reasonCode = event.reasonCode,
                    roleKeys = event.roleKeys,
                ),
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to record audit for authorization decision. requestName={} principalId={} error={}",
                event.requestName,
                event.principalId,
                e.message,
                e,
            )
        }
    }
}
