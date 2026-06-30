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
        const val PRINCIPAL_ID = "principal-1"
        const val RESOURCE_ID = "resource-1"
        const val WORKSPACE_HEADER = "X-Workspace-Id"
        const val USER_BEARER = "Bearer valid-token"
        const val API_VERSION_MEDIA_TYPE = "application/vnd.api.v1+json"
        const val ACCESS_SUMMARY_PATH = "/api/authorization/workspace-access/current"
        const val RESOURCE_PREVIEW_PATH_TEMPLATE = "/api/authorization/resources/%s/preview"
        const val LOCAL_AUTH_REGISTER_PATH = "/api/auth/register"
        const val LOCAL_AUTH_LOGIN_PATH = "/api/auth/login"
        const val LOCAL_AUTH_REFRESH_PATH = "/api/auth/refresh"
        const val LOCAL_AUTH_LOGOUT_PATH = "/api/auth/logout"
        const val LOCAL_AUTH_RESEND_PATH = "/api/auth/resend-verification"
        const val CURRENT_USER_PROFILE_PATH = "/api/auth/me"
        const val MEDIA_ASSETS_PATH = "/api/media/assets"
        const val GOVERNANCE_AUDIT_EVENTS_PATH = "/api/governance/audit-events"
        const val TENANCY_OWNERSHIP_TRANSFER_PATH = "/api/tenancy/workspace-ownership/owners/transfer"
        const val TENANCY_MEMBERSHIP_STATUS_PATH_TEMPLATE = "/api/tenancy/workspace-memberships/%s/status"
        const val ACCESS_SUMMARY_QUERY = "com.profiletailors.smp.authorization.application.current.workspace" +
            ".GetCurrentWorkspaceAccessSummaryQuery"
        const val RESOURCE_PREVIEW_QUERY = "com.profiletailors.smp.authorization.application.resource.getpreview" +
            ".GetResourcePreviewQuery"
        const val WORKSPACE_ACCESS_PERMISSION = "workspace:access:read"
        const val WORKSPACE_AUDIT_READ_PERMISSION = "workspace:audit:read"
        const val RESOURCE_PREVIEW_PERMISSION = "workspace:resource:read"
        const val WORKSPACE_ACCESS_ENTITLEMENT = "workspace.access.summary"
    }

    suspend fun resetDatabase() {
        applyLiquibaseBaseline()
        cleanupStatements().forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
        restoreRequiredBaselineRoles()
    }

    private suspend fun restoreRequiredBaselineRoles() {
        databaseClient.sql(
            """
            INSERT INTO roles (id, role_key, category)
            VALUES ('role-owner', 'owner', 'WORKSPACE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedEntitledAuthorizedMember() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = true)
        seedRolePermission(permissionId = "permission-1", permissionKey = WORKSPACE_ACCESS_PERMISSION)
    }

    suspend fun seedEntitledMemberWithoutAccessPermission() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = true)
        seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
    }

    suspend fun seedAuthenticatedPrincipalOnly() {
        seedUserPrincipal()
    }

    suspend fun seedDirectGrant(effect: String, permissionKey: String) {
        // Ensure permission exists before using it
        val existingPermission: String? = databaseClient.sql(
            "SELECT id FROM permissions WHERE permission_key = :permissionKey",
        )
            .bind("permissionKey", permissionKey)
            .map { row, _ -> requireNotNull(row.get("id", String::class.java)) }
            .one()
            .awaitSingleOrNull()

        val permissionId = if (existingPermission != null) {
            existingPermission
        } else {
            val newId = if (permissionKey ==
                WORKSPACE_ACCESS_PERMISSION
            ) {
                "permission-1"
            } else {
                "permission-dg-${System.currentTimeMillis()}"
            }
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)")
                .bind("id", newId)
                .bind("permissionKey", permissionKey)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            newId
        }

        databaseClient.sql(
            """
            INSERT INTO workspace_direct_grants (
                id, workspace_id, principal_id, principal_type, permission_id,
                effect, expires_at, conditions_json
            ) VALUES (
                :id, :workspaceId, :principalId, 'USER', :permissionId, :effect, NULL, NULL
            )
            """.trimIndent(),
        )
            .bind("id", "grant-$effect-$permissionId")
            .bind("workspaceId", WORKSPACE_ID)
            .bind("principalId", PRINCIPAL_ID)
            .bind("permissionId", permissionId)
            .bind("effect", effect)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedWorkspaceWithOwners() {
        // Seed workspace with owner-1 (current user) and owner-2 (target for transfer)
        seedUserPrincipal()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('$WORKSPACE_ID', 'Profile Tailors', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type)
            VALUES ('$WORKSPACE_ID', '$PRINCIPAL_ID', 'USER')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        // Seed owner-2 principal
        seedPrincipal(
            principalId = "owner-2",
            principalType = "USER",
            subject = "owner-2-subject",
            provider = "https://issuer.example",
            displayIdentity = "owner-2",
        )
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('owner-2', 'owner2@example.com', 'owner2')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type)
            VALUES ('$WORKSPACE_ID', 'owner-2', 'USER')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedWorkspaceWithMember() {
        // Seed workspace with member-2 to update status
        seedUserPrincipal()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('$WORKSPACE_ID', 'Profile Tailors', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        // Seed member-2 principal
        seedPrincipal(
            principalId = "member-2",
            principalType = "USER",
            subject = "member-2-subject",
            provider = "https://issuer.example",
            displayIdentity = "member-2",
        )
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('member-2', 'member2@example.com', 'member2')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('membership-2', '$WORKSPACE_ID', 'member-2', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedMemberWithPreviewPermission() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = false)
        seedRolePermission(permissionId = "permission-resource-read", permissionKey = RESOURCE_PREVIEW_PERMISSION)
    }

    suspend fun seedMemberWithAuditReadPermission() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = false)
        seedRolePermission(permissionId = "permission-audit-read", permissionKey = WORKSPACE_AUDIT_READ_PERMISSION)
    }

    suspend fun seedAuthorizedServiceAccount(entitled: Boolean, credentialStatus: String = "ACTIVE") {
        seedPrincipal(
            principalId = "service-principal-1",
            principalType = "SERVICE_ACCOUNT",
            subject = "service-account-subject",
            provider = "https://issuer.example",
            displayIdentity = "scheduler-bot",
        )
        seedWorkspaceAndRole(
            principalId = "service-principal-1",
            principalType = "SERVICE_ACCOUNT",
            entitled = entitled,
        )
        seedRolePermission(permissionId = "permission-1", permissionKey = WORKSPACE_ACCESS_PERMISSION)
        seedServiceAccountCredential(status = credentialStatus)
    }

    suspend fun seedAuthorizedApiKeyPrincipal(entitled: Boolean, credentialStatus: String = "ACTIVE") {
        seedPrincipal(
            principalId = "api-key-principal-1",
            principalType = "API_KEY",
            subject = "api-key-subject",
            provider = null,
            displayIdentity = "integration-key",
        )
        seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY", entitled = entitled)
        seedRolePermission(permissionId = "permission-1", permissionKey = WORKSPACE_ACCESS_PERMISSION)
        seedApiKeyCredential(status = credentialStatus)
    }

    suspend fun seedTargetScope(allowedResourceId: String) {
        databaseClient.sql(
            """
            INSERT INTO workspace_target_scopes (
                id, workspace_id, principal_id, principal_type, permission_id,
                target_resource_type, allowed_target_ids_json
            ) VALUES (
                'scope-1', '$WORKSPACE_ID', '$PRINCIPAL_ID', 'USER',
                'permission-resource-read', 'RESOURCE', :allowedTargetIdsJson
            )
            """.trimIndent(),
        )
            .bind("allowedTargetIdsJson", "[\"$allowedResourceId\"]")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    fun accessSummaryPath(): String = ACCESS_SUMMARY_PATH

    fun resourcePreviewPath(resourceId: String): String = RESOURCE_PREVIEW_PATH_TEMPLATE.format(resourceId)

    fun localAuthRegisterPath(): String = LOCAL_AUTH_REGISTER_PATH

    fun localAuthLoginPath(): String = LOCAL_AUTH_LOGIN_PATH

    fun localAuthRefreshPath(): String = LOCAL_AUTH_REFRESH_PATH

    fun localAuthLogoutPath(): String = LOCAL_AUTH_LOGOUT_PATH

    fun currentUserProfilePath(): String = CURRENT_USER_PROFILE_PATH

    fun localAuthResendPath(): String = LOCAL_AUTH_RESEND_PATH

    fun mediaAssetsPath(): String = MEDIA_ASSETS_PATH

    fun governanceAuditEventsPath(): String = GOVERNANCE_AUDIT_EVENTS_PATH

    fun tenancyOwnershipTransferPath(): String = TENANCY_OWNERSHIP_TRANSFER_PATH

    fun tenancyMembershipStatusPath(principalId: String): String =
        TENANCY_MEMBERSHIP_STATUS_PATH_TEMPLATE.format(principalId)

    suspend fun replaceActiveApiKeyCredential(): ApiKeyCredentialReplacementState {
        val successorLookupKey = "ptk_successor"
        val successorSecret = "successor-secret-value"
        val successorCredentialReference = "api-key-cred-2"
        val verifier = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
            successorSecret,
            org.springframework.security.crypto.bcrypt.BCrypt.gensalt(),
        )

        databaseClient.sql(
            """
            INSERT INTO api_key_credentials (
                id, principal_id, lookup_key, key_prefix, secret_verifier, status, revoked_at,
                replaced_by_credential_id, replaced_credential_id, replaced_at
            ) VALUES (
                :id, 'api-key-principal-1', :lookupKey, :keyPrefix, :verifier, 'ACTIVE', NULL,
                NULL, 'api-key-cred-1', NULL
            )
            """.trimIndent(),
        )
            .bind("id", successorCredentialReference)
            .bind("lookupKey", successorLookupKey)
            .bind("keyPrefix", successorLookupKey)
            .bind("verifier", verifier)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        databaseClient.sql(
            """
            UPDATE api_key_credentials
            SET status = 'INACTIVE',
                replaced_by_credential_id = :successorCredentialReference,
                replaced_at = :replacedAt
            WHERE id = 'api-key-cred-1'
            """.trimIndent(),
        )
            .bind("successorCredentialReference", successorCredentialReference)
            .bind("replacedAt", Instant.parse("2026-05-15T10:50:30Z"))
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return ApiKeyCredentialReplacementState(
            successorPlaintextApiKey = "$successorLookupKey.$successorSecret",
            successorCredentialReference = successorCredentialReference,
        )
    }

    suspend fun seedJwtAuthenticatedUserWithWorkspace(emailStatus: String) {
        seedAuthenticatedUserWithWorkspace(email = "bdd-user@example.com")
        databaseClient.sql(
            "UPDATE user_identities SET email_status = :emailStatus WHERE principal_id = :principalId",
        )
            .bind("emailStatus", emailStatus)
            .bind("principalId", PRINCIPAL_ID)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        seedWorkspace()
        seedWorkspaceMembershipIdempotent(PRINCIPAL_ID)
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

    suspend fun markEmailPENDING(email: String) {
        databaseClient.sql(
            "UPDATE user_identities SET email_status = 'PENDING' WHERE email = :email",
        )
            .bind("email", email)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
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
                subject = "local:$email",
                provider = null,
                displayIdentity = email.substringBefore('@'),
            )
        }
        val identityExists: String? = databaseClient.sql(
            "SELECT principal_id FROM user_identities WHERE principal_id = :id",
        )
            .bind("id", principalId)
            .map { row, _ -> row.get("principal_id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (identityExists == null) {
            databaseClient.sql(
                """
                INSERT INTO user_identities (principal_id, email, username)
                VALUES (:principalId, :email, :username)
                """.trimIndent(),
            )
                .bind("principalId", principalId)
                .bind("email", email)
                .bind("username", email.substringBefore('@'))
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
        val workspaceExists: String? = databaseClient.sql(
            "SELECT id FROM workspaces WHERE id = :id",
        )
            .bind("id", WORKSPACE_ID)
            .map { row, _ -> row.get("id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (workspaceExists == null) {
            seedWorkspace()
        }
        seedWorkspaceMembershipIdempotent(principalId)
    }

    suspend fun seedWorkspaceMembership(principalId: String, workspaceId: String = WORKSPACE_ID) {
        val membershipId = "membership-$principalId-$workspaceId"
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

    suspend fun countMediaAssets(workspaceId: String = WORKSPACE_ID): Long =
        databaseClient.sql("SELECT COUNT(*) AS total FROM media_assets WHERE workspace_id = :workspaceId")
            .bind("workspaceId", workspaceId)
            .map { row, _ -> (row.get("total") as Number).toLong() }
            .one()
            .awaitSingle()

    suspend fun firstWorkspaceIdForPrincipal(principalId: String): String? = databaseClient.sql(
        """
            SELECT workspace_id FROM workspace_memberships
            WHERE principal_id = :principalId
            ORDER BY created_at ASC
            LIMIT 1
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .map { row, _ -> row.get("workspace_id", String::class.java) as String }
        .one()
        .awaitSingleOrNull()

    suspend fun seedWorkspaceMembershipForWorkspace(principalId: String, workspaceId: String) {
        seedWorkspaceMembershipIdempotent(principalId, workspaceId)
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
                if (status ==
                    "REVOKED"
                ) {
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
        val verifier = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
            "secret-value",
            org.springframework.security.crypto.bcrypt.BCrypt.gensalt(),
        )
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
                if (status ==
                    "REVOKED"
                ) {
                    spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z"))
                } else {
                    spec.bindNull("revokedAt", Instant::class.java)
                }
            }
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

    private suspend fun seedUserPrincipal() {
        val principalExists: String? = databaseClient.sql(
            "SELECT id FROM principals WHERE id = :id",
        )
            .bind("id", PRINCIPAL_ID)
            .map { row, _ -> row.get("id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        val exists: String? = databaseClient.sql(
            "SELECT principal_id FROM user_identities WHERE principal_id = :id",
        )
            .bind("id", PRINCIPAL_ID)
            .map { row, _ -> row.get("principal_id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (exists != null) return
        if (principalExists == null) {
            seedPrincipal(
                principalId = PRINCIPAL_ID,
                principalType = "USER",
                subject = "subject-123",
                provider = "https://issuer.example",
                displayIdentity = "yuniel",
            )
        }
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('$PRINCIPAL_ID', 'jwt-user@example.com', 'jwt-user')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
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
                if (provider ==
                    null
                ) {
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
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('$WORKSPACE_ID', 'Profile Tailors', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
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
            databaseClient.sql(
                """
                INSERT INTO workspace_entitlements (id, workspace_id, entitlement_key, enabled)
                VALUES ('entitlement-1', '$WORKSPACE_ID', '$WORKSPACE_ACCESS_ENTITLEMENT', TRUE)
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
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
}
