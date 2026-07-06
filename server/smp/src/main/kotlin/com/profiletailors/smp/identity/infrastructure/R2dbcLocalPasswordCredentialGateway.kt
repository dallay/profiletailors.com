package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.LocalPasswordCredentialGateway
import com.profiletailors.smp.identity.application.LocalPasswordCredentialRecord
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcLocalPasswordCredentialGateway(private val databaseClient: DatabaseClient) :
    LocalPasswordCredentialGateway {

    override suspend fun create(principalId: String, passwordHash: String, passwordAlgorithm: String) {
        databaseClient.sql(
            """
            INSERT INTO local_password_credentials (principal_id, password_hash, password_algorithm)
            VALUES (:principalId, :passwordHash, :passwordAlgorithm)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("passwordHash", passwordHash)
            .bind("passwordAlgorithm", passwordAlgorithm)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? = databaseClient.sql(
        """
            SELECT ui.principal_id,
                   ui.email,
                   ui.username,
                   lpc.password_hash,
                   lpc.password_algorithm
            FROM user_identities ui
            INNER JOIN local_password_credentials lpc ON lpc.principal_id = ui.principal_id
            WHERE ui.email = :email
        """.trimIndent(),
    )
        .bind("email", email)
        .map { row, _ ->
            LocalPasswordCredentialRecord(
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                email = requireNotNull(row.get("email", String::class.java)),
                username = row.get("username", String::class.java),
                passwordHash = requireNotNull(row.get("password_hash", String::class.java)),
                passwordAlgorithm = row.get("password_algorithm", String::class.java),
            )
        }
        .one()
        .awaitSingleOrNull()

    override suspend fun updatePassword(principalId: String, passwordHash: String, passwordAlgorithm: String) {
        databaseClient.sql(
            """
            UPDATE local_password_credentials
            SET password_hash = :passwordHash,
                password_algorithm = :passwordAlgorithm,
                updated_at = CURRENT_TIMESTAMP
            WHERE principal_id = :principalId
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("passwordHash", passwordHash)
            .bind("passwordAlgorithm", passwordAlgorithm)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
