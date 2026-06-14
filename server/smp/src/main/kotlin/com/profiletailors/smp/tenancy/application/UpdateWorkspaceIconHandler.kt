package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient

@Service
internal class UpdateWorkspaceIconHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val databaseClient: DatabaseClient,
    private val workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
) : CommandWithResultHandler<UpdateWorkspaceIconCommand, UpdateWorkspaceIconResult> {

    override suspend fun handle(command: UpdateWorkspaceIconCommand): UpdateWorkspaceIconResult {
        val decision = workspaceAuthorizationDecider.decideDetailed(REQUIRED_PERMISSION)
        if (decision.decision == AuthorizationDecision.DENY) {
            throw AuthorizationDeniedException.forDecision(decision, REQUIRED_PERMISSION)
        }

        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)

        if (command.icon != null) {
            require(command.icon.matches(ICON_NAME_PATTERN)) {
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

    companion object {
        private val REQUIRED_PERMISSION = PermissionKey.of("workspace", "settings", "manage")
        private val ICON_NAME_PATTERN = Regex("^[a-z]([a-z-]*[a-z])?$")
    }
}
