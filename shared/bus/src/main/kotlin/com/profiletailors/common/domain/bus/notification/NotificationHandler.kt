package com.profiletailors.common.domain.bus.notification

interface NotificationHandler<in T> where T : Notification {
    suspend fun handle(notification: T)
}
