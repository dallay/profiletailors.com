package com.profiletailors.smp.governance.domain

import com.profiletailors.smp.authorization.domain.PermissionKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GovernancePermissionTest {

    @Test
    fun `MEDIA_READ has correct permission key`() {
        GovernancePermission.MEDIA_READ.permissionKey shouldBe
            PermissionKey.of("workspace", "governance", "media-read")
    }

    @Test
    fun `MEDIA_TAKEDOWN has correct permission key`() {
        GovernancePermission.MEDIA_TAKEDOWN.permissionKey shouldBe
            PermissionKey.of("workspace", "governance", "media-takedown")
    }
}
