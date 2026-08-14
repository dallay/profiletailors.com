package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

private const val ID_COLUMN = "id"
private const val WORKSPACE_ID_COLUMN = "workspace_id"
private const val PRINCIPAL_ID_COLUMN = "principal_id"
private const val PRINCIPAL_TYPE_COLUMN = "principal_type"
private const val STATUS_COLUMN = "status"
private const val WORKSPACE_ID_BINDING = "workspaceId"
private const val PRINCIPAL_ID_BINDING = "principalId"
private const val PRINCIPAL_TYPE_BINDING = "principalType"
private const val STATUS_BINDING = "status"

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
        .bind(WORKSPACE_ID_BINDING, workspaceId)
        .map { row, _ ->
            val principalTypeValue = requireNotNull(row.get(PRINCIPAL_TYPE_COLUMN, String::class.java))
            val statusValue = requireNotNull(row.get(STATUS_COLUMN, String::class.java))
            WorkspaceMembership(
                id = requireNotNull(row.get(ID_COLUMN, String::class.java)),
                workspaceId = requireNotNull(row.get(WORKSPACE_ID_COLUMN, String::class.java)),
                principalId = requireNotNull(row.get(PRINCIPAL_ID_COLUMN, String::class.java)),
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
            .bind(STATUS_BINDING, status.name)
            .bind(WORKSPACE_ID_BINDING, workspaceId)
            .bind(PRINCIPAL_ID_BINDING, principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun reconcile(workspaceId: String, principalId: String): WorkspaceMembership {
        val existing = databaseClient.sql(
            """
            SELECT id, workspace_id, principal_id, principal_type, status
            FROM workspace_memberships
            WHERE workspace_id = :workspaceId AND principal_id = :principalId
            FOR UPDATE
            """.trimIndent(),
        )
            .bind(WORKSPACE_ID_BINDING, workspaceId)
            .bind(PRINCIPAL_ID_BINDING, principalId)
            .map { row, _ ->
                WorkspaceMembership(
                    id = requireNotNull(row.get(ID_COLUMN, String::class.java)),
                    workspaceId = requireNotNull(row.get(WORKSPACE_ID_COLUMN, String::class.java)),
                    principalId = requireNotNull(row.get(PRINCIPAL_ID_COLUMN, String::class.java)),
                    principalType = PrincipalType.valueOf(
                        requireNotNull(row.get(PRINCIPAL_TYPE_COLUMN, String::class.java)),
                    ),
                    status = WorkspaceMembershipStatus.valueOf(
                        requireNotNull(row.get(STATUS_COLUMN, String::class.java)),
                    ),
                )
            }
            .one()
            .awaitSingleOrNull()

        if (existing != null) return existing

        val id = "wm-${UUID.randomUUID()}"
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES (:id, :workspaceId, :principalId, :principalType, :status)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind(WORKSPACE_ID_BINDING, workspaceId)
            .bind(PRINCIPAL_ID_BINDING, principalId)
            .bind(PRINCIPAL_TYPE_BINDING, PrincipalType.USER.name)
            .bind("status", WorkspaceMembershipStatus.ACTIVE.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return WorkspaceMembership(
            id = id,
            workspaceId = workspaceId,
            principalId = principalId,
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
    }
}
