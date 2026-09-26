package com.profiletailors.smp.publishing.infrastructure.events

import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventPublisher
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

fun interface PublicationEventStreamRegistry {
    /**
     * Exposes publication-change events for consumers to filter to their workspace.
     */
    fun stream(): Flux<PublicationEvent>
}

@Component
class ReactorPublicationEventPublisher :
    PublicationEventPublisher,
    PublicationEventStreamRegistry {
    private val sink = Sinks.many().multicast().directBestEffort<PublicationEvent>()

    /**
     * Attempts immediate delivery to subscribers with demand, without buffering or replay.
     * Emission failure results are ignored.
     */
    override fun publish(event: PublicationEvent) {
        sink.tryEmitNext(event)
    }

    /**
     * Returns the live event stream across all workspaces; past events are not replayed.
     */
    override fun stream(): Flux<PublicationEvent> = sink.asFlux()
}
