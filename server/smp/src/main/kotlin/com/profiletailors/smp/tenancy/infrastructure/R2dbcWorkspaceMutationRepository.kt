package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceMutationRepository(
    private val databaseClient: DatabaseClient,
) : WorkspaceMutationRepository {

    override suspend fun rename(workspaceId: String, newName: String): Boolean {
        val rowsUpdated = databaseClient.sql("UPDATE workspaces SET name = :name WHERE id = :id")
            .bind("name", newName)
            .bind("id", workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return rowsUpdated != 0L
    }

    override suspend fun updateIcon(workspaceId: String, icon: String?): Boolean {
        val rowsUpdated = databaseClient.sql("UPDATE workspaces SET icon = :icon WHERE id = :id")
            .let { spec ->
                if (icon == null) spec.bindNull("icon", String::class.java) else spec.bind("icon", icon)
            }
            .bind("id", workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return rowsUpdated != 0L
    }
}
