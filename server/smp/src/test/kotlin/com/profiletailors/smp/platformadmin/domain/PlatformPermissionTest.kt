package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformPermissionTest {

    @Test
    fun `PLATFORM_OWNER has all permissions`() {
        val perms = setOf(PlatformRole.PLATFORM_OWNER).effectivePermissions()
        assertEquals(PlatformPermission.entries.toSet(), perms)
    }

    @Test
    fun `PLATFORM_OPERATOR cannot manage operators`() {
        val perms = setOf(PlatformRole.PLATFORM_OPERATOR).effectivePermissions()
        assertFalse(PlatformPermission.OPERATORS_MANAGE in perms)
    }

    @Test
    fun `PLATFORM_OPERATOR can invite and read waitlist`() {
        val perms = setOf(PlatformRole.PLATFORM_OPERATOR).effectivePermissions()
        assertTrue(PlatformPermission.WAITLIST_INVITE in perms)
        assertTrue(PlatformPermission.WAITLIST_READ in perms)
    }

    @Test
    fun `SUPPORT_AGENT cannot invite candidates`() {
        val perms = setOf(PlatformRole.SUPPORT_AGENT).effectivePermissions()
        assertFalse(PlatformPermission.WAITLIST_INVITE in perms)
        assertFalse(PlatformPermission.INVITATIONS_REVOKE in perms)
    }

    @Test
    fun `AUDITOR cannot execute mutations`() {
        val perms = setOf(PlatformRole.AUDITOR).effectivePermissions()
        assertFalse(PlatformPermission.WAITLIST_INVITE in perms)
        assertFalse(PlatformPermission.WAITLIST_CANCEL in perms)
        assertFalse(PlatformPermission.INVITATIONS_REVOKE in perms)
        assertFalse(PlatformPermission.OPERATORS_MANAGE in perms)
    }

    @Test
    fun `AUDITOR can read audit events`() {
        val perms = setOf(PlatformRole.AUDITOR).effectivePermissions()
        assertTrue(PlatformPermission.AUDIT_READ in perms)
    }

    @Test
    fun `effective permissions are union of all active roles`() {
        val perms = setOf(PlatformRole.AUDITOR, PlatformRole.SUPPORT_AGENT).effectivePermissions()
        assertTrue(PlatformPermission.AUDIT_READ in perms)
        assertTrue(PlatformPermission.USERS_READ in perms)
    }

    @Test
    fun `workspace roles do not appear in platform permissions`() {
        // Empty role set yields empty permissions - workspace roles are not platform roles
        val perms = emptySet<PlatformRole>().effectivePermissions()
        assertTrue(perms.isEmpty())
    }

    @Test
    fun `PlatformPermission keys follow dot-notation format`() {
        PlatformPermission.entries.forEach { perm ->
            assertTrue(perm.key.startsWith("platform."), "Expected 'platform.' prefix on ${perm.key}")
            assertTrue(perm.key.split(".").size >= 3, "Expected at least 3 segments in ${perm.key}")
        }
    }
}
