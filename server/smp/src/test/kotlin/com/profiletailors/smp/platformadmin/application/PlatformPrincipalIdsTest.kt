package com.profiletailors.smp.platformadmin.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class PlatformPrincipalIdsTest {
    private val principalId = UUID.fromString("b5fdb517-3b51-4927-8e13-9e3cd728ba40")

    @Test
    fun `uuid conversion returns raw canonical identifier`() {
        assertEquals(principalId.toString(), PlatformPrincipalIds.fromUuid(principalId))
        assertEquals(principalId.toString(), PlatformPrincipalIds.fromUuid(principalId.toString().uppercase()))
        assertEquals(principalId, PlatformPrincipalIds.toUuid(principalId.toString()))
    }

    @Test
    fun `legacy user prefix is accepted without emitting it`() {
        assertEquals(principalId, PlatformPrincipalIds.toUuid("user-$principalId"))
        assertEquals(principalId.toString(), PlatformPrincipalIds.fromUuid("user-$principalId"))
    }

    @Test
    fun `tokens and semantic keys are not principal identifiers`() {
        listOf("valid-token", "e2e-admin", "role-owner", "platform.users.read").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PlatformPrincipalIds.toUuid(value) }
            assertThrows(IllegalArgumentException::class.java) { PlatformPrincipalIds.fromUuid(value) }
        }
    }
}
