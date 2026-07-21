package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.RenderedEmail
import com.profiletailors.notifications.domain.event.WaitlistEntryJoined
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class SendWelcomeEmailConsumerTest {

    private val fixedNow: Instant = Instant.parse("2026-07-20T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `dispatches welcome email and records SENT notification`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        val saved = slot<Notification>()
        coEvery { repository.save(capture(saved)) } answers { saved.captured }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendWelcomeEmailConsumer(dispatcher, repository, clock)
        consumer.consume(event())

        val pending = saved.captured
        assertEquals(NotificationStatus.PENDING, pending.status)
        assertEquals("user@example.com", pending.recipient.value)
        assertEquals("Profile Tailors Launch", pending.payload["waitlistName"])
        coVerify(exactly = 1) {
            dispatcher.dispatch("user@example.com", match { it.subject.contains("Welcome") && it.html != null })
        }
        coVerify(exactly = 1) { repository.update(match { it.status == NotificationStatus.SENT }) }
    }

    @Test
    fun `records FAILED notification when dispatcher returns failure`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Failure("smtp 5xx")

        val consumer = SendWelcomeEmailConsumer(dispatcher, repository, clock)
        consumer.consume(event())

        coVerify(exactly = 1) {
            repository.update(
                match {
                    it.status == NotificationStatus.FAILED && it.errorMessage == "smtp 5xx"
                },
            )
        }
    }

    @Test
    fun `skips dispatch when an entry with the same idempotency key already exists`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns mockk<Notification>(relaxed = true)

        val consumer = SendWelcomeEmailConsumer(dispatcher, repository, clock)
        consumer.consume(event())

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `computes idempotency key from the waitlist entry id`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val seenKey = slot<IdempotencyKey>()
        coEvery { repository.findByIdempotencyKey(capture(seenKey)) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendWelcomeEmailConsumer(dispatcher, repository, clock)
        consumer.consume(event(entryId = WaitlistEntryId("entry-42")))

        assertEquals("waitlist.welcome:entry-42", seenKey.captured.value)
    }

    @Test
    fun `subject is passed through to the dispatcher unchanged`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        val rendered = slot<RenderedEmail>()
        coEvery { dispatcher.dispatch(any(), capture(rendered)) } returns EmailDispatchResult.Success

        val consumer = SendWelcomeEmailConsumer(dispatcher, repository, clock)
        consumer.consume(event())

        assertNotNull(rendered.captured.html)
        assertTrue(rendered.captured.subject.startsWith("Welcome to the"))
    }

    private fun event(entryId: WaitlistEntryId = WaitlistEntryId("entry-1")): WaitlistEntryJoined = WaitlistEntryJoined(
        waitlistEntryId = entryId,
        waitlistKey = WaitlistKey("profile-tailors-launch"),
        waitlistName = "Profile Tailors Launch",
        normalizedEmail = "user@example.com",
        locale = "en",
    )
}
