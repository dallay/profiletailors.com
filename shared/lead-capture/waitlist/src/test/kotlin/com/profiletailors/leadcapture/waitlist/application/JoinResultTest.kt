package com.profiletailors.leadcapture.waitlist.application

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

internal class JoinResultTest {

    @Test
    fun `JOINED_NEW toString is uniform Accepted`() {
        assertEquals("Accepted", JoinResult.JOINED_NEW.toString())
    }

    @Test
    fun `ALREADY_JOINED toString is uniform Accepted`() {
        assertEquals("Accepted", JoinResult.ALREADY_JOINED.toString())
    }

    @Test
    fun `both canonical results render the same public string`() {
        assertEquals(JoinResult.JOINED_NEW.toString(), JoinResult.ALREADY_JOINED.toString())
    }

    @Test
    fun `JOINED_NEW and ALREADY_JOINED are distinct internally`() {
        assertNotEquals(JoinResult.JOINED_NEW, JoinResult.ALREADY_JOINED)
    }

    @Test
    fun `enum values are singletons`() {
        assertSame(JoinResult.JOINED_NEW, JoinResult.JOINED_NEW)
        assertSame(JoinResult.ALREADY_JOINED, JoinResult.ALREADY_JOINED)
    }

    @Test
    fun `equality is based on distinction not identity`() {
        assertEquals(JoinResult.JOINED_NEW, JoinResult.JOINED_NEW)
        assertEquals(JoinResult.JOINED_NEW.hashCode(), JoinResult.JOINED_NEW.hashCode())
    }
}
