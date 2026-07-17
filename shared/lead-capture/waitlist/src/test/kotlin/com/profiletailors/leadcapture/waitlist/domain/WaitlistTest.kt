package com.profiletailors.leadcapture.waitlist.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class WaitlistTest {

    @Test
    fun `new waitlist defaults to draft status`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
        )
        assertEquals(WaitlistStatus.DRAFT, waitlist.status)
    }

    @Test
    fun `waitlist can be activated from draft`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.DRAFT,
        )
        waitlist.activate()
        assertEquals(WaitlistStatus.ACTIVE, waitlist.status)
    }

    @Test
    fun `active waitlist can be paused`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        waitlist.pause()
        assertEquals(WaitlistStatus.PAUSED, waitlist.status)
    }

    @Test
    fun `paused waitlist can be reactivated`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.PAUSED,
        )
        waitlist.activate()
        assertEquals(WaitlistStatus.ACTIVE, waitlist.status)
    }

    @Test
    fun `waitlist can be closed`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        waitlist.close()
        assertEquals(WaitlistStatus.CLOSED, waitlist.status)
    }

    @Test
    fun `closed waitlist cannot be activated`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.CLOSED,
        )
        assertThrows<IllegalStateException> { waitlist.activate() }
    }

    @Test
    fun `archived waitlist is terminal`() {
        val waitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.CLOSED,
        )
        waitlist.archive()
        assertEquals(WaitlistStatus.ARCHIVED, waitlist.status)
        assertThrows<IllegalStateException> { waitlist.activate() }
        assertThrows<IllegalStateException> { waitlist.pause() }
        assertThrows<IllegalStateException> { waitlist.close() }
    }

    @Test
    fun `blank key is rejected`() {
        assertThrows<IllegalArgumentException> {
            Waitlist(
                id = WaitlistId("w-1"),
                key = WaitlistKey(""),
                name = "Profile Tailors Launch",
                context = "profile-tailors",
            )
        }
    }

    @Test
    fun `blank name is rejected`() {
        assertThrows<IllegalArgumentException> {
            Waitlist(
                id = WaitlistId("w-1"),
                key = WaitlistKey("profile-tailors-launch"),
                name = "",
                context = "profile-tailors",
            )
        }
    }
}
