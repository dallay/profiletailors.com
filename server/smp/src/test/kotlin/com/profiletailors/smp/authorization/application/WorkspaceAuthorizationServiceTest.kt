package com.profiletailors.smp.authorization.application

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.DirectGrant
import com.profiletailors.smp.authorization.domain.GrantEffect
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.Role
import com.profiletailors.smp.authorization.domain.RoleCategory
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WorkspaceAuthorizationServiceTest {

    private val principalContext = PrincipalContext(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "user-123",
    )
    private val resourceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )
    private val requiredPermission = PermissionKey.of("workspace", "access", "read")

    @Test
    fun `allows when active membership roles include required explicit permission`() = runTest {
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(
                WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                ),
            ),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(
                setOf(
                    Role(
                        key = "member",
                        category = RoleCategory.WORKSPACE,
                        permissions = setOf(requiredPermission),
                    ),
                ),
            ),
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
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(null),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(emptySet()),
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
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(
                WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member", "analyst"),
                ),
            ),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(
                setOf(
                    Role(
                        key = "member",
                        category = RoleCategory.WORKSPACE,
                        permissions = setOf(PermissionKey.of("workspace", "members", "manage")),
                    ),
                    Role(
                        key = "analyst",
                        category = RoleCategory.WORKSPACE,
                        permissions = setOf(requiredPermission),
                    ),
                ),
            ),
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
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(
                WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                ),
            ),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(
                setOf(
                    Role(
                        key = "member",
                        category = RoleCategory.WORKSPACE,
                        permissions = setOf(PermissionKey.of("workspace", "access", "write")),
                    ),
                ),
            ),
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
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(
                WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                ),
            ),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(emptySet()),
            directGrantResolver = FixedDirectGrantResolver(
                setOf(
                    DirectGrant(
                        permission = requiredPermission,
                        effect = GrantEffect.ALLOW,
                        resourceContext = resourceContext,
                    ),
                ),
            ),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.ALLOW, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.DIRECT_ALLOW, detailedDecision.reasonCode)
        assertEquals(emptySet<String>(), detailedDecision.roleKeys)
    }

    @Test
    fun `expired direct allow is ignored and falls back to missing permission`() = runTest {
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(
                WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                ),
            ),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(emptySet()),
            directGrantResolver = FixedDirectGrantResolver(
                setOf(
                    DirectGrant(
                        permission = requiredPermission,
                        effect = GrantEffect.ALLOW,
                        resourceContext = resourceContext,
                        expiresAt = Instant.parse("2026-05-15T09:00:00Z"),
                    ),
                ),
            ),
            clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_PERMISSION, detailedDecision.reasonCode)
        assertEquals(emptySet<String>(), detailedDecision.roleKeys)
    }

    @Test
    fun `direct deny reason is surfaced when explicit deny grant applies`() = runTest {
        val service = WorkspaceAuthorizationService(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(resourceContext),
            workspaceMembershipResolver = FixedWorkspaceMembershipResolver(
                WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                ),
            ),
            workspaceMembershipRoleResolver = FixedWorkspaceMembershipRoleResolver(
                setOf(
                    Role(
                        key = "member",
                        category = RoleCategory.WORKSPACE,
                        permissions = setOf(requiredPermission),
                    ),
                ),
            ),
            directGrantResolver = FixedDirectGrantResolver(
                setOf(
                    DirectGrant(
                        permission = requiredPermission,
                        effect = GrantEffect.DENY,
                        resourceContext = resourceContext,
                    ),
                ),
            ),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.DIRECT_DENY, detailedDecision.reasonCode)
        assertEquals(setOf("member"), detailedDecision.roleKeys)
    }

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
        override suspend fun resolve(membership: WorkspaceMembership): Set<Role> = roles
    }

    private class FixedDirectGrantResolver(
        private val grants: Set<DirectGrant>,
    ) : DirectGrantResolver {
        override suspend fun resolve(
            principalContext: PrincipalContext,
            resourceContext: ResourceContext,
        ): Set<DirectGrant> = grants
    }
}
