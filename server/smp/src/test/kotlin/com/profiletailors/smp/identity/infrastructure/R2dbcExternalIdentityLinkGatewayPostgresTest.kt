package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.ExternalIdentityLinkConflictException
import com.profiletailors.smp.identity.application.ExternalIdentityLinkProvisioningService
import com.profiletailors.smp.identity.application.VerifiedEmailSyncResult
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `create inserts explicit link and rejects duplicate identity pair without transfer`() = runTest {
        val originalPrincipalId = UUID.randomUUID()
        val otherPrincipalId = UUID.randomUUID()
        insertPrincipal(originalPrincipalId, "original-local-subject")
        insertPrincipal(otherPrincipalId, "other-local-subject")

        gateway.create(ISSUER, SUBJECT, originalPrincipalId.toString())
        val conflict = try {
            gateway.create(ISSUER, SUBJECT, otherPrincipalId.toString())
            error("Expected the unique identity pair to conflict")
        } catch (exception: ExternalIdentityLinkConflictException) {
            exception
        }

        assertEquals("Identity link already exists.", conflict.message)
        assertEquals(
            originalPrincipalId.toString(),
            gateway.findByIssuerAndSubject(ISSUER, SUBJECT).single().principalId,
        )
        assertEquals("original-local-subject", subjectFor(originalPrincipalId))
        assertEquals("other-local-subject", subjectFor(otherPrincipalId))
    }

    @Test
    fun `concurrent identity pair submissions persist only the first principal mapping`() = runTest {
        val firstPrincipalId = UUID.randomUUID()
        val secondPrincipalId = UUID.randomUUID()
        insertPrincipal(firstPrincipalId, "first-local-subject")
        insertPrincipal(secondPrincipalId, "second-local-subject")

        val outcomes = listOf(firstPrincipalId, secondPrincipalId).map { principalId ->
            async {
                runCatching { gateway.create(ISSUER, "concurrent-$SUBJECT", principalId.toString()) }
            }
        }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.exceptionOrNull() is ExternalIdentityLinkConflictException })
        assertEquals(
            outcomes.indexOfFirst { it.isSuccess }.let { index ->
                listOf(firstPrincipalId, secondPrincipalId)[index].toString()
            },
            gateway.findByIssuerAndSubject(ISSUER, "concurrent-$SUBJECT").single().principalId,
        )
    }

    @Test
    fun `conflicting identity pair leaves owner and challenger principal fields unchanged`() = runTest {
        val ownerPrincipalId = UUID.randomUUID()
        val challengerPrincipalId = UUID.randomUUID()
        insertPrincipal(ownerPrincipalId, "owner-local-subject", ISSUER, "Owner display identity")
        insertPrincipal(challengerPrincipalId, "challenger-local-subject", ISSUER, "Challenger display identity")
        insertUserIdentity(ownerPrincipalId, "owner@example.test")
        insertUserIdentity(challengerPrincipalId, "challenger@example.test")

        gateway.create(ISSUER, SUBJECT, ownerPrincipalId.toString())

        val ownerBaseline = snapshotOf(ownerPrincipalId)
        val challengerBaseline = snapshotOf(challengerPrincipalId)
        assertEquals(ISSUER, ownerBaseline.provider)
        assertEquals(ISSUER, challengerBaseline.provider)
        assertEquals("owner@example.test", ownerBaseline.email)
        assertEquals("challenger@example.test", challengerBaseline.email)

        val conflict = conflictFrom { gateway.create(ISSUER, SUBJECT, challengerPrincipalId.toString()) }

        assertEquals("Identity link already exists.", conflict.message)
        assertEquals(ownerBaseline, snapshotOf(ownerPrincipalId))
        assertEquals(challengerBaseline, snapshotOf(challengerPrincipalId))
        assertEquals(
            ownerPrincipalId.toString(),
            gateway.findByIssuerAndSubject(ISSUER, SUBJECT).single().principalId,
        )
        assertEquals("APPROVED", linkStatusFor(ISSUER, SUBJECT))
        assertEquals(1, linkCountFor(ISSUER, SUBJECT))
        assertEquals(0, linkCountForPrincipal(challengerPrincipalId))
    }

    @Test
    fun `provisioning seam reports identical non-disclosing conflicts for different owner principals`() = runTest {
        val provisioning = ExternalIdentityLinkProvisioningService(
            gateway,
            R2dbcPrincipalIdentityLookup(databaseClient),
            ISSUER,
        )
        val firstOwnerPrincipalId = UUID.randomUUID()
        val secondOwnerPrincipalId = UUID.randomUUID()
        val challengerPrincipalId = UUID.randomUUID()
        insertPrincipal(firstOwnerPrincipalId, "first-owner-local-subject", ISSUER)
        insertPrincipal(secondOwnerPrincipalId, "second-owner-local-subject", ISSUER)
        insertPrincipal(challengerPrincipalId, "challenger-local-subject", ISSUER)

        provisioning.provision(firstOwnerPrincipalId.toString(), ISSUER, "$SUBJECT-first-owner")
        provisioning.provision(secondOwnerPrincipalId.toString(), ISSUER, "$SUBJECT-second-owner")

        val firstOwnerBaseline = snapshotOf(firstOwnerPrincipalId)
        val secondOwnerBaseline = snapshotOf(secondOwnerPrincipalId)
        val challengerBaseline = snapshotOf(challengerPrincipalId)
        assertEquals(ISSUER, firstOwnerBaseline.provider)
        assertEquals(ISSUER, secondOwnerBaseline.provider)
        assertEquals(ISSUER, challengerBaseline.provider)

        val firstConflict = conflictFrom {
            provisioning.provision(challengerPrincipalId.toString(), ISSUER, "$SUBJECT-first-owner")
        }
        val secondConflict = conflictFrom {
            provisioning.provision(challengerPrincipalId.toString(), ISSUER, "$SUBJECT-second-owner")
        }

        assertEquals(firstConflict.message, secondConflict.message)
        assertEquals("Identity link already exists.", firstConflict.message)
        assertFalse(firstConflict.message.orEmpty().contains(firstOwnerPrincipalId.toString()))
        assertFalse(firstConflict.message.orEmpty().contains(secondOwnerPrincipalId.toString()))
        assertFalse(secondConflict.message.orEmpty().contains(firstOwnerPrincipalId.toString()))
        assertFalse(secondConflict.message.orEmpty().contains(secondOwnerPrincipalId.toString()))
        assertEquals(firstOwnerBaseline, snapshotOf(firstOwnerPrincipalId))
        assertEquals(secondOwnerBaseline, snapshotOf(secondOwnerPrincipalId))
        assertEquals(challengerBaseline, snapshotOf(challengerPrincipalId))
        assertEquals(
            firstOwnerPrincipalId.toString(),
            gateway.findByIssuerAndSubject(ISSUER, "$SUBJECT-first-owner").single().principalId,
        )
        assertEquals(
            secondOwnerPrincipalId.toString(),
            gateway.findByIssuerAndSubject(ISSUER, "$SUBJECT-second-owner").single().principalId,
        )
        assertEquals(0, linkCountForPrincipal(challengerPrincipalId))
    }

    @Test
    fun `multiple external identity links may reference one principal`() = runTest {
        val principalId = UUID.randomUUID()
        insertPrincipal(principalId, "multi-link-local-subject")

        gateway.create(ISSUER, "$SUBJECT-one", principalId.toString())
        gateway.create(ISSUER, "$SUBJECT-two", principalId.toString())

        assertEquals(
            2,
            listOf("$SUBJECT-one", "$SUBJECT-two").sumOf { subject ->
                gateway.findByIssuerAndSubject(ISSUER, subject).size
            },
        )
        assertEquals("multi-link-local-subject", subjectFor(principalId))
    }

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

    private suspend fun insertPrincipal(
        id: UUID,
        subject: String,
        provider: String? = null,
        displayIdentity: String = "External identity test principal",
    ) {
        val spec = databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, :provider, :displayIdentity)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("subject", subject)
            .bind("displayIdentity", displayIdentity)
        val bound = if (provider == null) {
            spec.bindNull("provider", String::class.java)
        } else {
            spec.bind("provider", provider)
        }
        bound.fetch()
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

    private suspend fun snapshotOf(principalId: UUID): PrincipalSnapshot = databaseClient.sql(
        """
        SELECT p.principal_type,
               p.subject,
               p.provider,
               p.display_identity,
               p.account_state,
               ui.email,
               ui.username,
               ui.email_status
        FROM principals p
        LEFT JOIN user_identities ui ON ui.principal_id = p.id
        WHERE p.id = :principalId
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .map { row, _ ->
            PrincipalSnapshot(
                principalType = requireNotNull(row.get("principal_type", String::class.java)),
                subject = requireNotNull(row.get("subject", String::class.java)),
                provider = row.get("provider", String::class.java),
                displayIdentity = row.get("display_identity", String::class.java),
                accountState = requireNotNull(row.get("account_state", String::class.java)),
                email = row.get("email", String::class.java),
                username = row.get("username", String::class.java),
                emailStatus = row.get("email_status", String::class.java),
            )
        }
        .one()
        .awaitSingle()

    private suspend fun linkStatusFor(issuer: String, subject: String): String = databaseClient.sql(
        "SELECT link_status FROM external_identity_links WHERE issuer = :issuer AND subject = :subject",
    )
        .bind("issuer", issuer)
        .bind("subject", subject)
        .map { row, _ -> requireNotNull(row.get("link_status", String::class.java)) }
        .one()
        .awaitSingle()

    private suspend fun linkCountFor(issuer: String, subject: String): Int = databaseClient.sql(
        "SELECT COUNT(*) AS count FROM external_identity_links WHERE issuer = :issuer AND subject = :subject",
    )
        .bind("issuer", issuer)
        .bind("subject", subject)
        .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)).toInt() }
        .one()
        .awaitSingle()

    private suspend fun linkCountForPrincipal(principalId: UUID): Int = databaseClient.sql(
        "SELECT COUNT(*) AS count FROM external_identity_links WHERE principal_id = :principalId",
    )
        .bind("principalId", principalId)
        .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)).toInt() }
        .one()
        .awaitSingle()

    private suspend fun conflictFrom(action: suspend () -> Unit): ExternalIdentityLinkConflictException = try {
        action()
        error("Expected the unique identity pair to conflict")
    } catch (exception: ExternalIdentityLinkConflictException) {
        exception
    }

    private data class PrincipalSnapshot(
        val principalType: String,
        val subject: String,
        val provider: String?,
        val displayIdentity: String?,
        val accountState: String,
        val email: String?,
        val username: String?,
        val emailStatus: String?,
    )

    companion object {
        private const val ISSUER = "https://keycloak.example.test/realms/pt"
        private const val SUBJECT = "keycloak-subject-1"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("external_identity_link_gateway")
    }
}
