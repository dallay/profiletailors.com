package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.smp.platformadmin.application.contracts.NotificationRepositoryPort
import com.profiletailors.smp.platformadmin.domain.NotificationDispatchException
import io.r2dbc.spi.R2dbcException
import org.springframework.stereotype.Component

@Component
class NotificationRepositoryPortAdapter(private val delegate: NotificationRepository) : NotificationRepositoryPort {

    override suspend fun findById(id: NotificationId): Notification? = delegate.findById(id)

    override suspend fun findByIdempotencyKey(key: IdempotencyKey): Notification? = delegate.findByIdempotencyKey(key)

    override suspend fun save(notification: Notification): Notification = try {
        delegate.save(notification)
    } catch (persistenceFailure: R2dbcException) {
        throw NotificationDispatchException(notification.idempotencyKey.value, persistenceFailure)
    }
}
