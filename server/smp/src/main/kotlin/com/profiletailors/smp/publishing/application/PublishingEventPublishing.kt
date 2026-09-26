package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventPublisher
import kotlinx.coroutines.CancellationException

/**
 * Attempts to publish [event], swallowing publisher failures except coroutine cancellation.
 * Call after a successful transaction when notification failure must not undo persistence.
 *
 * @throws CancellationException if the publisher signals cancellation.
 */
internal fun PublicationEventPublisher.publishBestEffort(event: PublicationEvent) {
    val result = runCatching { publish(event) }
    result.exceptionOrNull()?.let { failure ->
        if (failure is CancellationException) throw failure
    }
}
