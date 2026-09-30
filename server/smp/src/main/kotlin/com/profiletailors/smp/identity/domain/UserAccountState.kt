package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class UserAccountState {
    ACTIVE,
    DISABLED,
}
