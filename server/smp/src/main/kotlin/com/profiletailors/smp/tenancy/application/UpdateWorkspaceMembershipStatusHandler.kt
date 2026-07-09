package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipNotFoundException
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import com.profiletailors.smp.tenancy.application.WorkspaceOwnerAccessDeniedException
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnershipPolicy

@Service
internal class UpdateWorkspaceMembershipStatusHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val workspaceMembershipLookup: WorkspaceMembershipLookup,
    private val workspaceMembershipRepository: WorkspaceMembershipRepository,
    private val ownershipPolicy: WorkspaceOwnershipPolicy = WorkspaceOwnershipPolicy(),
    private val tenancyMutationAuditor: TenancyMutationAuditor,
    private val transactionRunner: AtomicTransactionRunner,
) : CommandWithResultHandler<UpdateWorkspaceMembershipStatusCommand, WorkspaceMembershipStatusResult> {
    @Suppress("ThrowsCount")
    override suspend fun handle(command: UpdateWorkspaceMembershipStatusCommand): WorkspaceMembershipStatusResult {
        val actor = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        return try {
            transactionRunner.runAtomically {
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
            }
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            when (exception) {
                is IllegalArgumentException, is IllegalStateException -> {
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

                else -> {
                    throw exception
                }
            }
        }
    }
}
