package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.tenancy.application.WorkspaceMembershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceMembershipRepository(
    private val databaseClient: DatabaseClient,
) : WorkspaceMembershipRepository {
    override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceMembership> =
        databaseClient.sql(
            """
            SELECT id, workspace_id, principal_id, principal_type, status
            FROM workspace_memberships
            WHERE workspace_id = :workspaceId
            ORDER BY principal_id
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .map { row, _ ->
                WorkspaceMembership(
                    id = requireNotNull(row.get("id", String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    principalId = requireNotNull(row.get("principal_id", String::class.java)),
                    principalType = com.profiletailors.smp.identity.domain.PrincipalType.valueOf(
                        requireNotNull(row.get("principal_type", String::class.java)),
                    ),
                    status = WorkspaceMembershipStatus.valueOf(
                        requireNotNull(row.get("status", String::class.java)),
                    ),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
            .toSet()

    override suspend fun updateStatus(
        workspaceId: String,
        principalId: String,
        status: WorkspaceMembershipStatus,
    ) {
        val rowsAffected = databaseClient.sql(
            """
            UPDATE workspace_memberships
            SET status = :status
            WHERE workspace_id = :workspaceId AND principal_id = :principalId
            """.trimIndent(),
        )
            .bind("status", status.name)
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        
        if (rowsAffected == 0L) {
            throw IllegalStateException(
                "Membership not found for principal '$principalId' in workspace '$workspaceId'"
            )
        }
    }
}
