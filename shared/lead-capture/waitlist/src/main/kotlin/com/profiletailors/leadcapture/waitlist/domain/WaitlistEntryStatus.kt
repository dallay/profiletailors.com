package com.profiletailors.leadcapture.waitlist.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class WaitlistEntryStatus {
    PENDING,
    INVITED,
    CONVERTED,
    CANCELLED,
}
