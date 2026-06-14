package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient

@Service
internal class RenameWorkspaceHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val databaseClient: DatabaseClient,
) : CommandWithResultHandler<RenameWorkspaceCommand, RenameWorkspaceResult> {

    override suspend fun handle(command: RenameWorkspaceCommand): RenameWorkspaceResult {
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)

        val trimmedName = command.newName.trim()
        require(trimmedName.isNotEmpty()) { "Workspace name cannot be blank." }
        require(trimmedName.length <= MAX_NAME_LENGTH) { "Workspace name cannot exceed $MAX_NAME_LENGTH characters." }

        val rowsUpdated = databaseClient.sql(
            "UPDATE workspaces SET name = :name WHERE id = :id",
        )
            .bind("name", trimmedName)
            .bind("id", workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        if (rowsUpdated == 0L) {
            throw IllegalStateException("Workspace '$workspaceId' not found.")
        }

        return RenameWorkspaceResult(
            workspaceId = workspaceId,
            name = trimmedName,
        )
    }

    companion object {
        private const val MAX_NAME_LENGTH = 255
    }
}
