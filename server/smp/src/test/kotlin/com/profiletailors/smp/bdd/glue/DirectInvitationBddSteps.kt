package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.test.web.reactive.server.WebTestClient
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val DIRECT_API_V1 = "application/vnd.api.v1+json"
private const val ADMIN_PRINCIPAL_ID = BDD_ADMIN_PRINCIPAL_ID
private const val ADMIN_BEARER = "Bearer $BDD_ADMIN_TOKEN"

class DirectInvitationBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var database: BddDatabaseSupport

    @Autowired
    private lateinit var state: PlatformAdminScenarioState

    private val json = ObjectMapper()

    @Given("an active direct invitation exists for {string}")
    fun activeDirectInvitationExists(email: String) = runBlocking {
        state.lastInvitationId = null
        seedInvitation(
            email = email,
            source = "DIRECT",
            sourceReferenceId = null,
            workspaceId = "invitation-workspace",
        )
        state.lastInvitationId = latestInvitationId(email)
    }

    @Given("an expired direct invitation exists for {string}")
    fun expiredDirectInvitationExists(email: String) = runBlocking {
        state.lastInvitationId = null
        seedInvitation(
            email = email,
            source = "DIRECT",
            sourceReferenceId = null,
            workspaceId = "invitation-workspace",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        state.lastInvitationId = latestInvitationId(email)
    }

    @Given("a revoked direct invitation exists for {string}")
    fun revokedDirectInvitationExists(email: String) = runBlocking {
        state.lastInvitationId = null
        seedInvitation(
            email = email,
            source = "DIRECT",
            sourceReferenceId = null,
            workspaceId = "invitation-workspace",
        )
        val invitationId = latestInvitationId(email)
        databaseClient.sql(
            "UPDATE invitations SET status = 'REVOKED', version = version + 1 WHERE id = :id",
        )
            .bind("id", UUID.fromString(invitationId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        state.lastInvitationId = invitationId
    }

    @Given("the authenticated principal has an existing credential and workspace membership")
    fun authenticatedPrincipalHasExistingCredentialAndWorkspaceMembership() = runBlocking {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'jwt-user@example.com')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES ('principal-1', 'jwt-user@example.com', 'jwt-user', 'VERIFIED')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO local_password_credentials (principal_id, password_hash)
            VALUES (:principalId, :passwordHash)
            ON CONFLICT (principal_id) DO NOTHING
            """.trimIndent(),
        )
            .bind("principalId", BddDatabaseSupport.PRINCIPAL_ID)
            .bind("passwordHash", BCrypt.hashpw("bdd-existing-password", BCrypt.gensalt()))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('invitation-membership-principal-1', 'invitation-workspace', :principalId, 'USER', 'ACTIVE')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("principalId", BddDatabaseSupport.PRINCIPAL_ID)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @Given("a consumed direct invitation exists for {string}")
    fun consumedDirectInvitationExists(email: String) = runBlocking {
        state.lastInvitationId = null
        seedInvitation(
            email = email,
            source = "DIRECT",
            sourceReferenceId = null,
            workspaceId = "invitation-workspace",
        )
        val invitationId = latestInvitationId(email)
        databaseClient.sql(
            """
            UPDATE invitations
            SET status = 'ACCEPTED', accepted_at = NOW(), accepted_principal_id = 'principal-1',
                version = version + 1
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(invitationId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        state.lastInvitationId = invitationId
    }

    @Then("the invitation acceptance workspace should be {string}")
    fun invitationAcceptanceWorkspaceShouldBe(workspaceId: String) {
        lastResponseJson().path("workspaceId").asText().let { assertEquals(workspaceId, it) }
    }

    @Then("the invitation acceptance membership status should be {string}")
    fun invitationAcceptanceMembershipStatusShouldBe(status: String) {
        assertEquals(status, lastResponseJson().path("membershipStatus").asText())
    }

    @Then("the invitation response should not contain the token")
    fun invitationResponseShouldNotContainToken() {
        assertTrue(!lastResponseJson().has("token"))
        assertTrue(!lastResponseJson().has("rawToken"))
    }

    @Then("the invitation response should not contain {string}")
    fun invitationResponseShouldNotContain(value: String) {
        assertTrue(!lastResponseJson().toString().contains(value))
    }

    @Then("exactly one direct invitation should exist for {string}")
    fun exactlyOneDirectInvitationShouldExist(email: String) = runBlocking {
        assertEquals(1L, database.countInvitationsByEmail(email))
    }

    @Then("the authenticated principal should have exactly one identity and credential")
    fun authenticatedPrincipalShouldHaveExactlyOneIdentityAndCredential() = runBlocking {
        assertEquals(1L, database.countIdentities(BddDatabaseSupport.PRINCIPAL_ID))
        assertEquals(1L, database.countPasswordCredentials(BddDatabaseSupport.PRINCIPAL_ID))
    }

    @Then("the invitation workspace should have exactly one workspace and membership for the authenticated principal")
    fun invitationWorkspaceShouldHaveExactlyOneWorkspaceAndMembership() = runBlocking {
        assertEquals(1L, database.countWorkspaces("invitation-workspace"))
        assertEquals(
            1L,
            database.countWorkspaceMemberships(
                principalId = BddDatabaseSupport.PRINCIPAL_ID,
                workspaceId = "invitation-workspace",
            ),
        )
    }

    @When("an unauthenticated principal accepts the invitation")
    fun unauthenticatedInvitationAcceptance() {
        state.lastResponse = webTestClient.post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .bodyValue("""{"token":"${state.invitationToken ?: "unused-token"}"}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the authenticated principal accepts the invitation with an empty token")
    fun authenticatedInvitationAcceptanceWithEmptyToken() {
        state.lastResponse = webTestClient.post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .bodyValue("""{"token":"   "}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the authenticated principal accepts the invitation")
    fun authenticatedInvitationAcceptance() = authenticatedInvitationAcceptanceWithToken(
        state.invitationToken ?: "unavailable-token",
    )

    @When("the authenticated principal accepts the invitation with an unavailable token")
    fun authenticatedInvitationAcceptanceWithUnavailableToken() = authenticatedInvitationAcceptanceWithToken(
        "unavailable-token",
    )

    private fun authenticatedInvitationAcceptanceWithToken(token: String) {
        state.lastResponse = webTestClient.post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .bodyValue("""{"token":"$token"}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the authenticated principal accepts the invitation again")
    fun authenticatedInvitationAcceptanceAgain() = authenticatedInvitationAcceptance()

    @When("the platform operator creates a direct invitation for {string}")
    fun operatorCreatesDirectInvitation(email: String) = runBlocking {
        seedInvitationWorkspace("invitation-workspace")
        state.lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/direct")
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(directInvitationPayload(email))
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()

        state.lastResponse?.responseBody?.let { body ->
            runCatching { json.readTree(body) }
                .getOrNull()
                ?.get("invitationId")
                ?.asText()
                ?.let { state.lastInvitationId = it }
        }
    }

    @When("an unauthenticated principal creates a direct invitation for {string}")
    fun unauthenticatedCreatesDirectInvitation(email: String) = runBlocking {
        seedInvitationWorkspace("invitation-workspace")
        state.lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/direct")
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(directInvitationPayload(email))
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator revokes the direct invitation")
    fun operatorRevokesDirectInvitation() = runBlocking {
        val invitationId = requireNotNull(state.lastInvitationId)
        val version = invitationVersion(invitationId)
        state.lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/$invitationId/direct-revoke")
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":$version}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator revokes the direct invitation with the returned version")
    fun operatorRevokesDirectInvitationWithReturnedVersion() {
        val invitationId = requireNotNull(state.lastInvitationId)
        val version = lastResponseJson().path("version").asLong()
        state.lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/$invitationId/direct-revoke")
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":$version}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator revokes a missing direct invitation")
    fun operatorRevokesMissingDirectInvitation() {
        val invitationId = UUID.randomUUID()
        state.lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/$invitationId/direct-revoke")
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":0}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator resends the direct invitation")
    fun operatorResendsDirectInvitation() {
        val invitationId = requireNotNull(state.lastInvitationId)
        state.lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/$invitationId/direct-resend")
            .header(HttpHeaders.ACCEPT, DIRECT_API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @Then("the direct invitation response should contain an id")
    fun directInvitationResponseShouldContainId() {
        assertNotNull(lastResponseJson().path("invitationId").asText(null))
    }

    private fun lastResponseJson() = json.readTree(requireNotNull(state.lastResponse).responseBody)

    private fun directInvitationPayload(email: String): String = json.writeValueAsString(
        mapOf(
            "email" to email.trim().lowercase(),
            "target" to "EXISTING_WORKSPACE",
            "workspaceId" to "invitation-workspace",
        ),
    )

    private suspend fun seedInvitation(
        email: String,
        source: String,
        sourceReferenceId: String?,
        workspaceId: String,
        createdAt: Instant = Instant.now(),
        expiresAt: Instant = createdAt.plus(Duration.ofDays(7)),
    ) {
        val token = "bdd-invitation-token-${UUID.randomUUID()}"
        val invitationId = UUID.randomUUID()
        val candidateKey = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val tokenHash = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
            token,
            org.springframework.security.crypto.bcrypt.BCrypt.gensalt(),
        )
        seedInvitationIssuer()
        seedInvitationWorkspace(workspaceId)
        databaseClient.sql(
            """
            INSERT INTO invitations (
                id, source, source_reference_id, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at
            ) VALUES (
                :id, :source, :sourceReferenceId, :workspaceId, :email,
                :candidateKey, :tokenHash, 'ACTIVE', :issuedBy, :createdAt, :expiresAt
            )
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .bind("source", source)
            .let { spec ->
                if (sourceReferenceId == null) {
                    spec.bindNull("sourceReferenceId", String::class.java)
                } else {
                    spec.bind("sourceReferenceId", sourceReferenceId)
                }
            }
            .bind("workspaceId", workspaceId)
            .bind("email", email.trim().lowercase())
            .bind("candidateKey", candidateKey)
            .bind("tokenHash", tokenHash)
            .bind("issuedBy", ADMIN_PRINCIPAL_ID)
            .bind("createdAt", createdAt)
            .bind("expiresAt", expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        state.invitationToken = token
    }

    private suspend fun seedInvitationIssuer() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', 'local:admin@platform.example', NULL, 'Platform Admin')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", ADMIN_PRINCIPAL_ID)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedInvitationWorkspace(workspaceId: String) {
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES (:id, 'Invitation BDD Workspace', 'ACTIVE', NULL)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun latestInvitationId(email: String): String = databaseClient.sql(
        "SELECT id FROM invitations WHERE invited_email_normalized = :email ORDER BY created_at DESC LIMIT 1",
    )
        .bind("email", email.trim().lowercase())
        .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
        .one()
        .awaitSingle()

    private suspend fun invitationVersion(invitationId: String): Long = databaseClient.sql(
        "SELECT version FROM invitations WHERE id = :id",
    )
        .bind("id", UUID.fromString(invitationId))
        .map { row, _ -> requireNotNull(row.get("version", Long::class.java)) }
        .one()
        .awaitSingle()
}
