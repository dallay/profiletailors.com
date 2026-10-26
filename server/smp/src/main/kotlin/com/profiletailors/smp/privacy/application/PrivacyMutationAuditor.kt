package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome

@Service
class PrivacyMutationAuditor(
    private val principalContextProvider: PrincipalContextProvider,
    private val auditHook: AuditHook,
) {
    /**
     * Record a successful mutation audit event.
     *
     * @param action The event action (e.g., "dsar.submitted", "dsar.completed")
     * @param requestId The DSAR request identifier
     * @param details Additional event details (type, status transitions, etc.)
     */
    suspend fun recordSuccess(action: String, requestId: String, details: Map<String, String> = emptyMap()) {
        val actor = principalContextProvider.require()
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = TARGET_TYPE,
                targetId = requestId,
                actorPrincipalId = actor.principalId,
                workspaceId = null,
                outcome = MutationAuditOutcome.SUCCESS,
                details = details,
            ),
        )
    }

    /**
     * Record a rejected mutation audit event.
     *
     * @param action The event action (e.g., "dsar.submitted")
     * @param requestId The DSAR request identifier
     * @param reason The rejection reason
     * @param details Additional event details
     */
    suspend fun recordRejected(
        action: String,
        requestId: String,
        reason: String,
        details: Map<String, String> = emptyMap(),
    ) {
        val actor = principalContextProvider.require()
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = TARGET_TYPE,
                targetId = requestId,
                actorPrincipalId = actor.principalId,
                workspaceId = null,
                outcome = MutationAuditOutcome.REJECTED,
                details = details + ("reason" to reason),
            ),
        )
    }

    private companion object {
        /** Target type for all DSAR audit events. */
        const val TARGET_TYPE = "DATA_SUBJECT_REQUEST"
    }
}
