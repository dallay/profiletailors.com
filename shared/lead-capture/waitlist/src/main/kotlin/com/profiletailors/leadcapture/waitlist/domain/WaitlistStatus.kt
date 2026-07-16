package com.profiletailors.leadcapture.waitlist.domain

enum class WaitlistStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    CLOSED,
    ARCHIVED,
    ;

    fun acceptsEntries(): Boolean = this == ACTIVE

    fun isClosedSignal(): Boolean = this == PAUSED || this == CLOSED || this == ARCHIVED
}
