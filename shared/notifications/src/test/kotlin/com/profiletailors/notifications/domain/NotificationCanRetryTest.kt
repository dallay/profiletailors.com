package com.profiletailors.notifications.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NotificationCanRetryTest {

    private val now = Instant.parse("2026-07-20T10:00:00Z")

    private fun createNotification(
        templateId: TemplateId = TemplateId("platform.password-recovery"),
        status: NotificationStatus = NotificationStatus.FAILED,
        failedAt: Instant? = now.minusSeconds(60),
        errorMessage: String? = "smtp error",
    ): Notification = Notification(
        id = NotificationId.generate(),
        idempotencyKey = IdempotencyKey("idem-key-${System.nanoTime()}"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("test@example.com"),
        templateId = templateId,
        payload = NotificationPayload(emptyMap()),
        status = status,
        sentAt = null,
        failedAt = failedAt,
        errorMessage = errorMessage,
        createdAt = now.minusSeconds(300),
        updatedAt = failedAt ?: now,
    )

    @Test
    fun `canRetry returns true for failed password-recovery notification`() {
        val notification = createNotification(
            templateId = TemplateId("platform.password-recovery"),
            status = NotificationStatus.FAILED,
            failedAt = now.minusSeconds(60),
            errorMessage = "smtp error",
        )
        assertTrue(notification.canRetry())
    }

    @Test
    fun `canRetry returns true for failed password-reset notification`() {
        val notification = createNotification(
            templateId = TemplateId("platform.password-reset-v2"),
            status = NotificationStatus.FAILED,
            failedAt = now.minusSeconds(120),
            errorMessage = "connection timeout",
        )
        assertTrue(notification.canRetry())
    }

    @Test
    fun `canRetry returns false for failed invitation notification`() {
        val notification = createNotification(
            templateId = TemplateId("platform.invitation"),
            status = NotificationStatus.FAILED,
            failedAt = now.minusSeconds(60),
            errorMessage = "smtp error",
        )
        assertFalse(notification.canRetry())
    }

    @Test
    fun `canRetry returns false for failed waitlist-invitation notification`() {
        val notification = createNotification(
            templateId = TemplateId("platform.waitlist-invitation"),
            status = NotificationStatus.FAILED,
            failedAt = now.minusSeconds(60),
            errorMessage = "smtp error",
        )
        assertFalse(notification.canRetry())
    }

    @Test
    fun `canRetry returns false for pending notification`() {
        val notification = createNotification(
            templateId = TemplateId("platform.password-recovery"),
            status = NotificationStatus.PENDING,
            failedAt = null,
            errorMessage = null,
        )
        assertFalse(notification.canRetry())
    }

    @Test
    fun `canRetry returns false for sent notification`() {
        val notification = createNotification(
            templateId = TemplateId("platform.password-recovery"),
            status = NotificationStatus.SENT,
            failedAt = null,
            errorMessage = null,
        ).markSent(now.minusSeconds(30))
        assertFalse(notification.canRetry())
    }

    @Test
    fun `canRetry returns false for failed notification with ineligible template`() {
        val notification = createNotification(
            templateId = TemplateId("platform.welcome"),
            status = NotificationStatus.FAILED,
            failedAt = now.minusSeconds(60),
            errorMessage = "smtp error",
        )
        assertFalse(notification.canRetry())
    }
}
