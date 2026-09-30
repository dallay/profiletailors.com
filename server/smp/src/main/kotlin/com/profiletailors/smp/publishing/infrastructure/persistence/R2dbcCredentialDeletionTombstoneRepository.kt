package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.CredentialDeletionReason
import com.profiletailors.smp.publishing.domain.CredentialDeletionTombstone
import com.profiletailors.smp.publishing.domain.CredentialDeletionTombstoneRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class R2dbcCredentialDeletionTombstoneRepository(private val databaseClient: DatabaseClient) :
    CredentialDeletionTombstoneRepository {
    override suspend fun findByWorkspaceAndConnection(
        workspaceId: String,
        connectionId: String,
    ): CredentialDeletionTombstone? = databaseClient.sql(
        """
        SELECT workspace_id, connection_id, provider, provider_connection_ref, credential_reference, activity_id,
               policy_version, reason, deleted_at
        FROM credential_deletion_tombstones
        WHERE workspace_id = :workspaceId AND connection_id = :connectionId
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("connectionId", connectionId)
        .map { row, _ -> row.toTombstone() }
        .one()
        .awaitSingleOrNull()

    override suspend fun record(tombstone: CredentialDeletionTombstone): CredentialDeletionTombstone {
        databaseClient.sql(
            """
            INSERT INTO credential_deletion_tombstones (
                workspace_id, connection_id, provider, provider_connection_ref, credential_reference, activity_id,
                policy_version, reason, deleted_at
            ) VALUES (
                :workspaceId, :connectionId, :provider, :providerConnectionRef, :credentialReference, :activityId,
                :policyVersion, :reason, :deletedAt
            )
            ON CONFLICT (workspace_id, connection_id) DO UPDATE SET
                provider_connection_ref = EXCLUDED.provider_connection_ref,
                credential_reference = EXCLUDED.credential_reference,
                activity_id = EXCLUDED.activity_id,
                policy_version = EXCLUDED.policy_version,
                reason = EXCLUDED.reason,
                deleted_at = EXCLUDED.deleted_at
            """.trimIndent(),
        )
            .bind("workspaceId", tombstone.workspaceId)
            .bind("connectionId", tombstone.connectionId)
            .bind("provider", tombstone.provider.name)
            .bind("providerConnectionRef", tombstone.providerConnectionRef)
            .bindNullable("credentialReference", tombstone.credentialReference, String::class.java)
            .bind("activityId", tombstone.activityId)
            .bind("policyVersion", tombstone.policyVersion)
            .bind("reason", tombstone.reason.name)
            .bind("deletedAt", tombstone.deletedAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return requireNotNull(findByWorkspaceAndConnection(tombstone.workspaceId, tombstone.connectionId))
    }
}

private fun Readable.toTombstone(): CredentialDeletionTombstone = CredentialDeletionTombstone(
    workspaceId = requireNotNull(get("workspace_id", String::class.java)),
    connectionId = requireNotNull(get("connection_id", String::class.java)),
    provider = SocialProvider.valueOf(requireNotNull(get("provider", String::class.java))),
    providerConnectionRef = requireNotNull(get("provider_connection_ref", String::class.java)),
    credentialReference = get("credential_reference", String::class.java),
    activityId = requireNotNull(get("activity_id", String::class.java)),
    policyVersion = get("policy_version", String::class.java).orEmpty(),
    reason = CredentialDeletionReason.valueOf(requireNotNull(get("reason", String::class.java))),
    deletedAt = requireNotNull(get("deleted_at", OffsetDateTime::class.java)).toInstant(),
)

private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value == null) {
    bindNull(name, type)
} else {
    bind(name, value)
}
