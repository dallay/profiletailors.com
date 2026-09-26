package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventPublisher
import kotlinx.coroutines.CancellationException

internal fun PublicationEventPublisher.publishBestEffort(event: PublicationEvent) {
    val result = runCatching { publish(event) }
    result.exceptionOrNull()?.let { failure ->
        if (failure is CancellationException) throw failure
    }
}
