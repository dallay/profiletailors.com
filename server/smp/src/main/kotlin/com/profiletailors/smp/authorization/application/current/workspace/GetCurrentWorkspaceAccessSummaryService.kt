package com.profiletailors.smp.authorization.application.current.workspace

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipRoleResolver
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContextProvider

private val REQUIRED_PERMISSION = PermissionKey.of("workspace", "access", "read")

@Service
internal class GetCurrentWorkspaceAccessSummaryService(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceMembershipResolver: WorkspaceMembershipResolver,
    private val workspaceMembershipRoleResolver: WorkspaceMembershipRoleResolver,
    private val workspaceAuthorizationService: WorkspaceAuthorizationDecider,
    private val auditHook: AuditHook,
    private val requestPathProvider: RequestPathProvider,
) {
    suspend fun execute(query: GetCurrentWorkspaceAccessSummaryQuery): WorkspaceAccessSummary {
        val principalContext = principalContextProvider.require()
        val resourceContext = resourceContextProvider.require()

        val decision = workspaceAuthorizationService.decideDetailed(
            requiredPermission = REQUIRED_PERMISSION,
            requiredEntitlementKey = GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT,
        )

        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = query::class.java.name,
                requestPath = requestPathProvider.require(),
                permission = REQUIRED_PERMISSION.value,
                principalId = principalContext.principalId,
                workspaceId = resourceContext.workspaceId,
                decision = decision.decision.name,
                reasonCode = decision.reasonCode.name,
                roleKeys = decision.roleKeys.toList(),
            ),
        )

        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(
                decision = decision,
                requiredPermission = REQUIRED_PERMISSION,
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
