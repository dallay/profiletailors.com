package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class AuthorizationDecision {
    ALLOW,
    DENY,
}
