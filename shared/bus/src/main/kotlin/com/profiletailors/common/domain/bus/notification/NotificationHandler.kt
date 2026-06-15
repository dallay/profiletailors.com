package com.profiletailors.common.domain.bus.notification

/**
 * Handler for a [Notification] — a fire-and-forget message that can be processed
 * by multiple handlers concurrently.
 *
 * @param T the notification type this handler can process
 */
interface NotificationHandler<in T> where T : Notification {
    /** Process a published notification. */
    suspend fun handle(notification: T)
}
