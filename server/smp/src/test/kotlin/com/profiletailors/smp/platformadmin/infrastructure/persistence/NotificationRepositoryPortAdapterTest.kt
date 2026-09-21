package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import com.profiletailors.smp.platformadmin.domain.NotificationDispatchException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals

internal class NotificationRepositoryPortAdapterTest {

    private val delegate = mockk<NotificationRepository>()
    private val adapter = NotificationRepositoryPortAdapter(delegate)

    @Test
    fun `save delegates to repository on success`() = runTest {
        val notification = pendingNotification()
        coEvery { delegate.save(notification) } returns notification

        val result = adapter.save(notification)

        assertEquals(notification, result)
        coVerify(exactly = 1) { delegate.save(notification) }
    }

    @Test
    fun `save translates persistence failure to dispatch exception`() = runTest {
        val notification = pendingNotification()
        val cause = mockk<R2dbcException>()
        coEvery { delegate.save(notification) } throws cause

        assertThrows<NotificationDispatchException> {
            adapter.save(notification)
        }
    }

    private fun pendingNotification(): Notification = Notification(
        id = NotificationId("ntf-1"),
        idempotencyKey = IdempotencyKey("retry-key-1"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("user@example.com"),
        templateId = TemplateId("platform.password-recovery"),
        payload = NotificationPayload(mapOf("message" to "Reset your password")),
        status = NotificationStatus.PENDING,
        sentAt = null,
        failedAt = null,
        errorMessage = null,
        createdAt = Instant.parse("2026-09-20T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-20T10:00:00Z"),
    )
}
