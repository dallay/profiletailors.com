package com.profiletailors.smp.notifications.infrastructure.events

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryJoinedNotification
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryJoinedNotifier
import com.profiletailors.notifications.domain.event.WaitlistEntryJoined
import com.profiletailors.spring.boot.bus.event.EventEmitter
import org.springframework.stereotype.Component

/**
 * Server-side adapter that translates the framework-free
 * [WaitlistEntryJoinedNotification] (emitted by the lead-capture shared module) into a
 * concrete [DomainEvent] and publishes it on the event bus.
 *
 * The bus then dispatches the event to any [com.profiletailors.common.domain.bus.event.EventConsumer]
 * subscribed to it — most notably the welcome email consumer in the notifications module.
 */
@Component
internal class WaitlistEntryJoinedEventTranslator(private val eventEmitter: EventEmitter<DomainEvent>) :
    WaitlistEntryJoinedNotifier {

    override suspend fun notify(notification: WaitlistEntryJoinedNotification) {
        eventEmitter.publish(
            WaitlistEntryJoined(
                waitlistEntryId = notification.waitlistEntryId,
                waitlistKey = notification.waitlistKey,
                waitlistName = notification.waitlistName,
                normalizedEmail = notification.normalizedEmail.value,
                locale = notification.locale?.value,
            ),
        )
    }
}
