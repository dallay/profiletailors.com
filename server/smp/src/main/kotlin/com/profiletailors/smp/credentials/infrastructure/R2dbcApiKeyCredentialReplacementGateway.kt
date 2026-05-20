package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialReplacementGateway
import com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory
import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialCommand
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialResult
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant

@Repository
class R2dbcApiKeyCredentialReplacementGateway(
    private val connectionFactory: ConnectionFactory,
    private val secretVerifier: ApiKeySecretVerifier,
    private val valueFactory: ApiKeyCredentialValueFactory,
    private val clock: Clock = Clock.systemUTC(),
) : ApiKeyCredentialReplacementGateway {

    override suspend fun replaceActiveCredential(
        command: ReplaceApiKeyCredentialCommand,
    ): ReplaceApiKeyCredentialResult {
        val predecessorReference = command.predecessorCredentialReference
        val successorCredentialReference = valueFactory.nextCredentialReference()
        val plaintextApiKey = valueFactory.nextPlaintextApiKey()
        val successorSecretVerifier = secretVerifier.hash(plaintextApiKey.secret)
        val replacedAt = clock.instant()

        val connection = connectionFactory.create().awaitSingle()
        try {
            connection.beginTransaction().awaitFirstOrNull()
            try {
                val predecessor = loadActivePredecessor(connection, predecessorReference)
                insertSuccessor(
                    connection = connection,
                    successorCredentialReference = successorCredentialReference,
                    predecessor = predecessor,
                    plaintextApiKey = plaintextApiKey,
                    successorSecretVerifier = successorSecretVerifier,
                )
                markPredecessorReplaced(
                    connection = connection,
                    predecessor = predecessor,
                    successorCredentialReference = successorCredentialReference,
                    replacedAt = replacedAt,
                )
                connection.commitTransaction().awaitFirstOrNull()
            } catch (exception: ApiKeyCredentialNotActiveException) {
                connection.rollbackTransaction().awaitFirstOrNull()
                throw exception
            }
        } finally {
            connection.close().awaitFirstOrNull()
        }

        return ReplaceApiKeyCredentialResult(
            predecessorCredentialReference = predecessorReference,
            successorCredentialReference = successorCredentialReference,
            successorPlaintextApiKey = plaintextApiKey.value,
        )
    }

    private suspend fun loadActivePredecessor(
        connection: Connection,
        credentialReference: String,
    ): PredecessorCredentialRecord {
        val predecessor = connection.createStatement(LOAD_PREDECESSOR_SQL)
            .bind("${'$'}1", credentialReference)
            .execute()
            .awaitSingle()
            .map { row, _ ->
                PredecessorCredentialRecord(
                    credentialReference = requireNotNull(row.get("id", String::class.java)),
                    principalId = requireNotNull(row.get("principal_id", String::class.java)),
                    status = requireNotNull(row.get("status", String::class.java)),
                    replacedAt = row.get("replaced_at", java.time.OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .awaitFirstOrNull()
            ?: throw ApiKeyCredentialNotActiveException(
                credentialReference = credentialReference,
                reason = ApiKeyCredentialFailureReason.MISSING,
            )

        ensureReplaceable(predecessor)
        return predecessor
    }

    private fun ensureReplaceable(predecessor: PredecessorCredentialRecord) {
        val failureReason = when {
            predecessor.replacedAt != null -> ApiKeyCredentialFailureReason.REPLACED
            predecessor.status == REVOKED_STATUS -> ApiKeyCredentialFailureReason.REVOKED
            predecessor.status != ACTIVE_STATUS -> ApiKeyCredentialFailureReason.INACTIVE
            else -> null
        }

        if (failureReason != null) {
            throw ApiKeyCredentialNotActiveException(
                credentialReference = predecessor.credentialReference,
                principalId = predecessor.principalId,
                reason = failureReason,
            )
        }
    }

    private suspend fun insertSuccessor(
        connection: Connection,
        successorCredentialReference: String,
        predecessor: PredecessorCredentialRecord,
        plaintextApiKey: ApiKeyCredentialValueFactory.PlaintextApiKey,
        successorSecretVerifier: String,
    ) {
        connection.createStatement(INSERT_SUCCESSOR_SQL)
            .bind("${'$'}1", successorCredentialReference)
            .bind("${'$'}2", predecessor.principalId)
            .bind("${'$'}3", plaintextApiKey.lookupKey)
            .bind("${'$'}4", plaintextApiKey.keyPrefix)
            .bind("${'$'}5", successorSecretVerifier)
            .bind("${'$'}6", predecessor.credentialReference)
            .execute()
            .awaitSingle()
            .rowsUpdated
            .awaitSingle()
    }

    private suspend fun markPredecessorReplaced(
        connection: Connection,
        predecessor: PredecessorCredentialRecord,
        successorCredentialReference: String,
        replacedAt: Instant,
    ) {
        val updatedRows = connection.createStatement(MARK_PREDECESSOR_SQL)
            .bind("${'$'}1", successorCredentialReference)
            .bind("${'$'}2", replacedAt)
            .bind("${'$'}3", predecessor.credentialReference)
            .bind("${'$'}4", predecessor.principalId)
            .execute()
            .awaitSingle()
            .rowsUpdated
            .awaitSingle()

        if (updatedRows != 1L) {
            throw ApiKeyCredentialNotActiveException(
                credentialReference = predecessor.credentialReference,
                principalId = predecessor.principalId,
                reason = ApiKeyCredentialFailureReason.INACTIVE,
            )
        }
    }

    private data class PredecessorCredentialRecord(
        val credentialReference: String,
        val principalId: String,
        val status: String,
        val replacedAt: Instant?,
    )

    companion object {
        private const val ACTIVE_STATUS = "ACTIVE"
        private const val REVOKED_STATUS = "REVOKED"

        private val LOAD_PREDECESSOR_SQL = """
            SELECT id,
                   principal_id,
                   status,
                   replaced_at
            FROM api_key_credentials
            WHERE id = $1
        """.trimIndent()

        private val INSERT_SUCCESSOR_SQL = """
            INSERT INTO api_key_credentials (
                id,
                principal_id,
                lookup_key,
                key_prefix,
                secret_verifier,
                status,
                revoked_at,
                replaced_credential_id,
                replaced_by_credential_id,
                replaced_at
            ) VALUES (
                $1,
                $2,
                $3,
                $4,
                $5,
                'ACTIVE',
                NULL,
                $6,
                NULL,
                NULL
            )
        """.trimIndent()

        private val MARK_PREDECESSOR_SQL = """
            UPDATE api_key_credentials
            SET status = 'INACTIVE',
                replaced_by_credential_id = $1,
                replaced_at = $2,
                revoked_at = NULL
            WHERE id = $3
              AND principal_id = $4
              AND status = 'ACTIVE'
              AND replaced_at IS NULL
        """.trimIndent()
    }
}
