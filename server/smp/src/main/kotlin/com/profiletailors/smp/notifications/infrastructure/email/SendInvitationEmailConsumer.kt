package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.InvitationEmail
import com.profiletailors.notifications.domain.InvitationEmailTarget
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.event.InvitationResent
import com.profiletailors.smp.notifications.infrastructure.persistence.DuplicateNotificationException
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.domain.DirectInvitationResent
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
internal class SendInvitationEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val acceptUrlTemplate: AcceptUrlTemplate,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(SendInvitationEmailConsumer::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    suspend fun onInvitationIssued(event: InvitationIssued) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipientEmail,
            workspaceName = event.workspaceName,
            target = event.target.toEmailTarget(),
            rawToken = event.rawToken,
            locale = event.locale,
            deliveryId = event.deliveryId,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    suspend fun onInvitationResent(event: InvitationResent) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipient,
            workspaceName = event.workspaceName,
            target = InvitationEmailTarget.EXISTING_WORKSPACE,
            rawToken = event.rawToken,
            locale = event.locale,
            deliveryId = null,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    suspend fun onDirectInvitationResent(event: DirectInvitationResent) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipient,
            workspaceName = event.workspaceName,
            target = event.target.toEmailTarget(),
            rawToken = event.rawToken,
            locale = event.locale,
            deliveryId = event.deliveryId,
        )
    }

    private suspend fun dispatch(
        invitationId: UUID,
        recipient: String,
        workspaceName: String,
        target: InvitationEmailTarget,
        rawToken: String,
        locale: String?,
        deliveryId: UUID?,
    ) {
        val acceptUrl = acceptUrlTemplate.build(rawToken)
        val normalizedEmail = recipient.trim().lowercase()
        val email = InvitationEmail(
            invitationId = invitationId,
            recipient = com.profiletailors.leadcapture.common.NormalizedEmail.fromPersisted(normalizedEmail),
            workspaceName = workspaceName,
            target = target,
            acceptUrl = acceptUrl,
            rawToken = rawToken,
            locale = locale,
            deliveryId = deliveryId,
        )
        val idempotencyKey = email.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Invitation email already dispatched for invitation '{}' key '{}' - skipping",
                invitationId,
                idempotencyKey.value,
            )
            return
        }

        val now = Instant.now(clock)
        val pending = Notification(
            id = NotificationId.generate(),
            idempotencyKey = idempotencyKey,
            channel = NotificationChannel.EMAIL,
            recipient = Recipient(normalizedEmail),
            templateId = com.profiletailors.notifications.domain.InvitationEmailTemplateId.INSTANCE,
            payload = email.toPayload(),
            status = NotificationStatus.PENDING,
            sentAt = null,
            failedAt = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )
        val rendered = email.render()
        val persisted = try {
            notificationRepository.save(pending)
        } catch (duplicate: DuplicateNotificationException) {
            log.info(
                "Invitation email already claimed for invitation '{}' key '{}' - skipping",
                invitationId,
                duplicate.idempotencyKey.value,
            )
            return
        }

        val result = emailDispatcher.dispatch(normalizedEmail, rendered)
        val now2 = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> persisted.markSent(now2)
            is EmailDispatchResult.Failure -> persisted.markFailed(now2, result.error)
        }
        notificationRepository.update(updated)
        logDispatchOutcome(invitationId, idempotencyKey, updated)
    }

    private fun logDispatchOutcome(invitationId: UUID, idempotencyKey: IdempotencyKey, updated: Notification) {
        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to send invitation email for invitation '{}' key '{}': {}",
                invitationId,
                idempotencyKey.value,
                updated.errorMessage,
            )
        } else {
            log.info(
                "Invitation email dispatched for invitation '{}' key '{}'",
                invitationId,
                idempotencyKey.value,
            )
        }
    }

    private fun InvitationTarget.toEmailTarget(): InvitationEmailTarget = when (this) {
        InvitationTarget.EXISTING_WORKSPACE -> InvitationEmailTarget.EXISTING_WORKSPACE
        InvitationTarget.NEW_WORKSPACE -> InvitationEmailTarget.NEW_WORKSPACE
    }
}
