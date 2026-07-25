package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository

@Service
internal class UpdateWorkspaceIconHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceMutationRepository: WorkspaceMutationRepository,
    private val tenancyAuthorizationGate: TenancyAuthorizationGate,
) : CommandWithResultHandler<UpdateWorkspaceIconCommand, UpdateWorkspaceIconResult> {

    override suspend fun handle(command: UpdateWorkspaceIconCommand): UpdateWorkspaceIconResult {
        tenancyAuthorizationGate.requireAllowed(TenancyAuthorizationPermission.WORKSPACE_SETTINGS_MANAGE)

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
        private val ICON_NAME_PATTERN = Regex("^[a-z]+(-[a-z]+)*$")
    }
}
