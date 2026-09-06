package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
data class Entitlement(val key: String, val enabled: Boolean) {
    init {
        require(key.isNotBlank()) { "Entitlement key must not be blank." }
    }
}
