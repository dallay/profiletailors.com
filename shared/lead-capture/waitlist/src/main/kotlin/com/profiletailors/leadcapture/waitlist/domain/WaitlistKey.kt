package com.profiletailors.leadcapture.waitlist.domain

import com.profiletailors.common.domain.ValueObject

@JvmInline
@ValueObject
value class WaitlistKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Waitlist key must not be blank" }
    }
}
