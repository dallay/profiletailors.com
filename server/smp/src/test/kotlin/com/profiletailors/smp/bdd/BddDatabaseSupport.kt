package com.profiletailors.smp.bdd.glue

import com.profiletailors.smp.publishing.domain.SocialPost
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
import java.util.UUID

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
    private val consentCleanupStatements = listOf(
        "DELETE FROM consent_record_events",
        "DELETE FROM consent_records",
    )

    companion object {
        const val WORKSPACE_ID = "10000000-0000-4000-8000-000000000001"
        const val PRINCIPAL_ID = "20000000-0000-4000-8000-000000000001"
        const val RESOURCE_ID = "30000000-0000-4000-8000-000000000001"
        const val WORKSPACE_HEADER = "X-Workspace-Id"
        const val QA_PRINCIPAL_ID = "20000000-0000-4000-8000-000000000003"
        const val QA_EMAIL = "qa@profiletailors.com"
        const val QA_PASSWORD = "FinalQaPassword789!"
        const val QA_WORKSPACE_ID = "10000000-0000-4000-8000-000000000003"
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
        const val MEDIA_ASSET_ID = "40000000-0000-4000-8000-000000000001"
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

    /**
     * Resets the BDD database to the Liquibase baseline and restores required test data.
     */
    suspend fun resetDatabase() {
        applyLiquibaseBaseline()
        cleanupStatements().forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
        restoreRequiredBaselineRoles()
        seedQaUser()
    }

    private suspend fun seedQaUser() {
        val exists: String? = databaseClient.sql(
            "SELECT principal_id FROM user_identities WHERE principal_id = :id",
        )
            .bind("id", UUID.fromString(QA_PRINCIPAL_ID))
            .map { row, _ -> requireNotNull(row.get("principal_id", UUID::class.java)).toString() }
            .one()
            .awaitSingleOrNull()
        if (exists != null) return
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) " +
                "VALUES (:id, 'USER', :email, 'local', 'QA User')",
        )
            .bind("id", UUID.fromString(QA_PRINCIPAL_ID))
            .bind("email", QA_EMAIL)
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username, email_status) " +
                "VALUES (:id, :email, 'qa', 'VERIFIED')",
        )
            .bind("id", UUID.fromString(QA_PRINCIPAL_ID))
            .bind("email", QA_EMAIL)
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO local_password_credentials (principal_id, password_hash) " +
                "VALUES (:id, :hash)",
        )
            .bind("id", UUID.fromString(QA_PRINCIPAL_ID))
            .bind(
                "hash",
                org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                    QA_PASSWORD,
                    org.springframework.security.crypto.bcrypt.BCrypt.gensalt(),
                ),
            )
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES (:id, 'QA Workspace', 'ACTIVE', 'flask')",
        )
            .bind("id", UUID.fromString(QA_WORKSPACE_ID))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by) " +
                "VALUES (:workspaceId, :ownerId, 'USER', :ownerId)",
        )
            .bind("workspaceId", UUID.fromString(QA_WORKSPACE_ID))
            .bind("ownerId", UUID.fromString(QA_PRINCIPAL_ID))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) " +
                "VALUES ('50000000-0000-4000-8000-000000000003', :workspaceId, :ownerId, 'USER', 'ACTIVE')",
        )
            .bind("workspaceId", UUID.fromString(QA_WORKSPACE_ID))
            .bind("ownerId", UUID.fromString(QA_PRINCIPAL_ID))
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun restoreRequiredBaselineRoles() {
        databaseClient.sql(
            """
            INSERT INTO roles (id, role_key, category)
            VALUES ('6ce35f6c-2f1e-5702-9b59-d7a0e9402006', 'WORKSPACE_OWNER', 'WORKSPACE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedEntitledAuthorizedMember() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = true)
        seedRolePermission(
            permissionId = "a1c2ad6d-c637-5205-a0af-9bb95c4239b2",
            permissionKey = WORKSPACE_ACCESS_PERMISSION,
        )
    }

    suspend fun seedEntitledMemberWithoutAccessPermission() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = true)
        seedRolePermission(
            permissionId = "1536040a-1a3c-58a6-943e-c66a8c120590",
            permissionKey = "workspace:members:manage",
        )
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
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
            .one()
            .awaitSingleOrNull()

        val permissionId = if (existingPermission != null) {
            existingPermission
        } else {
            val newId = if (permissionKey ==
                WORKSPACE_ACCESS_PERMISSION
            ) {
                "a1c2ad6d-c637-5205-a0af-9bb95c4239b2"
            } else {
                UUID.randomUUID().toString()
            }
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)")
                .bind("id", UUID.fromString(newId))
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
            .bind("id", UUID.randomUUID())
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("principalId", UUID.fromString(PRINCIPAL_ID))
            .bind("permissionId", UUID.fromString(permissionId))
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
            principalId = "6dd7b370-46f9-5f08-9a95-ba34f26bd8e7",
            principalType = "USER",
            subject = "owner-2-subject",
            provider = "https://issuer.example",
            displayIdentity = "owner-2",
        )
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('6dd7b370-46f9-5f08-9a95-ba34f26bd8e7', 'owner2@example.com', 'owner2')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type)
            VALUES ('$WORKSPACE_ID', '6dd7b370-46f9-5f08-9a95-ba34f26bd8e7', 'USER')
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
            principalId = "e6d7cb4c-50e4-52e7-9a54-76436681215a",
            principalType = "USER",
            subject = "member-2-subject",
            provider = "https://issuer.example",
            displayIdentity = "member-2",
        )
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('e6d7cb4c-50e4-52e7-9a54-76436681215a', 'member2@example.com', 'member2')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('80cf5168-eff9-5abd-95c7-aae94ea2d331', '$WORKSPACE_ID', 'e6d7cb4c-50e4-52e7-9a54-76436681215a', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedMemberWithPreviewPermission() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = false)
        seedRolePermission(
            permissionId = "f8b1076a-d58a-5ab3-98d1-840d86a28384",
            permissionKey = RESOURCE_PREVIEW_PERMISSION,
        )
    }

    suspend fun seedMemberWithAuditReadPermission() {
        seedUserPrincipal()
        seedWorkspaceAndRole(entitled = false)
        seedRolePermission(
            permissionId = "73cf192f-653d-59d2-b3a9-6fde1f5a51d1",
            permissionKey = WORKSPACE_AUDIT_READ_PERMISSION,
        )
    }

    suspend fun seedAuthorizedServiceAccount(entitled: Boolean, credentialStatus: String = "ACTIVE") {
        seedPrincipal(
            principalId = "76fb2b6a-c363-5719-8ccb-0799a29bf94e",
            principalType = "SERVICE_ACCOUNT",
            subject = "service-account-subject",
            provider = "https://issuer.example",
            displayIdentity = "scheduler-bot",
        )
        seedWorkspaceAndRole(
            principalId = "76fb2b6a-c363-5719-8ccb-0799a29bf94e",
            principalType = "SERVICE_ACCOUNT",
            entitled = entitled,
        )
        seedRolePermission(
            permissionId = "a1c2ad6d-c637-5205-a0af-9bb95c4239b2",
            permissionKey = WORKSPACE_ACCESS_PERMISSION,
        )
        seedServiceAccountCredential(status = credentialStatus)
    }

    suspend fun seedAuthorizedApiKeyPrincipal(entitled: Boolean, credentialStatus: String = "ACTIVE") {
        seedPrincipal(
            principalId = "10c6f425-30a0-59ad-a17d-05b77a4708f5",
            principalType = "API_KEY",
            subject = "api-key-subject",
            provider = null,
            displayIdentity = "integration-key",
        )
        seedWorkspaceAndRole(
            principalId = "10c6f425-30a0-59ad-a17d-05b77a4708f5",
            principalType = "API_KEY",
            entitled = entitled,
        )
        seedRolePermission(
            permissionId = "a1c2ad6d-c637-5205-a0af-9bb95c4239b2",
            permissionKey = WORKSPACE_ACCESS_PERMISSION,
        )
        seedApiKeyCredential(status = credentialStatus)
    }

    suspend fun seedTargetScope(allowedResourceId: String) {
        databaseClient.sql(
            """
            INSERT INTO workspace_target_scopes (
                id, workspace_id, principal_id, principal_type, permission_id,
                target_resource_type, allowed_target_ids_json
            ) VALUES (
                '90ec297a-2115-5671-9364-7befed7bcd48', '$WORKSPACE_ID', '$PRINCIPAL_ID', 'USER',
                'f8b1076a-d58a-5ab3-98d1-840d86a28384', 'RESOURCE', :allowedTargetIdsJson
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
        .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
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
        val successorCredentialReference = "00000000-0000-4000-8000-000000000041"
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
                :id, '10c6f425-30a0-59ad-a17d-05b77a4708f5', :lookupKey, :keyPrefix, :verifier, 'ACTIVE', NULL,
                NULL, '7df8d7ff-4304-5a31-8bb4-d830c7053bd2', NULL
            )
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(successorCredentialReference))
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
            WHERE id = '7df8d7ff-4304-5a31-8bb4-d830c7053bd2'
            """.trimIndent(),
        )
            .bind("successorCredentialReference", UUID.fromString(successorCredentialReference))
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
            .bind("principalId", UUID.fromString(PRINCIPAL_ID))
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
            .bind("id", UUID.fromString(workspaceId))
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
            .one()
            .awaitSingleOrNull()
        if (exists != null) return
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES (:id, 'Profile Tailors', 'ACTIVE', NULL)",
        )
            .bind("id", UUID.fromString(workspaceId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedAuthenticatedUserWithWorkspace(
        email: String = "yuniel@example.com",
        principalId: String = PRINCIPAL_ID,
        workspaceId: String = WORKSPACE_ID,
    ) {
        val principalExists: String? = databaseClient.sql(
            "SELECT id FROM principals WHERE id = :id",
        )
            .bind("id", UUID.fromString(principalId))
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
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
            .bind("id", UUID.fromString(principalId))
            .map { row, _ -> requireNotNull(row.get("principal_id", UUID::class.java)).toString() }
            .one()
            .awaitSingleOrNull()
        if (identityExists == null) {
            databaseClient.sql(
                """
                INSERT INTO user_identities (principal_id, email, username)
                VALUES (:principalId, :email, :username)
                """.trimIndent(),
            )
                .bind("principalId", UUID.fromString(principalId))
                .bind("email", email)
                .bind("username", email.substringBefore('@'))
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
        val workspaceExists: String? = databaseClient.sql(
            "SELECT id FROM workspaces WHERE id = :id",
        )
            .bind("id", UUID.fromString(workspaceId))
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
            .one()
            .awaitSingleOrNull()
        if (workspaceExists == null) {
            seedWorkspace(workspaceId)
        }
        seedWorkspaceMembershipIdempotent(principalId, workspaceId)
    }

    suspend fun seedWorkspaceMembership(principalId: String, workspaceId: String = WORKSPACE_ID) {
        val membershipId = UUID.randomUUID()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES (:id, :workspaceId, :principalId, 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .bind("id", membershipId)
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("principalId", UUID.fromString(principalId))
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
            .bind("assetId", UUID.fromString(assetId))
            .bind("workspaceId", UUID.fromString(workspaceId))
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
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("fileHash", fileHash)
            .map { row, _ -> requireNotNull(row.get("workspace_id", UUID::class.java)).toString() }
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
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("fileHash", fileHash)
            .bind("storageKey", key)
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun countMediaAssets(workspaceId: String = WORKSPACE_ID): Long =
        databaseClient.sql("SELECT COUNT(*) AS total FROM media_assets WHERE workspace_id = :workspaceId")
            .bind("workspaceId", UUID.fromString(workspaceId))
            .map { row, _ -> (row.get("total") as Number).toLong() }
            .one()
            .awaitSingle()

    suspend fun findMediaAssetStatus(assetId: String, workspaceId: String = WORKSPACE_ID): String? = databaseClient.sql(
        """
            SELECT status FROM media_assets
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
        """.trimIndent(),
    )
        .bind("assetId", UUID.fromString(assetId))
        .bind("workspaceId", UUID.fromString(workspaceId))
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
        .bind("principalId", UUID.fromString(principalId))
        .map { row, _ -> requireNotNull(row.get("workspace_id", UUID::class.java)).toString() }
        .one()
        .awaitSingleOrNull()

    suspend fun seedWorkspaceMembershipForWorkspace(principalId: String, workspaceId: String) {
        seedWorkspaceMembershipIdempotent(principalId, workspaceId)
    }

    suspend fun seedWorkspaceMembershipIdempotent(principalId: String, workspaceId: String = WORKSPACE_ID) {
        val membershipId = UUID.randomUUID()
        val exists: String? = databaseClient.sql(
            "SELECT id FROM workspace_memberships WHERE principal_id = :principalId AND workspace_id = :workspaceId",
        )
            .bind("principalId", UUID.fromString(principalId))
            .bind("workspaceId", UUID.fromString(workspaceId))
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
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
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("principalId", UUID.fromString(principalId))
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
                '0f974b95-b4af-52b3-901a-21333615a820', 'MUTATION', 'workspace.owner.add', NULL, NULL, NULL, '2c19bb64-6aee-5fd7-84c8-d6b49e51e46e',
                '$WORKSPACE_ID', 'WORKSPACE_OWNER', '6dd7b370-46f9-5f08-9a95-ba34f26bd8e7', 'SUCCESS', NULL, '[]', '{"ownerPrincipalId":"6dd7b370-46f9-5f08-9a95-ba34f26bd8e7"}',
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
            .bind("id", UUID.fromString(connectionId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("provider", provider)
            .bind("providerConnectionRef", "ref-$connectionId")
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Creates a social account associated with an existing social connection.
     *
     * @param accountId Unique account identifier.
     * @param connectionId Identifier of the associated social connection.
     * @param provider Social provider name.
     * @param providerAccountId Account identifier assigned by the provider.
     * @param accountKind Type of social account.
     * @param displayName Human-readable account name.
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
            .bind("id", UUID.fromString(accountId))
            .bind("connectionId", UUID.fromString(connectionId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
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
     * Ensures that a social connection exists for a workspace.
     *
     * @param connectionId The social connection identifier.
     * @param workspaceId The workspace identifier.
     * @param provider The social provider.
     * @param status The connection status.
     */
    suspend fun ensureSocialConnection(connectionId: String, workspaceId: String, provider: String, status: String) {
        databaseClient.sql(
            """
            INSERT INTO social_connections (id, workspace_id, provider, provider_connection_ref, status,
                                            credential_reference, connected_at, last_synced_at, created_at)
            VALUES (:id, :workspaceId, :provider, :providerConnectionRef, :status,
                    NULL, NOW(), NOW(), NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(connectionId))
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("provider", provider)
            .bind("providerConnectionRef", "ref-$connectionId")
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Ensures that a social account exists for the specified workspace and connection.
     *
     * @param accountId The social account identifier.
     * @param connectionId The social connection identifier.
     * @param workspaceId The workspace identifier.
     * @param provider The social provider name.
     * @param providerAccountId The account identifier assigned by the provider.
     * @param accountKind The type of social account.
     * @param displayName The account's display name.
     */
    suspend fun ensureSocialAccount(
        accountId: String,
        connectionId: String,
        workspaceId: String,
        provider: String,
        providerAccountId: String,
        accountKind: String,
        displayName: String,
    ) {
        ensureSocialConnection(connectionId, workspaceId, provider, "ACTIVE")
        databaseClient.sql(
            """
            INSERT INTO social_accounts (id, social_connection_id, workspace_id, provider, provider_account_id,
                                         account_type, display_name, profile_urn, status, created_at)
            VALUES (:id, :connectionId, :workspaceId, :provider, :providerAccountId,
                    :accountKind, :displayName, :profileUrn, 'ACTIVE', NOW())
            ON CONFLICT (id, workspace_id) DO NOTHING
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(accountId))
            .bind("connectionId", UUID.fromString(connectionId))
            .bind("workspaceId", UUID.fromString(workspaceId))
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
     * Seeds a social content post from a [SocialPost] domain object.
     *
     * @param post The social post to persist.
     */
    suspend fun seedSocialContentPost(post: SocialPost) {
        val lastModified = post.lastModifiedAt ?: post.publishedAt
        databaseClient.sql(
            """
            INSERT INTO social_content_posts (
                id, workspace_id, social_account_id, provider, external_post_id,
                published_at, last_modified_at, body, origin, local_publication_id,
                lifecycle, expires_at, created_at, updated_at
            ) VALUES (
                :id, :workspaceId, :socialAccountId, :provider, :externalPostId,
                :publishedAt, :lastModifiedAt, :body, :origin, :localPublicationId,
                :lifecycle, :expiresAt, :createdAt, :updatedAt
            )
            ON CONFLICT (workspace_id, provider, social_account_id, external_post_id)
            DO UPDATE SET published_at = EXCLUDED.published_at
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID())
            .bind("workspaceId", UUID.fromString(post.scope.value))
            .bind("socialAccountId", UUID.fromString(post.actorId))
            .bind("provider", post.provider.name)
            .bind("externalPostId", post.externalPostId.value)
            .bind("publishedAt", post.publishedAt)
            .bind("lastModifiedAt", lastModified)
            .let { spec ->
                val body = post.body
                if (body != null) spec.bind("body", body) else spec.bindNull("body", String::class.java)
            }
            .bind("origin", post.origin.name)
            .let { spec ->
                val localPubId = post.localPublicationId
                if (localPubId != null) {
                    spec.bind("localPublicationId", UUID.fromString(localPubId))
                } else {
                    spec.bindNull("localPublicationId", UUID::class.java)
                }
            }
            .bind("lifecycle", post.lifecycle.name)
            .bind("expiresAt", post.expiresAt)
            .bind("createdAt", Instant.now())
            .bind("updatedAt", Instant.now())
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
            .bind("id", UUID.fromString(publicationId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("authorPrincipalId", UUID.fromString(PRINCIPAL_ID))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
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
            .bind("id", UUID.fromString(publicationId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("authorPrincipalId", UUID.fromString(PRINCIPAL_ID))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
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
            .bind("id", UUID.fromString(publicationId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("authorPrincipalId", UUID.fromString(PRINCIPAL_ID))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
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
            .bind("id", UUID.fromString(publicationId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("authorPrincipalId", UUID.fromString(PRINCIPAL_ID))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
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
     * Seeds a [publications] row in **FAILED** status with NOW schedule mode and a
     * populated `failed_at` / `last_error_code`.
     */
    suspend fun seedFailedPublication(publicationId: String, socialAccountId: String) {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id,
                                      status, schedule_mode, priority, title, body_text,
                                      failed_at, last_error_code, last_error_message,
                                      created_at, updated_at)
            VALUES (:id, :workspaceId, :authorPrincipalId, 'LINKEDIN', :socialAccountId,
                    'FAILED', 'NOW', FALSE, 'failed title', 'failed body',
                    :failedAt, 'LINKEDIN_API_ERROR', 'simulated failure for BDD',
                    NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(publicationId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("authorPrincipalId", UUID.fromString(PRINCIPAL_ID))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
            .bind("failedAt", Instant.now().minusSeconds(60))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Seeds a [publications] row in **CANCELLED** status with NOW schedule mode.
     */
    suspend fun seedCancelledPublication(publicationId: String, socialAccountId: String) {
        databaseClient.sql(
            """
            INSERT INTO publications (id, workspace_id, author_principal_id, provider, social_account_id,
                                      status, schedule_mode, priority, title, body_text,
                                      created_at, updated_at)
            VALUES (:id, :workspaceId, :authorPrincipalId, 'LINKEDIN', :socialAccountId,
                    'CANCELLED', 'NOW', FALSE, 'cancelled title', 'cancelled body',
                    NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(publicationId))
            .bind("workspaceId", UUID.fromString(WORKSPACE_ID))
            .bind("authorPrincipalId", UUID.fromString(PRINCIPAL_ID))
            .bind("socialAccountId", UUID.fromString(socialAccountId))
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
                'b6fe3450-c487-5a70-9267-8ad68e83ae76', '76fb2b6a-c363-5719-8ccb-0799a29bf94e', 'https://issuer.example',
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

    /**
     * Seeds an API key credential with the specified status.
     *
     * @param status The credential status to store.
     */
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
                '7df8d7ff-4304-5a31-8bb4-d830c7053bd2', '10c6f425-30a0-59ad-a17d-05b77a4708f5', 'ptk_lookup', 'ptk_lookup',
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

    private fun shortLinksCleanupStatements(): List<String> = listOf(
        "DELETE FROM shortlink_idempotency",
        "DELETE FROM link_clicks",
        "DELETE FROM links",
    )

    private fun cleanupStatements(): List<String> = consentCleanupStatements + shortLinksCleanupStatements() + listOf(
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM invitations",
        "DELETE FROM waitlist_invitations",
        "DELETE FROM platform_role_assignments",
        "DELETE FROM waitlist_entries",
        "DELETE FROM waitlists WHERE id <> 'ea2c112c-d7f8-5e73-a4a8-ee4c372ed124'",
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
        "DELETE FROM bulk_import_rows",
        "DELETE FROM bulk_import_jobs",
        "DELETE FROM publications",
        // Social
        "DELETE FROM social_content_reply_commands",
        "DELETE FROM social_content_webhook_events",
        "DELETE FROM social_content_sync_checkpoints",
        "DELETE FROM social_content_payload_cache",
        "DELETE FROM social_content_comments",
        "DELETE FROM social_content_posts",
        "DELETE FROM social_content_actor_capabilities",
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

    /**
     * Applies the Liquibase database schema baseline.
     */
    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            liquibaseJdbcUrl,
            liquibaseUsername,
            liquibasePassword,
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET profiletailors.uuid_cutover_approved = 'on'")
            }
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts("uuid-native-identifiers"), LabelExpression())
        }
    }

    private suspend fun seedUserPrincipal() {
        val principalExists: String? = databaseClient.sql(
            "SELECT id FROM principals WHERE id = :id",
        )
            .bind("id", UUID.fromString(PRINCIPAL_ID))
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
            .one()
            .awaitSingleOrNull()
        val exists: String? = databaseClient.sql(
            "SELECT principal_id FROM user_identities WHERE principal_id = :id",
        )
            .bind("id", UUID.fromString(PRINCIPAL_ID))
            .map { row, _ -> requireNotNull(row.get("principal_id", UUID::class.java)).toString() }
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
            .bind("principalId", UUID.fromString(principalId))
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
            VALUES ('3b933158-0683-5e2c-b2ab-453853482dd2', '$WORKSPACE_ID', :principalId, :principalType, 'ACTIVE')
            """.trimIndent(),
        )
            .bind("principalId", UUID.fromString(principalId))
            .bind("principalType", principalType)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO roles (id, role_key, category)
            VALUES ('9f702b07-3409-55a7-92ca-d9019e382df3', 'member', 'WORKSPACE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO membership_roles (membership_id, role_id)
            VALUES ('3b933158-0683-5e2c-b2ab-453853482dd2', '9f702b07-3409-55a7-92ca-d9019e382df3')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        if (entitled) {
            databaseClient.sql(
                """
                INSERT INTO workspace_entitlements (id, workspace_id, entitlement_key, enabled)
                VALUES ('c7d20521-b394-5d16-a54b-2d6f150e2c04', '$WORKSPACE_ID', '$WORKSPACE_ACCESS_ENTITLEMENT', TRUE)
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
        }
    }

    private suspend fun seedRolePermission(permissionId: String, permissionKey: String) {
        databaseClient.sql(
            "INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)",
        )
            .bind("id", UUID.fromString(permissionId))
            .bind("permissionKey", permissionKey)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES ('9f702b07-3409-55a7-92ca-d9019e382df3', :permissionId)
            """.trimIndent(),
        )
            .bind("permissionId", UUID.fromString(permissionId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun seedLocalAccountWithPassword(email: String) {
        val principalId = if (email.equals("user@example.com", true)) {
            PRINCIPAL_ID
        } else {
            UUID.nameUUIDFromBytes("bdd-local:$email".toByteArray(Charsets.UTF_8)).toString()
        }
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) " +
                "VALUES (:id, 'USER', :subject, NULL, :display) ON CONFLICT (id) DO NOTHING",
        )
            .bind("id", UUID.fromString(principalId))
            .bind("subject", "local:$email")
            .bind("display", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username, email_status) " +
                "VALUES (:id, :email, :username, 'VERIFIED') ON CONFLICT DO NOTHING",
        )
            .bind("id", UUID.fromString(principalId))
            .bind("email", email.trim().lowercase())
            .bind("username", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO local_password_credentials (principal_id, password_hash) " +
                "VALUES (:id, :hash) ON CONFLICT (principal_id) DO NOTHING",
        )
            .bind("id", UUID.fromString(principalId))
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
        val principalId = UUID.nameUUIDFromBytes("bdd-local:$email".toByteArray(Charsets.UTF_8)).toString()
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) " +
                "VALUES (:id, 'USER', :subject, 'https://issuer.example', :display) ON CONFLICT (id) DO NOTHING",
        )
            .bind("id", UUID.fromString(principalId))
            .bind("subject", "oauth:$email")
            .bind("display", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username, email_status) " +
                "VALUES (:id, :email, :username, 'VERIFIED') ON CONFLICT DO NOTHING",
        )
            .bind("id", UUID.fromString(principalId))
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
            .bind("principalId", UUID.fromString(principalId))
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
            .bind("id", UUID.fromString(principalId)).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun invalidateAllActivePasswordResetTokens(principalId: String) {
        databaseClient.sql(
            "UPDATE password_reset_tokens SET used_at = NOW() " +
                "WHERE principal_id = :id AND used_at IS NULL",
        )
            .bind("id", UUID.fromString(principalId)).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun seedRefreshSession(principalId: String, lookupKey: String, secret: String) {
        seedLocalAccountWithPassword("user@example.com")
        databaseClient.sql(
            "INSERT INTO refresh_sessions (id, principal_id, lookup_key, token_verifier, status, expires_at) " +
                "VALUES (:id, :principalId, :lookup, :verifier, 'ACTIVE', NOW() + INTERVAL '7 days')",
        )
            .bind("id", UUID.randomUUID())
            .bind("principalId", UUID.fromString(principalId))
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
            .bind("id", UUID.fromString(principalId)).bind("hash", hash).fetch().rowsUpdated().awaitSingle()
    }

    suspend fun countAccountsByEmail(email: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM user_identities WHERE email = :email",
    ).bind("email", email).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    suspend fun countIdentities(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM user_identities WHERE principal_id = :id",
    ).bind("id", UUID.fromString(principalId)).map { row, _ ->
        (row.get("total") as Number).toLong()
    }.one().awaitSingle()

    suspend fun countInvitationsByEmail(email: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM invitations WHERE invited_email_normalized = :email",
    ).bind("email", email.trim().lowercase())
        .map { row, _ -> (row.get("total") as Number).toLong() }
        .one()
        .awaitSingle()

    suspend fun countPasswordCredentials(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM local_password_credentials WHERE principal_id = :id",
    ).bind("id", UUID.fromString(principalId)).map { row, _ ->
        (row.get("total") as Number).toLong()
    }.one().awaitSingle()

    suspend fun countWorkspaces(workspaceId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM workspaces WHERE id = :id",
    ).bind("id", UUID.fromString(workspaceId)).map { row, _ ->
        (row.get("total") as Number).toLong()
    }.one().awaitSingle()

    suspend fun countWorkspaceMemberships(principalId: String, workspaceId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM workspace_memberships " +
            "WHERE principal_id = :principalId AND workspace_id = :workspaceId",
    )
        .bind("principalId", UUID.fromString(principalId))
        .bind("workspaceId", UUID.fromString(workspaceId))
        .map { row, _ -> (row.get("total") as Number).toLong() }
        .one()
        .awaitSingle()

    suspend fun lookupPasswordHash(principalId: String): String? = databaseClient.sql(
        "SELECT password_hash FROM local_password_credentials WHERE principal_id = :id",
    ).bind("id", UUID.fromString(principalId)).map { row, _ ->
        requireNotNull(row.get("password_hash", String::class.java))
    }
        .one().awaitSingleOrNull()

    suspend fun countActivePasswordResetTokens(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens " +
            "WHERE principal_id = :id AND used_at IS NULL AND expires_at > NOW()",
    ).bind("id", UUID.fromString(principalId)).map { row, _ ->
        (row.get("total") as Number).toLong()
    }.one().awaitSingle()

    suspend fun countUsedPasswordResetTokens(principalId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens WHERE principal_id = :id AND used_at IS NOT NULL",
    ).bind("id", UUID.fromString(principalId)).map { row, _ ->
        (row.get("total") as Number).toLong()
    }.one().awaitSingle()

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
            VALUES (gen_random_uuid(), :principalId, :tokenHash, :requestedAt, :expiresAt, :usedAt)
            """.trimIndent(),
        )
            .bind("principalId", UUID.nameUUIDFromBytes("bdd-local:cleanup@example.com".toByteArray(Charsets.UTF_8)))
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
    ).bind("id", UUID.fromString(principalId)).map { row, _ ->
        (row.get("total") as Number).toLong()
    }.one().awaitSingle()

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
