package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository

@Service
internal class UpdateWorkspaceIconHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceMutationRepository: WorkspaceMutationRepository,
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

        check(workspaceMutationRepository.updateIcon(workspaceId, command.icon)) {
            "Workspace '$workspaceId' not found."
        }

        return UpdateWorkspaceIconResult(workspaceId = workspaceId, icon = command.icon)
    }

    companion object {
        private val REQUIRED_PERMISSION = PermissionKey.of("workspace", "settings", "manage")
        private val ICON_NAME_PATTERN = Regex("^[a-z]+(-[a-z]+)*$")
    }
}
