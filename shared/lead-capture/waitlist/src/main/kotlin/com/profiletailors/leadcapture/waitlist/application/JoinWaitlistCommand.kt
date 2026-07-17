package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey

data class JoinWaitlistCommand(
    val waitlistKey: WaitlistKey,
    val email: EmailAddress,
    val source: CaptureSource,
    val formId: String?,
    val locale: CaptureLocale?,
    val metadata: LeadMetadata,
    val consent: WaitlistConsent,
) {
    fun normalizedEmail(): NormalizedEmail = NormalizedEmail.from(email)
}
