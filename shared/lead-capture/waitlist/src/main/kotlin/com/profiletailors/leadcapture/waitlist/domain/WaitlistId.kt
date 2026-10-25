package com.profiletailors.leadcapture.waitlist.domain

import com.profiletailors.common.domain.ValueObject

@JvmInline
@ValueObject
value class WaitlistId(val value: String) {
    init {
        require(value.isNotBlank()) { "Waitlist id must not be blank" }
    }
}
