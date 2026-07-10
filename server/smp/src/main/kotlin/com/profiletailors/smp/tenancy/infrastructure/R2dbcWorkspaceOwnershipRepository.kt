package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Instant

private const val COL_WORKSPACE_ID = "workspace_id"
private const val COL_OWNER_PRINCIPAL_ID = "owner_principal_id"
private const val COL_OWNER_PRINCIPAL_TYPE = "owner_principal_type"
private const val COL_CREATED_BY = "created_by"
private const val COL_CREATED_AT = "created_at"

private const val BIND_WORKSPACE_ID = "workspaceId"
private const val BIND_PRINCIPAL_ID = "principalId"

@Repository
internal class R2dbcWorkspaceOwnershipRepository(
    private val databaseClient: DatabaseClient,
    private val transactionalOperator: TransactionalOperator,
) : WorkspaceOwnershipRepository {

    override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership> = databaseClient.sql(
        """
        SELECT workspace_id, owner_principal_id, owner_principal_type, created_by, created_at
        FROM workspace_ownerships
        WHERE workspace_id = :workspaceId
        """.trimIndent(),
    )
        .bind(BIND_WORKSPACE_ID, workspaceId)
        .map { row, _ ->
            WorkspaceOwnership(
                workspaceId = requireNotNull(row.get(COL_WORKSPACE_ID, String::class.java)),
                ownerPrincipalId = requireNotNull(row.get(COL_OWNER_PRINCIPAL_ID, String::class.java)),
                ownerPrincipalType = PrincipalType.valueOf(
                    requireNotNull(row.get(COL_OWNER_PRINCIPAL_TYPE, String::class.java)),
                ),
                createdBy = row.get(COL_CREATED_BY, String::class.java),
                createdAt = row.get(COL_CREATED_AT, Instant::class.java),
            )
        }
        .all()
        .collectList()
        .map { it.toSet() }
        .awaitSingle()

    override suspend fun add(ownership: WorkspaceOwnership) {
        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by, created_at)
            VALUES (:workspaceId, :ownerPrincipalId, :ownerPrincipalType, :createdBy, :createdAt)
            """.trimIndent(),
        )
            .bind(BIND_WORKSPACE_ID, ownership.workspaceId)
            .bind("ownerPrincipalId", ownership.ownerPrincipalId)
            .bind("ownerPrincipalType", ownership.ownerPrincipalType.name)
            .let { spec ->
                val createdBy = ownership.createdBy
                if (createdBy == null) {
                    spec.bindNull("createdBy", String::class.java)
                } else {
                    spec.bind("createdBy", createdBy)
                }
            }
            .bind("createdAt", ownership.createdAt ?: Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun remove(workspaceId: String, principalId: String) {
        databaseClient.sql(
            """
            DELETE FROM workspace_ownerships
            WHERE workspace_id = :workspaceId AND owner_principal_id = :principalId
            """.trimIndent(),
        )
            .bind(BIND_WORKSPACE_ID, workspaceId)
            .bind(BIND_PRINCIPAL_ID, principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun removeIfReplacementExists(workspaceId: String, principalId: String): Boolean {
        val rowsUpdated = databaseClient.sql(
            """
            SELECT owner_principal_id FROM workspace_ownerships
            WHERE workspace_id = :workspaceId
            FOR UPDATE
            """.trimIndent(),
        )
            .bind(BIND_WORKSPACE_ID, workspaceId)
            .then()
            .then(
                databaseClient.sql(
                    """
                    DELETE FROM workspace_ownerships
                    WHERE workspace_id = :workspaceId
                      AND owner_principal_id = :principalId
                      AND EXISTS (
                          SELECT 1 FROM workspace_ownerships
                          WHERE workspace_id = :workspaceId
                            AND owner_principal_id <> :principalId
                      )
                    """.trimIndent(),
                )
                    .bind(BIND_WORKSPACE_ID, workspaceId)
                    .bind(BIND_PRINCIPAL_ID, principalId)
                    .fetch()
                    .rowsUpdated(),
            )
            .`as`(transactionalOperator::transactional)
            .awaitSingle()

        return rowsUpdated > 0L
    }

    override suspend fun exists(workspaceId: String, principalId: String): Boolean {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM workspace_ownerships
            WHERE workspace_id = :workspaceId AND owner_principal_id = :principalId
            """.trimIndent(),
        )
            .bind(BIND_WORKSPACE_ID, workspaceId)
            .bind(BIND_PRINCIPAL_ID, principalId)
            .map { row, _ ->
                row.get(0, Long::class.javaObjectType) ?: 0L
            }
            .one()
            .awaitSingle()
        return count > 0L
    }
}
