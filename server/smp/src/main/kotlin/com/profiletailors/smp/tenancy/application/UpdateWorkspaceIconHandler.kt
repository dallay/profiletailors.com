package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient

@Service
internal class UpdateWorkspaceIconHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val databaseClient: DatabaseClient,
) : CommandWithResultHandler<UpdateWorkspaceIconCommand, UpdateWorkspaceIconResult> {

    override suspend fun handle(command: UpdateWorkspaceIconCommand): UpdateWorkspaceIconResult {
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)

        if (command.icon != null) {
            require(command.icon.matches(Regex("^[a-z]([a-z-]*[a-z])?$"))) {
                "Invalid icon name: '${command.icon}'. Use only lowercase letters and hyphens."
            }
        }

        val rowsUpdated = databaseClient.sql("UPDATE workspaces SET icon = :icon WHERE id = :id")
            .let {
                if (command.icon == null) it.bindNull("icon", String::class.java)
                else it.bind("icon", command.icon)
            }
            .bind("id", workspaceId)
            .fetch().rowsUpdated().awaitSingle()

        if (rowsUpdated == 0L) {
            throw IllegalStateException("Workspace '$workspaceId' not found.")
        }

        return UpdateWorkspaceIconResult(workspaceId = workspaceId, icon = command.icon)
    }
}
