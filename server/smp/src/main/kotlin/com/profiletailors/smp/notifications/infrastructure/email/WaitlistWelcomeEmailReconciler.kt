package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalUrlProvider
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.WelcomeEmail
import com.profiletailors.notifications.domain.WelcomeEmailTemplateId
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
internal class WaitlistWelcomeEmailReconciler(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val withdrawalUrlProvider: WaitlistWithdrawalUrlProvider,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(WaitlistWelcomeEmailReconciler::class.java)

    @Scheduled(
        fixedDelayString = "\${app.notifications.waitlist-welcome-recovery.interval:1m}",
        initialDelayString = "\${app.notifications.waitlist-welcome-recovery.initial-delay:1m}",
    )
    @Suppress("TooGenericExceptionCaught") // Each claimed notification must fail independently.
    suspend fun reconcile() {
        val now = Instant.now(clock)
        val notifications = notificationRepository.claimPending(
            templateId = WelcomeEmailTemplateId.INSTANCE,
            now = now,
            staleBefore = now.minus(STALE_AFTER),
            limit = BATCH_SIZE,
        )
        notifications.forEach { notification ->
            try {
                dispatch(notification, now)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                log.error("Failed to reconcile welcome notification '{}'", notification.id.value, failure)
                notificationRepository.update(
                    notification.markFailed(Instant.now(clock), failure.message ?: "Welcome email dispatch failed"),
                )
            }
        }
    }

    private suspend fun dispatch(notification: Notification, now: Instant) {
        val entryId = WelcomeEmail.entryIdFrom(notification.payload) ?: missingPayload(notification)
        val withdrawalUrl = withdrawalUrlProvider.urlFor(entryId, now)
        if (withdrawalUrl == null) {
            notificationRepository.update(notification.markPending(Instant.now(clock)))
            return
        }
        val welcome = WelcomeEmail.fromPayload(
            payload = notification.payload,
            recipient = NormalizedEmail.from(EmailAddress(notification.recipient.value)),
            withdrawalUrl = withdrawalUrl,
        ) ?: missingPayload(notification)
        val result = emailDispatcher.dispatch(notification.recipient.value, welcome.render())
        val updated = when (result) {
            is EmailDispatchResult.Success -> notification.markSent(Instant.now(clock))
            is EmailDispatchResult.Failure -> notification.markFailed(Instant.now(clock), result.error)
        }
        notificationRepository.update(updated)
        if (updated.status == NotificationStatus.FAILED) {
            log.error("Failed to reconcile welcome notification '{}'", notification.id.value)
        }
    }

    private fun missingPayload(notification: Notification): Nothing = throw MissingWelcomePayload(notification.id.value)

    private class MissingWelcomePayload(id: String) :
        IllegalStateException("Welcome notification payload is missing required data: $id")

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(5)
        const val BATCH_SIZE: Int = 20
    }
}
