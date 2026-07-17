package com.profiletailors.leadcapture.waitlist.domain

@JvmInline
value class WaitlistKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Waitlist key must not be blank" }
    }
}
