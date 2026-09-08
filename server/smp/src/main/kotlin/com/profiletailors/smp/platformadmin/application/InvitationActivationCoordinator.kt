package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import java.time.Clock

class InvitationActivationCoordinator(
    private val invitationRepository: InvitationRepository,
    private val tokenHasher: TokenHasher,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val workspaceProvisioningService: WorkspaceProvisioningService,
    private val membershipProvisioner: WorkspaceMembershipProvisioner,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
) {
    data class InvitationActivationResult(val invitation: Invitation, val membershipStatus: WorkspaceMembershipStatus)

    private fun fail(message: String): Nothing = throw InvitationNotAcceptableException(message)

    suspend fun activateForRegistration(
        rawToken: String,
        email: String,
        principalId: String,
    ): InvitationActivationResult {
        val candidateKey = (tokenHasher as? InvitationTokenCandidateKey)
            ?.candidateKey(rawToken)
            ?: fail(UNAVAILABLE)

        return transactionRunner.runAtomically {
            val invitation = invitationRepository.findByCandidateKeyForUpdate(candidateKey)
                ?.takeIf { tokenHasher.matches(rawToken, it.tokenHash) }
                ?.takeIf { it.status == InvitationStatus.ACTIVE }
                ?: fail(UNAVAILABLE)

            val identity = principalIdentityLookup.findByPrincipalId(principalId)
                ?.takeIf { normalize(it.email) == normalize(email) }
                ?: fail(UNAVAILABLE)

            if (identity.principalType != PrincipalType.USER) {
                fail(UNAVAILABLE)
            }

            val now = clock.instant()
            if (!invitation.isActive(now)) {
                fail(UNAVAILABLE)
            }

            val provisioned = when (invitation.target) {
                InvitationTarget.EXISTING_WORKSPACE -> null
                InvitationTarget.NEW_WORKSPACE -> workspaceProvisioningService.provisionDefaultWorkspace(
                    principalId = identity.principalId,
                    displayName = identity.email ?: email,
                )
            }

            val resolvedWorkspaceId = when (invitation.target) {
                InvitationTarget.EXISTING_WORKSPACE -> invitation.workspaceId
                InvitationTarget.NEW_WORKSPACE -> provisioned?.workspaceId
            }

            val accepted = invitation.accept(now, identity.principalId, resolvedWorkspaceId)
            val success = invitationRepository.updateIfVersionMatches(accepted)
            if (!success) throw OptimisticLockException()

            val workspaceIdToReconcile = resolvedWorkspaceId
                ?: invitation.workspaceId
                ?: throw IllegalStateException("workspaceId must be resolved for reconciliation")

            val membership = membershipProvisioner.reconcile(workspaceIdToReconcile, identity.principalId)

            InvitationActivationResult(accepted, membership.status)
        }
    }

    private fun normalize(value: String?): String = value?.trim()?.lowercase() ?: ""

    private companion object {
        private const val UNAVAILABLE = "unavailable"
    }
}

class OptimisticLockException : RuntimeException("Invitation update failed due to concurrent modification")
