package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.event.NotificationRetryRequested
import com.profiletailors.notifications.domain.redactPayload
import com.profiletailors.notifications.domain.redactSensitiveValue
import com.profiletailors.smp.platformadmin.application.command.RetryNotificationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.NotificationEventPublisher
import com.profiletailors.smp.platformadmin.application.contracts.NotificationRepositoryPort
import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.NotificationDispatchException
import com.profiletailors.smp.platformadmin.domain.NotificationNotFoundForRetryException
import com.profiletailors.smp.platformadmin.domain.NotificationNotRetryableException
import com.profiletailors.smp.platformadmin.domain.NotificationRetryConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.time.Instant
import java.util.UUID

private const val ERROR_MESSAGE_MAX_LENGTH = 200

class RetryNotificationHandler(
    private val notificationRepository: NotificationRepositoryPort,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val eventPublisher: NotificationEventPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
) {
    suspend fun handle(command: RetryNotificationCommand): NotificationSummary {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.NOTIFICATIONS_MANAGE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.NOTIFICATIONS_MANAGE)
        }

        val existing = loadRetryableNotification(command)

        val saved = try {
            transactionRunner.runAtomically {
                persistRetryAttempt(command, existing).also { attempt ->
                    eventPublisher.publish(
                        NotificationRetryRequested(
                            retryNotificationId = attempt.id.value,
                            idempotencyKey = attempt.idempotencyKey.value,
                        ),
                    )
                }
            }
        } catch (dispatchFailure: NotificationDispatchException) {
            publishOutcome(
                operatorPrincipalId = command.operatorPrincipalId,
                operatorRoles = command.operatorRoles,
                notification = existing,
                outcome = RetryOutcome.DISPATCH_FAILED,
                result = AdminAuditResult.FAILED,
                retryNotificationId = null,
            )
            throw dispatchFailure
        }

        publishOutcome(
            operatorPrincipalId = command.operatorPrincipalId,
            operatorRoles = command.operatorRoles,
            notification = existing,
            outcome = RetryOutcome.SUCCESS,
            result = AdminAuditResult.SUCCEEDED,
            retryNotificationId = saved.id.value,
        )

        return toSummary(saved)
    }

    private fun toSummary(saved: Notification): NotificationSummary {
        val redactedPayload = redactPayload(saved.payload.variables.mapValues { it.value })
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

    private suspend fun loadRetryableNotification(command: RetryNotificationCommand): Notification {
        val existing = notificationRepository.findById(command.notificationId)
            ?: throw NotificationNotFoundForRetryException(command.notificationId.value)

        if (existing.canRetry().not()) {
            publishOutcome(
                operatorPrincipalId = command.operatorPrincipalId,
                operatorRoles = command.operatorRoles,
                notification = existing,
                outcome = RetryOutcome.REJECTED,
                result = AdminAuditResult.REJECTED,
                retryNotificationId = null,
            )
            throw NotificationNotRetryableException(
                command.notificationId.value,
                existing.status.name,
                existing.templateId.value,
            )
        }
        return existing
    }

    private suspend fun persistRetryAttempt(command: RetryNotificationCommand, existing: Notification): Notification {
        val retryIdempotencyKey = command.idempotencyKey
            ?: "retry-${command.notificationId.value}-${UUID.randomUUID()}"
        if (notificationRepository.findByIdempotencyKey(IdempotencyKey(retryIdempotencyKey)) != null) {
            throw NotificationRetryConflictException(retryIdempotencyKey)
        }

        val now = clock.instant()
        val retryNotification = Notification(
            id = NotificationId.generate(),
            idempotencyKey = IdempotencyKey(retryIdempotencyKey),
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

        return notificationRepository.save(retryNotification)
    }

    private suspend fun publishOutcome(
        operatorPrincipalId: UUID,
        operatorRoles: Set<PlatformRole>,
        notification: Notification,
        outcome: RetryOutcome,
        result: AdminAuditResult,
        retryNotificationId: String?,
    ) {
        val rawPriorError = notification.errorMessage ?: ""
        val redactedPriorError = redactSensitiveValue(rawPriorError).take(ERROR_MESSAGE_MAX_LENGTH)
        val auditEvent = AdminAuditEvent(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.now(clock),
            operatorPrincipalId = operatorPrincipalId,
            operatorPlatformRoles = operatorRoles,
            action = AdminAuditAction.NOTIFICATION_RETRIED,
            targetType = "NOTIFICATION",
            targetId = notification.id.value,
            result = result,
            metadata = buildMap {
                put("notificationId", notification.id.value)
                put("channel", notification.channel.name)
                put("templateId", notification.templateId.value)
                put("retryOutcome", outcome.name)
                put("priorStatus", notification.status.name)
                put("priorError", redactedPriorError)
                if (retryNotificationId != null) {
                    put("retryNotificationId", retryNotificationId)
                }
            },
        )
        auditPublisher.publish(auditEvent)
    }

    private enum class RetryOutcome {
        SUCCESS,
        REJECTED,
        DISPATCH_FAILED,
    }
}
