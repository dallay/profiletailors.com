package com.profiletailors.notifications.domain

import com.profiletailors.common.domain.ValueObject

/**
 * Delivery status of a [Notification].
 *
 * - [PENDING]  the notification has been recorded but the dispatch has not been attempted yet.
 * - [SENT]     the notification was successfully delivered to the channel provider.
 * - [FAILED]   the channel provider rejected the dispatch. Inspect [Notification.errorMessage] for details.
 */
@ValueObject
enum class NotificationStatus {
    PENDING,
    DISPATCHING,
    SENT,
    FAILED,
}
