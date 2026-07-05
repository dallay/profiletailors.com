package com.profiletailors.smp.infrastructure.db

import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `master changelog does not include dev seed data`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")

        assertFalse(master.contains("db/changelog/dev/"), "Production changelog must not include dev seed changelogs")
        assertFalse(master.contains("db/changelog/data/dev/"), "Production changelog must not include dev seed data")
    }

    @Test
    fun `dev changelog includes production baseline and dev seed data`() {
        val dev = resourceText("db/changelog/db.changelog-dev.yaml")

        assertTrue(
            dev.contains("db/changelog/db.changelog-master.yaml"),
            "Dev changelog must include production baseline",
        )
        assertTrue(
            dev.contains("db/changelog/dev/001-seed-test-data.yaml"),
            "Dev changelog must include dev seed changelog",
        )
    }

    @Test
    fun `committed changelog resources do not contain password credential seeds`() {
        val seed = resourceText("db/changelog/dev/001-seed-test-data.yaml")

        assertFalse(seed.contains("password_hash"), "Committed dev changelog must not seed password hashes")
        assertFalse(seed.contains(Regex("\\$2[aby]\\$")), "Committed dev changelog must not contain BCrypt hashes")
        assertTrue(
            javaClass.classLoader.getResource("db/changelog/data/dev/local_password_credentials_dev.csv") == null,
            "Password credential seed files must not be committed",
        )
    }

    @Test
    fun `external metadata forward rollback changelog drops constraints and columns`() {
        val rollback = "db/changelog/media/006-drop-external-metadata.yaml"

        assertNotNull(javaClass.classLoader.getResource(rollback), "Resource $rollback must exist")
        val changeset = resourceText(rollback)
        assertTrue(changeset.contains("DROP CONSTRAINT chk_asset_uploaded_implies_no_provider"))
        assertTrue(changeset.contains("chk_asset_uploaded_implies_no_provider"))
        assertTrue(changeset.contains("chk_asset_external_implies_provider_and_id"))
        assertTrue(changeset.contains("dropColumn"))
        listOf("source_provider", "external_id", "source_url", "author_name", "author_url", "metadata")
            .forEach { column -> assertTrue(changeset.contains("columnName: $column")) }
    }

    @Test
    fun `baseline changelogs define required phase one tables`() {
        val tables = listOf(
            "db/changelog/identity/001-create-principals.yaml" to "principals",
            "db/changelog/identity/002-create-user-identities.yaml" to "user_identities",
            "db/changelog/tenancy/001-create-workspaces.yaml" to "workspaces",
            "db/changelog/tenancy/002-create-workspace-ownerships.yaml" to "workspace_ownerships",
            "db/changelog/tenancy/003-create-workspace-memberships.yaml" to "workspace_memberships",
            "db/changelog/authorization/001-create-permissions.yaml" to "permissions",
            "db/changelog/authorization/002-create-roles.yaml" to "roles",
            "db/changelog/authorization/003-create-role-permissions.yaml" to "role_permissions",
            "db/changelog/authorization/004-create-membership-roles.yaml" to "membership_roles",
            "db/changelog/authorization/005-create-workspace-direct-grants.yaml" to "workspace_direct_grants",
            "db/changelog/authorization/006-create-workspace-entitlements.yaml" to "workspace_entitlements",
            "db/changelog/authorization/007-create-workspace-target-scopes.yaml" to "workspace_target_scopes",
            "db/changelog/governance/001-create-audit-events.yaml" to "audit_events",
            "db/changelog/credentials/002-create-api-key-credentials.yaml" to "api_key_credentials",
            "db/changelog/publishing/001-create-social-connections.yaml" to "social_connections",
            "db/changelog/publishing/002-create-social-accounts.yaml" to "social_accounts",
            "db/changelog/publishing/003-create-publication-assets.yaml" to "publication_assets",
            "db/changelog/publishing/004-create-publications.yaml" to "publications",
            "db/changelog/publishing/005-create-publication-asset-links.yaml" to "publication_asset_links",
            "db/changelog/publishing/006-create-publication-jobs.yaml" to "publication_jobs",
            "db/changelog/publishing/007-create-delivery-attempts.yaml" to "delivery_attempts",
        )
        tables.forEach { (path, table) -> assertHasTable(path, table) }
    }

    private fun assertHasTable(path: String, tableName: String) {
        assertTrue(resourceText(path).contains("tableName: $tableName"))
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
        "db/changelog/governance/001-create-audit-events.yaml",
        "db/changelog/credentials/002-create-api-key-credentials.yaml",
        "db/changelog/publishing/001-create-social-connections.yaml",
        "db/changelog/publishing/002-create-social-accounts.yaml",
        "db/changelog/publishing/003-create-publication-assets.yaml",
        "db/changelog/publishing/004-create-publications.yaml",
        "db/changelog/publishing/005-create-publication-asset-links.yaml",
        "db/changelog/publishing/006-create-publication-jobs.yaml",
        "db/changelog/publishing/007-create-delivery-attempts.yaml",
    )
}
