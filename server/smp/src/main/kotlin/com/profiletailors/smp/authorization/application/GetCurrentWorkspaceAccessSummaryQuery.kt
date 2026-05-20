package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.platform.application.Query
import com.profiletailors.smp.platform.application.QueryHandler
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipRoleResolver
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact

object GetCurrentWorkspaceAccessSummaryQuery : Query<WorkspaceAccessSummary> {
    const val CURRENT_WORKSPACE_ACCESS_ENTITLEMENT: String = "workspace.access.summary"
}

data class WorkspaceAccessSummary(
    val workspaceId: String,
    val principalId: String,
    val roles: List<String>,
    val permissions: List<String>,
)

class GetCurrentWorkspaceAccessSummaryHandler(
    private val principalContextProvider: com.profiletailors.smp.platform.application.PrincipalContextProvider,
    private val resourceContextProvider: com.profiletailors.smp.platform.application.ResourceContextProvider,
    private val workspaceMembershipResolver: WorkspaceMembershipResolver,
    private val workspaceMembershipRoleResolver: WorkspaceMembershipRoleResolver,
    private val workspaceAuthorizationService: WorkspaceAuthorizationDecider,
    private val auditHook: AuditHook,
) : QueryHandler<GetCurrentWorkspaceAccessSummaryQuery, WorkspaceAccessSummary> {

    override suspend fun handle(query: GetCurrentWorkspaceAccessSummaryQuery): WorkspaceAccessSummary {
        val principalContext = principalContextProvider.require()
        val resourceContext = resourceContextProvider.require()
        val requiredPermission = PermissionKey.of("workspace", "access", "read")
        val decision = workspaceAuthorizationService.decideDetailed(
            requiredPermission = requiredPermission,
            requiredEntitlementKey = GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT,
        )

        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = query::class.java.name,
                requestPath = "/api/authorization/workspace-access/current",
                permission = requiredPermission.value,
                principalId = principalContext.principalId,
                workspaceId = resourceContext.workspaceId,
                decision = decision.decision,
                reasonCode = decision.reasonCode,
                roleKeys = decision.roleKeys.toList(),
            ),
        )

        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(
                decision = decision,
                requiredPermission = requiredPermission,
                requiredEntitlementKey = GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT,
            )
        }

        val membership = workspaceMembershipResolver.resolve(principalContext, resourceContext)
            ?.takeIf { it.isActive() }
            ?: throw AuthorizationDeniedException("Active workspace membership is required.")
        val roles = workspaceMembershipRoleResolver.resolve(membership)

        return WorkspaceAccessSummary(
            workspaceId = requireNotNull(resourceContext.workspaceId),
            principalId = principalContext.principalId,
            roles = roles.map { it.key }.sorted(),
            permissions = roles.flatMap { role -> role.permissions }
                .map { permission -> permission.value }
                .distinct()
                .sorted(),
        )
    }
}

class AuthorizationDeniedException(
    message: String = "Access denied.",
) : IllegalStateException(message) {
    companion object {
        fun forDecision(
            decision: AuthorizationDecisionResult,
            requiredPermission: PermissionKey,
            requiredEntitlementKey: String? = null,
            targetResourceId: String? = null,
        ): AuthorizationDeniedException = when (decision.reasonCode) {
            com.profiletailors.smp.platform.application.AuthorizationReasonCode.MISSING_ENTITLEMENT ->
                AuthorizationDeniedException("Missing required entitlement ${requiredEntitlementKey ?: "unknown"}.")
            com.profiletailors.smp.platform.application.AuthorizationReasonCode.MISSING_MEMBERSHIP ->
                AuthorizationDeniedException("Active workspace membership is required.")
            com.profiletailors.smp.platform.application.AuthorizationReasonCode.SCOPE_REDUCED_TARGET ->
                AuthorizationDeniedException(
                    "Requested target ${targetResourceId ?: "unknown"} is outside the allowed scope.",
                )
            else ->
                AuthorizationDeniedException("Missing required permission ${requiredPermission.value}.")
        }
    }
}
