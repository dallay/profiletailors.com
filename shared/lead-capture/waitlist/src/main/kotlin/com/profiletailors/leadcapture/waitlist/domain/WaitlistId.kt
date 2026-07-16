package com.profiletailors.leadcapture.waitlist.domain

@JvmInline
value class WaitlistId(val value: String) {
    init {
        require(value.isNotBlank()) { "Waitlist id must not be blank" }
    }
}
