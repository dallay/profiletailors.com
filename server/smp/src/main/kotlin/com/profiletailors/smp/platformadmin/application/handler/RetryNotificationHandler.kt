package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.smp.platformadmin.application.command.RetryNotificationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.NotificationRepositoryPort
import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.NotificationNotFoundForRetryException
import com.profiletailors.smp.platformadmin.domain.NotificationNotRetryableException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.platformadmin.infrastructure.util.redactPayload
import java.time.Clock
import java.time.Instant
import java.util.UUID

private const val ERROR_MESSAGE_MAX_LENGTH = 200

class RetryNotificationHandler(
    private val notificationRepository: NotificationRepositoryPort,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: RetryNotificationCommand): NotificationSummary {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.NOTIFICATIONS_MANAGE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.NOTIFICATIONS_MANAGE)
        }

        val existing = notificationRepository.findById(command.notificationId)
            ?: throw NotificationNotFoundForRetryException(command.notificationId.value)

        if (existing.canRetry().not()) {
            throw NotificationNotRetryableException(
                command.notificationId.value,
                existing.status.name,
                existing.templateId.value,
            )
        }

        val retryIdempotencyKey = command.idempotencyKey
            ?: "retry-${command.notificationId.value}-${UUID.randomUUID()}"
        val now = clock.instant()

        val retryNotification = Notification(
            id = NotificationId.generate(),
            idempotencyKey = com.profiletailors.notifications.domain.IdempotencyKey(retryIdempotencyKey),
            channel = existing.channel,
            recipient = existing.recipient,
            templateId = existing.templateId,
            payload = existing.payload,
            status = NotificationStatus.PENDING,
            sentAt = null,
            failedAt = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )

        val saved = notificationRepository.save(retryNotification)

        val payloadMap = existing.payload.variables
        val redactedPayload = redactPayload(payloadMap.mapValues { it.value })

        publishAuditEvent(
            operatorPrincipalId = command.operatorPrincipalId,
            operatorRoles = command.operatorRoles,
            notificationId = command.notificationId.value,
            channel = existing.channel.name,
            templateId = existing.templateId.value,
            priorStatus = existing.status.name,
            priorError = existing.errorMessage,
            retryNotificationId = saved.id.value,
        )

        return NotificationSummary(
            id = saved.id.value,
            channel = saved.channel.name,
            templateId = saved.templateId.value,
            recipient = saved.recipient.value,
            status = saved.status.name,
            errorMessage = saved.errorMessage,
            createdAt = saved.createdAt,
            sentAt = saved.sentAt,
            failedAt = saved.failedAt,
            redactedPayload = redactedPayload,
        )
    }

    private suspend fun publishAuditEvent(
        operatorPrincipalId: UUID,
        operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        notificationId: String,
        channel: String,
        templateId: String,
        priorStatus: String,
        priorError: String?,
        retryNotificationId: String,
    ) {
        val auditEvent = AdminAuditEvent(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.now(clock),
            operatorPrincipalId = operatorPrincipalId,
            operatorPlatformRoles = operatorRoles,
            action = AdminAuditAction.NOTIFICATION_RETRIED,
            targetType = "NOTIFICATION",
            targetId = notificationId,
            result = AdminAuditResult.SUCCEEDED,
            metadata = mapOf<String, String>(
                "channel" to channel,
                "templateId" to templateId,
                "priorStatus" to priorStatus,
                "priorError" to (priorError?.take(ERROR_MESSAGE_MAX_LENGTH) ?: ""),
                "retryNotificationId" to retryNotificationId,
            ),
        )
        auditPublisher.publish(auditEvent)
    }
}
