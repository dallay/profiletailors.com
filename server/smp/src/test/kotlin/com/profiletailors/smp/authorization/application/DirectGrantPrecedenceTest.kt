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

class DirectGrantPrecedenceTest {

    @Test
    fun `expired direct grant is ignored`() = runTest {
        val principalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "user-123",
        )
        val resourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
        val requiredPermission = PermissionKey.of("workspace", "access", "read")
        val service = WorkspaceAuthorizationService(
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
                ): WorkspaceMembership = WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                )
            },
            workspaceMembershipRoleResolver = object : WorkspaceMembershipRoleResolver {
                override suspend fun resolve(membership: WorkspaceMembership): Set<Role> = emptySet()
            },
            directGrantResolver = object : DirectGrantResolver {
                override suspend fun resolve(
                    principalContext: PrincipalContext,
                    resourceContext: ResourceContext,
                ): Set<DirectGrant> = setOf(
                    DirectGrant(
                        permission = requiredPermission,
                        effect = GrantEffect.ALLOW,
                        resourceContext = resourceContext,
                        expiresAt = Instant.parse("2026-05-15T09:00:00Z"),
                    ),
                )
            },
            clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC),
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.MISSING_PERMISSION, detailedDecision.reasonCode)
    }

    @Test
    fun `explicit deny overrides role based allow`() = runTest {
        val principalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "user-123",
        )
        val resourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
        val requiredPermission = PermissionKey.of("workspace", "access", "read")
        val service = WorkspaceAuthorizationService(
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
                ): WorkspaceMembership = WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "principal-1",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                    roleKeys = setOf("member"),
                )
            },
            workspaceMembershipRoleResolver = object : WorkspaceMembershipRoleResolver {
                override suspend fun resolve(membership: WorkspaceMembership): Set<Role> = setOf(
                    Role(
                        key = "member",
                        category = RoleCategory.WORKSPACE,
                        permissions = setOf(requiredPermission),
                    ),
                )
            },
            directGrantResolver = object : DirectGrantResolver {
                override suspend fun resolve(
                    principalContext: PrincipalContext,
                    resourceContext: ResourceContext,
                ): Set<DirectGrant> = setOf(
                    DirectGrant(
                        permission = requiredPermission,
                        effect = GrantEffect.DENY,
                        resourceContext = resourceContext,
                    ),
                )
            },
        )

        val detailedDecision = service.decideDetailed(requiredPermission)

        assertEquals(AuthorizationDecision.DENY, detailedDecision.decision)
        assertEquals(AuthorizationReasonCode.DIRECT_DENY, detailedDecision.reasonCode)
    }
}
