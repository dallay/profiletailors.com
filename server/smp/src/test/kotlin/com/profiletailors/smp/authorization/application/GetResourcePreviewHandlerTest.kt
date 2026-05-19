package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GetResourcePreviewHandlerTest {

    companion object {
        private const val PRINCIPAL_ID = "principal-1"
        private const val WORKSPACE_ID = "workspace-1"
        private const val RESOURCE_ID = "resource-1"
        private const val PERMISSION_RESOURCE_READ = "workspace:resource:read"
        private const val TARGET_RESOURCE_TYPE = "RESOURCE"
        private const val OUTSIDE_SCOPE_RESOURCE_ID = "resource-9"
    }

    @Test
    fun `returns resource preview for authorized principal and emits allow audit fact`() = runTest {
        val auditHook = CapturingAuditHook()
        val handler = buildHandler(
            auditHook = auditHook,
            decisionResult = AuthorizationDecisionResult(
                decision = AuthorizationDecision.ALLOW,
                reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                roleKeys = setOf("member"),
            ),
            assertResourceOverride = true,
        )

        val preview = handler.handle(GetResourcePreviewQuery(RESOURCE_ID))

        assertEquals(
            ResourcePreview(
                workspaceId = WORKSPACE_ID,
                resourceId = RESOURCE_ID,
                principalId = PRINCIPAL_ID,
                previewAllowed = true,
            ),
            preview,
        )
        assertEquals(
            listOf(
                auditFact(
                    requestPath = "/api/authorization/resources/$RESOURCE_ID/preview",
                    decision = AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                ),
            ),
            auditHook.facts,
        )
    }

    @Test
    fun `throws scope-specific denial when scope excludes resource target`() = runTest {
        val auditHook = CapturingAuditHook()
        val handler = buildHandler(
            auditHook = auditHook,
            decisionResult = AuthorizationDecisionResult(
                decision = AuthorizationDecision.DENY,
                reasonCode = AuthorizationReasonCode.SCOPE_REDUCED_TARGET,
                roleKeys = setOf("member"),
            ),
        )

        val error = assertThrows(AuthorizationDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(GetResourcePreviewQuery(OUTSIDE_SCOPE_RESOURCE_ID))
            }
        }

        assertEquals("Requested target $OUTSIDE_SCOPE_RESOURCE_ID is outside the allowed scope.", error.message)
        assertEquals(
            listOf(
                auditFact(
                    requestPath = "/api/authorization/resources/$OUTSIDE_SCOPE_RESOURCE_ID/preview",
                    decision = AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.SCOPE_REDUCED_TARGET,
                ),
            ),
            auditHook.facts,
        )
    }

    private fun buildHandler(
        auditHook: CapturingAuditHook,
        decisionResult: AuthorizationDecisionResult,
        assertResourceOverride: Boolean = false,
    ): GetResourcePreviewHandler {
        val principalContext = principalContext()
        val resourceContext = workspaceContext()

        return GetResourcePreviewHandler(
            principalContextProvider = object : PrincipalContextProvider {
                override suspend fun current(): PrincipalContext = principalContext
            },
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = resourceContext
            },
            workspaceAuthorizationDecider = object : WorkspaceAuthorizationDecider {
                override suspend fun decide(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                    resourceContextOverride: ResourceContext?,
                ): AuthorizationDecision = decisionResult.decision

                override suspend fun decideDetailed(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                    resourceContextOverride: ResourceContext?,
                ): AuthorizationDecisionResult {
                    if (assertResourceOverride) {
                        assertEquals(RESOURCE_ID, resourceContextOverride?.targetResourceId)
                        assertEquals(TARGET_RESOURCE_TYPE, resourceContextOverride?.targetResourceType)
                    }
                    return decisionResult
                }
            },
            auditHook = auditHook,
        )
    }

    private fun principalContext(): PrincipalContext = PrincipalContext(
        principalId = PRINCIPAL_ID,
        principalType = PrincipalType.USER,
        subject = "subject-123",
    )

    private fun workspaceContext(): ResourceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = WORKSPACE_ID,
    )

    private fun auditFact(
        requestPath: String,
        decision: AuthorizationDecision,
        reasonCode: AuthorizationReasonCode,
    ): AuthorizationDecisionAuditFact = AuthorizationDecisionAuditFact(
        requestName = GetResourcePreviewQuery::class.java.name,
        requestPath = requestPath,
        permission = PERMISSION_RESOURCE_READ,
        principalId = PRINCIPAL_ID,
        workspaceId = WORKSPACE_ID,
        decision = decision,
        reasonCode = reasonCode,
        roleKeys = listOf("member"),
    )

    private class CapturingAuditHook : AuditHook {
        val facts = mutableListOf<AuthorizationDecisionAuditFact>()

        override suspend fun onRequestHandled(
            requestName: String,
            outcome: com.profiletailors.smp.platform.application.RequestOutcome,
        ) = Unit

        override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) {
            facts += fact
        }
    }
}
