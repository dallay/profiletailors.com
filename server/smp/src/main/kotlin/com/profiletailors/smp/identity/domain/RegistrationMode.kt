package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class RegistrationMode {
    OPEN,
    INVITE_ONLY,
    CLOSED,
    ;

    fun evaluate(hasInvitationToken: Boolean): RegistrationDecision = when {
        this == OPEN -> RegistrationDecision.ALLOWED
        this == INVITE_ONLY && hasInvitationToken -> RegistrationDecision.ALLOWED
        this == INVITE_ONLY -> RegistrationDecision.INVITATION_REQUIRED
        else -> RegistrationDecision.CLOSED
    }
}
