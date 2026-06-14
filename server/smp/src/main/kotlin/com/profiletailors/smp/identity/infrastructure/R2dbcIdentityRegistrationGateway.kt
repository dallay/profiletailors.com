package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.EmailVerificationTokenData
import com.profiletailors.smp.identity.application.IdentityRegistrationGateway
import com.profiletailors.smp.identity.domain.EmailStatus
import io.r2dbc.spi.Readable
import io.r2dbc.spi.RowMetadata
import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcIdentityRegistrationGateway(
    private val databaseClient: DatabaseClient,
) : IdentityRegistrationGateway {
    override suspend fun createUserIdentity(
        principalId: String,
        subject: String,
        email: String,
        username: String,
        provider: String?,
        displayIdentity: String,
        emailStatus: EmailStatus,
    ) {
        var principalInsert = databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, :principalType, :subject, :provider, :displayIdentity)
            """.trimIndent(),
        )
            .bind("id", principalId)
            .bind("principalType", PrincipalType.USER.name)
            .bind("subject", subject)
            .bind("displayIdentity", displayIdentity)

        principalInsert = if (provider == null) {
            principalInsert.bindNull("provider", String::class.java)
        } else {
            principalInsert.bind("provider", provider)
        }

        principalInsert
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES (:principalId, :email, :username, :emailStatus)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("email", email)
            .bind("username", username)
            .bind("emailStatus", emailStatus.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun createEmailVerificationToken(
        email: String,
        tokenHash: String,
        expiresAt: Instant,
    ) {
        databaseClient.sql(
            """
            INSERT INTO email_verification_tokens (email, token_hash, expires_at)
            VALUES (:email, :tokenHash, :expiresAt)
            """.trimIndent(),
        )
            .bind("email", email)
            .bind("tokenHash", tokenHash)
            .bind("expiresAt", expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun verifyEmailToken(tokenHash: String): EmailVerificationTokenData? =
        databaseClient.sql(
            """
            SELECT evt.email, evt.token_hash, evt.expires_at, evt.used_at
            FROM email_verification_tokens evt
            WHERE evt.token_hash = :tokenHash
            """.trimIndent(),
        )
            .bind("tokenHash", tokenHash)
            .map(::mapTokenData)
            .one()
            .awaitSingleOrNull()

    override suspend fun markTokenUsed(tokenHash: String, now: Instant) {
        databaseClient.sql(
            """
            UPDATE email_verification_tokens
            SET used_at = :now
            WHERE token_hash = :tokenHash
            """.trimIndent(),
        )
            .bind("tokenHash", tokenHash)
            .bind("now", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus) {
        databaseClient.sql(
            """
            UPDATE user_identities
            SET email_status = :emailStatus
            WHERE email = :email
            """.trimIndent(),
        )
            .bind("email", email)
            .bind("emailStatus", emailStatus.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun invalidateEmailTokens(email: String) {
        databaseClient.sql(
            """
            UPDATE email_verification_tokens
            SET used_at = CURRENT_TIMESTAMP
            WHERE email = :email AND used_at IS NULL
            """.trimIndent(),
        )
            .bind("email", email)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun findActiveTokenByEmail(email: String): EmailVerificationTokenData? =
        databaseClient.sql(
            """
            SELECT evt.email, evt.token_hash, evt.expires_at, evt.used_at
            FROM email_verification_tokens evt
            WHERE evt.email = :email AND evt.used_at IS NULL AND evt.expires_at > CURRENT_TIMESTAMP
            ORDER BY evt.created_at DESC
            LIMIT 1
            """.trimIndent(),
        )
            .bind("email", email)
            .map(::mapTokenData)
            .one()
            .awaitSingleOrNull()

    private fun mapTokenData(
        row: Readable,
        @Suppress("UNUSED_PARAMETER") metadata: RowMetadata,
    ): EmailVerificationTokenData = EmailVerificationTokenData(
        email = requireNotNull(row.get("email", String::class.java)),
        tokenHash = requireNotNull(row.get("token_hash", String::class.java)),
        expiresAt = requireNotNull(row.get("expires_at", Instant::class.java)),
        usedAt = row.get("used_at", Instant::class.java),
    )
}
