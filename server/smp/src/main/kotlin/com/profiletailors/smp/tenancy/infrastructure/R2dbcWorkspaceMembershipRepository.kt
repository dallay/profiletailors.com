package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
internal class R2dbcWorkspaceMembershipRepository(private val databaseClient: DatabaseClient) :
    WorkspaceMembershipRepository {

    override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceMembership> = databaseClient.sql(
        """
        SELECT id, workspace_id, principal_id, principal_type, status
        FROM workspace_memberships
        WHERE workspace_id = :workspaceId
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .map { row, _ ->
            val principalTypeValue = requireNotNull(row.get("principal_type", String::class.java))
            val statusValue = requireNotNull(row.get("status", String::class.java))
            WorkspaceMembership(
                id = requireNotNull(row.get("id", String::class.java)),
                workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                principalType = PrincipalType.valueOf(principalTypeValue),
                status = WorkspaceMembershipStatus.valueOf(statusValue),
            )
        }
        .all()
        .collectList()
        .map { it.toSet() }
        .awaitSingle()

    override suspend fun updateStatus(workspaceId: String, principalId: String, status: WorkspaceMembershipStatus) {
        databaseClient.sql(
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
    }
}
