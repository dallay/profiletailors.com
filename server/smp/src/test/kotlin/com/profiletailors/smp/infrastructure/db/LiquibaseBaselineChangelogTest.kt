package com.profiletailors.smp.infrastructure.db

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiquibaseBaselineChangelogTest {

    @Test
    fun `master changelog includes all phase one baseline resources`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")

        expectedResources().forEach { resource ->
            assertTrue(master.contains(resource), "Master changelog should include $resource")
            assertNotNull(javaClass.classLoader.getResource(resource), "Resource $resource must exist")
        }
    }

    @Test
    fun `baseline changelogs define required phase one tables`() {
        assertTrue(resourceText("db/changelog/identity/001-create-principals.yaml").contains("tableName: principals"))
        assertTrue(resourceText("db/changelog/identity/002-create-user-identities.yaml").contains("tableName: user_identities"))
        assertTrue(resourceText("db/changelog/tenancy/001-create-workspaces.yaml").contains("tableName: workspaces"))
        assertTrue(resourceText("db/changelog/tenancy/002-create-workspace-ownerships.yaml").contains("tableName: workspace_ownerships"))
        assertTrue(resourceText("db/changelog/tenancy/003-create-workspace-memberships.yaml").contains("tableName: workspace_memberships"))
        assertTrue(resourceText("db/changelog/authorization/001-create-permissions.yaml").contains("tableName: permissions"))
        assertTrue(resourceText("db/changelog/authorization/002-create-roles.yaml").contains("tableName: roles"))
        assertTrue(resourceText("db/changelog/authorization/003-create-role-permissions.yaml").contains("tableName: role_permissions"))
        assertTrue(resourceText("db/changelog/authorization/004-create-membership-roles.yaml").contains("tableName: membership_roles"))
        assertTrue(resourceText("db/changelog/authorization/005-create-workspace-direct-grants.yaml").contains("tableName: workspace_direct_grants"))
        assertTrue(resourceText("db/changelog/authorization/006-create-workspace-entitlements.yaml").contains("tableName: workspace_entitlements"))
        assertTrue(resourceText("db/changelog/authorization/007-create-workspace-target-scopes.yaml").contains("tableName: workspace_target_scopes"))
        assertTrue(resourceText("db/changelog/credentials/002-create-api-key-credentials.yaml").contains("tableName: api_key_credentials"))
    }

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing resource $path" }
            .bufferedReader()
            .use { it.readText() }

    private fun expectedResources(): List<String> = listOf(
        "db/changelog/identity/001-create-principals.yaml",
        "db/changelog/identity/002-create-user-identities.yaml",
        "db/changelog/tenancy/001-create-workspaces.yaml",
        "db/changelog/tenancy/002-create-workspace-ownerships.yaml",
        "db/changelog/tenancy/003-create-workspace-memberships.yaml",
        "db/changelog/authorization/001-create-permissions.yaml",
        "db/changelog/authorization/002-create-roles.yaml",
        "db/changelog/authorization/003-create-role-permissions.yaml",
        "db/changelog/authorization/004-create-membership-roles.yaml",
        "db/changelog/authorization/005-create-workspace-direct-grants.yaml",
        "db/changelog/authorization/006-create-workspace-entitlements.yaml",
        "db/changelog/authorization/007-create-workspace-target-scopes.yaml",
        "db/changelog/credentials/002-create-api-key-credentials.yaml",
    )
}
