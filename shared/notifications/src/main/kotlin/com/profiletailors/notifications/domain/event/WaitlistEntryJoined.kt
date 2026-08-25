package com.profiletailors.notifications.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey

/**
 * Domain event consumed by the notifications module when a new entry is persisted to
 * a waitlist.
 *
 * This event is published by the server-side adapter
 * [com.profiletailors.smp.leadcapture.infrastructure.events.WaitlistEntryJoinedEventTranslator],
 * which translates the framework-free
 * [com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryJoinedNotification]
 * into a [DomainEvent] and dispatches it on the bus.
 *
 * @property waitlistEntryId id of the newly-created entry
 * @property waitlistKey public key of the waitlist that received the entry
 * @property waitlistName human-readable name of the waitlist (used for email templates)
 * @property normalizedEmail email submitted by the user, normalised
 * @property locale optional BCP-47 locale code (e.g. "en", "es")
 */
data class WaitlistEntryJoined(
    val waitlistEntryId: WaitlistEntryId,
    val waitlistKey: WaitlistKey,
    val waitlistName: String,
    val normalizedEmail: String,
    val locale: String?,
) : BaseDomainEvent()
