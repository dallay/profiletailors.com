package com.profiletailors.smp.infrastructure.db

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalIdentityLinksPostgresSchemaTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    @Test
    fun `valid link keeps the local principal identity unchanged`() = runTest {
        val linkedPrincipalId = UUID.randomUUID()
        insertPrincipal(linkedPrincipalId)
        insertExternalIdentityLink(ISSUER, SUBJECT, linkedPrincipalId)
        val principalIdentity = databaseClient.sql(
            "SELECT subject, provider FROM principals WHERE id = :id",
        )
            .bind("id", linkedPrincipalId)
            .map { row, _ ->
                requireNotNull(row.get("subject", String::class.java)) to row.get("provider", String::class.java)
            }
            .one()
            .awaitSingle()
        assertEquals("local-$linkedPrincipalId", principalIdentity.first)
        assertEquals(null, principalIdentity.second)
        assertEquals(1L, externalIdentityLinkCount())
    }

    @Test
    fun `duplicate issuer and subject is rejected`() = runTest {
        val principalId = UUID.randomUUID()
        insertPrincipal(principalId)
        insertExternalIdentityLink(ISSUER, SUBJECT, principalId)
        val duplicateIdentityError = runCatching {
            insertExternalIdentityLink(ISSUER, SUBJECT, UUID.randomUUID())
        }.exceptionOrNull()

        assertNotNull(duplicateIdentityError)
        assertTrue(requireNotNull(duplicateIdentityError).stackTraceToString().contains("external_identity_links"))
    }

    @Test
    fun `concurrent duplicate issuer and subject inserts allow one link`() = runTest {
        val principalId = UUID.randomUUID()
        insertPrincipal(principalId)
        val concurrentResults = listOf(
            async {
                runCatching {
                    insertExternalIdentityLink(ISSUER, "concurrent-subject", principalId)
                }
            },
            async {
                runCatching {
                    insertExternalIdentityLink(ISSUER, "concurrent-subject", principalId)
                }
            },
        ).awaitAll()

        assertEquals(1, concurrentResults.count { it.isSuccess })
        assertEquals(1, concurrentResults.count { it.isFailure })
    }

    @Test
    fun `principal foreign key rejects missing principal`() = runTest {
        val missingPrincipalError = runCatching {
            insertExternalIdentityLink(ISSUER, "missing-principal-subject", UUID.randomUUID())
        }.exceptionOrNull()

        assertNotNull(missingPrincipalError)
        assertTrue(requireNotNull(missingPrincipalError).stackTraceToString().contains("external_identity_links"))
    }

    private suspend fun insertPrincipal(principalId: UUID) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, NULL, 'Linked principal')
            """.trimIndent(),
        )
            .bind("id", principalId)
            .bind("subject", "local-$principalId")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertExternalIdentityLink(issuer: String, subject: String, principalId: UUID) {
        databaseClient.sql(
            """
            INSERT INTO external_identity_links (issuer, subject, principal_id, link_status)
            VALUES (:issuer, :subject, :principalId, 'APPROVED')
            """.trimIndent(),
        )
            .bind("issuer", issuer)
            .bind("subject", subject)
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun externalIdentityLinkCount(): Long = databaseClient.sql(
        "SELECT COUNT(*) FROM external_identity_links",
    )
        .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
        .one()
        .awaitSingle()

    companion object {
        private const val ISSUER = "https://keycloak.example.test/realms/pt"
        private const val SUBJECT = "keycloak-subject-1"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("external_identity_links_schema")
    }
}
