package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import com.profiletailors.smp.tenancy.domain.WorkspaceStatus
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Clock
import java.util.UUID

/**
 * R2DBC implementation of [WorkspaceProvisioningService].
 *
 * Creates the workspace, ownership, and membership records in a single
 * transactional flow during user registration.
 */
@Service
class R2dbcWorkspaceProvisioningService(private val databaseClient: DatabaseClient, private val clock: Clock) :
    WorkspaceProvisioningService {

    override suspend fun provisionDefaultWorkspace(
        principalId: String,
        displayName: String,
    ): WorkspaceProvisioningService.ProvisionedWorkspace {
        val workspaceId = "ws-${UUID.randomUUID()}"
        val workspaceName = "${displayName.trim().takeIf { it.isNotEmpty() } ?: "My"}'s Workspace"
        val now = clock.instant()
        val membershipId = "wm-${UUID.randomUUID()}"

        // Sequential inserts — same pattern as R2dbcIdentityRegistrationGateway.
        // R2DBC auto-commits each statement; if any insert fails the exception
        // propagates and the caller handles the rollback at the use-case level.

        // 1. Create workspace
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, created_at)
            VALUES (:id, :name, :status, :createdAt)
            """.trimIndent(),
        )
            .bind("id", workspaceId)
            .bind("name", workspaceName)
            .bind("status", WorkspaceStatus.ACTIVE.name)
            .bind("createdAt", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        // 2. Add workspace ownership
        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by, created_at)
            VALUES (:workspaceId, :ownerPrincipalId, :ownerPrincipalType, :createdBy, :createdAt)
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("ownerPrincipalId", principalId)
            .bind("ownerPrincipalType", PrincipalType.USER.name)
            .bind("createdBy", principalId)
            .bind("createdAt", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        // 3. Create workspace membership
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status, created_at)
            VALUES (:id, :workspaceId, :principalId, :principalType, :status, :createdAt)
            """.trimIndent(),
        )
            .bind("id", membershipId)
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .bind("principalType", PrincipalType.USER.name)
            .bind("status", WorkspaceMembershipStatus.ACTIVE.name)
            .bind("createdAt", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO membership_roles (membership_id, role_id, created_at)
            VALUES (:membershipId, :roleId, :createdAt)
            ON CONFLICT (membership_id, role_id) DO NOTHING
            """.trimIndent(),
        )
            .bind("membershipId", membershipId)
            .bind("roleId", "role-owner")
            .bind("createdAt", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return WorkspaceProvisioningService.ProvisionedWorkspace(
            workspaceId = workspaceId,
            name = workspaceName,
        )
    }
}
