package com.profiletailors.leadcapture.waitlist.application.contracts

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey

/**
 * Notification emitted by [com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandler]
 * after a new entry has been persisted successfully.
 *
 * This is a framework-free value object: the lead-capture shared module cannot depend on
 * the bus or on a [BaseDomainEvent] base class (architectural rule LeadCaptureArchTest).
 * The server-side adapter translates this into a concrete [com.profiletailors.common.domain.bus.event.DomainEvent]
 * and publishes it to the event bus.
 */
data class WaitlistEntryJoinedNotification(
    val waitlistEntryId: WaitlistEntryId,
    val waitlistKey: WaitlistKey,
    val waitlistName: String,
    val normalizedEmail: NormalizedEmail,
    val locale: CaptureLocale?,
)

/**
 * Outbound port that the [com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandler]
 * uses to notify the rest of the system that a new entry was persisted.
 *
 * The lead-capture shared module does not know about the bus; the server-side
 * implementation translates these calls into a [com.profiletailors.common.domain.bus.event.DomainEvent]
 * published via the event bus.
 */
fun interface WaitlistEntryJoinedNotifier {
    suspend fun notify(notification: WaitlistEntryJoinedNotification)

    companion object {
        /** A no-op notifier used in tests and contexts where events are not consumed. */
        val noop: WaitlistEntryJoinedNotifier = WaitlistEntryJoinedNotifier { }
    }
}
