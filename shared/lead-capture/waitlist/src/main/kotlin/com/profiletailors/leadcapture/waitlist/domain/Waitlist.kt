package com.profiletailors.leadcapture.waitlist.domain

import java.time.Instant

class Waitlist(
    val id: WaitlistId,
    val key: WaitlistKey,
    val name: String,
    val context: String,
    status: WaitlistStatus = WaitlistStatus.DRAFT,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
) {
    var status: WaitlistStatus = status
        private set

    init {
        require(name.isNotBlank()) { "Waitlist name must not be blank" }
        require(context.isNotBlank()) { "Waitlist context must not be blank" }
    }

    fun activate(now: Instant = Instant.now()) {
        check(status != WaitlistStatus.CLOSED) { "Cannot activate a closed waitlist" }
        check(status != WaitlistStatus.ARCHIVED) { "Cannot activate an archived waitlist" }
        status = WaitlistStatus.ACTIVE
        updatedAt = now
    }

    fun pause(now: Instant = Instant.now()) {
        check(status != WaitlistStatus.ARCHIVED) { "Cannot pause an archived waitlist" }
        check(status != WaitlistStatus.CLOSED) { "Cannot pause a closed waitlist" }
        status = WaitlistStatus.PAUSED
        updatedAt = now
    }

    fun close(now: Instant = Instant.now()) {
        check(status != WaitlistStatus.ARCHIVED) { "Cannot close an archived waitlist" }
        status = WaitlistStatus.CLOSED
        updatedAt = now
    }

    fun archive(now: Instant = Instant.now()) {
        check(status == WaitlistStatus.CLOSED) { "Only closed waitlists can be archived" }
        status = WaitlistStatus.ARCHIVED
        updatedAt = now
    }
}
