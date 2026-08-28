package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.RegistrationDecision

fun interface RegistrationPolicy {
    /**
     * Determines the registration decision based on whether an invitation token is present.
     *
     * @param hasInvitationToken Whether an invitation token is present.
     * @return The registration decision.
     */
    fun evaluate(hasInvitationToken: Boolean): RegistrationDecision
}
