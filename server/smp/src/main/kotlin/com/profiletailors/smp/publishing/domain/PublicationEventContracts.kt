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

fun PublicationEventType.wireName(): String = when (this) {
    PublicationEventType.CREATED -> "publication.created"
    PublicationEventType.UPDATED -> "publication.updated"
    PublicationEventType.RESCHEDULED -> "publication.rescheduled"
    PublicationEventType.DELETED -> "publication.deleted"
    PublicationEventType.STATUS_CHANGED -> "publication.status-changed"
}

fun interface PublicationEventPublisher {
    fun publish(event: PublicationEvent)
}

object NoOpPublicationEventPublisher : PublicationEventPublisher {
    override fun publish(event: PublicationEvent) = Unit
}
