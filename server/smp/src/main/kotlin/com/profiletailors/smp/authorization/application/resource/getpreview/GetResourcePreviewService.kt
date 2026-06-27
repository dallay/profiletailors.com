package com.profiletailors.smp.authorization.application.resource.getpreview

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider

private const val RESOURCE_TARGET_TYPE = "RESOURCE"
private val REQUIRED_PERMISSION = PermissionKey.of("workspace", "resource", "read")

@Service
internal class GetResourcePreviewService(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
    private val auditHook: AuditHook,
    private val requestPathProvider: RequestPathProvider,
) {
    suspend fun execute(query: GetResourcePreviewQuery): ResourcePreview {
        val principalContext = principalContextProvider.require()
        val baseResourceContext = resourceContextProvider.require()
        val targetAwareContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = baseResourceContext.workspaceId,
            resourceOwnerId = baseResourceContext.resourceOwnerId,
            targetResourceType = RESOURCE_TARGET_TYPE,
            targetResourceId = query.resourceId,
            scopeHints = baseResourceContext.scopeHints,
        )

        val decision = workspaceAuthorizationDecider.decideDetailed(
            requiredPermission = REQUIRED_PERMISSION,
            resourceContextOverride = targetAwareContext,
        )

        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = query::class.java.name,
                requestPath = requestPathProvider.require(),
                permission = REQUIRED_PERMISSION.value,
                principalId = principalContext.principalId,
                workspaceId = targetAwareContext.workspaceId,
                decision = decision.decision.name,
                reasonCode = decision.reasonCode.name,
                roleKeys = decision.roleKeys.toList(),
            ),
        )

        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(
                decision = decision,
                requiredPermission = REQUIRED_PERMISSION,
                requiredEntitlementKey = null,
                targetResourceId = query.resourceId,
            )
        }

        return ResourcePreview(
            workspaceId = requireNotNull(targetAwareContext.workspaceId),
            resourceId = query.resourceId,
            principalId = principalContext.principalId,
            previewAllowed = true,
        )
    }
}
