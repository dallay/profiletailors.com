package com.profiletailors.smp.publishing.domain

import java.time.Instant

data class PublicationEvent(
    val type: PublicationEventType,
    val workspaceId: String,
    val publicationId: String,
    val socialAccountId: String? = null,
    val occurredAt: Instant,
) {
    init {
        require(workspaceId.isNotBlank()) { "workspaceId must not be blank." }
        require(publicationId.isNotBlank()) { "publicationId must not be blank." }
    }
}

enum class PublicationEventType {
    CREATED,
    UPDATED,
    RESCHEDULED,
    DELETED,
    STATUS_CHANGED,
}

/**
 * Returns the dotted SSE change type, using a hyphen for status-changed.
 */
fun PublicationEventType.wireName(): String = when (this) {
    PublicationEventType.CREATED -> "publication.created"
    PublicationEventType.UPDATED -> "publication.updated"
    PublicationEventType.RESCHEDULED -> "publication.rescheduled"
    PublicationEventType.DELETED -> "publication.deleted"
    PublicationEventType.STATUS_CHANGED -> "publication.status-changed"
}

fun interface PublicationEventPublisher {
    /**
     * Publishes publication-change metadata; delivery and failure handling depend on the implementation.
     */
    fun publish(event: PublicationEvent)
}

object NoOpPublicationEventPublisher : PublicationEventPublisher {
    /**
     * Discards the event without delivery or other side effects.
     */
    override fun publish(event: PublicationEvent) = Unit
}
