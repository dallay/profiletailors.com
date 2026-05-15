package com.profiletailors.smp.tenancy.domain

import com.profiletailors.smp.identity.domain.PrincipalType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceDomainModelsTest {

    @Test
    fun `workspace is operational only while active`() {
        assertTrue(Workspace(id = "workspace-1", name = "Profile Tailors", status = WorkspaceStatus.ACTIVE).isOperational())
        assertFalse(Workspace(id = "workspace-1", name = "Profile Tailors", status = WorkspaceStatus.SUSPENDED).isOperational())
    }

    @Test
    fun `workspace membership supports multiple roles and active status`() {
        val membership = WorkspaceMembership(
            workspaceId = "workspace-1",
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
            roleKeys = setOf("workspace:access:read", "workspace:members:manage"),
        )

        assertTrue(membership.isActive())
        assertTrue(membership.roleKeys.contains("workspace:access:read"))
        assertTrue(membership.roleKeys.contains("workspace:members:manage"))
    }

    @Test
    fun `workspace ownership remains independent from membership roles`() {
        val ownership = WorkspaceOwnership(
            workspaceId = "workspace-1",
            ownerPrincipalId = "owner-1",
            ownerPrincipalType = PrincipalType.USER,
        )

        assertTrue(ownership.belongsTo("owner-1"))
        assertFalse(ownership.belongsTo("member-2"))
    }
}
