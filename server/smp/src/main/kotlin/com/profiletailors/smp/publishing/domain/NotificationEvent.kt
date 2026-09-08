package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant

/**
 * Durable notification event for user-actionable LinkedIn publication and connection outcomes.
 * Persisted to a dedicated notification_events table for later delivery.
 */
@AggregateRoot
data class NotificationEvent(
    val id: String,
    val workspaceId: String,
    val provider: SocialProvider,
    val socialAccountId: String,
    val publicationId: String? = null,
    val category: NotificationCategory,
    val message: String,
    val suggestedAction: String? = null,
    val publicUrl: String? = null,
    val occurredAt: Instant,
    val createdAt: Instant? = null,
) {
    init {
        require(workspaceId.isNotBlank()) { "NotificationEvent workspaceId must not be blank." }
    }
}

@ValueObject
enum class NotificationCategory {
    PUBLICATION_SUCCEEDED,
    PUBLICATION_FAILED,
    PUBLICATION_BLOCKED,
    RECONNECT_REQUIRED,
    CAPABILITY_DENIED,
    MEDIA_PROCESSING_FAILED,
    RECURRENCE_PAUSED,
    AMBIGUOUS_OUTCOME,
}
