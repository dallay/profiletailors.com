package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.tenancy.application.WorkspaceNameReader
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
internal class R2dbcWorkspaceNameReader(private val databaseClient: DatabaseClient) : WorkspaceNameReader {
    override suspend fun findName(workspaceId: String): String? = databaseClient.sql(
        """
            SELECT name
            FROM workspaces
            WHERE id = :workspaceId
              AND status = 'ACTIVE'
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .map { row, _ -> requireNotNull(row.get("name", String::class.java)) }
        .one()
        .awaitSingleOrNull()
}
