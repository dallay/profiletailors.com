package com.profiletailors.smp.bdd.glue

import com.profiletailors.smp.identity.application.EmailVerificationTokenHasher
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class PlatformAdminBootstrapBddSteps {
    @Autowired
    private lateinit var database: BddDatabaseSupport

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var emailSender: RecordingEmailSender

    private var email = ""
    private var invitationToken = ""
    private var verificationToken = ""
    private var bootstrapStatus: Int? = null
    private var bootstrapCode: String? = null
    private var verifyStatus: Int? = null
    private var privateRoutesUnauthorized = false

    @Before("@bootstrap")
    fun resetBootstrapScenario() = runBlocking {
        database.resetDatabase()
        emailSender.reset()
        email = ""
        bootstrapStatus = null
        bootstrapCode = null
        verifyStatus = null
        privateRoutesUnauthorized = false
        verificationToken = ""
    }

    @Given("a terminal-issued bootstrap invitation exists for {string}")
    fun seedInvitation(address: String) = runBlocking {
        email = address
        invitationToken = "terminal-only-${UUID.randomUUID()}"
        databaseClient.sql(
            """
            INSERT INTO platform_admin_bootstrap_invitations
                (singleton_id, email_normalized, token_hash, expires_at, created_at, status)
            VALUES (1, :email, :tokenHash, :expiresAt, :createdAt, 'PENDING')
            """.trimIndent(),
        ).bind("email", email)
            .bind("tokenHash", EmailVerificationTokenHasher.hash(invitationToken))
            .bind("expiresAt", Instant.now().plusSeconds(1800))
            .bind("createdAt", Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @When("the recipient sets a password, accepts current Terms, and verifies email")
    fun completeBootstrapAndVerify() {
        val requirements = client.get().uri("/api/bootstrap/requirements")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .returnResult()
        val currentTermsVersion = requireNotNull(
            requirements.responseBody?.let { body ->
                val json = String(body)
                Regex(""""currentTermsVersion"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            },
        )

        val complete = client.post().uri("/api/bootstrap/complete")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "invitationToken" to invitationToken,
                    "password" to "bootstrap-password-2026",
                    "acceptedTermsVersion" to currentTermsVersion,
                ),
            )
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .returnResult()
        bootstrapStatus = complete.status.value()
        check(emailSender.awaitDelivery())
        val message = emailSender.messages.single().content.text
        val verificationLink = Regex("""https://app\.profiletailors\.com/verify-email\?token=([^\s]+)""")
            .find(message)
        verificationToken = requireNotNull(verificationLink?.groupValues?.get(1))

        val verified = client.post().uri("/api/auth/verify-email")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("token" to verificationToken))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .returnResult()
        verifyStatus = verified.status.value()
    }

    @When("an unauthenticated recipient submits an invalid bootstrap credential")
    fun submitInvalidCredential() {
        val response = client.post().uri("/api/bootstrap/complete")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "invitationToken" to "not-issued-token",
                    "password" to "bootstrap-password-2026",
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .returnResult()
        bootstrapStatus = response.status.value()
        val body = requireNotNull(response.responseBody).decodeToString()
        bootstrapCode = Regex(""""code"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
    }

    @When("an unauthenticated client requests bootstrap status and issuance")
    fun requestPrivateBootstrapRoutes() {
        client.get().uri("/api/bootstrap/status").exchange().expectStatus().isUnauthorized
        client.post().uri("/api/bootstrap/issue").exchange().expectStatus().isUnauthorized
        privateRoutesUnauthorized = true
    }

    @Then("the invitation verification email uses the existing app route and is sent once")
    fun verifyEmailUsesExistingRoute() {
        assertEquals(202, bootstrapStatus)
        assertEquals(200, verifyStatus)
        assertEquals(1, emailSender.messages.size)
        assertEquals("https://app.profiletailors.com/verify-email", verificationUrlBase())
    }

    @Then("the verified recipient has platform consent and no tenant data")
    fun verifyPlatformConsentAndNoTenantData() = runBlocking {
        val principalId = principalId()
        assertNotNull(principalId)
        val platformConsentCount = databaseClient.sql(
            """
            SELECT COUNT(*) FROM consent_records
            WHERE scope_kind = 'PLATFORM' AND workspace_id IS NULL AND waitlist_id IS NULL
              AND subject_kind = 'USER' AND subject_value = :principalId
              AND purpose = 'terms.acceptance' AND policy_version = 'terms-v1.0.0'
            """.trimIndent(),
        ).bind("principalId", principalId!!.toString())
            .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }
            .one()
            .awaitSingle()
        assertEquals(1L, platformConsentCount)
        assertEquals(0L, countForPrincipal("workspace_memberships", "principal_id", principalId))
        assertEquals(0L, countForPrincipal("workspace_ownerships", "owner_principal_id", principalId))
        assertEquals(
            0L,
            databaseClient.sql(
                """
                SELECT COUNT(*) FROM consent_records
                WHERE subject_value = :principalId AND purpose = 'age-eligibility.18-plus'
                """.trimIndent(),
            ).bind("principalId", principalId.toString())
                .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }.one().awaitSingle(),
        )
    }

    @Then("the owner grant audit identifies only the system bootstrap actor")
    fun verifySystemActorAudit() = runBlocking {
        val principalId = requireNotNull(principalId())
        val actor = databaseClient.sql(
            """
            SELECT assigned_by, actor_kind FROM platform_role_assignments
            WHERE principal_id = :principalId AND role = 'PLATFORM_OWNER' AND revoked_at IS NULL
            """.trimIndent(),
        ).bind("principalId", principalId).map { row, _ ->
            row.get("assigned_by", UUID::class.java) to requireNotNull(row.get("actor_kind", String::class.java))
        }.one().awaitSingle()
        assertNull(actor.first)
        assertEquals("SYSTEM_BOOTSTRAP", actor.second)
        val auditActor = databaseClient.sql(
            """
            SELECT actor_kind, operator_principal_id, operator_platform_roles, metadata
            FROM platform_admin_audit_events WHERE action = 'BOOTSTRAP_OWNER_GRANTED'
            """.trimIndent(),
        ).map { row, _ ->
            AuditActor(
                actorKind = requireNotNull(row.get("actor_kind", String::class.java)),
                operatorPrincipalId = row.get("operator_principal_id", UUID::class.java),
                operatorPlatformRoles = requireNotNull(row.get("operator_platform_roles", Array<String>::class.java))
                    .toList(),
                metadata = row.get("metadata", String::class.java),
            )
        }.one().awaitSingle()
        assertEquals("SYSTEM_BOOTSTRAP", auditActor.actorKind)
        assertNull(auditActor.operatorPrincipalId)
        assertEquals(emptyList<String>(), auditActor.operatorPlatformRoles)
        val metadata = requireNotNull(auditActor.metadata)
        assertFalse(metadata.contains(invitationToken))
        assertFalse(metadata.contains(EmailVerificationTokenHasher.hash(invitationToken)))
    }

    @Then("the bootstrap response code is {string}")
    fun verifyBootstrapCode(expectedCode: String) = runBlocking {
        assertEquals(400, bootstrapStatus)
        assertEquals(expectedCode, bootstrapCode)
    }

    @Then("no bootstrap owner or tenant data is created")
    fun verifyNoBootstrapMutation() = runBlocking {
        assertEquals(
            0L,
            databaseClient.sql("SELECT COUNT(*) FROM platform_role_assignments WHERE role = 'PLATFORM_OWNER'")
                .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }.one().awaitSingle(),
        )
        assertEquals(
            0L,
            databaseClient.sql("SELECT COUNT(*) FROM user_identities WHERE email = 'bootstrap-owner@example.test'")
                .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }.one().awaitSingle(),
        )
    }

    @Then("both requests are unauthorized")
    fun verifyPrivateRoutesAreUnauthenticated() = assertTrue(privateRoutesUnauthorized)

    private suspend fun principalId(): UUID? = databaseClient.sql(
        "SELECT id FROM principals WHERE subject = :subject",
    ).bind("subject", "local:$email")
        .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)) }
        .one()
        .awaitSingleOrNull()

    private suspend fun countForPrincipal(table: String, column: String, principalId: UUID): Long =
        databaseClient.sql("SELECT COUNT(*) FROM $table WHERE $column = :principalId")
            .bind("principalId", principalId)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }
            .one()
            .awaitSingle()

    private fun verificationUrlBase(): String = requireNotNull(emailSender.messages.single().content.text)
        .lineSequence()
        .first { it.contains("/verify-email?token=") }
        .substringBefore("?token=")

    private data class AuditActor(
        val actorKind: String,
        val operatorPrincipalId: UUID?,
        val operatorPlatformRoles: List<String>,
        val metadata: String?,
    )
}
