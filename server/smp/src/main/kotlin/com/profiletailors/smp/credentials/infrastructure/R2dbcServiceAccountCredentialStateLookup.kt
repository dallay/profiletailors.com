package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ActiveServiceAccountCredential
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialFailureReason
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcServiceAccountCredentialStateLookup(private val databaseClient: DatabaseClient) :
    ServiceAccountCredentialStateLookup {
    override suspend fun requireActive(
        credentialReference: String,
        subject: String,
        provider: String,
    ): ActiveServiceAccountCredential {
        val record = databaseClient.sql(
            """
            SELECT sac.principal_id, sac.credential_reference, sac.status
            FROM service_account_credentials sac
            INNER JOIN principals p ON p.id = sac.principal_id
            WHERE sac.credential_reference = :credentialReference
              AND sac.provider = :provider
              AND p.principal_type = 'SERVICE_ACCOUNT'
              AND p.subject = :subject
              AND p.provider = :provider
            """.trimIndent(),
        )
            .bind("credentialReference", credentialReference)
            .bind("subject", subject)
            .bind("provider", provider)
            .map { row, _ ->
                ServiceAccountCredentialRecord(
                    principalId = requireNotNull(row.get("principal_id", String::class.java)),
                    credentialReference = requireNotNull(row.get("credential_reference", String::class.java)),
                    status = requireNotNull(row.get("status", String::class.java)),
                )
            }
            .one()
            .awaitSingleOrNull()
            ?: throw ServiceAccountCredentialNotActiveException(
                credentialReference = credentialReference,
                subject = subject,
                provider = provider,
                reason = ServiceAccountCredentialFailureReason.MISSING,
            )

        if (record.status != ACTIVE_STATUS) {
            throw ServiceAccountCredentialNotActiveException(
                credentialReference = credentialReference,
                subject = subject,
                provider = provider,
                principalId = record.principalId,
                reason = if (record.status == REVOKED_STATUS) {
                    ServiceAccountCredentialFailureReason.REVOKED
                } else {
                    ServiceAccountCredentialFailureReason.MISSING
                },
            )
        }

        return ActiveServiceAccountCredential(
            principalId = record.principalId,
            credentialReference = record.credentialReference,
        )
    }

    private data class ServiceAccountCredentialRecord(
        val principalId: String,
        val credentialReference: String,
        val status: String,
    )

    companion object {
        private const val ACTIVE_STATUS = "ACTIVE"
        private const val REVOKED_STATUS = "REVOKED"
    }
}
