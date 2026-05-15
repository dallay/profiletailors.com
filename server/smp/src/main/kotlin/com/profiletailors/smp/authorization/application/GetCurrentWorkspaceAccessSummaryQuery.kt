package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.Query
import com.profiletailors.smp.platform.application.QueryHandler

object GetCurrentWorkspaceAccessSummaryQuery : Query<WorkspaceAccessSummary>

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
        val decision = workspaceAuthorizationService.decideDetailed(requiredPermission)

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
            throw AuthorizationDeniedException("Missing required permission ${requiredPermission.value}.")
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
) : IllegalStateException(message)
