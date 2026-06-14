package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import com.profiletailors.smp.tenancy.domain.WorkspaceStatus
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Mono
import java.time.Clock
import java.util.UUID

/**
 * R2DBC implementation of [WorkspaceProvisioningService].
 *
 * Creates the workspace, ownership, and membership records in a single
 * transactional flow during user registration.
 */
@Service
class R2dbcWorkspaceProvisioningService(
    private val databaseClient: DatabaseClient,
    private val clock: Clock,
) : WorkspaceProvisioningService {

    override suspend fun provisionDefaultWorkspace(
        principalId: String,
        displayName: String,
    ): WorkspaceProvisioningService.ProvisionedWorkspace {
        val workspaceId = "ws-${UUID.randomUUID()}"
        val workspaceName = "${displayName.trim().takeIf { it.isNotEmpty() } ?: "My"}'s Workspace"
        val now = clock.instant()
        val membershipId = "wm-${UUID.randomUUID()}"

        // Use DatabaseClient.inTransaction() for reactive transaction management.
        // @Transactional from org.springframework.transaction.annotation does NOT work
        // in WebFlux + R2DBC because it relies on thread-bound PlatformTransactionManager.
        return databaseClient.inTransaction { client ->
            Mono.`when`(
                // 1. Create workspace
                client.sql(
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
                    .rowsUpdated(),

                // 2. Add workspace ownership
                client.sql(
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
                    .rowsUpdated(),

                // 3. Create workspace membership
                client.sql(
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
                    .rowsUpdated(),
            ).thenReturn(
                WorkspaceProvisioningService.ProvisionedWorkspace(
                    workspaceId = workspaceId,
                    name = workspaceName,
                ),
            )
        }.awaitSingle()
    }
}
