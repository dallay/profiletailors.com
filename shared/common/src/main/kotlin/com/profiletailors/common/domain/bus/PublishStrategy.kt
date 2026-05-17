package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.notification.NotificationHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface PublishStrategy {
    suspend fun <T : Notification> publish(
        notification: T,
        notificationHandlers: Collection<NotificationHandler<T>>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    )
}
