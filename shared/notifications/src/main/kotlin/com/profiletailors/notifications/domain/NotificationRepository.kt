package com.profiletailors.notifications.domain

/**
 * Outbound port for persisting [Notification] records.
 *
 * Implementations live in the infrastructure layer of the server module (e.g. R2DBC). The
 * repository is the audit log of every dispatch attempt; consumers query it to enforce
 * idempotency before sending.
 */
interface NotificationRepository {

    /**
     * Persist a new notification. Implementations must enforce the unique constraint on
     * [Notification.idempotencyKey] and surface the conflict as a duplicate insert.
     */
    suspend fun save(notification: Notification): Notification

    /**
     * Update an existing notification (e.g. to mark it SENT or FAILED).
     */
    suspend fun update(notification: Notification): Notification

    /**
     * Find a notification by its idempotency key, or `null` if no notification with that
     * key has been recorded.
     */
    suspend fun findByIdempotencyKey(key: IdempotencyKey): Notification?
}
