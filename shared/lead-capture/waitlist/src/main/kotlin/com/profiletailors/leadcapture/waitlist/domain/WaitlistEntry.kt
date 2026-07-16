package com.profiletailors.leadcapture.waitlist.domain

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import java.time.Instant

class WaitlistEntry(
    val id: WaitlistEntryId,
    val waitlistId: WaitlistId,
    val email: EmailAddress,
    val normalizedEmail: NormalizedEmail,
    val source: CaptureSource,
    val formId: String?,
    val locale: CaptureLocale?,
    val metadata: LeadMetadata,
    val consent: WaitlistConsent,
    val joinedAt: Instant,
    status: WaitlistEntryStatus = WaitlistEntryStatus.PENDING,
    invitedAt: Instant? = null,
    convertedAt: Instant? = null,
    cancelledAt: Instant? = null,
) {
    var status: WaitlistEntryStatus = status
        private set
    var invitedAt: Instant? = invitedAt
        private set
    var convertedAt: Instant? = convertedAt
        private set
    var cancelledAt: Instant? = cancelledAt
        private set

    fun invite(at: Instant) {
        check(status == WaitlistEntryStatus.PENDING) { "Only pending entries can be invited" }
        status = WaitlistEntryStatus.INVITED
        invitedAt = at
    }

    fun convert(at: Instant) {
        check(status == WaitlistEntryStatus.INVITED) { "Only invited entries can be converted" }
        status = WaitlistEntryStatus.CONVERTED
        convertedAt = at
    }

    fun cancel(at: Instant) {
        check(status != WaitlistEntryStatus.CONVERTED) { "Cannot cancel a converted entry" }
        check(status != WaitlistEntryStatus.CANCELLED) { "Entry is already cancelled" }
        status = WaitlistEntryStatus.CANCELLED
        cancelledAt = at
    }
}
