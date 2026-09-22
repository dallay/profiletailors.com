package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.RenderedEmail
import com.profiletailors.notifications.domain.event.NotificationRetryRequested
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Consumes [NotificationRetryRequested] events and dispatches the retry attempt created by
 * Back Office administrators.
 *
 * The retry row is recorded as PENDING by the admin handler before this event is published;
 * this consumer performs the actual delivery and marks the row SENT or FAILED so operators
 * can observe the outcome. Rows that are missing or already processed are skipped.
 */
@Component
@Subscribe(filterBy = NotificationRetryRequested::class)
internal class DispatchNotificationRetryConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) : EventConsumer<NotificationRetryRequested> {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun consume(event: NotificationRetryRequested) {
        val retry = notificationRepository.findById(NotificationId(event.retryNotificationId))
        if (retry == null) {
            log.warn("Notification retry '{}' not found - skipping dispatch", event.retryNotificationId)
            return
        }
        if (retry.status != NotificationStatus.PENDING) {
            log.info(
                "Notification retry '{}' already processed with status '{}' - skipping dispatch",
                event.retryNotificationId,
                retry.status,
            )
            return
        }

        val rendered = renderRetryEmail(retry)
        val result = emailDispatcher.dispatch(retry.recipient.value, rendered)
        val now = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> retry.markSent(now)
            is EmailDispatchResult.Failure -> retry.markFailed(now, result.error)
        }
        notificationRepository.update(updated)

        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to dispatch notification retry '{}': {}",
                event.retryNotificationId,
                updated.errorMessage,
            )
        } else {
            log.info("Dispatched notification retry '{}'", event.retryNotificationId)
        }
    }

    private fun renderRetryEmail(retry: Notification): RenderedEmail {
        val message = retry.payload["message"] ?: "A password recovery was requested for your account."
        val resetLink = retry.payload["resetLink"] ?: retry.payload["resetUrl"]
        val text = buildString {
            appendLine("Hi,")
            appendLine()
            appendLine(message)
            if (resetLink != null) {
                appendLine()
                appendLine("Reset link: $resetLink")
            }
            appendLine()
            append("— The Profile Tailors team")
        }
        return RenderedEmail(subject = "Password recovery", text = text, html = null)
    }
}
