package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.ConnectedSocialChannel
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class R2dbcSocialConnectionRepository(private val databaseClient: DatabaseClient) : SocialConnectionRepository {
    override suspend fun upsert(connection: SocialConnection): SocialConnection = upsertPostgres(connection)

    private suspend fun upsertPostgres(connection: SocialConnection): SocialConnection = databaseClient.sql(
        """
        INSERT INTO social_connections (
            id, workspace_id, provider, provider_connection_ref, status, credential_reference, connected_at, last_synced_at
        ) VALUES (
            :id, :workspaceId, :provider, :providerConnectionRef, :status, :credentialReference, :connectedAt, :lastSyncedAt
        )
        ON CONFLICT (workspace_id, provider, provider_connection_ref) DO UPDATE
        SET status = EXCLUDED.status,
            credential_reference = EXCLUDED.credential_reference,
            connected_at = EXCLUDED.connected_at,
            last_synced_at = EXCLUDED.last_synced_at
        RETURNING id, workspace_id, provider, provider_connection_ref, status, credential_reference,
                  connected_at, last_synced_at, created_at
        """.trimIndent(),
    )
        .bindSocialConnection(connection)
        .map { row, _ -> row.toSocialConnection() }
        .one()
        .awaitSingle()

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
            .map { row, _ -> row.toSocialConnection() }
            .one()
            .awaitSingleOrNull()
}

@Repository
class R2dbcSocialAccountRepository(
    private val databaseClient: DatabaseClient,
    private val meterRegistry: MeterRegistry,
) : SocialAccountRepository {
    private val avatarPersistedCounter: Counter = Counter.builder("publishing.linkedin.avatar.persisted")
        .description("Number of times a LinkedIn avatar URL has been successfully persisted")
        .register(meterRegistry)

    override suspend fun upsert(account: SocialAccount): SocialAccount = upsertPostgres(account).also {
        if (account.avatarUrl != null) {
            avatarPersistedCounter.increment()
        }
    }

    private suspend fun upsertPostgres(account: SocialAccount): SocialAccount = databaseClient.sql(
        """
        INSERT INTO social_accounts (
            id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, profile_urn, avatar_url, status
        ) VALUES (
            :id, :socialConnectionId, :workspaceId, :provider, :providerAccountId, :accountType, :displayName, :profileUrn, :avatarUrl, :status
        )
        ON CONFLICT (workspace_id, provider, provider_account_id) DO UPDATE
        SET social_connection_id = EXCLUDED.social_connection_id,
            account_type = EXCLUDED.account_type,
            display_name = EXCLUDED.display_name,
            profile_urn = EXCLUDED.profile_urn,
            avatar_url = EXCLUDED.avatar_url,
            status = EXCLUDED.status
        RETURNING id, social_connection_id, workspace_id, provider, provider_account_id, account_type,
                  display_name, profile_urn, avatar_url, status, created_at
        """.trimIndent(),
    )
        .bindSocialAccount(account)
        .map { row, _ -> row.toSocialAccount() }
        .one()
        .awaitSingle()

    override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? =
        databaseClient.sql(
            """
            SELECT id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, profile_urn, avatar_url, status, created_at
            FROM social_accounts
            WHERE workspace_id = :workspaceId AND id = :id
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("id", accountId)
            .map { row, _ -> row.toSocialAccount() }
            .one()
            .awaitSingleOrNull()

    override suspend fun findFirstActiveByWorkspace(workspaceId: String): SocialAccount? = databaseClient.sql(
        """
        SELECT id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, profile_urn, avatar_url, status, created_at
        FROM social_accounts
        WHERE workspace_id = :workspaceId AND status = 'ACTIVE'
        ORDER BY created_at ASC
        LIMIT 1
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .map { row, _ -> row.toSocialAccount() }
        .one()
        .awaitSingleOrNull()
}

@Suppress("StringLiteralDuplication")
@Repository
class R2dbcConnectedSocialChannelReadRepository(private val databaseClient: DatabaseClient) :
    ConnectedSocialChannelReadRepository {
    override suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<SocialConnectionStatus>,
    ): List<ConnectedSocialChannel> {
        val effectiveStatuses = statuses.ifEmpty { setOf(SocialConnectionStatus.ACTIVE) }.toList()
        val placeholders = effectiveStatuses.mapIndexed { index, _ -> ":status$index" }.joinToString(", ")
        var spec = databaseClient.sql(
            """
            SELECT
                a.id AS social_account_id,
                a.social_connection_id AS connection_id,
                a.provider,
                a.account_type,
                a.display_name,
                a.status,
                a.profile_urn,
                a.avatar_url,
                c.connected_at,
                c.last_synced_at
            FROM social_accounts a
            JOIN social_connections c
              ON c.id = a.social_connection_id
             AND c.workspace_id = a.workspace_id
            WHERE a.workspace_id = :workspaceId
              AND a.status IN ($placeholders)
              AND c.status IN ($placeholders)
            ORDER BY c.connected_at DESC NULLS LAST, a.created_at DESC
            """.trimIndent(),
        ).bind("workspaceId", workspaceId)
        effectiveStatuses.forEachIndexed { index, status ->
            spec = spec.bind("status$index", status.name)
        }
        return spec.map { row, _ ->
            ConnectedSocialChannel(
                socialAccountId = requireNotNull(row.get("social_account_id", String::class.java)),
                connectionId = requireNotNull(row.get("connection_id", String::class.java)),
                provider = SocialProvider.valueOf(requireNotNull(row.get("provider", String::class.java))),
                accountKind = SocialAccountKind.valueOf(requireNotNull(row.get("account_type", String::class.java))),
                displayName = requireNotNull(row.get("display_name", String::class.java)),
                status = SocialConnectionStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                profileUrn = row.get("profile_urn", String::class.java),
                avatarUrl = row.get("avatar_url", String::class.java),
                connectedAt = row.get("connected_at", OffsetDateTime::class.java)?.toInstant(),
                lastSyncedAt = row.get("last_synced_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
            .all()
            .collectList()
            .awaitSingle()
    }
}

private fun Readable.toSocialConnection(): SocialConnection = SocialConnection(
    id = requireNotNull(get("id", String::class.java)),
    workspaceId = requireNotNull(get("workspace_id", String::class.java)),
    provider = SocialProvider.valueOf(requireNotNull(get("provider", String::class.java))),
    providerConnectionRef = requireNotNull(get("provider_connection_ref", String::class.java)),
    status = SocialConnectionStatus.valueOf(requireNotNull(get("status", String::class.java))),
    credentialReference = get("credential_reference", String::class.java),
    connectedAt = get("connected_at", OffsetDateTime::class.java)?.toInstant(),
    lastSyncedAt = get("last_synced_at", OffsetDateTime::class.java)?.toInstant(),
    createdAt = get("created_at", OffsetDateTime::class.java)?.toInstant(),
)

private fun Readable.toSocialAccount(): SocialAccount = SocialAccount(
    id = requireNotNull(get("id", String::class.java)),
    socialConnectionId = requireNotNull(get("social_connection_id", String::class.java)),
    workspaceId = requireNotNull(get("workspace_id", String::class.java)),
    provider = SocialProvider.valueOf(requireNotNull(get("provider", String::class.java))),
    providerAccountId = requireNotNull(get("provider_account_id", String::class.java)),
    kind = SocialAccountKind.valueOf(requireNotNull(get("account_type", String::class.java))),
    displayName = requireNotNull(get("display_name", String::class.java)),
    profileUrn = get("profile_urn", String::class.java),
    avatarUrl = get("avatar_url", String::class.java),
    status = SocialConnectionStatus.valueOf(requireNotNull(get("status", String::class.java))),
    createdAt = get("created_at", OffsetDateTime::class.java)?.toInstant(),
)

private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindSocialConnection(
    connection: SocialConnection,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec = this
    .bind("id", connection.id)
    .bind("workspaceId", connection.workspaceId)
    .bind("provider", connection.provider.name)
    .bind("providerConnectionRef", connection.providerConnectionRef)
    .bind("status", connection.status.name)
    .bindNullable("credentialReference", connection.credentialReference, String::class.java)
    .bindNullable("connectedAt", connection.connectedAt, java.time.Instant::class.java)
    .bindNullable("lastSyncedAt", connection.lastSyncedAt, java.time.Instant::class.java)

private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindSocialAccount(
    account: SocialAccount,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec = this
    .bind("id", account.id)
    .bind("socialConnectionId", account.socialConnectionId)
    .bind("workspaceId", account.workspaceId)
    .bind("provider", account.provider.name)
    .bind("providerAccountId", account.providerAccountId)
    .bind("accountType", account.kind.name)
    .bind("displayName", account.displayName)
    .bindNullable("profileUrn", account.profileUrn, String::class.java)
    .bindNullable("avatarUrl", account.avatarUrl, String::class.java)
    .bind("status", account.status.name)
