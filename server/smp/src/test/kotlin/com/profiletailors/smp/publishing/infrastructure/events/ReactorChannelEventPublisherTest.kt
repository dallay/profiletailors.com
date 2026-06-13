package com.profiletailors.smp.publishing.infrastructure.events

import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

class ReactorChannelEventPublisherTest {
    @Test
    fun `publishes events to active subscribers`() {
        val publisher = ReactorChannelEventPublisher()
        val event = ChannelEvent(
            type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
            workspaceId = "workspace-1",
            socialAccountId = "account-1",
            occurredAt = Instant.parse("2026-06-12T12:00:00Z"),
        )

        StepVerifier.create(publisher.stream().take(1))
            .then { publisher.publish(event) }
            .assertNext { emitted -> assertEquals(event, emitted) }
            .verifyComplete()
    }

    @Test
    fun `workspace filters can isolate active workspace events`() {
        val publisher = ReactorChannelEventPublisher()
        val activeWorkspaceEvent = ChannelEvent(
            type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
            workspaceId = "workspace-1",
            socialAccountId = "account-1",
            occurredAt = Instant.parse("2026-06-12T12:00:00Z"),
        )
        val otherWorkspaceEvent = activeWorkspaceEvent.copy(
            workspaceId = "workspace-2",
            socialAccountId = "account-2",
        )

        StepVerifier.create(publisher.stream().filter { it.workspaceId == "workspace-1" }.take(1))
            .then {
                publisher.publish(otherWorkspaceEvent)
                publisher.publish(activeWorkspaceEvent)
            }
            .expectNext(activeWorkspaceEvent)
            .verifyComplete()
    }
}
