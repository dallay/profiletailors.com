package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationId

interface NotificationRepositoryPort {
    suspend fun findById(id: NotificationId): Notification?
    suspend fun findByIdempotencyKey(key: IdempotencyKey): Notification?
    suspend fun save(notification: Notification): Notification
}
