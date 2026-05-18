package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.Role
import com.profiletailors.smp.authorization.domain.RoleCategory
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GetCurrentWorkspaceAccessSummaryHandlerTest {

    @Test
    fun `returns current workspace access summary for authorized member and emits allow audit fact`() = runTest {
        val principalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "subject-123",
        )
        val resourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
        val membership = WorkspaceMembership(
            id = "membership-1",
            workspaceId = "workspace-1",
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val roles = setOf(
            Role(
                key = "member",
                category = RoleCategory.WORKSPACE,
                permissions = setOf(PermissionKey.of("workspace", "access", "read")),
            ),
        )
        val auditHook = CapturingAuditHook()
        val handler = GetCurrentWorkspaceAccessSummaryHandler(
            principalContextProvider = object : PrincipalContextProvider {
                override suspend fun current(): PrincipalContext = principalContext
            },
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = resourceContext
            },
            workspaceMembershipResolver = object : WorkspaceMembershipResolver {
                override suspend fun resolve(
                    principalContext: PrincipalContext,
                    resourceContext: ResourceContext,
                ): WorkspaceMembership = membership
            },
            workspaceMembershipRoleResolver = object : WorkspaceMembershipRoleResolver {
                override suspend fun resolve(membership: WorkspaceMembership): Set<Role> = roles
            },
            workspaceAuthorizationService = object : WorkspaceAuthorizationDecider {
                override suspend fun decide(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                ): AuthorizationDecision = AuthorizationDecision.ALLOW

                override suspend fun decideDetailed(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                ): AuthorizationDecisionResult =
                    AuthorizationDecisionResult(
                        decision = AuthorizationDecision.ALLOW,
                        reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                        roleKeys = setOf("member"),
                    )
            },
            auditHook = auditHook,
        )

        val summary = handler.handle(GetCurrentWorkspaceAccessSummaryQuery)

        assertEquals("workspace-1", summary.workspaceId)
        assertEquals("principal-1", summary.principalId)
        assertEquals(listOf("member"), summary.roles)
        assertEquals(listOf("workspace:access:read"), summary.permissions)
        assertEquals(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GetCurrentWorkspaceAccessSummaryQuery::class.java.name,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
            auditHook.facts,
        )
    }

    @Test
    fun `throws when authorization is denied and emits deny audit fact`() = runTest {
        val principalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "subject-123",
        )
        val resourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
        val auditHook = CapturingAuditHook()
        val handler = GetCurrentWorkspaceAccessSummaryHandler(
            principalContextProvider = object : PrincipalContextProvider {
                override suspend fun current(): PrincipalContext = principalContext
            },
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = resourceContext
            },
            workspaceMembershipResolver = object : WorkspaceMembershipResolver {
                override suspend fun resolve(
                    principalContext: PrincipalContext,
                    resourceContext: ResourceContext,
                ): WorkspaceMembership? = null
            },
            workspaceMembershipRoleResolver = object : WorkspaceMembershipRoleResolver {
                override suspend fun resolve(membership: WorkspaceMembership): Set<Role> = emptySet()
            },
            workspaceAuthorizationService = object : WorkspaceAuthorizationDecider {
                override suspend fun decide(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                ): AuthorizationDecision = AuthorizationDecision.DENY

                override suspend fun decideDetailed(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                ): AuthorizationDecisionResult =
                    AuthorizationDecisionResult(
                        decision = AuthorizationDecision.DENY,
                        reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                        roleKeys = emptySet(),
                    )
            },
            auditHook = auditHook,
        )

        val error = assertThrows(AuthorizationDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(GetCurrentWorkspaceAccessSummaryQuery)
            }
        }

        assertEquals("Missing required permission workspace:access:read.", error.message)
        assertEquals(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GetCurrentWorkspaceAccessSummaryQuery::class.java.name,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                    roleKeys = emptyList(),
                ),
            ),
            auditHook.facts,
        )
    }

    @Test
    fun `emits missing entitlement audit fact distinct from missing permission`() = runTest {
        val principalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "subject-123",
        )
        val resourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
        val auditHook = CapturingAuditHook()
        val handler = GetCurrentWorkspaceAccessSummaryHandler(
            principalContextProvider = object : PrincipalContextProvider {
                override suspend fun current(): PrincipalContext = principalContext
            },
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = resourceContext
            },
            workspaceMembershipResolver = object : WorkspaceMembershipResolver {
                override suspend fun resolve(
                    principalContext: PrincipalContext,
                    resourceContext: ResourceContext,
                ): WorkspaceMembership? = null
            },
            workspaceMembershipRoleResolver = object : WorkspaceMembershipRoleResolver {
                override suspend fun resolve(membership: WorkspaceMembership): Set<Role> = emptySet()
            },
            workspaceAuthorizationService = object : WorkspaceAuthorizationDecider {
                override suspend fun decide(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                ): AuthorizationDecision = AuthorizationDecision.DENY

                override suspend fun decideDetailed(
                    requiredPermission: PermissionKey,
                    requiredEntitlementKey: String?,
                ): AuthorizationDecisionResult =
                    AuthorizationDecisionResult(
                        decision = AuthorizationDecision.DENY,
                        reasonCode = AuthorizationReasonCode.MISSING_ENTITLEMENT,
                        roleKeys = emptySet(),
                    )
            },
            auditHook = auditHook,
        )

        val error = assertThrows(AuthorizationDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(GetCurrentWorkspaceAccessSummaryQuery)
            }
        }

        assertEquals(
            "Missing required entitlement ${GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT}.",
            error.message,
        )
        assertEquals(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GetCurrentWorkspaceAccessSummaryQuery::class.java.name,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_ENTITLEMENT,
                    roleKeys = emptyList(),
                ),
            ),
            auditHook.facts,
        )
    }

    private class CapturingAuditHook : AuditHook {
        val facts = mutableListOf<AuthorizationDecisionAuditFact>()

        override suspend fun onRequestHandled(requestName: String, outcome: com.profiletailors.smp.platform.application.RequestOutcome) = Unit

        override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) {
            facts += fact
        }
    }
}
