@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.profiletailors.smp.notifications.infrastructure.events

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotification
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.notifications.domain.event.WaitlistEntryJoined
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class WaitlistEntryJoinedEventAdapterTest {

    private fun notification(
        entryId: String = "entry-123",
        email: String = "user@example.com",
        locale: CaptureLocale? = CaptureLocale("es"),
    ): WaitlistEntryJoinedNotification = WaitlistEntryJoinedNotification(
        waitlistEntryId = WaitlistEntryId(entryId),
        waitlistKey = WaitlistKey("profile-tailors-launch"),
        waitlistName = "Profile Tailors Launch",
        normalizedEmail = NormalizedEmail.from(EmailAddress(email)),
        locale = locale,
    )

    @Test
    fun `notify schedules a WaitlistEntryJoined event with the same fields as the notification`() = runTest {
        val publisher = mockk<EventPublisher<DomainEvent>>()
        val emitted = slot<WaitlistEntryJoined>()
        coEvery { publisher.publish(capture(emitted)) } returns Unit

        val adapter = WaitlistEntryJoinedEventAdapter(
            eventPublisher = publisher,
            publishScope = TestScope(testScheduler),
        )

        adapter.notify(notification())
        advanceUntilIdle()

        val event = emitted.captured
        assertEquals(WaitlistEntryId("entry-123"), event.waitlistEntryId)
        assertEquals(WaitlistKey("profile-tailors-launch"), event.waitlistKey)
        assertEquals("Profile Tailors Launch", event.waitlistName)
        assertEquals("user@example.com", event.normalizedEmail)
        assertEquals("es", event.locale)
    }

    @Test
    fun `notify translates null locale to null on the emitted event`() = runTest {
        val publisher = mockk<EventPublisher<DomainEvent>>()
        val emitted = slot<WaitlistEntryJoined>()
        coEvery { publisher.publish(capture(emitted)) } returns Unit

        val adapter = WaitlistEntryJoinedEventAdapter(
            eventPublisher = publisher,
            publishScope = TestScope(testScheduler),
        )

        adapter.notify(notification(entryId = "entry-no-locale", locale = null))
        advanceUntilIdle()

        assertEquals(null, emitted.captured.locale)
    }

    @Test
    fun `notify swallows exceptions thrown by the publisher and does not propagate`() = runTest {
        val publisher = mockk<EventPublisher<DomainEvent>>()
        val emitted = slot<WaitlistEntryJoined>()
        coEvery { publisher.publish(capture(emitted)) } throws RuntimeException("boom")

        val adapter = WaitlistEntryJoinedEventAdapter(
            eventPublisher = publisher,
            publishScope = TestScope(testScheduler),
        )

        // The adapter must not propagate the publisher failure synchronously.
        adapter.notify(notification())
        advanceUntilIdle()
        // If we reach this line without exception, the swallowing worked.
        assertNotNull(emitted, "publish was reached")
    }

    @Test
    fun `emitted event is a DomainEvent subclass of WaitlistEntryJoined`() = runTest {
        val publisher = mockk<EventPublisher<DomainEvent>>()
        val emitted = slot<DomainEvent>()
        coEvery { publisher.publish(capture(emitted)) } returns Unit

        val adapter = WaitlistEntryJoinedEventAdapter(
            eventPublisher = publisher,
            publishScope = TestScope(testScheduler),
        )

        adapter.notify(notification())
        advanceUntilIdle()

        val event = emitted.captured
        assertTrue(event is WaitlistEntryJoined)
    }
}
