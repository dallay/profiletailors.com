package com.profiletailors.leadcapture.waitlist.domain

data class WaitlistConsent(val earlyAccess: Boolean, val marketing: Boolean = false, val version: String) {
    init {
        require(version.isNotBlank()) { "Consent version must not be blank" }
    }
}
