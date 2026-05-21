package com.profiletailors.smp.tenancy.domain

import com.profiletailors.common.domain.context.PrincipalType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceDomainModelsTest {

    private val ownershipPolicy = WorkspaceOwnershipPolicy()

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
            status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
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

    @Test
    fun `ownership policy allows multiple owners while preserving active memberships`() {
        val owners = setOf(
            WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
            WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-2",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        val memberships = setOf(
            WorkspaceMembership(
                workspaceId = "workspace-1",
                principalId = "owner-1",
                principalType = PrincipalType.USER,
                status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
            ),
            WorkspaceMembership(
                workspaceId = "workspace-1",
                principalId = "owner-2",
                principalType = PrincipalType.USER,
                status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
            ),
        )

        assertDoesNotThrow { ownershipPolicy.ensureAtLeastOneOwner(owners) }
        assertDoesNotThrow { ownershipPolicy.ensureOwnersRemainActiveMembers(owners, memberships) }
    }

    @Test
    fun `workspace must always keep at least one owner`() {
        assertThrows(WorkspaceMustHaveAtLeastOneOwnerException::class.java) {
            ownershipPolicy.ensureAtLeastOneOwner(emptySet())
        }
    }

    @Test
    fun `last owner cannot be removed without replacement`() {
        val soleOwner = WorkspaceOwnership(
            workspaceId = "workspace-1",
            ownerPrincipalId = "owner-1",
            ownerPrincipalType = PrincipalType.USER,
        )

        assertThrows(LastOwnerRemovalRequiresReplacementException::class.java) {
            ownershipPolicy.ensureOwnerRemovalAllowed(setOf(soleOwner), soleOwner)
        }
    }

    @Test
    fun `owner must remain an active workspace member`() {
        val owners = setOf(
            WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        val memberships = setOf(
            WorkspaceMembership(
                workspaceId = "workspace-1",
                principalId = "owner-1",
                principalType = PrincipalType.USER,
                status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.REMOVED,
            ),
        )

        assertThrows(OwnerMustRemainActiveMemberException::class.java) {
            ownershipPolicy.ensureOwnersRemainActiveMembers(owners, memberships)
        }
    }

    @Test
    fun `membership status change cannot deactivate last active owner`() {
        val owners = setOf(
            WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        val membership = WorkspaceMembership(
            workspaceId = "workspace-1",
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
        )

        assertThrows(OwnerMustRemainActiveMemberException::class.java) {
            ownershipPolicy.ensureMembershipStatusChangeAllowed(
                ownerships = owners,
                memberships = setOf(membership),
                membershipToChange = membership,
                targetStatus = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.SUSPENDED,
            )
        }
    }
}
