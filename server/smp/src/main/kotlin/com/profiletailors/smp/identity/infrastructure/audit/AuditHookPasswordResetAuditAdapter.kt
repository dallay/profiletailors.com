package com.profiletailors.smp.identity.infrastructure.audit

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.identity.application.PasswordResetAuditEvent
import com.profiletailors.smp.identity.application.PasswordResetAuditPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AuditHookPasswordResetAuditAdapter(private val auditHook: AuditHook) : PasswordResetAuditPort {
    /**
     * Records a completed password reset in the mutation audit stream.
     *
     * @param event The completed password reset event to audit.
     * @throws kotlinx.coroutines.CancellationException If the coroutine is cancelled while recording the audit.
     */
    override suspend fun recordCompleted(event: PasswordResetAuditEvent) {
        try {
            auditHook.onMutation(
                MutationAuditFact(
                    action = PASSWORD_RESET_COMPLETED,
                    targetType = IDENTITY_PRINCIPAL,
                    targetId = event.principalId,
                    actorPrincipalId = event.principalId,
                    workspaceId = null,
                    outcome = MutationAuditOutcome.SUCCESS,
                    details = mapOf(OCCURRED_AT to event.occurredAt.toString()),
                ),
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            logger.warn("Password reset completed audit emission failed")
        }
    }

    private companion object {
        const val PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED"
        const val IDENTITY_PRINCIPAL = "IDENTITY_PRINCIPAL"
        const val OCCURRED_AT = "occurredAt"
        val logger = LoggerFactory.getLogger(AuditHookPasswordResetAuditAdapter::class.java)
    }
}
