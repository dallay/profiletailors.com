package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.identity.application.InvitationRegistrationGateway
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class InvitationRegistrationGatewayAdapter(
    private val invitationRepository: InvitationAcceptanceRepository,
    private val tokenHasher: TokenHasher,
    private val membershipProvisioner: WorkspaceMembershipProvisioner,
    private val clock: Clock,
) : InvitationRegistrationGateway {
    /**
     * Accepts an invitation for registration and provisions workspace membership.
     *
     * @param rawToken The raw invitation token.
     * @param email The email address associated with the registration.
     * @param principalId The principal identifier to provision in the workspace.
     * @return The identifier of the invitation's workspace.
     * @throws InvitationNotAcceptableException If the invitation cannot be validated or accepted.
     */
    override suspend fun acceptForRegistration(rawToken: String, email: String, principalId: String): String {
        val now = clock.instant()
        val candidateKey = (tokenHasher as? InvitationTokenCandidateKey)
            ?.candidateKey(rawToken)
            ?: throw invalidInvitation()
        val invitation = invitationRepository.findByCandidateKeyForUpdate(candidateKey)
            ?.takeIf { tokenHasher.matches(rawToken, it.tokenHash) }
            ?.takeIf { it.status == InvitationStatus.ACTIVE }
            ?.takeIf { normalize(it.invitedEmailNormalized) == normalize(email) }
            ?.takeIf { it.isActive(now) }
            ?: throw invalidInvitation()
        membershipProvisioner.reconcile(invitation.workspaceId, principalId)
        if (!invitationRepository.markAccepted(invitation.id, now, principalId)) {
            throw invalidInvitation()
        }
        return invitation.workspaceId
    }

    /**
 * Creates the generic exception used when an invitation cannot be accepted.
 *
 * @return An exception indicating that the invitation is unavailable.
 */
private fun invalidInvitation(): InvitationNotAcceptableException = InvitationNotAcceptableException("unavailable")

    /**
 * Normalizes text by trimming surrounding whitespace and converting it to lowercase.
 *
 * @param value The text to normalize.
 * @return The normalized text.
 */
private fun normalize(value: String): String = value.trim().lowercase()
}
