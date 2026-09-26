package com.profiletailors.smp.publishing.infrastructure.events

import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventPublisher
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

fun interface PublicationEventStreamRegistry {
    fun stream(): Flux<PublicationEvent>
}

@Component
class ReactorPublicationEventPublisher :
    PublicationEventPublisher,
    PublicationEventStreamRegistry {
    private val sink = Sinks.many().multicast().directBestEffort<PublicationEvent>()

    override fun publish(event: PublicationEvent) {
        sink.tryEmitNext(event)
    }

    override fun stream(): Flux<PublicationEvent> = sink.asFlux()
}
