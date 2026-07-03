package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialReplacementGateway
import com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory
import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialCommand
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialResult
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Clock
import java.time.Instant

@Repository
class R2dbcApiKeyCredentialReplacementGateway(
    private val databaseClient: DatabaseClient,
    private val transactionalOperator: TransactionalOperator,
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

        transactionalOperator.transactional(
            mono {
                val predecessor = loadActivePredecessor(predecessorReference)
                insertSuccessor(
                    successorCredentialReference = successorCredentialReference,
                    predecessor = predecessor,
                    plaintextApiKey = plaintextApiKey,
                    successorSecretVerifier = successorSecretVerifier,
                )
                markPredecessorReplaced(
                    predecessor = predecessor,
                    successorCredentialReference = successorCredentialReference,
                    replacedAt = replacedAt,
                )
                Unit
            },
        ).awaitSingle()

        return ReplaceApiKeyCredentialResult(
            predecessorCredentialReference = predecessorReference,
            successorCredentialReference = successorCredentialReference,
            successorPlaintextApiKey = plaintextApiKey.value,
        )
    }

    private suspend fun loadActivePredecessor(credentialReference: String): PredecessorCredentialRecord {
        val row = databaseClient.sql(LOAD_PREDECESSOR_SQL)
            .bind("credentialReference", credentialReference)
            .map { r, _ ->
                PredecessorCredentialRecord(
                    credentialReference = requireNotNull(r.get("id", String::class.java)),
                    principalId = requireNotNull(r.get("principal_id", String::class.java)),
                    status = requireNotNull(r.get("status", String::class.java)),
                    replacedAt = r.get("replaced_at", java.time.OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .one()
            .awaitSingleOrNull()
            ?: throw ApiKeyCredentialNotActiveException(
                credentialReference = credentialReference,
                reason = ApiKeyCredentialFailureReason.MISSING,
            )

        ensureReplaceable(row)
        return row
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
        successorCredentialReference: String,
        predecessor: PredecessorCredentialRecord,
        plaintextApiKey: ApiKeyCredentialValueFactory.PlaintextApiKey,
        successorSecretVerifier: String,
    ) {
        databaseClient.sql(INSERT_SUCCESSOR_SQL)
            .bind("successorCredentialReference", successorCredentialReference)
            .bind("principalId", predecessor.principalId)
            .bind("lookupKey", plaintextApiKey.lookupKey)
            .bind("keyPrefix", plaintextApiKey.keyPrefix)
            .bind("secretVerifier", successorSecretVerifier)
            .bind("predecessorCredentialReference", predecessor.credentialReference)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun markPredecessorReplaced(
        predecessor: PredecessorCredentialRecord,
        successorCredentialReference: String,
        replacedAt: Instant,
    ) {
        val updatedRows = databaseClient.sql(MARK_PREDECESSOR_SQL)
            .bind("successorCredentialReference", successorCredentialReference)
            .bind("replacedAt", replacedAt)
            .bind("predecessorCredentialReference", predecessor.credentialReference)
            .bind("principalId", predecessor.principalId)
            .fetch()
            .rowsUpdated()
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
            WHERE id = :credentialReference
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
                :successorCredentialReference,
                :principalId,
                :lookupKey,
                :keyPrefix,
                :secretVerifier,
                'ACTIVE',
                NULL,
                :predecessorCredentialReference,
                NULL,
                NULL
            )
        """.trimIndent()

        private val MARK_PREDECESSOR_SQL = """
            UPDATE api_key_credentials
            SET status = 'INACTIVE',
                replaced_by_credential_id = :successorCredentialReference,
                replaced_at = :replacedAt,
                revoked_at = NULL
            WHERE id = :predecessorCredentialReference
              AND principal_id = :principalId
              AND status = 'ACTIVE'
              AND replaced_at IS NULL
        """.trimIndent()
    }
}
