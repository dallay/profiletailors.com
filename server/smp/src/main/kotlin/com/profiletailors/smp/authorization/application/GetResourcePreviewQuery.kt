package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.Query
import com.profiletailors.smp.platform.application.QueryHandler
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType

private const val RESOURCE_PREVIEW_PATH = "/api/authorization/resources"
private const val RESOURCE_TARGET_TYPE = "RESOURCE"

data class GetResourcePreviewQuery(
    val resourceId: String,
) : Query<ResourcePreview>

data class ResourcePreview(
    val workspaceId: String,
    val resourceId: String,
    val principalId: String,
    val previewAllowed: Boolean,
)

class GetResourcePreviewHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
    private val auditHook: AuditHook,
) : QueryHandler<GetResourcePreviewQuery, ResourcePreview> {

    override suspend fun handle(query: GetResourcePreviewQuery): ResourcePreview {
        val principalContext = principalContextProvider.require()
        val baseResourceContext = resourceContextProvider.require()
        val requiredPermission = PermissionKey.of("workspace", "resource", "read")
        val targetAwareContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = baseResourceContext.workspaceId,
            resourceOwnerId = baseResourceContext.resourceOwnerId,
            targetResourceType = RESOURCE_TARGET_TYPE,
            targetResourceId = query.resourceId,
            scopeHints = baseResourceContext.scopeHints,
        )

        val decision = workspaceAuthorizationDecider.decideDetailed(
            requiredPermission = requiredPermission,
            resourceContextOverride = targetAwareContext,
        )

        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = query::class.java.name,
                requestPath = "$RESOURCE_PREVIEW_PATH/${query.resourceId}/preview",
                permission = requiredPermission.value,
                principalId = principalContext.principalId,
                workspaceId = targetAwareContext.workspaceId,
                decision = decision.decision,
                reasonCode = decision.reasonCode,
                roleKeys = decision.roleKeys.toList(),
            ),
        )

        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(
                decision = decision,
                requiredPermission = requiredPermission,
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
