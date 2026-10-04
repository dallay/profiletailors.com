package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject

private val ALIAS_PATTERN = Regex("^[A-Za-z0-9]{4,32}$")

@JvmInline
@ValueObject
value class CustomAlias(val value: String) {
    init {
        require(ALIAS_PATTERN.matches(value)) {
            "Custom alias must match ^[A-Za-z0-9]{4,32}$"
        }
        require(value !in LinkPolicies.RESERVED_ALIASES) {
            "Alias '$value' is reserved"
        }
    }

    fun toShortCode(): ShortCode = ShortCode(value)
}
