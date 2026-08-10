package com.profiletailors.smp.governance.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.smp.governance.application.PrincipalIdentityPort
import com.profiletailors.smp.governance.domain.event.TakedownApproved
import com.profiletailors.smp.governance.domain.event.TakedownRejected
import com.profiletailors.smp.governance.domain.event.TakedownReported
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Sends a "new takedown report" email to every workspace owner so they can review it.
 *
 * Idempotency is enforced per (reportId, recipient): if a notification with the same
 * idempotency key already exists, dispatch is skipped.
 */
@Component
@Subscribe(filterBy = TakedownReported::class)
internal class SendTakedownReportedEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val workspaceOwnershipRepository: WorkspaceOwnershipRepository,
    private val principalIdentityPort: PrincipalIdentityPort,
    private val clock: Clock,
) : EventConsumer<TakedownReported> {

    private val log = LoggerFactory.getLogger(SendTakedownReportedEmailConsumer::class.java)

    override suspend fun consume(event: TakedownReported) {
        val ownerPrincipalIds = workspaceOwnershipRepository.findOwnerIds(event.workspaceId)
        if (ownerPrincipalIds.isEmpty()) {
            log.warn(
                "No workspace owners found for workspace '{}'; skipping takedown reported email for report '{}'",
                event.workspaceId,
                event.reportId,
            )
            return
        }

        for (ownerPrincipalId in ownerPrincipalIds) {
            sendToOwner(event, ownerPrincipalId)
        }
    }

    private suspend fun sendToOwner(event: TakedownReported, ownerPrincipalId: String) {
        val recipient = principalIdentityPort.findEmailByPrincipalId(ownerPrincipalId)
        if (recipient.isNullOrBlank()) {
            log.warn(
                "Could not resolve email for owner '{}' on workspace '{}'; skipping",
                ownerPrincipalId,
                event.workspaceId,
            )
            return
        }

        val email = TakedownReportedEmail(
            reportId = event.reportId,
            recipient = recipient,
            assetId = event.assetId,
            reason = event.reason,
            reporterEmail = event.reporterEmail,
            mediaReferenceUrl = event.mediaReferenceUrl,
        )
        val idempotencyKey = email.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Takedown reported email for report '{}' to '{}' already dispatched — skipping",
                event.reportId,
                recipient,
            )
            return
        }

        dispatchAndLog(email, recipient)
    }

    private suspend fun dispatchAndLog(email: TakedownReportedEmail, recipient: String) {
        val now = Instant.now(clock)
        val pending = Notification(
            id = NotificationId.generate(),
            idempotencyKey = email.idempotencyKey(),
            channel = NotificationChannel.EMAIL,
            recipient = Recipient(recipient),
            templateId = TakedownReportedEmailTemplateId.INSTANCE,
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

        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to dispatch takedown reported email for report '{}' to '{}': {}",
                email.reportId,
                recipient,
                updated.errorMessage,
            )
        } else {
            log.info(
                "Dispatched takedown reported email for report '{}' to '{}'",
                email.reportId,
                recipient,
            )
        }
    }
}

/**
 * Sends a "your report was approved" email to the original reporter.
 */
@Component
@Subscribe(filterBy = TakedownApproved::class)
internal class SendTakedownApprovedEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) : EventConsumer<TakedownApproved> {

    private val log = LoggerFactory.getLogger(SendTakedownApprovedEmailConsumer::class.java)

    override suspend fun consume(event: TakedownApproved) {
        val email = TakedownApprovedEmail(
            reportId = event.reportId,
            recipient = event.reporterEmail,
            assetId = event.assetId,
        )
        val idempotencyKey = email.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Takedown approved email for report '{}' already dispatched — skipping",
                event.reportId,
            )
            return
        }

        val now = Instant.now(clock)
        val pending = Notification(
            id = NotificationId.generate(),
            idempotencyKey = idempotencyKey,
            channel = NotificationChannel.EMAIL,
            recipient = Recipient(email.recipient),
            templateId = TakedownApprovedEmailTemplateId.INSTANCE,
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

        val result = emailDispatcher.dispatch(email.recipient, rendered)
        val now2 = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> persisted.markSent(now2)
            is EmailDispatchResult.Failure -> persisted.markFailed(now2, result.error)
        }
        notificationRepository.update(updated)

        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to dispatch takedown approved email for report '{}': {}",
                event.reportId,
                updated.errorMessage,
            )
        } else {
            log.info("Dispatched takedown approved email for report '{}'", event.reportId)
        }
    }
}

/**
 * Sends a "your report was rejected" email to the original reporter.
 */
@Component
@Subscribe(filterBy = TakedownRejected::class)
internal class SendTakedownRejectedEmailConsumer(
    private val emailDispatcher: EmailDispatcher,
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) : EventConsumer<TakedownRejected> {

    private val log = LoggerFactory.getLogger(SendTakedownRejectedEmailConsumer::class.java)

    override suspend fun consume(event: TakedownRejected) {
        val email = TakedownRejectedEmail(
            reportId = event.reportId,
            recipient = event.reporterEmail,
            assetId = event.assetId,
            rejectionReason = event.rejectionReason,
        )
        val idempotencyKey = email.idempotencyKey()

        if (notificationRepository.findByIdempotencyKey(idempotencyKey) != null) {
            log.info(
                "Takedown rejected email for report '{}' already dispatched — skipping",
                event.reportId,
            )
            return
        }

        val now = Instant.now(clock)
        val pending = Notification(
            id = NotificationId.generate(),
            idempotencyKey = idempotencyKey,
            channel = NotificationChannel.EMAIL,
            recipient = Recipient(email.recipient),
            templateId = TakedownRejectedEmailTemplateId.INSTANCE,
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

        val result = emailDispatcher.dispatch(email.recipient, rendered)
        val now2 = Instant.now(clock)
        val updated = when (result) {
            is EmailDispatchResult.Success -> persisted.markSent(now2)
            is EmailDispatchResult.Failure -> persisted.markFailed(now2, result.error)
        }
        notificationRepository.update(updated)

        if (updated.status == NotificationStatus.FAILED) {
            log.error(
                "Failed to dispatch takedown rejected email for report '{}': {}",
                event.reportId,
                updated.errorMessage,
            )
        } else {
            log.info("Dispatched takedown rejected email for report '{}'", event.reportId)
        }
    }
}
