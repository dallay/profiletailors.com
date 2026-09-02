package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import java.time.Clock
import java.time.Instant

data class AcceptInvitationCommand(
    val rawToken: String,
    val authenticatedPrincipalId: String,
    val authenticatedEmail: String,
) : CommandWithResult<InvitationAcceptanceResult>

data class InvitationAcceptanceResult(val workspaceId: String, val membershipStatus: String)

interface InvitationAcceptanceRepository {
    /**
 * Finds and locks the invitation associated with a candidate token key.
 *
 * @param candidateKey The candidate key derived from the invitation token.
 * @return The matching invitation, or `null` if no invitation exists.
 */
suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation?
    /**
 * Attempts to accept an invitation for a principal and persist the result.
 *
 * @param acceptedAt The time at which the invitation is accepted.
 * @param principalId The authenticated principal accepting the invitation.
 * @return `true` if the invitation was accepted and persisted, `false` otherwise.
 */
suspend fun markAccepted(invitationId: InvitationId, acceptedAt: Instant, principalId: String): Boolean
}
class InvitationAcceptanceRepositoryFacade(private val invitationRepository: InvitationRepository) :
    InvitationAcceptanceRepository {
    /**
         * Finds and locks an invitation associated with the candidate key.
         *
         * @param candidateKey The candidate key derived from the invitation token.
         * @return The matching invitation, or `null` if none exists.
         */
        override suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation? =
        invitationRepository.findByCandidateKeyForUpdate(candidateKey)

    /**
         * Marks an invitation as accepted for a principal at the specified time.
         *
         * @param invitationId The invitation to accept.
         * @param acceptedAt The time acceptance occurs.
         * @param principalId The principal accepting the invitation.
         * @return `true` if the invitation was accepted and persisted, `false` otherwise.
         */
        override suspend fun markAccepted(invitationId: InvitationId, acceptedAt: Instant, principalId: String): Boolean =
        invitationRepository.findById(invitationId)?.let { invitation ->
            invitation.acceptOrNull(acceptedAt, principalId)?.let {
                invitationRepository.updateIfVersionMatches(it)
            }
        } ?: false
}

/**
 * Attempts to accept the invitation for the specified principal.
 *
 * @param acceptedAt The time at which the invitation is accepted.
 * @param principalId The identifier of the accepting principal.
 * @return The accepted invitation, or `null` if acceptance fails.
 */
private fun Invitation.acceptOrNull(acceptedAt: Instant, principalId: String): Invitation? = runCatching {
    accept(acceptedAt, principalId)
}.getOrNull()

class AcceptInvitationHandler(
    private val invitationRepository: InvitationAcceptanceRepository,
    private val tokenHasher: TokenHasher,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val membershipProvisioner: WorkspaceMembershipProvisioner,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
) : CommandWithResultHandler<AcceptInvitationCommand, InvitationAcceptanceResult> {
    override suspend fun handle(command: AcceptInvitationCommand): InvitationAcceptanceResult =
        transactionRunner.runAtomically {
            val candidateKey = (tokenHasher as? InvitationTokenCandidateKey)
                ?.candidateKey(command.rawToken)
                ?: throw invalidInvitation()
            val invitation = invitationRepository.findByCandidateKeyForUpdate(candidateKey)
                ?.takeIf { tokenHasher.matches(command.rawToken, it.tokenHash) }
                ?.takeIf { it.status == InvitationStatus.ACTIVE }
                ?: throw invalidInvitation()
            val identity = principalIdentityLookup.findByPrincipalId(command.authenticatedPrincipalId)
                ?.takeIf { normalize(it.email) == normalize(command.authenticatedEmail) }
                ?.takeIf { normalize(it.email) == invitation.invitedEmailNormalized }
                ?: throw invalidInvitation()
            if (identity.principalType != PrincipalType.USER) throw invalidInvitation()

            val now = clock.instant()
            if (!invitation.isActive(now)) throw invalidInvitation()
            val membership = membershipProvisioner.reconcile(invitation.workspaceId, identity.principalId)
            if (!invitationRepository.markAccepted(invitation.id, now, identity.principalId)) {
                throw invalidInvitation()
            }
            InvitationAcceptanceResult(invitation.workspaceId, membership.status.name)
        }

    private fun invalidInvitation(): InvitationNotAcceptableException = InvitationNotAcceptableException("unavailable")

    private fun normalize(value: String?): String = value?.trim()?.lowercase() ?: ""
}
