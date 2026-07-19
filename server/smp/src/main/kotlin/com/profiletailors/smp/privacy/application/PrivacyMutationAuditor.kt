package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome

/**
 * Auditor for privacy DSAR (Data Subject Access Request) lifecycle events.
 *
 * Follows the [com.profiletailors.smp.tenancy.application.TenancyMutationAuditor]
 * pattern exactly. Records mutation audit events for all DSAR lifecycle
 * transitions: submitted, status_changed, completed, rejected, failed.
 *
 * ## Sentinel Workspace ID
 * Cross-workspace DSAR events use a sentinel `workspace_id` of `"__DSAR__"`
 * to distinguish them from workspace-scoped events in audit queries.
 * This avoids per-workspace audit events for global operations like
 * deletion which span all workspaces.
 *
 * ## Target Type
 * All events target `"DATA_SUBJECT_REQUEST"` with the [targetId] set to
 * the request's unique identifier.
 *
 * @since 1.0.0
 */
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
                workspaceId = SENTINEL_WORKSPACE_ID,
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
                workspaceId = SENTINEL_WORKSPACE_ID,
                outcome = MutationAuditOutcome.REJECTED,
                details = details + ("reason" to reason),
            ),
        )
    }

    private companion object {
        /** Sentinel workspace ID for cross-workspace DSAR events. */
        const val SENTINEL_WORKSPACE_ID = "__DSAR__"

        /** Target type for all DSAR audit events. */
        const val TARGET_TYPE = "DATA_SUBJECT_REQUEST"
    }
}
