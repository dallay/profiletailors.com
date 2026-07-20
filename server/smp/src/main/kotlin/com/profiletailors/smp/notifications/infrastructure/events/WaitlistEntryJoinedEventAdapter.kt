package com.profiletailors.smp.notifications.infrastructure.events

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotification
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotifier
import com.profiletailors.notifications.domain.event.WaitlistEntryJoined
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
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
internal class WaitlistEntryJoinedEventAdapter(private val eventPublisher: EventPublisher<DomainEvent>) :
    WaitlistEntryJoinedNotifier {

    @Suppress("TooGenericExceptionCaught")
    override fun notify(notification: WaitlistEntryJoinedNotification) {
        try {
            scope.launch {
                eventPublisher.publish(
                    WaitlistEntryJoined(
                        waitlistEntryId = notification.waitlistEntryId,
                        waitlistKey = notification.waitlistKey,
                        waitlistName = notification.waitlistName,
                        normalizedEmail = notification.normalizedEmail.value,
                        locale = notification.locale?.value,
                    ),
                )
            }
        } catch (e: Exception) {
            log.warn(
                "Failed to schedule WaitlistEntryJoined event for entry '{}' on waitlist '{}'",
                notification.waitlistEntryId.value,
                notification.waitlistKey.value,
                e,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WaitlistEntryJoinedEventAdapter::class.java)
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}
