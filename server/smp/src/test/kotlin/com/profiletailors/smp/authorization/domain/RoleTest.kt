package com.profiletailors.smp.authorization.domain

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleTest {

    @Test
    fun `role composes explicit permissions`() {
        val permission = PermissionKey.of("workspace", "members", "manage")
        val role = Role(
            key = "workspace-admin",
            category = RoleCategory.WORKSPACE,
            permissions = setOf(permission),
        )

        assertTrue(role.permissions.contains(permission))
    }
}
