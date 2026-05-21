package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationScope
import com.profiletailors.smp.authorization.domain.DirectGrant
import com.profiletailors.smp.authorization.domain.DirectGrantResolver
import com.profiletailors.smp.authorization.domain.Entitlement
import com.profiletailors.smp.authorization.domain.EntitlementResolver
import com.profiletailors.smp.authorization.domain.GrantEffect
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.Role
import com.profiletailors.smp.authorization.domain.RoleCategory
import com.profiletailors.smp.authorization.domain.ScopeResolver
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipRoleResolver
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WorkspaceAuthorizationServiceTest {

    companion object {
        private const val PRINCIPAL_ID = "principal-1"
        private const val WORKSPACE_ID = "workspace-1"
        private const val RESOURCE_ID = "resource-1"
    }

    private val principalContext = PrincipalContext(
        principalId = PRINCIPAL_ID,
        principalType = PrincipalType.USER,
        subject = "user-123",
    )
    private val resourceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = WORKSPACE_ID,
    )
    private val targetAwareResourceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = WORKSPACE_ID,
        targetResourceType = "RESOURCE",
        targetResourceId = RESOURCE_ID,
    )
    private val requiredPermission = PermissionKey.of("workspace", "access", "read")
    private val resourcePreviewPermission = PermissionKey.of("workspace", "resource", "read")
    private val currentWorkspaceAccessEntitlement = Entitlement(
        key = GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT,
        enabled = true,
    )

    @Test
    fun `allows when active membership roles include required explicit permission`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", requiredPermission)),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
        )

        val decision = service.decide(requiredPermission)
        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.ALLOW, decision)
        assertEquals(AuthorizationDecision.ALLOW, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.ROLE_PERMISSION, detailedDecision.reasonCode)
        assertEquals(setOf("member"), detailedDecision.roleKeys)
    }

    @Test
    fun `denies by default when active membership is missing`() = runTest {
        val service = buildService(
            membership = null,
            roles = emptySet(),
        )

        val decision = service.decide(requiredPermission)
        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, decision)
        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_MEMBERSHIP, detailedDecision.reasonCode)
        assertEquals(emptySet<String>(), detailedDecision.roleKeys)
    }

    @Test
    fun `allows when required permission is composed across multiple workspace roles`() = runTest {
        val service = buildService(
            membership = activeMembership("member", "analyst"),
            roles = setOf(
                workspaceRole("member", PermissionKey.of("workspace", "members", "manage")),
                workspaceRole("analyst", requiredPermission),
            ),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
        )

        val decision = service.decide(requiredPermission)
        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.ALLOW, decision)
        assertEquals(AuthorizationDecision.ALLOW, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.ROLE_PERMISSION, detailedDecision.reasonCode)
        assertEquals(setOf("analyst", "member"), detailedDecision.roleKeys)
    }

    @Test
    fun `denies when roles do not contain exact required permission`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", PermissionKey.of("workspace", "access", "write"))),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
        )

        val decision = service.decide(requiredPermission)
        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, decision)
        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_PERMISSION, detailedDecision.reasonCode)
        assertEquals(setOf("member"), detailedDecision.roleKeys)
    }

    @Test
    fun `direct allow reason is surfaced when explicit allow grant applies`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = emptySet(),
            directGrants = setOf(directGrant(permission = requiredPermission, effect = GrantEffect.ALLOW)),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.ALLOW, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.DIRECT_ALLOW, detailedDecision.reasonCode)
        assertEquals(emptySet<String>(), detailedDecision.roleKeys)
    }

    @Test
    fun `expired direct allow is ignored and falls back to missing permission`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = emptySet(),
            directGrants = setOf(
                directGrant(
                    permission = requiredPermission,
                    effect = GrantEffect.ALLOW,
                    expiresAt = Instant.parse("2026-05-15T09:00:00Z"),
                ),
            ),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
            clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_PERMISSION, detailedDecision.reasonCode)
        assertEquals(emptySet<String>(), detailedDecision.roleKeys)
    }

    @Test
    fun `direct deny reason is surfaced when explicit deny grant applies`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", requiredPermission)),
            directGrants = setOf(directGrant(permission = requiredPermission, effect = GrantEffect.DENY)),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.DIRECT_DENY, detailedDecision.reasonCode)
        assertEquals(setOf("member"), detailedDecision.roleKeys)
    }

    @Test
    fun `allows entitled and authorized principal on current workspace slice`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", requiredPermission)),
            entitlements = setOf(currentWorkspaceAccessEntitlement),
        )

        val detailedDecision = service.decideDetailed(
            requiredPermission = requiredPermission,
            requiredEntitlementKey = GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT,
        )

        assertEquals(AuthorizationDecision.ALLOW, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.ROLE_PERMISSION, detailedDecision.reasonCode)
    }

    @Test
    fun `denies authorized principal when workspace entitlement is missing`() = runTest {
        val service = buildService(
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", requiredPermission)),
            entitlements = emptySet(),
        )

        val detailedDecision = service.decideDetailed(
            requiredPermission = requiredPermission,
            requiredEntitlementKey = GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT,
        )

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_ENTITLEMENT, detailedDecision.reasonCode)
    }

    @Test
    fun `allows when base permission exists and target scope matches requested resource`() = runTest {
        val service = buildService(
            currentResourceContext = targetAwareResourceContext,
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", resourcePreviewPermission)),
            scopes = setOf(targetScope(RESOURCE_ID)),
        )

        val detailedDecision = service.decideDetailed(requiredPermission = resourcePreviewPermission)

        assertEquals(AuthorizationDecision.ALLOW, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.ROLE_PERMISSION, detailedDecision.reasonCode)
    }

    @Test
    fun `denies with scope-specific reason when base permission exists but target scope excludes requested resource`() = runTest {
        val service = buildService(
            currentResourceContext = targetAwareResourceContext,
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", resourcePreviewPermission)),
            scopes = setOf(targetScope("resource-2")),
        )

        val detailedDecision = service.decideDetailed(requiredPermission = resourcePreviewPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.SCOPE_REDUCED_TARGET, detailedDecision.reasonCode)
    }

    @Test
    fun `scope cannot manufacture access when base permission is missing`() = runTest {
        val service = buildService(
            currentResourceContext = targetAwareResourceContext,
            membership = activeMembership(),
            roles = setOf(workspaceRole("member", requiredPermission)),
            scopes = setOf(targetScope(RESOURCE_ID)),
        )

        val detailedDecision = service.decideDetailed(requiredPermission = resourcePreviewPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_PERMISSION, detailedDecision.reasonCode)
    }

    private fun buildService(
        currentResourceContext: ResourceContext = resourceContext,
        membership: WorkspaceMembership? = activeMembership(),
        roles: Set<Role> = emptySet(),
        directGrants: Set<DirectGrant> = emptySet(),
        scopes: Set<AuthorizationScope> = emptySet(),
        entitlements: Set<Entitlement> = emptySet(),
        clock: Clock = Clock.systemUTC(),
    ): WorkspaceAuthorizationService = WorkspaceAuthorizationService(
        principalContextProvider = FixedPrincipalContextProvider(principalContext),
        resourceContextProvider = FixedResourceContextProvider(currentResourceContext),
        workspaceMembershipResolver = FixedWorkspaceMembershipResolver(membership),
        workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(roles),
        directGrantResolver = FixedDirectGrantResolver(directGrants),
        scopeResolver = FixedScopeResolver(scopes),
        entitlementResolver = FixedEntitlementResolver(entitlements),
        clock = clock,
    )

    private fun activeMembership(vararg roleKeys: String): WorkspaceMembership = WorkspaceMembership(
        workspaceId = WORKSPACE_ID,
        principalId = PRINCIPAL_ID,
        principalType = PrincipalType.USER,
        status = WorkspaceMembershipStatus.ACTIVE,
        roleKeys = if (roleKeys.isEmpty()) setOf("member") else roleKeys.toSet(),
    )

    private fun workspaceRole(key: String, vararg permissions: PermissionKey): Role = Role(
        key = key,
        category = RoleCategory.WORKSPACE,
        permissions = permissions.toSet(),
    )

    private fun directGrant(
        permission: PermissionKey,
        effect: GrantEffect,
        resourceContext: ResourceContext = this.resourceContext,
        expiresAt: Instant? = null,
    ): DirectGrant = DirectGrant(
        permission = permission,
        effect = effect,
        resourceContext = resourceContext,
        expiresAt = expiresAt,
    )

    private fun targetScope(allowedResourceId: String): AuthorizationScope = AuthorizationScope(
        permission = resourcePreviewPermission,
        resourceContextType = ResourceContextType.WORKSPACE,
        targetResourceType = "RESOURCE",
        allowedTargetResourceIds = setOf(allowedResourceId),
    )

    private class FixedPrincipalContextProvider(
        private val principalContext: PrincipalContext,
    ) : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(
        private val resourceContext: ResourceContext,
    ) : ResourceContextProvider {
        override fun current(): ResourceContext = resourceContext
    }

    private class FixedWorkspaceMembershipResolver(
        private val membership: WorkspaceMembership?,
    ) : WorkspaceMembershipResolver {
        override suspend fun resolve(
            principalContext: PrincipalContext,
            resourceContext: ResourceContext,
        ): WorkspaceMembership? = membership
    }

    private class FixedWorkspaceMembershipRoleResolver(
        private val roles: Set<Role>,
    ) : WorkspaceMembershipRoleResolver {
        override suspend fun resolve(membership: com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot): Set<Role> = roles
    }

    private class FixedDirectGrantResolver(
        private val grants: Set<DirectGrant>,
    ) : DirectGrantResolver {
        override suspend fun resolve(
            principalContext: PrincipalContext,
            resourceContext: ResourceContext,
        ): Set<DirectGrant> = grants
    }

    private class FixedScopeResolver(
        private val scopes: Set<AuthorizationScope>,
    ) : ScopeResolver {
        override suspend fun resolve(
            principalContext: PrincipalContext,
            resourceContext: ResourceContext,
        ): Set<AuthorizationScope> = scopes
    }

    private class FixedEntitlementResolver(
        private val entitlements: Set<Entitlement>,
    ) : EntitlementResolver {
        override suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement> = entitlements
    }
}
