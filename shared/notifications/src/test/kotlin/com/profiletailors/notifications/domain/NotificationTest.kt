package com.profiletailors.notifications.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
    fun `NotificationId preserves canonical UUID values`() {
        val value = "01234567-89ab-cdef-0123-456789abcdef"
        assertEquals(value, NotificationId(value).value)
    }

    @Test
    fun `NotificationId generates canonical UUID values without a prefix`() {
        val value = NotificationId.generate().value
        assertEquals(UUID.fromString(value).toString(), value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "ntf-01234567-89ab-cdef-0123-456789abcdef",
            "not-a-uuid",
            "1-1-1-1-1",
            "0123456789abcdef0123456789abcdef",
            "01234567-89AB-CDEF-0123-456789ABCDEF",
            " 01234567-89ab-cdef-0123-456789abcdef",
            "01234567-89ab-cdef-0123-456789abcdef ",
            "01234567-89ab-cdef-0123-456789abcdeg",
        ],
    )
    fun `NotificationId rejects noncanonical UUID values`(value: String) {
        assertFailsWith<IllegalArgumentException> { NotificationId(value) }
    }

    @Test
    fun `NotificationId generate produces unique values`() {
        val first = NotificationId.generate().value
        val second = NotificationId.generate().value
        assertNotEquals(first, second)
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
