package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.IdentityRegistrationGateway
import kotlinx.coroutines.reactor.awaitSingle
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
            INSERT INTO user_identities (principal_id, email, username)
            VALUES (:principalId, :email, :username)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("email", email)
            .bind("username", username)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
