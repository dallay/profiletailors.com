package com.profiletailors.smp.platformadmin.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class PlatformPrincipalIdsTest {
    private val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `fromUuid prefixes a bare uuid`() {
        assertEquals("user-$uuid", PlatformPrincipalIds.fromUuid(uuid))
    }

    @Test
    fun `fromUuid keeps an already prefixed principal id`() {
        assertEquals("user-$uuid", PlatformPrincipalIds.fromUuid("user-$uuid"))
    }

    @Test
    fun `fromUuid never double prefixes`() {
        val once = PlatformPrincipalIds.fromUuid(uuid.toString())

        assertEquals("user-$uuid", once)
        assertFalse(once.startsWith("user-user-"))
        assertEquals(once, PlatformPrincipalIds.fromUuid(once))
    }

    @Test
    fun `toUuid strips the prefix and parses bare uuids`() {
        assertEquals(uuid, PlatformPrincipalIds.toUuid("user-$uuid"))
        assertEquals(uuid, PlatformPrincipalIds.toUuid(uuid.toString()))
    }
}
