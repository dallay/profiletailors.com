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

    private var lastRawPasswordResetToken: String = ""

    companion object {
        const val WORKSPACE_ID = "workspace-1"
        const val PRINCIPAL_ID = "principal-1"
        const val RESOURCE_ID = "resource-1"
        const val WORKSPACE_HEADER = "X-Workspace-Id"
        const val USER_BEARER = "Bearer valid-token"
        const val VERIFIED_USER_BEARER = "Bearer verified-token"
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
        const val MEDIA_ASSET_ID = "asset-bdd-1"
        const val MEDIA_ASSET_FILE_HASH = "b591d9820ae723ef0604a2014276dea6a9a26566b5f857a146a51fae9b22da41"
        const val PUBLISHING_PUBLICATIONS_PATH = "/api/publishing/publications"
        const val RECURRING_SCHEDULES_PATH_TEMPLATE = "/api/v1/workspaces/%s/recurring"
        const val PUBLISHING_CHANNELS_PATH = "/api/publishing/channels"
        const val PUBLISHING_CHANNEL_PROVIDERS_PATH = "/api/publishing/channels/providers"
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

    fun publishingPublicationsPath(): String = PUBLISHING_PUBLICATIONS_PATH

    fun recurringSchedulesPath(): String = RECURRING_SCHEDULES_PATH_TEMPLATE.format(WORKSPACE_ID)

    suspend fun countScheduledPublications(): Long = databaseClient.sql(
        "SELECT COUNT(*) AS count FROM publications WHERE workspace_id = :workspaceId AND status = 'SCHEDULED'",
    )
        .bind("workspaceId", WORKSPACE_ID)
        .map { row, _ -> requireNotNull(row.get("count", Long::class.java)) }
        .one()
        .awaitSingle()

    fun publishingChannelsPath(): String = PUBLISHING_CHANNELS_PATH

    fun publishingChannelProvidersPath(): String = PUBLISHING_CHANNEL_PROVIDERS_PATH

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

    suspend fun seedMediaAsset(
        assetId: String = MEDIA_ASSET_ID,
        workspaceId: String = WORKSPACE_ID,
        fileHash: String = MEDIA_ASSET_FILE_HASH,
        sourceType: String = "UPLOADED",
        mediaType: String = "image/png",
        status: String = "READY",
        fileSizeBytes: Long? = 12_345,
        originalFilename: String? = "bdd-asset.png",
    ) {
        // Ensure a matching blob exists for FK constraint
        seedFileBlob(workspaceId = workspaceId, fileHash = fileHash)

        val storageKey = "uploads/$workspaceId/$fileHash"
        databaseClient.sql(
            """
            INSERT INTO media_assets (asset_id, workspace_id, source_type, media_type, storage_key,
                                      file_hash, original_filename, file_size_bytes, status, created_at)
            VALUES (:assetId, :workspaceId, :sourceType, :mediaType, :storageKey,
                    :fileHash, :originalFilename, :fileSizeBytes, :status, NOW())
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .bind("sourceType", sourceType)
            .bind("mediaType", mediaType)
            .bind("storageKey", storageKey)
            .bind("fileHash", fileHash)
            .let { spec ->
                if (originalFilename != null) {
                    spec.bind("originalFilename", originalFilename)
                } else {
                    spec.bindNull("originalFilename", String::class.java)
                }
            }
            .let { spec ->
                if (fileSizeBytes != null) {
                    spec.bind("fileSizeBytes", fileSizeBytes)
                } else {
                    spec.bindNull("fileSizeBytes", Long::class.java)
                }
            }
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedFileBlob(
        workspaceId: String = WORKSPACE_ID,
        fileHash: String = MEDIA_ASSET_FILE_HASH,
        storageKey: String? = null,
        status: String = "READY",
    ) {
        val existing: String? = databaseClient.sql(
            "SELECT workspace_id FROM workspace_file_blobs WHERE workspace_id = :workspaceId AND file_hash = :fileHash",
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .map { row, _ -> row.get("workspace_id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (existing != null) return

        val key = storageKey ?: "uploads/$workspaceId/$fileHash"
        databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (workspace_id, file_hash, storage_key, file_size_bytes,
                                              detected_media_type, status, created_at, updated_at)
            VALUES (:workspaceId, :fileHash, :storageKey, 12345, 'image/png', :status, NOW(), NOW())
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .bind("storageKey", key)
            .bind("status", status)
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

    suspend fun findMediaAssetStatus(assetId: String, workspaceId: String = WORKSPACE_ID): String? = databaseClient.sql(
        """
            SELECT status FROM media_assets
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
        """.trimIndent(),
    )
        .bind("assetId", assetId)
        .bind("workspaceId", workspaceId)
        .map { row, _ -> row.get("status") as String }
        .one()
        .awaitSingleOrNull()

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

    /**
     * Seeds a [social_connections] row for the default workspace.
     *
     * @param connectionId  Unique connection identifier (used as the row's primary key).
     * @param provider      Social provider name (e.g. "LINKEDIN").
     * @param status        Connection status (e.g. "ACTIVE", "EXPIRED").
     */
    suspend fun seedSocialConnection(connectionId: String, provider: String, status: String) {
        databaseClient.sql(
            """
            INSERT INTO social_connections (id, workspace_id, provider, provider_connection_ref, status,
                                            credential_reference, connected_at, last_synced_at, created_at)
            VALUES (:id, :workspaceId, :provider, :providerConnectionRef, :status,
                    NULL, NOW(), NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", connectionId)
            .bind("workspaceId", WORKSPACE_ID)
            .bind("provider", provider)
            .bind("providerConnectionRef", "ref-$connectionId")
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Seeds a [social_accounts] row linked to an existing social connection.
     *
     * @param accountId         Unique account identifier.
     * @param connectionId      Foreign key to [seedSocialConnection].
     * @param provider          Social provider name (e.g. "LINKEDIN").
     * @param providerAccountId Account ID on the provider side.
     * @param accountKind       Type of account (e.g. "PERSONAL_PROFILE", "COMPANY_PAGE").
     * @param displayName       Human-readable display name.
     */
    suspend fun seedSocialAccount(
        accountId: String,
        connectionId: String,
        provider: String,
        providerAccountId: String,
        accountKind: String,
        displayName: String,
    ) {
        databaseClient.sql(
            """
            INSERT INTO social_accounts (id, social_connection_id, workspace_id, provider, provider_account_id,
                                         account_type, display_name, profile_urn, status, created_at)
            VALUES (:id, :connectionId, :workspaceId, :provider, :providerAccountId,
                    :accountKind, :displayName, :profileUrn, 'ACTIVE', NOW())
            """.trimIndent(),
        )
            .bind("id", accountId)
            .bind("connectionId", connectionId)
            .bind("workspaceId", WORKSPACE_ID)
            .bind("provider", provider)
            .bind("providerAccountId", providerAccountId)
            .bind("accountKind", accountKind)
            .bind("displayName", displayName)
            .bind("profileUrn", providerSocialProfileUrn(provider, providerAccountId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Seeds a [publications] row in **DRAFT** status with **NOW** schedule mode.
     *
     * @param publicationId  Unique publication identifier.
     * @param socialAccountId Foreign key to a seeded social account.
     * @param title          Optional publication title.
     * @param bodyText       Optional publication body text.
     */
    suspend fun seedDraftPublication(
        publicationId: String,
        socialAccountId: String,
        title: String?,
        bodyText: String?,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id,
                                      status, schedule_mode, priority, title, body_text,
                                      scheduled_for, created_at, updated_at)
            VALUES (:id, :workspaceId, :authorPrincipalId, 'LINKEDIN', :socialAccountId,
                    'DRAFT', 'NOW', FALSE, :title, :bodyText,
                    NOW(), NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", publicationId)
            .bind("workspaceId", WORKSPACE_ID)
            .bind("authorPrincipalId", PRINCIPAL_ID)
            .bind("socialAccountId", socialAccountId)
            .let { spec ->
                if (title != null) spec.bind("title", title) else spec.bindNull("title", String::class.java)
            }
            .let { spec ->
                if (bodyText != null) spec.bind("bodyText", bodyText) else spec.bindNull("bodyText", String::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Seeds a [publications] row in **SCHEDULED** status with **SCHEDULED_AT** schedule mode.
     *
     * @param publicationId   Unique publication identifier.
     * @param socialAccountId Foreign key to a seeded social account.
     * @param scheduledFor    The future timestamp at which the publication should go live.
     * @param title           Optional publication title.
     * @param bodyText        Optional publication body text.
     */
    suspend fun seedScheduledPublication(
        publicationId: String,
        socialAccountId: String,
        scheduledFor: Instant,
        title: String?,
        bodyText: String?,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id,
                                      status, schedule_mode, priority, title, body_text, scheduled_for,
                                      created_at, updated_at)
            VALUES (:id, :workspaceId, :authorPrincipalId, 'LINKEDIN', :socialAccountId,
                    'SCHEDULED', 'SCHEDULED_AT', FALSE, :title, :bodyText, :scheduledFor,
                    NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", publicationId)
            .bind("workspaceId", WORKSPACE_ID)
            .bind("authorPrincipalId", PRINCIPAL_ID)
            .bind("socialAccountId", socialAccountId)
            .bind("scheduledFor", scheduledFor)
            .let { spec ->
                if (title != null) spec.bind("title", title) else spec.bindNull("title", String::class.java)
            }
            .let { spec ->
                if (bodyText != null) spec.bind("bodyText", bodyText) else spec.bindNull("bodyText", String::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
            .also { rows ->
                require(rows > 0L) { "seedScheduledPublication: INSERT affected 0 rows for id=$publicationId" }
            }
    }

    /**
     * Seeds a [publications] row in **QUEUED** status with **NOW** schedule mode.
     *
     * @param publicationId   Unique publication identifier.
     * @param socialAccountId Foreign key to a seeded social account.
     * @param title           Optional publication title.
     * @param bodyText        Optional publication body text.
     */
    suspend fun seedQueuedPublication(
        publicationId: String,
        socialAccountId: String,
        title: String?,
        bodyText: String?,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id,
                                      status, schedule_mode, priority, title, body_text,
                                      created_at, updated_at)
            VALUES (:id, :workspaceId, :authorPrincipalId, 'LINKEDIN', :socialAccountId,
                    'QUEUED', 'NOW', FALSE, :title, :bodyText,
                    NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", publicationId)
            .bind("workspaceId", WORKSPACE_ID)
            .bind("authorPrincipalId", PRINCIPAL_ID)
            .bind("socialAccountId", socialAccountId)
            .let { spec ->
                if (title != null) spec.bind("title", title) else spec.bindNull("title", String::class.java)
            }
            .let { spec ->
                if (bodyText != null) spec.bind("bodyText", bodyText) else spec.bindNull("bodyText", String::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedPublishedPublication(
        publicationId: String,
        socialAccountId: String,
        title: String?,
        bodyText: String?,
        publishedAt: Instant = Instant.now(),
    ) {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id,
                                      status, schedule_mode, priority, title, body_text,
                                      published_at, created_at, updated_at)
            VALUES (:id, :workspaceId, :authorPrincipalId, 'LINKEDIN', :socialAccountId,
                    'PUBLISHED', 'NOW', FALSE, :title, :bodyText,
                    :publishedAt, NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", publicationId)
            .bind("workspaceId", WORKSPACE_ID)
            .bind("authorPrincipalId", PRINCIPAL_ID)
            .bind("socialAccountId", socialAccountId)
            .bind("publishedAt", publishedAt)
            .let { spec ->
                if (title != null) spec.bind("title", title) else spec.bindNull("title", String::class.java)
            }
            .let { spec ->
                if (bodyText != null) spec.bind("bodyText", bodyText) else spec.bindNull("bodyText", String::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Returns the social-profile URN for a given [provider] and [providerAccountId].
     *
     * Known providers:
     * - **LINKEDIN**: `urn:li:profile:{providerAccountId}`
     * - **Other**: `urn:{provider}:profile:{providerAccountId}`
     */
    private fun providerSocialProfileUrn(provider: String, providerAccountId: String): String = when (provider) {
        "LINKEDIN" -> "urn:li:profile:$providerAccountId"
        else -> "urn:${provider.lowercase()}:profile:$providerAccountId"
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
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM waitlist_invitations",
        "DELETE FROM platform_role_assignments",
        "DELETE FROM waitlist_entries",
        "DELETE FROM waitlists WHERE id <> 'profile-tailors-launch'",
        "DELETE FROM compliance_risk_acceptances",
        "DELETE FROM compliance_control_evidences",
        // evidence_links FK references compliance_evidences, must be deleted first
        "DELETE FROM evidence_links",
        "DELETE FROM compliance_evidences",
        "DELETE FROM compliance_control_evidence_requirements",
        "DELETE FROM compliance_control_applicability_dimensions",
        "DELETE FROM compliance_control_applicability_rules",
        "DELETE FROM compliance_controls",
        // Audit
        "DELETE FROM audit_events",
        "DELETE FROM notification_events",
        // Authorization
        "DELETE FROM workspace_target_scopes",
        "DELETE FROM workspace_direct_grants",
        "DELETE FROM workspace_entitlements",
        "DELETE FROM membership_roles",
        "DELETE FROM role_permissions",
        "DELETE FROM roles",
        "DELETE FROM permissions",
        // Auth / Session
        "DELETE FROM refresh_sessions",
        "DELETE FROM local_password_credentials",
        "DELETE FROM api_key_credentials",
        "DELETE FROM service_account_credentials",
        // Ideas (reference publications + workspaces — must be deleted before both)
        "DELETE FROM ideas",
        "DELETE FROM idea_board_configs",
        // Publishing
        "DELETE FROM recurring_schedules",
        "DELETE FROM publication_asset_links",
        "DELETE FROM delivery_attempts",
        "DELETE FROM publication_jobs",
        "DELETE FROM publication_assets",
        "DELETE FROM publications",
        // Social
        "DELETE FROM social_accounts",
        "DELETE FROM social_connections",
        // Media
        "DELETE FROM media_assets",
        "DELETE FROM workspace_file_blobs",
        "DELETE FROM workspace_upload_slots",
        "DELETE FROM media_rate_limits",
        // Hashtags
        "DELETE FROM hashtag_saved_sets",
        // Workspace
        "DELETE FROM workspace_memberships",
        "DELETE FROM workspace_ownerships",
        "DELETE FROM workspaces",
        // Identity
        "DELETE FROM password_reset_notification_failures",
        "DELETE FROM password_reset_tokens",
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
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES ('$PRINCIPAL_ID', 'jwt-user@example.com', 'jwt-user', 'VERIFIED')
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

    suspend fun seedLocalAccountWithPassword(email: String) {
        val principalId = if (email.equals("user@example.com", true)) {
            PRINCIPAL_ID
        } else {
            "principal-${email.substringBefore('@')}"
        }
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) " +
                "VALUES (:id, 'USER', :subject, NULL, :display) ON CONFLICT (id) DO NOTHING",
        )
            .bind("id", principalId)
            .bind("subject", "local:$email")
            .bind("display", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username, email_status) " +
                "VALUES (:id, :email, :username, 'VERIFIED') ON CONFLICT DO NOTHING",
        )
            .bind("id", principalId)
            .bind("email", email.trim().lowercase())
            .bind("username", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO local_password_credentials (principal_id, password_hash) " +
                "VALUES (:id, :hash) ON CONFLICT (principal_id) DO NOTHING",
        )
            .bind("id", principalId)
            .bind(
                "hash",
                org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                    "OldSecurePassword123!",
                    org.springframework.security.crypto.bcrypt.BCrypt.gensalt(),
                ),
            )
            .fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedAccountWithoutPasswordCredential(email: String) {
        val principalId = "principal-${email.substringBefore('@')}"
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) " +
                "VALUES (:id, 'USER', :subject, 'https://issuer.example', :display) ON CONFLICT (id) DO NOTHING",
        )
            .bind("id", principalId)
            .bind("subject", "oauth:$email")
            .bind("display", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username, email_status) " +
                "VALUES (:id, :email, :username, 'VERIFIED') ON CONFLICT DO NOTHING",
        )
            .bind("id", principalId)
            .bind("email", email.trim().lowercase())
            .bind("username", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedActivePasswordResetToken(
        principalId: String,
        email: String,
        expiresAt: Instant = Instant.parse("2026-12-31T00:00:00Z"),
        requestedAt: Instant = Instant.parse("2026-12-01T00:00:00Z"),
    ) {
        seedLocalAccountWithPassword(email)
        lastRawPasswordResetToken = "raw-token-${java.util.UUID.randomUUID()}"
        val hash = sha256(lastRawPasswordResetToken)
        databaseClient.sql(
            "INSERT INTO password_reset_tokens (id, principal_id, token_hash, requested_at, expires_at, used_at) " +
                "VALUES (gen_random_uuid(), :principalId, :hash, :requestedAt, :expiresAt, NULL)",
        )
            .bind("principalId", principalId)
            .bind("hash", hash)
            .bind("requestedAt", requestedAt)
            .bind("expiresAt", expiresAt)
            .fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedExpiredPasswordResetToken(principalId: String, email: String) = seedActivePasswordResetToken(
        principalId,
        email,
        Instant.parse("2020-01-01T00:00:00Z"),
        Instant.parse("2019-12-31T23:00:00Z"),
    )

    suspend fun seedUsedPasswordResetToken(principalId: String, email: String) {
        seedActivePasswordResetToken(principalId, email)
        databaseClient.sql("UPDATE password_reset_tokens SET used_at = NOW() WHERE principal_id = :id")
            .bind("id", principalId).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun invalidateAllActivePasswordResetTokens(principalId: String) {
        databaseClient.sql(
            "UPDATE password_reset_tokens SET used_at = NOW() " +
                "WHERE principal_id = :id AND used_at IS NULL",
        )
            .bind("id", principalId).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedRefreshSession(principalId: String, lookupKey: String, secret: String) {
        seedLocalAccountWithPassword("user@example.com")
        databaseClient.sql(
            "INSERT INTO refresh_sessions (id, principal_id, lookup_key, token_verifier, status, expires_at) " +
                "VALUES (:id, :principalId, :lookup, :verifier, 'ACTIVE', NOW() + INTERVAL '7 days')",
        )
            .bind("id", "bdd-refresh-${java.util.UUID.randomUUID()}")
            .bind("principalId", principalId)
            .bind("lookup", lookupKey)
            .bind(
                "verifier",
                org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                    secret,
                    org.springframework.security.crypto.bcrypt.BCrypt.gensalt(),
                ),
            )
            .fetch().rowsUpdated().awaitSingle()
    }

    suspend fun updatePasswordHash(principalId: String, hash: String) {
        databaseClient.sql("UPDATE local_password_credentials SET password_hash = :hash WHERE principal_id = :id")
            .bind("id", principalId).bind("hash", hash).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun countAccountsByEmail(email: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM user_identities WHERE email = :email",
    ).bind("email", email).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    suspend fun countPasswordCredentials(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM local_password_credentials WHERE principal_id = :id",
    ).bind("id", principalId).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    suspend fun lookupPasswordHash(principalId: String): String? = databaseClient.sql(
        "SELECT password_hash FROM local_password_credentials WHERE principal_id = :id",
    ).bind("id", principalId).map { row, _ -> row.get("password_hash", String::class.java)!! }
        .one().awaitSingleOrNull()

    suspend fun countActivePasswordResetTokens(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens " +
            "WHERE principal_id = :id AND used_at IS NULL AND expires_at > NOW()",
    ).bind("id", principalId).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    suspend fun countUsedPasswordResetTokens(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens WHERE principal_id = :id AND used_at IS NOT NULL",
    ).bind("id", principalId).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    /**
     * Counts all password reset tokens in the database.
     *
     * @return The total number of password reset tokens.
     */
    suspend fun countAllPasswordResetTokens(): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens",
    ).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    /**
     * Seeds a password reset token for the cleanup account.
     *
     * @param tokenHash The hashed password reset token.
     * @param expiresAt The token expiration timestamp.
     * @param usedAt The timestamp when the token was used, or `null` for an unused token.
     */
    suspend fun seedPasswordResetToken(tokenHash: String, expiresAt: Instant, usedAt: Instant? = null) {
        seedLocalAccountWithPassword("cleanup@example.com")
        databaseClient.sql(
            """
            INSERT INTO password_reset_tokens (id, principal_id, token_hash, requested_at, expires_at, used_at)
            VALUES (gen_random_uuid(), 'principal-cleanup', :tokenHash, :requestedAt, :expiresAt, :usedAt)
            """.trimIndent(),
        )
            .bind("tokenHash", tokenHash)
            .bind("requestedAt", expiresAt.minusSeconds(1800))
            .bind("expiresAt", expiresAt)
            .let { statement ->
                if (usedAt == null) {
                    statement.bindNull("usedAt", Instant::class.java)
                } else {
                    statement.bind("usedAt", usedAt)
                }
            }
            .fetch().rowsUpdated().awaitSingle()
    }

    /**
     * Checks whether a password reset token exists for the specified hash.
     *
     * @param tokenHash The hashed password reset token to search for.
     * @return `true` if a matching token exists, `false` otherwise.
     */
    suspend fun passwordResetTokenExists(tokenHash: String): Boolean = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens WHERE token_hash = :tokenHash",
    ).bind("tokenHash", tokenHash)
        .map { row, _ -> (row.get("total") as Number).toLong() > 0 }
        .one().awaitSingle()

    /**
     * Counts active refresh sessions for a principal.
     *
     * @param principalId The principal whose active refresh sessions are counted.
     * @return The number of active refresh sessions.
     */
    suspend fun countActiveRefreshSessions(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM refresh_sessions WHERE principal_id = :id AND status = 'ACTIVE'",
    ).bind("id", principalId).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    suspend fun findRawTokenValues(): List<String> = databaseClient.sql(
        "SELECT token_hash FROM password_reset_tokens",
    ).map { row, _ -> row.get("token_hash", String::class.java)!! }.all().collectList().awaitSingle()

    suspend fun findRawPasswordRows(): List<String> = databaseClient.sql(
        "SELECT password_hash FROM local_password_credentials",
    ).map { row, _ -> row.get("password_hash", String::class.java)!! }.all().collectList().awaitSingle()

    fun lastRawToken(): String = lastRawPasswordResetToken

    private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
