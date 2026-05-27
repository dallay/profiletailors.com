package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class R2dbcSocialConnectionRepository(
    private val databaseClient: DatabaseClient,
) : SocialConnectionRepository {
    override suspend fun upsert(connection: SocialConnection): SocialConnection {
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference, connected_at, last_synced_at
            ) VALUES (
                :id, :workspaceId, :provider, :providerConnectionRef, :status, :credentialReference, :connectedAt, :lastSyncedAt
            )
            """.trimIndent(),
        )
            .bind("id", connection.id)
            .bind("workspaceId", connection.workspaceId)
            .bind("provider", connection.provider.name)
            .bind("providerConnectionRef", connection.providerConnectionRef)
            .bind("status", connection.status.name)
            .bindNullable("credentialReference", connection.credentialReference, String::class.java)
            .bindNullable("connectedAt", connection.connectedAt, java.time.Instant::class.java)
            .bindNullable("lastSyncedAt", connection.lastSyncedAt, java.time.Instant::class.java)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return connection
    }

    override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
        databaseClient.sql(
            """
            SELECT id, workspace_id, provider, provider_connection_ref, status, credential_reference, connected_at, last_synced_at, created_at
            FROM social_connections
            WHERE workspace_id = :workspaceId AND id = :id
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("id", connectionId)
            .map { row, _ ->
                SocialConnection(
                    id = requireNotNull(row.get("id", String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    provider = SocialProvider.valueOf(requireNotNull(row.get("provider", String::class.java))),
                    providerConnectionRef = requireNotNull(row.get("provider_connection_ref", String::class.java)),
                    status = SocialConnectionStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                    credentialReference = row.get("credential_reference", String::class.java),
                    connectedAt = row.get("connected_at", OffsetDateTime::class.java)?.toInstant(),
                    lastSyncedAt = row.get("last_synced_at", OffsetDateTime::class.java)?.toInstant(),
                    createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .one()
            .awaitSingleOrNull()
}

@Repository
class R2dbcSocialAccountRepository(
    private val databaseClient: DatabaseClient,
) : SocialAccountRepository {
    override suspend fun upsert(account: SocialAccount): SocialAccount {
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, profile_urn, status
            ) VALUES (
                :id, :socialConnectionId, :workspaceId, :provider, :providerAccountId, :accountType, :displayName, :profileUrn, :status
            )
            """.trimIndent(),
        )
            .bind("id", account.id)
            .bind("socialConnectionId", account.socialConnectionId)
            .bind("workspaceId", account.workspaceId)
            .bind("provider", account.provider.name)
            .bind("providerAccountId", account.providerAccountId)
            .bind("accountType", account.kind.name)
            .bind("displayName", account.displayName)
            .bindNullable("profileUrn", account.profileUrn, String::class.java)
            .bind("status", account.status.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return account
    }

    override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? =
        databaseClient.sql(
            """
            SELECT id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, profile_urn, status, created_at
            FROM social_accounts
            WHERE workspace_id = :workspaceId AND id = :id
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("id", accountId)
            .map { row, _ ->
                SocialAccount(
                    id = requireNotNull(row.get("id", String::class.java)),
                    socialConnectionId = requireNotNull(row.get("social_connection_id", String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    provider = SocialProvider.valueOf(requireNotNull(row.get("provider", String::class.java))),
                    providerAccountId = requireNotNull(row.get("provider_account_id", String::class.java)),
                    kind = SocialAccountKind.valueOf(requireNotNull(row.get("account_type", String::class.java))),
                    displayName = requireNotNull(row.get("display_name", String::class.java)),
                    profileUrn = row.get("profile_urn", String::class.java),
                    status = SocialConnectionStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                    createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .one()
            .awaitSingleOrNull()
}

private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: String?,
    type: Class<String>,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
    value?.let { bind(name, it) } ?: bindNull(name, type)

private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: java.time.Instant?,
    type: Class<java.time.Instant>,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
    value?.let { bind(name, it) } ?: bindNull(name, type)
