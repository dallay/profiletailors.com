package com.profiletailors.notifications.domain

import com.profiletailors.common.domain.ValueObject

/**
 * Delivery channel for a [Notification].
 */
@ValueObject
enum class NotificationChannel {
    EMAIL,
    SMS,
    PUSH,
}
