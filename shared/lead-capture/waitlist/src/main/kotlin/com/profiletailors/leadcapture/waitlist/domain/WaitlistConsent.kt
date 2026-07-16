package com.profiletailors.leadcapture.waitlist.domain

data class WaitlistConsent(val earlyAccess: Boolean, val marketing: Boolean = false, val version: String) {
    init {
        require(earlyAccess) { "Early access consent is required (ADR-0011)" }
        require(version.isNotBlank()) { "Consent version must not be blank" }
    }
}
