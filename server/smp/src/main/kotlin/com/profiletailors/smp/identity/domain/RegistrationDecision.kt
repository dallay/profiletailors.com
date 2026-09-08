package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class RegistrationDecision {
    ALLOWED,
    INVITATION_REQUIRED,
    CLOSED,
}
