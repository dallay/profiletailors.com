package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class PrincipalStatus {
    ACTIVE,
    DEACTIVATED,
    SUSPENDED,
}
