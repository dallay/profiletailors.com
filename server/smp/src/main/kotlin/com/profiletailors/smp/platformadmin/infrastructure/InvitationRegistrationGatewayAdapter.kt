package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.identity.application.InvitationRegistrationGateway
import com.profiletailors.smp.platformadmin.application.InvitationActivationCoordinator
import org.springframework.stereotype.Component

@Component
class InvitationRegistrationGatewayAdapter(private val coordinator: InvitationActivationCoordinator) :
    InvitationRegistrationGateway {
    override suspend fun acceptForRegistration(rawToken: String, email: String, principalId: String): String {
        val result = coordinator.activateForRegistration(rawToken, email, principalId)
        return result.invitation.workspaceId
            ?: result.invitation.id.value.toString()
    }
}
