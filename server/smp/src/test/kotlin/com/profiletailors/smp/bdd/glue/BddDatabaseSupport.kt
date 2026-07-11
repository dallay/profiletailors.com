package com.profiletailors.smp.bdd.glue

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager
import java.time.Instant

@Suppress("LargeClass")
class BddDatabaseSupport(
    private val databaseClient: DatabaseClient,
    private val liquibaseJdbcUrl: String,
    private val liquibaseUsername: String,
    private val liquibasePassword: String,
) {
    data class LocalAuthSession(val accessToken: String, val refreshCookie: String)

    data class ApiKeyCredentialReplacementState(
        val successorPlaintextApiKey: String,
        val successorCredentialReference: String,
    )

    companion object {
        const val WORKSPACE_ID = "workspace-1"
        const val PRINCIPAL_ID = "member-1"
        const val WORKSPACE_ACCESS_ENTITLEMENT = "workspace.access"
        const val API_VERSION_MEDIA_TYPE = "application/vnd.api.v1+json"
        const val WORKSPACE_HEADER = "X-Workspace-Id"
        const val USER_BEARER = "Bearer user-token"
        const val WORKSPACE_ACCESS_PERMISSION = "workspace.access"
        const val REGISTER_PATH = "/api/auth/register"
        const val LOGIN_PATH = "/api/auth/login"
        const val REFRESH_PATH = "/api/auth/refresh"
        const val LOGOUT_PATH = "/api/auth/logout"
        const val RESEND_PATH = "/api/auth/resend-verification"
        const val ACCESS_SUMMARY_PATH = "/api/tenancy/workspaces/access-summary"
        const val ME_PATH = "/api/auth/me"
        const val MEDIA_PATH = "/api/media/assets"
    }

    fun localAuthRegisterPath(): String = REGISTER_PATH
    fun localAuthLoginPath(): String = LOGIN_PATH
    fun localAuthRefreshPath(): String = REFRESH_PATH
    fun localAuthLogoutPath(): String = LOGOUT_PATH
    fun localAuthResendPath(): String = RESEND_PATH
    fun accessSummaryPath(): String = ACCESS_SUMMARY_PATH
    fun resourcePreviewPath(resourceId: String): String = "/api/media/assets/$resourceId/preview"
    fun currentUserProfilePath(): String = ME_PATH
    fun mediaAssetsPath(): String = MEDIA_PATH

    fun setupDatabase() {
        applyLiquibaseBaseline()
    }

    suspend fun resetDatabase() {
        clearDatabase()
    }

    suspend fun clearDatabase() {
        cleanupStatements().forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
    }

    suspend fun seedWorkspace(workspaceId: String = WORKSPACE_ID) {
        val exists: String? = databaseClient.sql(
            "SELECT id FROM workspaces WHERE id = :id",
        )
            .bind("id", workspaceId)
            .map { row, _ -> row.get("id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (exists != null) return
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES (:id, 'Profile Tailors', 'ACTIVE', NULL)",
        )
            .bind("id", workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedAuthenticatedUserWithWorkspace(
        email: String = "yuniel@example.com",
        principalId: String = PRINCIPAL_ID,
    ) {
        val principalExists: String? = databaseClient.sql(
            "SELECT id FROM principals WHERE id = :id",
        )
            .bind("id", principalId)
            .map { row, _ -> row.get("id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (principalExists == null) {
            seedPrincipal(
                principalId = principalId,
                principalType = "USER",
                subject = "subject-123",
                provider = "https://issuer.example",
                displayIdentity = "yuniel",
            )
        }
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES (:principalId, :email, 'yuniel', 'VERIFIED')
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("email", email)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        seedWorkspace()
        seedWorkspaceMembershipIdempotent(principalId)
    }

    suspend fun markEmailVerified(email: String) {
        databaseClient.sql(
            "UPDATE user_identities SET email_status = 'VERIFIED' WHERE email = :email",
        )
            .bind("email", email)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun markEmailPending(email: String) {
        databaseClient.sql(
            "UPDATE user_identities SET email_status = 'PENDING' WHERE email = :email",
        )
            .bind("email", email)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedVerificationToken(email: String, token: String) {
        databaseClient.sql(
            """
            INSERT INTO email_verification_tokens (id, email, token, expires_at, created_at)
            VALUES ('id-' || :token, :email, :token, TIMESTAMP '2099-01-01 00:00:00', NOW())
            """.trimIndent(),
        )
            .bind("email", email)
            .bind("token", token)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedWorkspaceMembershipIdempotent(principalId: String, workspaceId: String = WORKSPACE_ID) {
        val membershipId = "membership-$principalId-$workspaceId"
        val exists: String? = databaseClient.sql(
            "SELECT id FROM workspace_memberships WHERE principal_id = :principalId AND workspace_id = :workspaceId",
        )
            .bind("principalId", principalId)
            .bind("workspaceId", workspaceId)
            .map { row, _ -> row.get("id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (exists != null) return
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES (:id, :workspaceId, :principalId, 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .bind("id", membershipId)
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedAuditEventRecords() {
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, request_name, request_path, permission, actor_principal_id,
                workspace_id, target_type, target_id, outcome, reason_code, role_keys_json, details_json, created_at
            ) VALUES (
                'audit-1', 'MUTATION', 'workspace.owner.add', NULL, NULL, NULL, 'owner-1',
                '$WORKSPACE_ID', 'WORKSPACE_OWNER', 'owner-2', 'SUCCESS', NULL, '[]', '{"ownerPrincipalId":"owner-2"}',
                TIMESTAMP WITH TIME ZONE '2026-05-20T12:00:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedEntitledAuthorizedMember() {
        seedAuthenticatedUserWithWorkspace()
        seedWorkspaceEntitlement(WORKSPACE_ID, WORKSPACE_ACCESS_ENTITLEMENT)
        seedRolePermission("permission-1", WORKSPACE_ACCESS_PERMISSION)
    }

    suspend fun seedEntitledMemberWithoutAccessPermission() {
        seedAuthenticatedUserWithWorkspace()
        seedWorkspaceEntitlement(WORKSPACE_ID, WORKSPACE_ACCESS_ENTITLEMENT)
    }

    suspend fun seedAuthenticatedPrincipalOnly() {
        seedPrincipal(PRINCIPAL_ID, "USER", "subject-1", null, "test")
    }

    suspend fun seedDirectGrant(effect: String, permission: String) {
        databaseClient.sql(
            "INSERT INTO workspace_direct_grants (id, workspace_id, principal_id, permission, effect) " +
                "VALUES ('dg-1', :workspaceId, :principalId, :permission, :effect)",
        )
            .bind("workspaceId", WORKSPACE_ID)
            .bind("principalId", PRINCIPAL_ID)
            .bind("permission", permission)
            .bind("effect", effect)
            .fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedMemberWithPreviewPermission() {
        seedAuthenticatedUserWithWorkspace()
        seedRolePermission("p-preview", "media.asset.preview")
    }

    suspend fun seedMemberWithAuditReadPermission() {
        seedAuthenticatedUserWithWorkspace()
        seedRolePermission("p-audit", "workspace.audit_event.read")
    }

    suspend fun seedTargetScope(resourceId: String) {
        databaseClient.sql(
            "INSERT INTO workspace_target_scopes (id, workspace_id, principal_id, target_type, target_id) " +
                "VALUES ('ts-1', :workspaceId, :principalId, 'MEDIA_ASSET', :targetId)",
        )
            .bind("workspaceId", WORKSPACE_ID)
            .bind("principalId", PRINCIPAL_ID)
            .bind("targetId", resourceId)
            .fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedJwtAuthenticatedUserWithWorkspace(emailStatus: String = "VERIFIED") {
        seedAuthenticatedUserWithWorkspace()
        if (emailStatus == "PENDING") {
            markEmailPending("yuniel@example.com")
        }
    }

    suspend fun countMediaAssets(): Long = databaseClient.sql("SELECT COUNT(*) FROM media_assets")
        .map { row, _ -> requireNotNull(row.get(0, java.lang.Long::class.java)).toLong() }
        .one()
        .awaitSingle()

    suspend fun seedAuthorizedServiceAccount(entitled: Boolean = true, credentialStatus: String = "ACTIVE") {
        seedPrincipal("service-principal-1", "SERVICE_ACCOUNT", "svc-1", "https://issuer", "svc")
        seedServiceAccountCredential(credentialStatus)
        seedWorkspaceAndRole("service-principal-1", "SERVICE_ACCOUNT", entitled)
        seedRolePermission("p-sa", WORKSPACE_ACCESS_PERMISSION)
    }

    suspend fun seedAuthorizedApiKeyPrincipal(entitled: Boolean = true) {
        seedPrincipal("api-key-principal-1", "API_KEY", "ak-1", null, "ak")
        seedApiKeyCredential("ACTIVE")
        seedWorkspaceAndRole("api-key-principal-1", "API_KEY", entitled)
        seedRolePermission("p-ak", WORKSPACE_ACCESS_PERMISSION)
    }

    suspend fun replaceActiveApiKeyCredential(): ApiKeyCredentialReplacementState =
        ApiKeyCredentialReplacementState("new-key", "ak-2")

    private suspend fun seedWorkspaceEntitlement(workspaceId: String, key: String) {
        databaseClient.sql(
            "INSERT INTO workspace_entitlements (id, workspace_id, entitlement_key, enabled) " +
                "VALUES ('e-1', :workspaceId, :key, TRUE)",
        )
            .bind("workspaceId", workspaceId)
            .bind("key", key)
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedServiceAccountCredential(status: String) {
        databaseClient.sql(
            """
            INSERT INTO service_account_credentials (
                id, principal_id, provider, credential_reference, status, revoked_at
            ) VALUES (
                'svc-cred-row-1', 'service-principal-1', 'https://issuer.example',
                'svc-cred-1', :status, :revokedAt
            )
            """.trimIndent(),
        )
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") {
                    spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z"))
                } else {
                    spec.bindNull("revokedAt", Instant::class.java)
                }
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedApiKeyCredential(status: String) {
        val verifier = "verifier"
        databaseClient.sql(
            """
            INSERT INTO api_key_credentials (
                id, principal_id, lookup_key, key_prefix, secret_verifier, status, revoked_at,
                replaced_by_credential_id, replaced_credential_id, replaced_at
            ) VALUES (
                'api-key-cred-1', 'api-key-principal-1', 'ptk_lookup', 'ptk_lookup',
                :verifier, :status, :revokedAt, NULL, NULL, NULL
            )
            """.trimIndent(),
        )
            .bind("verifier", verifier)
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") {
                    spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z"))
                } else {
                    spec.bindNull("revokedAt", Instant::class.java)
                }
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedPrincipal(
        principalId: String,
        principalType: String,
        subject: String,
        provider: String?,
        displayIdentity: String,
    ) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:principalId, :principalType, :subject, :provider, :displayIdentity)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .bind("subject", subject)
            .let { spec ->
                if (provider == null) {
                    spec.bindNull("provider", String::class.java)
                } else {
                    spec.bind("provider", provider)
                }
            }
            .bind("displayIdentity", displayIdentity)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedWorkspaceAndRole(
        principalId: String = PRINCIPAL_ID,
        principalType: String = "USER",
        entitled: Boolean,
    ) {
        seedWorkspace()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('membership-1', '$WORKSPACE_ID', :principalId, :principalType, 'ACTIVE')
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            "INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')",
        ).fetch().rowsUpdated().awaitSingle()
        if (entitled) {
            seedWorkspaceEntitlement(WORKSPACE_ID, WORKSPACE_ACCESS_ENTITLEMENT)
        }
    }

    private suspend fun seedRolePermission(permissionId: String, permissionKey: String) {
        databaseClient.sql(
            "INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)",
        )
            .bind("id", permissionId)
            .bind("permissionKey", permissionKey)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            "INSERT INTO role_permissions (role_id, permission_id) VALUES ('role-1', :permissionId)",
        )
            .bind("permissionId", permissionId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM audit_events",
        "DELETE FROM workspace_target_scopes",
        "DELETE FROM workspace_direct_grants",
        "DELETE FROM workspace_entitlements",
        "DELETE FROM membership_roles",
        "DELETE FROM role_permissions",
        "DELETE FROM roles",
        "DELETE FROM permissions",
        "DELETE FROM refresh_sessions",
        "DELETE FROM local_password_credentials",
        "DELETE FROM api_key_credentials",
        "DELETE FROM service_account_credentials",
        "DELETE FROM media_assets",
        "DELETE FROM workspace_file_blobs",
        "DELETE FROM workspace_upload_slots",
        "DELETE FROM media_rate_limits",
        "DELETE FROM workspace_memberships",
        "DELETE FROM workspace_ownerships",
        "DELETE FROM workspaces",
        "DELETE FROM user_identities",
        "DELETE FROM principals",
    )

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            liquibaseJdbcUrl,
            liquibaseUsername,
            liquibasePassword,
        ).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts(), LabelExpression())
        }
    }
}
