package com.profiletailors.notifications.domain

import java.time.Instant
import java.util.UUID

/**
 * Audit record for a notification dispatched by the application.
 *
 * Notifications are immutable once persisted. A new instance is created for every dispatch
 * attempt, even if it is a retry of the same logical notification. The [idempotencyKey] is
 * what makes a retry idempotent: consumers must check it before sending to avoid duplicates.
 */
data class Notification(
    val id: NotificationId,
    val idempotencyKey: IdempotencyKey,
    val channel: NotificationChannel,
    val recipient: Recipient,
    val templateId: TemplateId,
    val payload: NotificationPayload,
    val status: NotificationStatus,
    val sentAt: Instant?,
    val failedAt: Instant?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(sentAt == null || status == NotificationStatus.SENT) {
            "sentAt must be null unless status is SENT"
        }
        require(failedAt == null || status == NotificationStatus.FAILED) {
            "failedAt must be null unless status is FAILED"
        }
        require(errorMessage == null == (status != NotificationStatus.FAILED)) {
            "errorMessage must be set iff status is FAILED"
        }
    }

    /**
     * Mark this notification as successfully sent.
     */
    fun markSent(at: Instant): Notification = copy(
        status = NotificationStatus.SENT,
        sentAt = at,
        failedAt = null,
        errorMessage = null,
        updatedAt = at,
    )

    /**
     * Mark this notification as failed, recording the error message.
     */
    fun markFailed(at: Instant, error: String): Notification = copy(
        status = NotificationStatus.FAILED,
        failedAt = at,
        errorMessage = error,
        updatedAt = at,
    )
}

/** Identifier for a persisted [Notification]. */
@JvmInline
value class NotificationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Notification id cannot be blank" }
    }

    companion object {
        fun generate(): NotificationId = NotificationId("ntf-${UUID.randomUUID()}")
    }
}

/**
 * Key used to deduplicate notification dispatches.
 *
 * Two dispatches with the same idempotency key represent the same logical notification
 * (e.g. a welcome email for a specific waitlist submission). Consumers must check the
 * repository before sending to avoid duplicates on retry.
 */
@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Idempotency key cannot be blank" }
    }
}

/** Email address or phone number the notification is addressed to. */
@JvmInline
value class Recipient(val value: String) {
    init {
        require(value.isNotBlank()) { "Recipient cannot be blank" }
    }
}

/** Identifier of the template used to render the notification. */
@JvmInline
value class TemplateId(val value: String) {
    init {
        require(value.isNotBlank()) { "Template id cannot be blank" }
    }
}

/**
 * Variables used to render a notification template. Stored as a string-keyed map of
 * primitives so the persistence layer can serialise it without bespoke converters.
 */
data class NotificationPayload(val variables: Map<String, String>) {
    init {
        variables.forEach { (key, _) ->
            require(key.isNotBlank()) { "Payload variable keys cannot be blank" }
        }
    }

    operator fun get(key: String): String? = variables[key]

    companion object {
        val EMPTY: NotificationPayload = NotificationPayload(emptyMap())
    }
}
