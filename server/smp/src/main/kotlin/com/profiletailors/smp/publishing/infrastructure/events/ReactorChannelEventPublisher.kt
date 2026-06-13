package com.profiletailors.smp.publishing.infrastructure.events

import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

fun interface ChannelEventStreamRegistry {
    fun stream(): Flux<ChannelEvent>
}

/**
 * In-memory best-effort MVP channel event bus.
 *
 * Events are intentionally not persisted and do not cross application instances;
 * REST channel listing remains the canonical source of truth.
 */
@Component
class ReactorChannelEventPublisher : ChannelEventPublisher, ChannelEventStreamRegistry {
    private val sink = Sinks.many().multicast().directBestEffort<ChannelEvent>()

    override fun publish(event: ChannelEvent) {
        sink.tryEmitNext(event)
    }

    override fun stream(): Flux<ChannelEvent> = sink.asFlux()
}
