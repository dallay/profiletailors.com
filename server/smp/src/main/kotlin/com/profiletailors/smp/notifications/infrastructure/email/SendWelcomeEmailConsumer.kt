package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.WelcomeEmail
import com.profiletailors.notifications.domain.WelcomeEmailTemplateId
import com.profiletailors.notifications.domain.event.WaitlistEntryJoined
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Consumes [WaitlistEntryJoined] events and dispatches a welcome email to the joiner.
 *
 * The handler enforces idempotency by recording the attempted dispatch in the
 * [NotificationRepository] and refusing to re-send if a record with the same
 * [com.profiletailors.notifications.domain.IdempotencyKey] already exists.
 *
 * If the email dispatcher returns a failure the notification is recorded with status
 * FAILED so operators can inspect and retry.
 */
@Component
@Subscribe(filterBy = WaitlistEntryJoined::class)
internal class SendWelcomeEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) : EventConsumer<WaitlistEntryJoined> {

    private val log = LoggerFactory.getLogger(SendWelcomeEmailConsumer::class.java)

    override suspend fun consume(event: WaitlistEntryJoined) {
        val welcome = WelcomeEmail(
            waitlistEntryId = event.waitlistEntryId,
            recipient = NormalizedEmail.from(EmailAddress(event.normalizedEmail)),
            waitlistName = event.waitlistName,
            locale = event.locale,
        )
        val idempotencyKey = welcome.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Welcome email already dispatched for entry '{}' on waitlist '{}' — skipping",
                event.waitlistEntryId.value,
                event.waitlistKey.value,
            )
            return
        }

        val now = Instant.now(clock)
        val pending = Notification(
            id = NotificationId.generate(),
            idempotencyKey = idempotencyKey,
            channel = NotificationChannel.EMAIL,
            recipient = Recipient(event.normalizedEmail),
            templateId = WelcomeEmailTemplateId.INSTANCE,
            payload = welcome.toPayload(),
            status = NotificationStatus.PENDING,
            sentAt = null,
            failedAt = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )
        val rendered = welcome.render()
        val persisted = notificationRepository.save(pending)

        val result = emailDispatcher.dispatch(event.normalizedEmail, rendered)
        val now2 = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> persisted.markSent(now2)
            is EmailDispatchResult.Failure -> persisted.markFailed(now2, result.error)
        }
        notificationRepository.update(updated)

        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to send welcome email to '{}' for entry '{}' on waitlist '{}': {}",
                event.normalizedEmail,
                event.waitlistEntryId.value,
                event.waitlistKey.value,
                updated.errorMessage,
            )
        } else {
            log.info(
                "Welcome email dispatched to '{}' for entry '{}' on waitlist '{}'",
                event.normalizedEmail,
                event.waitlistEntryId.value,
                event.waitlistKey.value,
            )
        }
    }
}
