package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository

@Service
internal class RenameWorkspaceHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceMutationRepository: WorkspaceMutationRepository,
    private val workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
) : CommandWithResultHandler<RenameWorkspaceCommand, RenameWorkspaceResult> {

    override suspend fun handle(command: RenameWorkspaceCommand): RenameWorkspaceResult {
        val decision = workspaceAuthorizationDecider.decideDetailed(REQUIRED_PERMISSION)
        if (decision.decision == AuthorizationDecision.DENY) {
            throw AuthorizationDeniedException.forDecision(decision, REQUIRED_PERMISSION)
        }

        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)

        val trimmedName = command.newName.trim()
        require(trimmedName.isNotEmpty()) { "Workspace name cannot be blank." }
        require(trimmedName.length <= MAX_NAME_LENGTH) { "Workspace name cannot exceed $MAX_NAME_LENGTH characters." }

        check(workspaceMutationRepository.rename(workspaceId, trimmedName)) {
            "Workspace '$workspaceId' not found."
        }

        return RenameWorkspaceResult(
            workspaceId = workspaceId,
            name = trimmedName,
        )
    }

    companion object {
        private const val MAX_NAME_LENGTH = 255
        private val REQUIRED_PERMISSION = PermissionKey.of("workspace", "settings", "manage")
    }
}
