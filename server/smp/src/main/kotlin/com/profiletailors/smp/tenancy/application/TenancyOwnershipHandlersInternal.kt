package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.tenancy.application.AddWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.OwnerTargetMustBeActiveMemberException
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipCommand
import com.profiletailors.smp.tenancy.application.WorkspaceOwnerAccessDeniedException
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipResult
import com.profiletailors.smp.tenancy.domain.LastOwnerRemovalRequiresReplacementException
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import java.time.Clock

internal class AddWorkspaceOwnerHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val workspaceMembershipLookup: WorkspaceMembershipLookup,
    private val clock: Clock,
    private val tenancyMutationAuditor: TenancyMutationAuditor,
) : CommandWithResultHandler<AddWorkspaceOwnerCommand, WorkspaceOwnershipResult> {
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

            return WorkspaceOwnershipResult(
                workspaceId = workspaceId,
                ownerPrincipalIds = workspaceOwnershipRepository.findByWorkspaceId(workspaceId)
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
        resourceContext: ResourceContext,
    ) = workspaceMembershipLookup.resolve(targetPrincipalId, resourceContext)
        ?.takeIf { membership -> membership.isActive() }
        ?: throw OwnerTargetMustBeActiveMemberException(
            targetPrincipalId,
            requireNotNull(resourceContext.workspaceId),
        )
}

internal class TransferWorkspaceOwnershipHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val workspaceMembershipLookup: WorkspaceMembershipLookup,
    private val clock: Clock,
    private val tenancyMutationAuditor: TenancyMutationAuditor,
) : CommandWithResultHandler<TransferWorkspaceOwnershipCommand, WorkspaceOwnershipResult> {
    @Suppress("ThrowsCount", "LongMethod")
    override suspend fun handle(command: TransferWorkspaceOwnershipCommand): WorkspaceOwnershipResult {
        val actor = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)

        return try {
            require(command.targetPrincipalId != actor.principalId) { "Cannot transfer ownership to yourself" }

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

            // Atomically removes the actor's ownership only when a replacement owner already
            // exists in the DB.  This closes the TOCTOU race: a concurrent transfer that removed
            // the replacement between the "add" above and this delete cannot cause the workspace
            // to become ownerless — the database operation simply returns false in that case.
            val removed = workspaceOwnershipRepository.removeIfReplacementExists(
                workspaceId,
                actorOwnership.ownerPrincipalId,
            )
            if (!removed) {
                throw LastOwnerRemovalRequiresReplacementException(workspaceId)
            }

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
