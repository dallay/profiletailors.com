package com.profiletailors.leadcapture.waitlist.application.ports

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey

/** Request emitted by the waitlist context when a new entry has consent worth recording as legal evidence. */
data class WaitlistConsentRecordRequest(
    val waitlistKey: WaitlistKey,
    val entryId: WaitlistEntryId,
    val normalizedEmail: NormalizedEmail,
    val consent: WaitlistConsent,
    val locale: CaptureLocale?,
    val source: CaptureSource,
)

/**
 * Port used by waitlist to record consent without depending on governance internals.
 */
fun interface WaitlistConsentRecorder {
    fun record(request: WaitlistConsentRecordRequest)

    companion object {
        val noop: WaitlistConsentRecorder = WaitlistConsentRecorder { }
    }
}
