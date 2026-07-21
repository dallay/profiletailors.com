package com.profiletailors.smp.governance.domain

import com.profiletailors.smp.authorization.domain.PermissionKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GovernancePermissionTest {

    @Test
    fun `MEDIA_READ has correct permission key`() {
        assertEquals(
            PermissionKey.of("workspace", "governance", "media-read"),
            GovernancePermission.MEDIA_READ.permissionKey,
        )
    }

    @Test
    fun `MEDIA_TAKEDOWN has correct permission key`() {
        assertEquals(
            PermissionKey.of("workspace", "governance", "media-takedown"),
            GovernancePermission.MEDIA_TAKEDOWN.permissionKey,
        )
    }
}
