package com.profiletailors.smp.infrastructure.db

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class LiquibaseBaselineChangelogTest {

    @Test
    fun `master changelog includes all phase one baseline resources`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")

        expectedResources().forEach { resource ->
            master shouldContain resource
            javaClass.classLoader.getResource(resource).shouldNotBeNull()
        }
    }

    @Test
    fun `release configuration excludes development seed data by default`() {
        val application = resourceText("application.yaml")
        val development = resourceText("application-dev.yaml")
        val developmentSeed = resourceText("db/changelog/dev/001-seed-test-data.yaml")

        application shouldContain "contexts: \${SMP_LIQUIBASE_CONTEXTS:prod}"
        development shouldContain "contexts: \${SMP_LIQUIBASE_CONTEXTS:dev}"
        developmentSeed shouldContain "context: \"@dev\""
    }

    @Test
    fun `licence column changelog adds nullable column`() {
        val changelog = "db/changelog/media/007-add-licence-column.yaml"

        javaClass.classLoader.getResource(changelog).shouldNotBeNull()
        val changeset = resourceText(changelog)
        changeset shouldContain "licence"
        changeset shouldContain "VARCHAR(64)"
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
        resourceText(path) shouldContain "tableName: $tableName"
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
