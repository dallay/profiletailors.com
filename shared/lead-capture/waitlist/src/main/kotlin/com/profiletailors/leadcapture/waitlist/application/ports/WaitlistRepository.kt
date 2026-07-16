package com.profiletailors.leadcapture.waitlist.application.ports

import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey

interface WaitlistRepository {
    fun findByKey(key: WaitlistKey): Waitlist?
}
