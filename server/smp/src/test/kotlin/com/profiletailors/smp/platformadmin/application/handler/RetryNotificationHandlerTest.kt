package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import com.profiletailors.smp.platformadmin.application.command.RetryNotificationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.NotificationEventPublisher
import com.profiletailors.smp.platformadmin.application.contracts.NotificationRepositoryPort
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.NotificationDispatchException
import com.profiletailors.smp.platformadmin.domain.NotificationNotFoundForRetryException
import com.profiletailors.smp.platformadmin.domain.NotificationNotRetryableException
import com.profiletailors.smp.platformadmin.domain.NotificationRetryConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal class RetryNotificationHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneId.systemDefault())
    private val operatorId = UUID.randomUUID()
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)

    private val notificationRepository = mockk<NotificationRepositoryPort>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val eventPublisher = mockk<NotificationEventPublisher>(relaxed = true)

    private val handler = RetryNotificationHandler(
        notificationRepository = notificationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        clock = fixedClock,
    )

    private fun createFailedPasswordRecoveryNotification(): Notification = Notification(
        id = NotificationId("ntf-123"),
        idempotencyKey = com.profiletailors.notifications.domain.IdempotencyKey("original-key"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("user@example.com"),
        templateId = TemplateId("platform.password-recovery"),
        payload = NotificationPayload(mapOf("token" to "secret", "userId" to "123")),
        status = NotificationStatus.FAILED,
        sentAt = null,
        failedAt = Instant.parse("2026-09-20T09:00:00Z"),
        errorMessage = "smtp error",
        createdAt = Instant.parse("2026-09-20T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-20T09:00:00Z"),
    )

    private fun createFailedInvitationNotification(): Notification = Notification(
        id = NotificationId("ntf-456"),
        idempotencyKey = com.profiletailors.notifications.domain.IdempotencyKey("invitation-key"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("user@example.com"),
        templateId = TemplateId("platform.invitation"),
        payload = NotificationPayload(mapOf("acceptUrl" to "http://example.com/accept")),
        status = NotificationStatus.FAILED,
        sentAt = null,
        failedAt = Instant.parse("2026-09-20T09:00:00Z"),
        errorMessage = "smtp error",
        createdAt = Instant.parse("2026-09-20T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-20T09:00:00Z"),
    )

    @Test
    fun `handle throws PlatformAccessDeniedException when operator lacks permission`() = runTest {
        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.AUDITOR),
            notificationId = NotificationId("ntf-123"),
        )

        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws NotificationNotFoundForRetryException when notification not found`() = runTest {
        coEvery { notificationRepository.findById(any()) } returns null

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-unknown"),
        )

        assertThrows<NotificationNotFoundForRetryException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws NotificationNotRetryableException for invitation template`() = runTest {
        val notification = createFailedInvitationNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-456")) } returns notification

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-456"),
        )

        assertThrows<NotificationNotRetryableException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle creates retry notification for eligible notification`() = runTest {
        val existing = createFailedPasswordRecoveryNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-123")) } returns existing
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(any()) } coAnswers { firstArg() }

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-123"),
        )

        val result = handler.handle(command)

        assert(result.channel == "EMAIL")
        assert(result.templateId == "platform.password-recovery")
        assert(result.recipient == "user@example.com")
        assert(result.status == "PENDING")
        assert(result.id != "ntf-123")

        coVerify { notificationRepository.save(any()) }
    }

    @Test
    fun `handle publishes audit event with correct metadata`() = runTest {
        val existing = createFailedPasswordRecoveryNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-123")) } returns existing
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(any()) } coAnswers { firstArg() }

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-123"),
        )

        handler.handle(command)

        val auditEventSlot = slot<AdminAuditEvent>()
        coVerify { auditPublisher.publish(capture(auditEventSlot)) }

        val captured = auditEventSlot.captured
        assert(captured.action.name == "NOTIFICATION_RETRIED")
        assert(captured.targetType == "NOTIFICATION")
        assert(captured.targetId == "ntf-123")
        assert(captured.result.name == "SUCCEEDED")
    }

    @Test
    fun `handle publishes REJECTED audit when template is ineligible`() = runTest {
        val notification = createFailedInvitationNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-456")) } returns notification

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-456"),
        )

        assertThrows<NotificationNotRetryableException> {
            handler.handle(command)
        }

        val auditEventSlot = slot<AdminAuditEvent>()
        coVerify { auditPublisher.publish(capture(auditEventSlot)) }

        val captured = auditEventSlot.captured
        assert(captured.action.name == "NOTIFICATION_RETRIED")
        assert(captured.targetId == "ntf-456")
        assert(captured.result.name == "REJECTED")
        assert(captured.metadata["retryOutcome"] == "REJECTED")
        assert(captured.metadata["templateId"] == "platform.invitation")
        coVerify(exactly = 0) { notificationRepository.save(any()) }
    }

    @Test
    fun `handle throws conflict when idempotency key was already used`() = runTest {
        val existing = createFailedPasswordRecoveryNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-123")) } returns existing
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns existing

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-123"),
            idempotencyKey = "already-used-key",
        )

        assertThrows<NotificationRetryConflictException> {
            handler.handle(command)
        }

        coVerify(exactly = 0) { notificationRepository.save(any()) }
    }

    @Test
    fun `handle publishes FAILED audit when persistence fails`() = runTest {
        val existing = createFailedPasswordRecoveryNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-123")) } returns existing
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(any()) } throws
            NotificationDispatchException("retry-key", RuntimeException("connection reset"))

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-123"),
        )

        assertThrows<NotificationDispatchException> {
            handler.handle(command)
        }

        val auditEventSlot = slot<AdminAuditEvent>()
        coVerify { auditPublisher.publish(capture(auditEventSlot)) }

        val captured = auditEventSlot.captured
        assert(captured.action.name == "NOTIFICATION_RETRIED")
        assert(captured.result.name == "FAILED")
        assert(captured.metadata["retryOutcome"] == "DISPATCH_FAILED")
    }

    @Test
    fun `handle redacts sensitive priorError in audit metadata`() = runTest {
        val existing = createFailedPasswordRecoveryNotification().copy(
            errorMessage = "Failed to send email: authentication token expired",
        )
        coEvery { notificationRepository.findById(NotificationId("ntf-123")) } returns existing
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(any()) } coAnswers { firstArg() }

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-123"),
        )

        handler.handle(command)

        val auditEventSlot = slot<AdminAuditEvent>()
        coVerify { auditPublisher.publish(capture(auditEventSlot)) }

        assert(auditEventSlot.captured.metadata["priorError"] == "[REDACTED]")
    }

    @Test
    fun `handle publishes retry event and SUCCESS outcome metadata`() = runTest {
        val existing = createFailedPasswordRecoveryNotification()
        coEvery { notificationRepository.findById(NotificationId("ntf-123")) } returns existing
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(any()) } coAnswers { firstArg() }

        val command = RetryNotificationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = operatorRoles,
            notificationId = NotificationId("ntf-123"),
        )

        val result = handler.handle(command)

        coVerify { eventPublisher.publish(any()) }

        val auditEventSlot = slot<AdminAuditEvent>()
        coVerify { auditPublisher.publish(capture(auditEventSlot)) }

        val captured = auditEventSlot.captured
        assert(captured.metadata["retryOutcome"] == "SUCCESS")
        assert(captured.metadata["notificationId"] == "ntf-123")
        assert(captured.metadata["retryNotificationId"] == result.id)
    }
}
