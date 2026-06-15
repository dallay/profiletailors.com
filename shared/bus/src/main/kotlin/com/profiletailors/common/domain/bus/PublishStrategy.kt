package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.notification.NotificationHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Strategy for dispatching a [Notification] to multiple handlers.
 *
 * Built-in strategies: [StopOnExceptionPublishStrategy], [ParallelNoWaitPublishStrategy].
 *
 * @see PublishStrategies
 */
interface PublishStrategy {
    /**
     * Publish a notification to all registered handlers.
     * @param T the notification type
     * @param notification the notification to publish
     * @param notificationHandlers the handlers to invoke
     * @param dispatcher coroutine dispatcher for async execution
     */
    suspend fun <T : Notification> publish(
        notification: T,
        notificationHandlers: Collection<NotificationHandler<T>>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    )
}
