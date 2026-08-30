package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.InvitationEmail
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.event.InvitationCreated
import com.profiletailors.notifications.domain.event.InvitationDeliveryAttempted
import com.profiletailors.notifications.domain.event.InvitationResent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Consumes [InvitationCreated] and [InvitationResent] domain events and dispatches the
 * matching invitation email to the invitee.
 *
 * The handler enforces idempotency by recording the attempted dispatch in the
 * [NotificationRepository] and refusing to re-send if a record with the same idempotency
 * key already exists. After the email dispatcher returns, the consumer publishes a
 * secondary [InvitationDeliveryAttempted] event so the platform-admin bounded context
 * can update the invitation's `deliveryStatus` without notifications needing to depend
 * on platform-admin types.
 *
 * The raw invitation token is dropped on the floor after rendering; it never appears in
 * audit events, persisted notifications, or downstream event payloads (only inside the
 * accept URL, which is the single legitimate delivery surface).
 */
@Component
@Subscribe(filterBy = InvitationCreated::class)
internal class SendInvitationEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val deliveryEventPublisher: EventPublisher<DomainEvent>,
    private val clock: Clock,
) : EventConsumer<InvitationCreated> {

    private val log = LoggerFactory.getLogger(SendInvitationEmailConsumer::class.java)

    override suspend fun consume(event: InvitationCreated) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipient,
            workspaceName = event.workspaceName,
            acceptUrl = event.acceptUrl,
            locale = event.locale,
            rawToken = event.rawToken,
        )
    }

    suspend fun consume(event: InvitationResent) {
        dispatch(
            invitationId = event.invitationId,
            recipient = event.recipient,
            workspaceName = event.workspaceName,
            acceptUrl = event.acceptUrl,
            locale = event.locale,
            rawToken = event.rawToken,
        )
    }

    private suspend fun dispatch(
        invitationId: java.util.UUID,
        recipient: String,
        workspaceName: String,
        acceptUrl: String,
        locale: String?,
        rawToken: String,
    ) {
        val email = InvitationEmail(
            invitationId = invitationId,
            recipient = normalizedEmailFromRecipient(recipient),
            workspaceName = workspaceName,
            acceptUrl = acceptUrl,
            rawToken = rawToken,
            locale = locale,
        )
        val idempotencyKey = email.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Invitation email already dispatched for invitation '{}' — skipping",
                invitationId,
            )
            publishDeliveryAttempted(invitationId, "SENT")
            return
        }

        val now = Instant.now(clock)
        val pending = Notification(
            id = NotificationId.generate(),
            idempotencyKey = idempotencyKey,
            channel = NotificationChannel.EMAIL,
            recipient = Recipient(recipient),
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

        val result = emailDispatcher.dispatch(recipient, rendered)
        val now2 = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> persisted.markSent(now2)
            is EmailDispatchResult.Failure -> persisted.markFailed(now2, result.error)
        }
        notificationRepository.update(updated)

        val outcome = if (updated.status == NotificationStatus.SENT) "SENT" else "FAILED"
        publishDeliveryAttempted(invitationId, outcome)

        if (outcome == "FAILED") {
            log.error(
                "Failed to send invitation email to '{}' for invitation '{}': {}",
                recipient,
                invitationId,
                updated.errorMessage,
            )
        } else {
            log.info(
                "Invitation email dispatched to '{}' for invitation '{}'",
                recipient,
                invitationId,
            )
        }
    }

    private suspend fun publishDeliveryAttempted(invitationId: java.util.UUID, status: String) {
        deliveryEventPublisher.publish(InvitationDeliveryAttempted(invitationId = invitationId, status = status))
    }

    /**
     * The shared [com.profiletailors.notifications.domain.InvitationEmail] value object
     * accepts a normalised [com.profiletailors.leadcapture.common.NormalizedEmail]. The
     * events carry the recipient as a plain string; we promote it through
     * [com.profiletailors.leadcapture.common.EmailAddress] to keep a single normalisation
     * boundary inside the notifications module.
     */
    private fun normalizedEmailFromRecipient(value: String) =
        com.profiletailors.leadcapture.common.NormalizedEmail.from(
            com.profiletailors.leadcapture.common.EmailAddress(value),
        )
}

@Component
@Subscribe(filterBy = InvitationResent::class)
internal class SendInvitationResentEmailConsumer(private val delegate: SendInvitationEmailConsumer) :
    EventConsumer<InvitationResent> {
    override suspend fun consume(event: InvitationResent) {
        delegate.consume(event)
    }
}
