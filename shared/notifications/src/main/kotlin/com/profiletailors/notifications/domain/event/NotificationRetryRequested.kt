package com.profiletailors.notifications.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent

data class NotificationRetryRequested(val retryNotificationId: String, val idempotencyKey: String) :
    BaseDomainEvent() {
    init {
        require(retryNotificationId.isNotBlank()) { "Retry notification id must not be blank" }
        require(idempotencyKey.isNotBlank()) { "Retry idempotency key must not be blank" }
    }
}
