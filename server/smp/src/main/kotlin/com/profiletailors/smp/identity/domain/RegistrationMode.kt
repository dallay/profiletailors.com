package com.profiletailors.smp.identity.domain

enum class RegistrationMode {
    OPEN,
    INVITE_ONLY,
    CLOSED,
    ;

    /**
     * Determines whether registration is allowed for this mode.
     *
     * @param hasInvitationToken Whether an invitation token is available.
     * @return The registration decision for this mode and token state.
     */
    fun evaluate(hasInvitationToken: Boolean): RegistrationDecision = when {
        this == OPEN -> RegistrationDecision.ALLOWED
        this == INVITE_ONLY && hasInvitationToken -> RegistrationDecision.ALLOWED
        this == INVITE_ONLY -> RegistrationDecision.INVITATION_REQUIRED
        else -> RegistrationDecision.CLOSED
    }
}
