package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.RegistrationDecision

fun interface RegistrationPolicy {
    fun evaluate(hasInvitationToken: Boolean): RegistrationDecision
}
