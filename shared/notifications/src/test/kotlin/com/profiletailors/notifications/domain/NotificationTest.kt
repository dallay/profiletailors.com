package com.profiletailors.notifications.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal class NotificationTest {

    private val now = Instant.parse("2026-07-20T10:00:00Z")
    private val pending =
        sample(status = NotificationStatus.PENDING, sentAt = null, failedAt = null, errorMessage = null)

    @Test
    fun `validates invariants in init`() {
        assertFailsWith<IllegalArgumentException> {
            sample(
                status = NotificationStatus.SENT,
                sentAt = now,
                failedAt = null,
                errorMessage = "previous failure",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            sample(
                status = NotificationStatus.FAILED,
                sentAt = null,
                failedAt = null,
                errorMessage = null,
            )
        }
    }

    @Test
    fun `markSent transitions to SENT with sentAt and clears error fields`() {
        val sentAt = now.plusSeconds(5)
        val updated = pending.markSent(sentAt)
        assertEquals(NotificationStatus.SENT, updated.status)
        assertEquals(sentAt, updated.sentAt)
        assertNull(updated.failedAt)
        assertNull(updated.errorMessage)
        assertEquals(sentAt, updated.updatedAt)
    }

    @Test
    fun `markFailed transitions to FAILED with failedAt and error message`() {
        val failedAt = now.plusSeconds(5)
        val updated = pending.markFailed(failedAt, "smtp 5xx")
        assertEquals(NotificationStatus.FAILED, updated.status)
        assertEquals(failedAt, updated.failedAt)
        assertEquals("smtp 5xx", updated.errorMessage)
        assertNull(updated.sentAt)
        assertEquals(failedAt, updated.updatedAt)
    }

    @Test
    fun `NotificationId rejects blank values`() {
        assertFailsWith<IllegalArgumentException> { NotificationId("") }
        assertFailsWith<IllegalArgumentException> { NotificationId("   ") }
    }

    @Test
    fun `NotificationId generate produces a non-blank unique value`() {
        val first = NotificationId.generate().value
        val second = NotificationId.generate().value
        assert(first.startsWith("ntf-"))
        assert(first != second)
    }

    @Test
    fun `NotificationPayload rejects blank keys`() {
        assertFailsWith<IllegalArgumentException> {
            NotificationPayload(mapOf("" to "value"))
        }
    }

    @Test
    fun `NotificationPayload empty is empty`() {
        assertEquals(emptyMap(), NotificationPayload.EMPTY.variables)
    }

    private fun sample(
        status: NotificationStatus,
        sentAt: Instant?,
        failedAt: Instant?,
        errorMessage: String?,
    ): Notification = Notification(
        id = NotificationId.generate(),
        idempotencyKey = IdempotencyKey("test:key"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("user@example.com"),
        templateId = TemplateId("test.template"),
        payload = NotificationPayload(mapOf("a" to "1")),
        status = status,
        sentAt = sentAt,
        failedAt = failedAt,
        errorMessage = errorMessage,
        createdAt = now,
        updatedAt = now,
    )
}
