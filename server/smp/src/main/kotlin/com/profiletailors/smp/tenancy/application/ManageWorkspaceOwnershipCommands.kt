package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.application.Command
import com.profiletailors.smp.platform.application.CommandHandler
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnershipPolicy
import java.time.Clock

data class AddWorkspaceOwnerCommand(
    val targetPrincipalId: String,
) : Command<WorkspaceOwnershipResult>

data class RemoveWorkspaceOwnerCommand(
    val targetPrincipalId: String,
) : Command<WorkspaceOwnershipResult>

data class TransferWorkspaceOwnershipCommand(
    val targetPrincipalId: String,
) : Command<WorkspaceOwnershipResult>

data class WorkspaceOwnershipResult(
    val workspaceId: String,
    val ownerPrincipalIds: List<String>,
)

class AddWorkspaceOwnerHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val workspaceMembershipLookup: WorkspaceMembershipLookup,
    private val clock: Clock,
    private val tenancyMutationAuditor: TenancyMutationAuditor,
) : CommandHandler<AddWorkspaceOwnerCommand, WorkspaceOwnershipResult> {
    @Suppress("ThrowsCount")
    override suspend fun handle(command: AddWorkspaceOwnerCommand): WorkspaceOwnershipResult {
        val actor = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        return try {
            val currentOwners = workspaceOwnershipRepository.requireCurrentOwners(workspaceId)
            val actorOwnership = currentOwners.firstOrNull { ownership -> ownership.belongsTo(actor.principalId) }
                ?: throw WorkspaceOwnerAccessDeniedException()

            val targetMembership = requireActiveMembership(command.targetPrincipalId, resourceContext)
            if (!workspaceOwnershipRepository.exists(workspaceId, command.targetPrincipalId)) {
                workspaceOwnershipRepository.add(
                    WorkspaceOwnership(
                        workspaceId = workspaceId,
                        ownerPrincipalId = targetMembership.principalId,
                        ownerPrincipalType = targetMembership.principalType,
                        createdAt = clock.instant(),
                        createdBy = actorOwnership.ownerPrincipalId,
                    ),
                )
            }

            WorkspaceOwnershipResult(
                workspaceId = workspaceId,
                ownerPrincipalIds = workspaceOwnershipRepository
                    .findByWorkspaceId(workspaceId)
                    .map { ownership -> ownership.ownerPrincipalId }
                    .sorted(),
            ).also {
                tenancyMutationAuditor.recordSuccess(
                    action = "workspace.owner.add",
                    targetType = "WORKSPACE_OWNER",
                    targetId = command.targetPrincipalId,
                    workspaceId = workspaceId,
                    details = mapOf("ownerPrincipalId" to command.targetPrincipalId),
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            when (exception) {
                is IllegalArgumentException, is IllegalStateException -> {
                    tenancyMutationAuditor.recordRejected(
                        action = "workspace.owner.add",
                        targetType = "WORKSPACE_OWNER",
                        targetId = command.targetPrincipalId,
                        workspaceId = workspaceId,
                        reason = exception::class.simpleName ?: "Exception",
                        details = mapOf("ownerPrincipalId" to command.targetPrincipalId),
                    )
                    throw exception
                }
                else -> throw exception
            }
        }
    }

    private suspend fun requireActiveMembership(
        targetPrincipalId: String,
        resourceContext: com.profiletailors.smp.platform.domain.ResourceContext,
    ) = workspaceMembershipLookup.resolve(targetPrincipalId, resourceContext)
        ?.takeIf { membership -> membership.isActive() }
        ?: throw OwnerTargetMustBeActiveMemberException(
            targetPrincipalId,
            requireNotNull(resourceContext.workspaceId),
        )
}

class TransferWorkspaceOwnershipHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val workspaceMembershipLookup: WorkspaceMembershipLookup,
    private val clock: Clock,
    private val tenancyMutationAuditor: TenancyMutationAuditor,
    private val ownershipPolicy: WorkspaceOwnershipPolicy = WorkspaceOwnershipPolicy(),
) : CommandHandler<TransferWorkspaceOwnershipCommand, WorkspaceOwnershipResult> {
    @Suppress("ThrowsCount", "LongMethod")
    override suspend fun handle(command: TransferWorkspaceOwnershipCommand): WorkspaceOwnershipResult {
        val actor = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        
        // Prevent transferring ownership to self
        if (command.targetPrincipalId == actor.principalId) {
            throw IllegalArgumentException("Cannot transfer ownership to yourself")
        }
        
        return try {
            val currentOwners = workspaceOwnershipRepository.requireCurrentOwners(workspaceId)
            val actorOwnership = currentOwners.firstOrNull { it.belongsTo(actor.principalId) }
                ?: throw WorkspaceOwnerAccessDeniedException()
            val targetMembership = workspaceMembershipLookup.resolve(command.targetPrincipalId, resourceContext)
                ?.takeIf { membership -> membership.isActive() }
                ?: throw OwnerTargetMustBeActiveMemberException(command.targetPrincipalId, workspaceId)

            if (!workspaceOwnershipRepository.exists(workspaceId, command.targetPrincipalId)) {
                workspaceOwnershipRepository.add(
                    WorkspaceOwnership(
                        workspaceId = workspaceId,
                        ownerPrincipalId = targetMembership.principalId,
                        ownerPrincipalType = targetMembership.principalType,
                        createdAt = clock.instant(),
                        createdBy = actorOwnership.ownerPrincipalId,
                    ),
                )
            }

            val updatedOwners = workspaceOwnershipRepository.findByWorkspaceId(workspaceId)
            ownershipPolicy.ensureOwnerRemovalAllowed(updatedOwners, actorOwnership)
            workspaceOwnershipRepository.remove(workspaceId, actorOwnership.ownerPrincipalId)

            WorkspaceOwnershipResult(
                workspaceId = workspaceId,
                ownerPrincipalIds = workspaceOwnershipRepository.findByWorkspaceId(workspaceId)
                    .map { ownership -> ownership.ownerPrincipalId }
                    .sorted(),
            ).also {
                tenancyMutationAuditor.recordSuccess(
                    action = "workspace.owner.transfer",
                    targetType = "WORKSPACE_OWNER",
                    targetId = command.targetPrincipalId,
                    workspaceId = workspaceId,
                    details = mapOf(
                        "fromPrincipalId" to actor.principalId,
                        "toPrincipalId" to command.targetPrincipalId,
                    ),
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            when (exception) {
                is IllegalArgumentException, is IllegalStateException -> {
                    tenancyMutationAuditor.recordRejected(
                        action = "workspace.owner.transfer",
                        targetType = "WORKSPACE_OWNER",
                        targetId = command.targetPrincipalId,
                        workspaceId = workspaceId,
                        reason = exception::class.simpleName ?: "Exception",
                        details = mapOf(
                            "fromPrincipalId" to actor.principalId,
                            "toPrincipalId" to command.targetPrincipalId,
                        ),
                    )
                    throw exception
                }
                else -> throw exception
            }
        }
    }
}

class WorkspaceOwnerAccessDeniedException : IllegalStateException(
    "Only a current workspace owner may manage workspace ownership.",
)

class WorkspaceOwnerNotFoundException(
    principalId: String,
    workspaceId: String,
) : IllegalStateException(
    "Owner '$principalId' was not found for workspace '$workspaceId'.",
)

class OwnerTargetMustBeActiveMemberException(
    principalId: String,
    workspaceId: String,
) : IllegalStateException(
    "Principal '$principalId' must be an active member of workspace '$workspaceId' to hold ownership.",
)
