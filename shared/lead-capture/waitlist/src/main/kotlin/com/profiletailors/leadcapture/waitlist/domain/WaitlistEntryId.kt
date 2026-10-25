package com.profiletailors.leadcapture.waitlist.domain

import com.profiletailors.common.domain.ValueObject

@JvmInline
@ValueObject
value class WaitlistEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Waitlist entry id must not be blank" }
    }
}
