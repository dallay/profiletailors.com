package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.VerifiedEmailSyncResult
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcExternalIdentityLinkGatewayPostgresTest : PostgresDatabaseTestBase() {

    override val postgres: PostgreSQLContainer<*> = postgresContainer
    private val gateway by lazy { R2dbcExternalIdentityLinkGateway(databaseClient) }

    @Test
    fun `verified email sync updates linked principal and skips another principals email`() = runTest {
        val linkedPrincipalId = UUID.randomUUID()
        val emailOwnerPrincipalId = UUID.randomUUID()
        insertPrincipal(linkedPrincipalId, "local-linked-subject")
        insertPrincipal(emailOwnerPrincipalId, "local-email-owner-subject")
        insertUserIdentity(linkedPrincipalId, "linked@example.test")
        insertUserIdentity(emailOwnerPrincipalId, "claimed@example.test")
        insertLink(linkedPrincipalId)

        val resolvedLink = gateway.findByIssuerAndSubject(ISSUER, SUBJECT).single()
        assertEquals(linkedPrincipalId.toString(), resolvedLink.principalId)

        assertEquals(
            VerifiedEmailSyncResult.CONFLICT,
            gateway.synchronizeVerifiedEmail(linkedPrincipalId.toString(), "claimed@example.test"),
        )

        assertEquals("linked@example.test", emailFor(linkedPrincipalId))
        assertEquals("claimed@example.test", emailFor(emailOwnerPrincipalId))

        assertEquals(
            VerifiedEmailSyncResult.UPDATED,
            gateway.synchronizeVerifiedEmail(linkedPrincipalId.toString(), "verified-new@example.test"),
        )
        assertEquals("verified-new@example.test", emailFor(linkedPrincipalId))
        assertEquals("VERIFIED", emailStatusFor(linkedPrincipalId))
        assertEquals("local-linked-subject", subjectFor(linkedPrincipalId))
    }

    @Test
    fun `lookup does not resolve by email when the issuer subject link is absent`() = runTest {
        val principalId = UUID.randomUUID()
        insertPrincipal(principalId, "local-email-subject")
        insertUserIdentity(principalId, "external@example.test")

        val links = gateway.findByIssuerAndSubject(ISSUER, "unlinked-subject")

        assertTrue(links.isEmpty())
    }

    private suspend fun insertPrincipal(id: UUID, subject: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, NULL, 'External identity test principal')
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("subject", subject)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertUserIdentity(principalId: UUID, email: String) {
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES (:principalId, :email, :username, 'PENDING')
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("email", email)
            .bind("username", "user-$principalId")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertLink(principalId: UUID) {
        databaseClient.sql(
            """
            INSERT INTO external_identity_links (issuer, subject, principal_id, link_status)
            VALUES (:issuer, :subject, :principalId, 'APPROVED')
            """.trimIndent(),
        )
            .bind("issuer", ISSUER)
            .bind("subject", SUBJECT)
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun emailFor(principalId: UUID): String? = databaseClient.sql(
        "SELECT email FROM user_identities WHERE principal_id = :principalId",
    )
        .bind("principalId", principalId)
        .map { row, _ -> row.get("email", String::class.java) ?: "" }
        .one()
        .awaitSingle()

    private suspend fun emailStatusFor(principalId: UUID): String = databaseClient.sql(
        "SELECT email_status FROM user_identities WHERE principal_id = :principalId",
    )
        .bind("principalId", principalId)
        .map { row, _ -> requireNotNull(row.get("email_status", String::class.java)) }
        .one()
        .awaitSingle()

    private suspend fun subjectFor(principalId: UUID): String = databaseClient.sql(
        "SELECT subject FROM principals WHERE id = :principalId",
    )
        .bind("principalId", principalId)
        .map { row, _ -> requireNotNull(row.get("subject", String::class.java)) }
        .one()
        .awaitSingle()

    companion object {
        private const val ISSUER = "https://keycloak.example.test/realms/pt"
        private const val SUBJECT = "keycloak-subject-1"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("external_identity_link_gateway")
    }
}
