@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.profiletailors.smp.notifications.infrastructure.events

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotification
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.notifications.domain.event.WaitlistEntryJoined
import com.profiletailors.spring.boot.bus.event.EventEmitter
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val publisher = mockk<EventEmitter<DomainEvent>>()
        val emitted = slot<WaitlistEntryJoined>()
        coEvery { publisher.publish(capture(emitted)) } returns Unit

        val adapter = WaitlistEntryJoinedEventAdapter(eventEmitter = publisher)

        adapter.notify(notification())

        val event = emitted.captured
        assertEquals(WaitlistEntryId("entry-123"), event.waitlistEntryId)
        assertEquals(WaitlistKey("profile-tailors-launch"), event.waitlistKey)
        assertEquals("Profile Tailors Launch", event.waitlistName)
        assertEquals("user@example.com", event.normalizedEmail)
        assertEquals("es", event.locale)
    }

    @Test
    fun `notify translates null locale to null on the emitted event`() = runTest {
        val publisher = mockk<EventEmitter<DomainEvent>>()
        val emitted = slot<WaitlistEntryJoined>()
        coEvery { publisher.publish(capture(emitted)) } returns Unit

        val adapter = WaitlistEntryJoinedEventAdapter(eventEmitter = publisher)

        adapter.notify(notification(entryId = "entry-no-locale", locale = null))

        assertEquals(null, emitted.captured.locale)
    }

    @Test
    fun `notify propagates publisher failures`() = runTest {
        val publisher = mockk<EventEmitter<DomainEvent>>()
        coEvery { publisher.publish(any<DomainEvent>()) } throws RuntimeException("boom")
        val adapter = WaitlistEntryJoinedEventAdapter(eventEmitter = publisher)

        val error = assertFailsWith<RuntimeException> {
            adapter.notify(notification())
        }

        assertEquals("boom", error.message)
    }

    @Test
    fun `emitted event is a DomainEvent subclass of WaitlistEntryJoined`() = runTest {
        val publisher = mockk<EventEmitter<DomainEvent>>()
        val emitted = slot<DomainEvent>()
        coEvery { publisher.publish(capture(emitted)) } returns Unit

        val adapter = WaitlistEntryJoinedEventAdapter(eventEmitter = publisher)

        adapter.notify(notification())

        val event = emitted.captured
        assertTrue(event is WaitlistEntryJoined)
    }
}
