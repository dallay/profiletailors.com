package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.ProviderCredentialInvalidator
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class ProviderCredentials(
    val provider: SocialProvider,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long?,
    val refreshTokenExpiresAtEpochSeconds: Long? = null,
    val lastRefreshAttemptAtEpochSeconds: Long? = null,
    val lastRefreshStatus: String? = null,
    val grantedScopes: String? = null,
    val scope: String?,
)

interface ProviderCredentialGateway : ProviderCredentialInvalidator {
    suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID
    suspend fun resolveCredential(id: UUID): ProviderCredentials
    override suspend fun invalidateCredential(id: UUID)
}

@Component
class R2dbcProviderCredentialGateway(
    private val db: DatabaseClient,
    private val encryptionService: CredentialEncryptionService,
) : ProviderCredentialGateway {
    private val mapper = jacksonObjectMapper()

    override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID {
        val encryptedPayload = encryptionService.encrypt(mapper.writeValueAsString(credentials))
        val id = UUID.randomUUID()
        return db.sql(
            """
            INSERT INTO secure_credentials (
                id, owner_type, owner_id, provider, encrypted_payload,
                access_token_expires_at, refresh_token_expires_at, last_refresh_attempt_at,
                last_refresh_status, granted_scopes
            ) VALUES (
                :id, :ownerType, :ownerId, :provider, :payload,
                :accessTokenExpiresAt, :refreshTokenExpiresAt, :lastRefreshAttemptAt,
                :lastRefreshStatus, :grantedScopes
            )
            ON CONFLICT (owner_type, owner_id, provider) DO UPDATE SET
                encrypted_payload = EXCLUDED.encrypted_payload,
                access_token_expires_at = EXCLUDED.access_token_expires_at,
                refresh_token_expires_at = EXCLUDED.refresh_token_expires_at,
                last_refresh_attempt_at = EXCLUDED.last_refresh_attempt_at,
                last_refresh_status = EXCLUDED.last_refresh_status,
                granted_scopes = EXCLUDED.granted_scopes,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("ownerType", ownerType)
            .bind("ownerId", ownerId)
            .bind("provider", credentials.provider.name)
            .bind("payload", encryptedPayload)
            .bindNullable(
                "accessTokenExpiresAt",
                credentials.expiresAtEpochSeconds.toOffsetDateTime(),
                OffsetDateTime::class.java,
            )
            .bindNullable(
                "refreshTokenExpiresAt",
                credentials.refreshTokenExpiresAtEpochSeconds.toOffsetDateTime(),
                OffsetDateTime::class.java,
            )
            .bindNullable(
                "lastRefreshAttemptAt",
                credentials.lastRefreshAttemptAtEpochSeconds.toOffsetDateTime(),
                OffsetDateTime::class.java,
            )
            .bindNullable("lastRefreshStatus", credentials.lastRefreshStatus, String::class.java)
            .bindNullable("grantedScopes", credentials.grantedScopes, String::class.java)
            .map { row, _ -> row.get("id", UUID::class.java) ?: error("Credential id missing after upsert") }
            .one()
            .awaitSingle()
    }

    override suspend fun resolveCredential(id: UUID): ProviderCredentials = db.sql(
        "SELECT encrypted_payload FROM secure_credentials WHERE id = :id",
    )
        .bind("id", id)
        .map { row, _ ->
            val payload = row.get("encrypted_payload", ByteArray::class.java)
                ?: error("Credential payload missing for id $id")
            mapper.readValue(encryptionService.decrypt(payload), ProviderCredentials::class.java)
        }
        .one()
        .awaitSingle()

    override suspend fun invalidateCredential(id: UUID) {
        db.sql(
            "DELETE FROM secure_credentials WHERE id = :id",
        )
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}

private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value == null) {
    bindNull(name, type)
} else {
    bind(name, value)
}

private fun Long?.toOffsetDateTime(): OffsetDateTime? = this?.let {
    OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC)
}
