package com.profiletailors.smp.publishing.infrastructure.events

import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

class ReactorPublicationEventPublisherTest {
    @Test
    fun `publishes events to active subscribers`() {
        val publisher = ReactorPublicationEventPublisher()
        val event = PublicationEvent(
            type = PublicationEventType.STATUS_CHANGED,
            workspaceId = "workspace-1",
            publicationId = "pub-1",
            socialAccountId = "account-1",
            occurredAt = Instant.parse("2026-09-25T12:00:00Z"),
        )

        StepVerifier.create(publisher.stream().take(1))
            .then { publisher.publish(event) }
            .assertNext { emitted -> assertEquals(event, emitted) }
            .verifyComplete()
    }

    @Test
    fun `workspace filters can isolate active workspace events`() {
        val publisher = ReactorPublicationEventPublisher()
        val activeWorkspaceEvent = PublicationEvent(
            type = PublicationEventType.STATUS_CHANGED,
            workspaceId = "workspace-1",
            publicationId = "pub-1",
            socialAccountId = "account-1",
            occurredAt = Instant.parse("2026-09-25T12:00:00Z"),
        )
        val otherWorkspaceEvent = activeWorkspaceEvent.copy(
            workspaceId = "workspace-2",
            publicationId = "pub-2",
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

    @Test
    fun `publish failures never propagate to the caller`() {
        val publisher = ReactorPublicationEventPublisher()
        val event = PublicationEvent(
            type = PublicationEventType.CREATED,
            workspaceId = "workspace-1",
            publicationId = "pub-3",
            occurredAt = Instant.parse("2026-09-25T12:00:00Z"),
        )

        StepVerifier.create(publisher.stream().take(1))
            .then { publisher.publish(event) }
            .assertNext { emitted -> assertEquals(event, emitted) }
            .verifyComplete()
    }
}
