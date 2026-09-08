package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.InvitationEmail
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.event.InvitationResent
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Consumes [InvitationIssued] and [InvitationResent] domain events and dispatches the
 * matching invitation email to the invitee.
 *
 * The handler enforces idempotency by recording the attempted dispatch in the
 * [NotificationRepository] and refusing to re-send if a record with the same idempotency
 * key already exists.
 *
 * Post-commit guarantee: Uses [TransactionalEventListener] with [TransactionPhase.AFTER_COMMIT]
 * to ensure the invitation is durably persisted before notification delivery begins.
 *
 * The raw invitation token is dropped on the floor after rendering; it never appears in
 * audit events, persisted notifications, or downstream event payloads (only inside the
 * accept URL, which is the single legitimate delivery surface).
 */
@Component
internal class SendInvitationEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val acceptUrlTemplate: AcceptUrlTemplate,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(SendInvitationEmailConsumer::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    suspend fun onInvitationIssued(event: InvitationIssued) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipientEmail,
            workspaceName = event.workspaceName,
            rawToken = event.rawToken,
            locale = event.locale,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    suspend fun onInvitationResent(event: InvitationResent) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipient,
            workspaceName = event.workspaceName,
            rawToken = event.rawToken,
            locale = event.locale,
        )
    }

    private suspend fun dispatch(
        invitationId: UUID,
        recipient: String,
        workspaceName: String,
        rawToken: String,
        locale: String?,
    ) {
        val acceptUrl = acceptUrlTemplate.build(rawToken)
        val normalizedEmail = recipient.trim().lowercase()
        val email = InvitationEmail(
            invitationId = invitationId,
            recipient = com.profiletailors.leadcapture.common.NormalizedEmail.fromPersisted(normalizedEmail),
            workspaceName = workspaceName,
            acceptUrl = acceptUrl,
            rawToken = rawToken,
            locale = locale,
        )
        val idempotencyKey = email.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Invitation email already dispatched for invitation '{}' - skipping",
                invitationId,
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
        val persisted = notificationRepository.save(pending)

        val result = emailDispatcher.dispatch(normalizedEmail, rendered)
        val now2 = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> persisted.markSent(now2)
            is EmailDispatchResult.Failure -> persisted.markFailed(now2, result.error)
        }
        notificationRepository.update(updated)

        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to send invitation email for invitation '{}': {}",
                invitationId,
                updated.errorMessage,
            )
        } else {
            log.info(
                "Invitation email dispatched for invitation '{}'",
                invitationId,
            )
        }
    }
}
