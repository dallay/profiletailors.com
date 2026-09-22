package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.domain.UserAccountState
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcPrincipalIdentityLookup(private val databaseClient: DatabaseClient) : PrincipalIdentityLookup {
    /**
     * Finds principal and user-identity facts for an email address.
     *
     * @return The matching identity facts, or `null` when no user identity has the email address.
     */
    override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = databaseClient.sql(
        """
            SELECT p.id,
                   p.principal_type,
                   p.subject,
                   p.provider,
                   p.display_identity,
                   ui.email,
                   ui.username,
                   ui.email_status,
                   p.account_state
            FROM principals p
            INNER JOIN user_identities ui ON ui.principal_id = p.id
            WHERE ui.email = :email
        """.trimIndent(),
    )
        .bind("email", email)
        .map { row, _ -> mapPrincipalIdentityFacts(row) }
        .one()
        .awaitSingleOrNull()

    /**
     * Finds identity facts by principal ID, including principals without a user-identity record.
     *
     * @return The matching identity facts, or `null` when the principal does not exist.
     */
    override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = databaseClient.sql(
        """
            SELECT p.id,
                   p.principal_type,
                   p.subject,
                   p.provider,
                   p.display_identity,
                   ui.email,
                   ui.username,
                   ui.email_status,
                   p.account_state
            FROM principals p
            LEFT JOIN user_identities ui ON ui.principal_id = p.id
            WHERE p.id = :principalId
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .map { row, _ -> mapPrincipalIdentityFacts(row) }
        .one()
        .awaitSingleOrNull()

    /**
     * Finds identity facts by principal type, subject, and provider.
     *
     * A `null` provider matches only principals whose provider is also `null`.
     *
     * @return The matching identity facts, or `null` when no principal matches all criteria.
     */
    override suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts? {
        val sql = if (provider == null) {
            """
            SELECT p.id,
                   p.principal_type,
                   p.subject,
                   p.provider,
                   p.display_identity,
                   ui.email,
                   ui.username,
                   ui.email_status,
                   p.account_state
            FROM principals p
            LEFT JOIN user_identities ui ON ui.principal_id = p.id
            WHERE p.principal_type = :principalType
              AND p.subject = :subject
              AND p.provider IS NULL
            """.trimIndent()
        } else {
            """
            SELECT p.id,
                   p.principal_type,
                   p.subject,
                   p.provider,
                   p.display_identity,
                   ui.email,
                   ui.username,
                   ui.email_status,
                   p.account_state
            FROM principals p
            LEFT JOIN user_identities ui ON ui.principal_id = p.id
            WHERE p.principal_type = :principalType
              AND p.subject = :subject
              AND p.provider = :provider
            """.trimIndent()
        }

        var spec = databaseClient.sql(sql)
            .bind("principalType", principalType.name)
            .bind("subject", subject)

        if (provider != null) {
            spec = spec.bind("provider", provider)
        }

        return spec
            .map { row, _ -> mapPrincipalIdentityFacts(row) }
            .one()
            .awaitSingleOrNull()
    }

    /**
     * Converts a selected principal row and its optional user identity into identity facts.
     *
     * @throws IllegalArgumentException If a required column is missing or an enum value is unsupported.
     */
    private fun mapPrincipalIdentityFacts(row: Readable): PrincipalIdentityFacts {
        val principalTypeValue = requireNotNull(row.get("principal_type", String::class.java))
        val emailStatusRaw = row.get("email_status", String::class.java)
        return PrincipalIdentityFacts(
            principalId = requireNotNull(row.get("id", String::class.java)),
            principalType = PrincipalType.valueOf(principalTypeValue),
            subject = requireNotNull(row.get("subject", String::class.java)),
            provider = row.get("provider", String::class.java),
            displayIdentity = row.get("display_identity", String::class.java),
            email = row.get("email", String::class.java),
            username = row.get("username", String::class.java),
            emailStatus = emailStatusRaw?.let { EmailStatus.valueOf(it) },
            accountState = UserAccountState.valueOf(
                requireNotNull(row.get("account_state", String::class.java)),
            ),
        )
    }
}
