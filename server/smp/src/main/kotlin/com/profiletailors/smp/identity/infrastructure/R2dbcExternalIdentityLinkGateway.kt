package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.ExternalIdentityLinkGateway
import com.profiletailors.smp.identity.application.VerifiedEmailSyncResult
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.ExternalIdentityLink
import com.profiletailors.smp.identity.domain.ExternalIdentityLinkStatus
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class R2dbcExternalIdentityLinkGateway(private val databaseClient: DatabaseClient) : ExternalIdentityLinkGateway {
    override suspend fun findByIssuerAndSubject(issuer: String, subject: String): List<ExternalIdentityLink> =
        databaseClient.sql(
            """
            SELECT issuer, subject, principal_id, link_status
            FROM external_identity_links
            WHERE issuer = :issuer AND subject = :subject
            LIMIT 2
            """.trimIndent(),
        )
            .bind("issuer", issuer)
            .bind("subject", subject)
            .map { row, _ ->
                ExternalIdentityLink(
                    issuer = requireNotNull(row.get("issuer", String::class.java)),
                    subject = requireNotNull(row.get("subject", String::class.java)),
                    principalId = requireNotNull(row.get("principal_id", UUID::class.java)).toString(),
                    status = ExternalIdentityLinkStatus.valueOf(
                        requireNotNull(row.get("link_status", String::class.java)),
                    ),
                )
            }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun synchronizeVerifiedEmail(principalId: String, email: String): VerifiedEmailSyncResult {
        val principalUuid = UUID.fromString(principalId)
        val existingEmail = databaseClient.sql(
            "SELECT email FROM user_identities WHERE principal_id = :principalId",
        )
            .bind("principalId", principalUuid)
            .map { row, _ -> UserIdentityEmail(row.get("email", String::class.java)) }
            .one()
            .awaitSingleOrNull()
            ?.email

        if (existingEmail == email) {
            databaseClient.sql(
                "UPDATE user_identities SET email_status = :emailStatus WHERE principal_id = :principalId",
            )
                .bind("emailStatus", EmailStatus.VERIFIED.name)
                .bind("principalId", principalUuid)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            return VerifiedEmailSyncResult.UNCHANGED
        }

        val conflictingPrincipal = databaseClient.sql(
            "SELECT principal_id FROM user_identities WHERE email = :email",
        )
            .bind("email", email)
            .map { row, _ -> requireNotNull(row.get("principal_id", UUID::class.java)) }
            .one()
            .awaitSingleOrNull()

        if (conflictingPrincipal != null && conflictingPrincipal != principalUuid) {
            return VerifiedEmailSyncResult.CONFLICT
        }

        return try {
            val updatedRows = databaseClient.sql(
                """
                UPDATE user_identities
                SET email = :email, email_status = :emailStatus
                WHERE principal_id = :principalId
                """.trimIndent(),
            )
                .bind("email", email)
                .bind("emailStatus", EmailStatus.VERIFIED.name)
                .bind("principalId", principalUuid)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            if (updatedRows == 1L) VerifiedEmailSyncResult.UPDATED else VerifiedEmailSyncResult.CONFLICT
        } catch (exception: DataIntegrityViolationException) {
            if (exception.message.orEmpty().contains("uq_user_identities_email")) {
                VerifiedEmailSyncResult.CONFLICT
            } else {
                throw exception
            }
        }
    }

    private data class UserIdentityEmail(val email: String?)
}
