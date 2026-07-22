package com.profiletailors.notifications.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

internal class EventPublisherNoopTest {

    @Test
    fun `noop publisher is constructible and swallows events`() = runTest {
        val publisher: EventPublisher<BaseDomainEvent> = EventPublisher.noop()
        assertNotNull(publisher)
        publisher.publish(SampleEvent())
    }

    private data class SampleEvent(val id: String = "evt-1") : BaseDomainEvent()
}
