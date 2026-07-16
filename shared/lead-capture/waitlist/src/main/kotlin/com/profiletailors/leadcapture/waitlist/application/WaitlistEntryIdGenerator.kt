package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId

fun interface WaitlistEntryIdGenerator {
    fun generate(waitlistId: WaitlistId, normalizedEmail: NormalizedEmail): WaitlistEntryId
}
