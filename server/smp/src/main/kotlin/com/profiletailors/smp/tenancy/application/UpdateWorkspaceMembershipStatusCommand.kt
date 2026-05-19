package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.platform.application.Command
import com.profiletailors.smp.platform.application.CommandHandler
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnershipPolicy

data class UpdateWorkspaceMembershipStatusCommand(
    val targetPrincipalId: String,
    val targetStatus: WorkspaceMembershipStatus,
) : Command<WorkspaceMembershipStatusResult>

data class WorkspaceMembershipStatusResult(
    val workspaceId: String,
    val principalId: String,
    val status: WorkspaceMembershipStatus,
)

class UpdateWorkspaceMembershipStatusHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val workspaceMembershipLookup: WorkspaceMembershipLookup,
    private val workspaceMembershipRepository: WorkspaceMembershipRepository,
    private val ownershipPolicy: WorkspaceOwnershipPolicy = WorkspaceOwnershipPolicy(),
    private val tenancyMutationAuditor: TenancyMutationAuditor,
) : CommandHandler<UpdateWorkspaceMembershipStatusCommand, WorkspaceMembershipStatusResult> {
    @Suppress("ThrowsCount")
    override suspend fun handle(command: UpdateWorkspaceMembershipStatusCommand): WorkspaceMembershipStatusResult {
        val actor = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        return try {
            val currentOwners = workspaceOwnershipRepository.requireCurrentOwners(workspaceId)
            currentOwners.firstOrNull { ownership -> ownership.belongsTo(actor.principalId) }
                ?: throw WorkspaceOwnerAccessDeniedException()

            val membership = workspaceMembershipLookup.resolve(command.targetPrincipalId, resourceContext)
                ?: throw WorkspaceMembershipNotFoundException(command.targetPrincipalId, workspaceId)
            val memberships = workspaceMembershipRepository.findByWorkspaceId(workspaceId)

            ownershipPolicy.ensureMembershipStatusChangeAllowed(
                ownerships = currentOwners,
                memberships = memberships,
                membershipToChange = membership,
                targetStatus = command.targetStatus,
            )

            workspaceMembershipRepository.updateStatus(workspaceId, command.targetPrincipalId, command.targetStatus)
            WorkspaceMembershipStatusResult(
                workspaceId = workspaceId,
                principalId = command.targetPrincipalId,
                status = command.targetStatus,
            ).also {
                tenancyMutationAuditor.recordSuccess(
                    action = "workspace.membership.status.update",
                    targetType = "WORKSPACE_MEMBERSHIP",
                    targetId = command.targetPrincipalId,
                    workspaceId = workspaceId,
                    details = mapOf("targetStatus" to command.targetStatus.name),
                )
            }
        } catch (exception: IllegalArgumentException) {
            tenancyMutationAuditor.recordRejected(
                action = "workspace.membership.status.update",
                targetType = "WORKSPACE_MEMBERSHIP",
                targetId = command.targetPrincipalId,
                workspaceId = workspaceId,
                reason = exception::class.simpleName ?: "Exception",
                details = mapOf("targetStatus" to command.targetStatus.name),
            )
            throw exception
        }
    }
}

class WorkspaceMembershipNotFoundException(
    principalId: String,
    workspaceId: String,
) : IllegalStateException(
    "Membership for principal '$principalId' was not found in workspace '$workspaceId'.",
)
