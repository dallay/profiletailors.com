package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class RegistrationMode {
    OPEN,
    INVITE_ONLY,
    CLOSED,
    ;

    /**
     * Determines the registration decision for this mode and invitation-token status.
     *
     * @param hasInvitationToken Whether an invitation token is present.
     * @return The applicable registration decision.
     */
    fun evaluate(hasInvitationToken: Boolean): RegistrationDecision = when {
        this == OPEN -> RegistrationDecision.ALLOWED
        this == INVITE_ONLY && hasInvitationToken -> RegistrationDecision.ALLOWED
        this == INVITE_ONLY -> RegistrationDecision.INVITATION_REQUIRED
        else -> RegistrationDecision.CLOSED
    }
}
