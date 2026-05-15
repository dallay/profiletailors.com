package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.PrincipalType
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcPrincipalIdentityLookup(
    private val databaseClient: DatabaseClient,
) : PrincipalIdentityLookup {
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
                   ui.username
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
                   ui.username
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
            .map { row, _ ->
                PrincipalIdentityFacts(
                    principalId = requireNotNull(row.get("id", String::class.java)),
                    principalType = PrincipalType.valueOf(requireNotNull(row.get("principal_type", String::class.java))),
                    subject = requireNotNull(row.get("subject", String::class.java)),
                    provider = row.get("provider", String::class.java),
                    displayIdentity = row.get("display_identity", String::class.java),
                    email = row.get("email", String::class.java),
                    username = row.get("username", String::class.java),
                )
            }
            .one()
            .awaitSingleOrNull()
    }
}
