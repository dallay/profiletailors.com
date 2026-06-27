package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ActiveApiKeyCredential
import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcApiKeyCredentialStateLookup(
    private val databaseClient: DatabaseClient,
    private val secretVerifier: ApiKeySecretVerifier,
) : ApiKeyCredentialStateLookup {

    override suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential {
        val (lookupKey, presentedSecret) = parseApiKey(presentedApiKey)
        val record = lookupCredential(lookupKey)
        validateCredentialState(record, presentedSecret)

        return ActiveApiKeyCredential(
            principalId = record.principalId,
            credentialReference = record.credentialReference,
            subject = record.subject,
            provider = record.provider,
        )
    }

    private fun parseApiKey(key: String): Pair<String, String> {
        val segments = key.split(API_KEY_DELIMITER, limit = 2)
        if (segments.size != 2 || segments.any { it.isBlank() }) {
            throw ApiKeyCredentialNotActiveException(
                credentialReference = "invalid",
                reason = ApiKeyCredentialFailureReason.INVALID,
            )
        }
        return segments[0] to segments[1]
    }

    private suspend fun lookupCredential(lookupKey: String): ApiKeyCredentialRecord = databaseClient.sql(LOOKUP_SQL)
        .bind("lookupKey", lookupKey)
        .map { row, _ ->
            ApiKeyCredentialRecord(
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                credentialReference = requireNotNull(row.get("id", String::class.java)),
                secretVerifier = requireNotNull(row.get("secret_verifier", String::class.java)),
                status = requireNotNull(row.get("status", String::class.java)),
                subject = requireNotNull(row.get("subject", String::class.java)),
                provider = row.get("provider", String::class.java),
                replacedAt = row.get("replaced_at", java.time.OffsetDateTime::class.java),
            )
        }
        .one()
        .awaitSingleOrNull()
        ?: throw ApiKeyCredentialNotActiveException(
            credentialReference = lookupKey,
            reason = ApiKeyCredentialFailureReason.MISSING,
        )

    private fun validateCredentialState(record: ApiKeyCredentialRecord, presentedSecret: String) {
        val reason = when {
            record.status == REVOKED_STATUS -> ApiKeyCredentialFailureReason.REVOKED
            record.status != ACTIVE_STATUS -> ApiKeyCredentialFailureReason.INACTIVE
            record.replacedAt != null -> ApiKeyCredentialFailureReason.REPLACED
            !secretVerifier.matches(presentedSecret, record.secretVerifier) -> ApiKeyCredentialFailureReason.INVALID
            else -> return
        }
        throw ApiKeyCredentialNotActiveException(
            credentialReference = record.credentialReference,
            principalId = record.principalId,
            reason = reason,
        )
    }

    private data class ApiKeyCredentialRecord(
        val principalId: String,
        val credentialReference: String,
        val secretVerifier: String,
        val status: String,
        val subject: String,
        val provider: String?,
        val replacedAt: java.time.OffsetDateTime?,
    )

    companion object {
        private const val API_KEY_DELIMITER = "."
        private const val ACTIVE_STATUS = "ACTIVE"
        private const val REVOKED_STATUS = "REVOKED"

        private val LOOKUP_SQL = """
            SELECT akc.principal_id,
                   akc.id,
                   akc.secret_verifier,
                   akc.status,
                   akc.replaced_at,
                   p.subject,
                   p.provider
            FROM api_key_credentials akc
            INNER JOIN principals p ON p.id = akc.principal_id
            WHERE akc.lookup_key = :lookupKey
              AND p.principal_type = 'API_KEY'
        """.trimIndent()
    }
}
