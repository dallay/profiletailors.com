package com.profiletailors.leadcapture.waitlist.application

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class JoinResultTest {

    @Test
    fun `JOINED_NEW toString is Accepted`() {
        assertEquals("Accepted", JoinResult.JOINED_NEW.toString())
    }

    @Test
    fun `ALREADY_JOINED toString is Accepted`() {
        assertEquals("Accepted", JoinResult.ALREADY_JOINED.toString())
    }

    @Test
    fun `JOINED_NEW and ALREADY_JOINED are distinct`() {
        assertNotEquals(JoinResult.JOINED_NEW, JoinResult.ALREADY_JOINED)
    }
}
