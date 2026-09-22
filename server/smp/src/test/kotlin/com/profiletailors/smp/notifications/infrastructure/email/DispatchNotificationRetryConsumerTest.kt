package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import com.profiletailors.notifications.domain.event.NotificationRetryRequested
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

internal class DispatchNotificationRetryConsumerTest {

    private val fixedNow: Instant = Instant.parse("2026-09-20T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `dispatches pending retry and marks notification SENT`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val pending = pendingRetry()
        coEvery { repository.findById(NotificationId("ntf-retry-1")) } returns pending
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = DispatchNotificationRetryConsumer(dispatcher, repository, clock)
        consumer.consume(NotificationRetryRequested("ntf-retry-1", "retry-key-1"))

        coVerify(exactly = 1) {
            dispatcher.dispatch(
                "user@example.com",
                match {
                    it.subject.contains("Password recovery") &&
                        it.text.contains("https://example.com/reset?token=abc123")
                },
            )
        }
        coVerify(exactly = 1) { repository.update(match { it.status == NotificationStatus.SENT }) }
    }

    @Test
    fun `marks notification FAILED when dispatcher returns failure`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findById(NotificationId("ntf-retry-1")) } returns pendingRetry()
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Failure("smtp 5xx")

        val consumer = DispatchNotificationRetryConsumer(dispatcher, repository, clock)
        consumer.consume(NotificationRetryRequested("ntf-retry-1", "retry-key-1"))

        val updated = slot<Notification>()
        coVerify(exactly = 1) { repository.update(capture(updated)) }
        assertEquals(NotificationStatus.FAILED, updated.captured.status)
        assertEquals("smtp 5xx", updated.captured.errorMessage)
    }

    @Test
    fun `skips dispatch when retry notification is missing`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findById(any()) } returns null

        val consumer = DispatchNotificationRetryConsumer(dispatcher, repository, clock)
        consumer.consume(NotificationRetryRequested("ntf-missing", "retry-key-1"))

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `skips dispatch when retry was already processed`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findById(NotificationId("ntf-retry-1")) } returns pendingRetry().markSent(fixedNow)

        val consumer = DispatchNotificationRetryConsumer(dispatcher, repository, clock)
        consumer.consume(NotificationRetryRequested("ntf-retry-1", "retry-key-1"))

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `rendered retry email contains recovery message`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findById(NotificationId("ntf-retry-1")) } returns pendingRetry()
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = DispatchNotificationRetryConsumer(dispatcher, repository, clock)
        consumer.consume(NotificationRetryRequested("ntf-retry-1", "retry-key-1"))

        coVerify(exactly = 1) {
            dispatcher.dispatch(any(), match { it.text.contains("Reset your password") })
        }
    }

    private fun pendingRetry(): Notification = Notification(
        id = NotificationId("ntf-retry-1"),
        idempotencyKey = IdempotencyKey("retry-key-1"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("user@example.com"),
        templateId = TemplateId("platform.password-recovery"),
        payload = NotificationPayload(
            mapOf(
                "message" to "Reset your password",
                "resetLink" to "https://example.com/reset?token=abc123",
            ),
        ),
        status = NotificationStatus.PENDING,
        sentAt = null,
        failedAt = null,
        errorMessage = null,
        createdAt = fixedNow,
        updatedAt = fixedNow,
    )
}
