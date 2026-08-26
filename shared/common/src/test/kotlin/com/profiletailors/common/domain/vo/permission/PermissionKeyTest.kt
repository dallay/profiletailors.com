package com.profiletailors.common.domain.vo.permission

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PermissionKeyTest {

    @Test
    fun `creates valid permission key with three segments`() {
        val key = PermissionKey("workspace:access:read")

        assertEquals("workspace:access:read", key.value)
    }

    @Test
    fun `of factory trims whitespace from each segment`() {
        val key = PermissionKey.of("  workspace  ", "  access  ", "  read  ")

        assertEquals("workspace:access:read", key.value)
    }

    @Test
    fun `accepts dashes in domain segment`() {
        val key = PermissionKey("platform-admin:users:read")

        assertEquals("platform-admin:users:read", key.value)
    }

    @Test
    fun `accepts dashes in resource segment`() {
        val key = PermissionKey("workspace:social-content:read")

        assertEquals("workspace:social-content:read", key.value)
    }

    @Test
    fun `accepts dashes in action segment`() {
        val key = PermissionKey("workspace:governance:media-takedown")

        assertEquals("workspace:governance:media-takedown", key.value)
    }

    @Test
    fun `accepts digits in all segments`() {
        val key = PermissionKey("workspace2:resource3:action4")

        assertEquals("workspace2:resource3:action4", key.value)
    }

    @Test
    fun `rejects key with only two segments`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace:read")
        }
    }

    @Test
    fun `rejects key with four segments`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace:access:read:extra")
        }
    }

    @Test
    fun `rejects key with uppercase letters`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("Workspace:access:read")
        }
    }

    @Test
    fun `rejects key with underscores`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace:access:read_all")
        }
    }

    @Test
    fun `rejects key with dots`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace.access.read")
        }
    }

    @Test
    fun `rejects empty string`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("")
        }
    }

    @Test
    fun `rejects blank segments`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace::read")
        }
    }

    @Test
    fun `two equal keys are equal`() {
        val a = PermissionKey("workspace:access:read")
        val b = PermissionKey("workspace:access:read")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `rejects key with spaces in value`() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionKey("workspace: access :read")
        }
    }
}
