package com.profiletailors.leadcapture.waitlist.domain

@JvmInline
value class WaitlistEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Waitlist entry id must not be blank" }
    }
}
