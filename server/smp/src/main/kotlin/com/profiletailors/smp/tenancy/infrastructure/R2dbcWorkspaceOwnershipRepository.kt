package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import com.profiletailors.smp.identity.domain.PrincipalType
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class R2dbcWorkspaceOwnershipRepository(
    private val databaseClient: DatabaseClient,
) : WorkspaceOwnershipRepository {
    override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership> =
        databaseClient.sql(
            """
            SELECT workspace_id, owner_principal_id, owner_principal_type, created_by, created_at
            FROM workspace_ownerships
            WHERE workspace_id = :workspaceId
            ORDER BY owner_principal_id
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .map { row, _ ->
                WorkspaceOwnership(
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    ownerPrincipalId = requireNotNull(row.get("owner_principal_id", String::class.java)),
                    ownerPrincipalType = PrincipalType.valueOf(
                        requireNotNull(row.get("owner_principal_type", String::class.java)),
                    ),
                    createdBy = row.get("created_by", String::class.java),
                    createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
            .toSet()

    override suspend fun add(ownership: WorkspaceOwnership) {
        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (
                workspace_id,
                owner_principal_id,
                owner_principal_type,
                created_by,
                created_at
            ) VALUES (
                :workspaceId,
                :ownerPrincipalId,
                :ownerPrincipalType,
                :createdBy,
                :createdAt
            )
            """.trimIndent(),
        )
            .bind("workspaceId", ownership.workspaceId)
            .bind("ownerPrincipalId", ownership.ownerPrincipalId)
            .bind("ownerPrincipalType", ownership.ownerPrincipalType.name)
            .let { spec ->
                ownership.createdBy
                    ?.let { createdBy -> spec.bind("createdBy", createdBy) }
                    ?: spec.bindNull("createdBy", String::class.java)
            }
            .let { spec ->
                ownership.createdAt
                    ?.let { createdAt -> spec.bind("createdAt", createdAt) }
                    ?: spec.bindNull("createdAt", java.time.Instant::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun remove(workspaceId: String, principalId: String) {
        databaseClient.sql(
            "DELETE FROM workspace_ownerships WHERE workspace_id = :workspaceId AND owner_principal_id = :principalId",
        )
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun exists(workspaceId: String, principalId: String): Boolean =
        databaseClient.sql(
            """
            SELECT 1 FROM workspace_ownerships 
            WHERE workspace_id = :workspaceId AND owner_principal_id = :principalId
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .map { _, _ -> true }
            .one()
            .awaitSingleOrNull() == true
}
