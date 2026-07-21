package com.profiletailors.smp.authorization.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PermissionKeyTest {

    @Test
    fun `creates explicit permission key using domain resource action format`() {
        val permissionKey = PermissionKey.of("workspace", "access", "read")

        assertEquals("workspace:access:read", permissionKey.value)
    }

    @Test
    fun `rejects malformed permission identifiers`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace.read")
        }

        assertEquals("Permission keys must use the format <domain>:<resource>:<action>.", error.message)
    }

    @Test
    fun `creates permission key with dash in action segment`() {
        val permissionKey = PermissionKey.of("workspace", "governance", "media-read")

        assertEquals("workspace:governance:media-read", permissionKey.value)
    }

    @Test
    fun `creates permission key with dash in resource segment`() {
        val permissionKey = PermissionKey.of("workspace", "governance", "media-takedown")

        assertEquals("workspace:governance:media-takedown", permissionKey.value)
    }
}
