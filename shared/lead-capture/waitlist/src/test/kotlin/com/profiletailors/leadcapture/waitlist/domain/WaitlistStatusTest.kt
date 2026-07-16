package com.profiletailors.leadcapture.waitlist.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class WaitlistStatusTest {

    @Test
    fun `active status accepts entries`() {
        assertTrue(WaitlistStatus.ACTIVE.acceptsEntries())
    }

    @Test
    fun `paused status does not accept entries`() {
        assertFalse(WaitlistStatus.PAUSED.acceptsEntries())
    }

    @Test
    fun `closed status does not accept entries`() {
        assertFalse(WaitlistStatus.CLOSED.acceptsEntries())
    }

    @Test
    fun `draft status does not accept entries`() {
        assertFalse(WaitlistStatus.DRAFT.acceptsEntries())
    }

    @Test
    fun `archived status does not accept entries`() {
        assertFalse(WaitlistStatus.ARCHIVED.acceptsEntries())
    }

    @Test
    fun `only paused and closed and archived signal waitlist_closed`() {
        assertTrue(WaitlistStatus.PAUSED.isClosedSignal())
        assertTrue(WaitlistStatus.CLOSED.isClosedSignal())
        assertTrue(WaitlistStatus.ARCHIVED.isClosedSignal())
        assertFalse(WaitlistStatus.ACTIVE.isClosedSignal())
        assertFalse(WaitlistStatus.DRAFT.isClosedSignal())
    }
}
