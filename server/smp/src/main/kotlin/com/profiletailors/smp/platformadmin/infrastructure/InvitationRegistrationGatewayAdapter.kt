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

    private fun invalidInvitation(): InvitationNotAcceptableException = InvitationNotAcceptableException("unavailable")

    private fun normalize(value: String): String = value.trim().lowercase()
}
