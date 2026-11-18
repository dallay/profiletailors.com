package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.bdd.support.TestOidcProvider
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

class IdentityLinkProvisioningBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private val json = ObjectMapper()
    private var responseStatus: Int? = null
    private var operatorId = UUID.randomUUID()
    private var targetId = UUID.randomUUID()
    private var operatorSubject = ""
    private var linkSubject = ""
    private var issuer = ""
    private var sessionCookie = ""
    private var csrfToken = ""
    private var csrfHeaderName = ""
    private var originalMapping: String? = null
    private var responseBody = ""
    private var persistenceFailureInstalled = false

    @Before("@platform-admin")
    fun resetIdentityLinkState() = runBlocking {
        databaseClient.sql("DELETE FROM external_identity_links").fetch().rowsUpdated().awaitSingle()
        bddDatabaseSupport.resetDatabase()
        responseStatus = null
        operatorId = UUID.randomUUID()
        targetId = UUID.randomUUID()
        operatorSubject = "bdd-operator-${UUID.randomUUID()}"
        linkSubject = "bdd-linked-subject-${UUID.randomUUID()}"
        issuer = ""
        sessionCookie = ""
        csrfToken = ""
        csrfHeaderName = ""
        originalMapping = null
        responseBody = ""
        persistenceFailureInstalled = false
    }

    @After("@platform-admin")
    fun removeIdentityLinkPersistenceFailure() = runBlocking {
        if (persistenceFailureInstalled) {
            databaseClient.sql("DROP TRIGGER fail_identity_link_provisioning ON external_identity_links")
                .fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("DROP FUNCTION fail_identity_link_provisioning()")
                .fetch().rowsUpdated().awaitSingle()
        }
    }

    @When("an unauthenticated client submits an identity-link request")
    fun unauthenticatedClientSubmitsIdentityLinkRequest() {
        responseStatus = webTestClient.post()
            .uri("/api/admin/users/${TARGET_ID}/identity-link")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header("Idempotency-Key", "identity-link-unauthenticated")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("issuer" to "https://keycloak.ahome.quest/realms/pt", "subject" to "subject"))
            .exchange()
            .expectBody()
            .returnResult()
            .status.value()
    }

    @Given("an operator with USERS_MANAGE and a target principal with no external identity link")
    fun seedOperatorAndTarget() = runBlocking {
        issuer = TestOidcProvider.start()
        insertPrincipal(operatorId, "identity-link-operator")
        insertUserIdentity(operatorId, "identity-link-operator@example.test")
        insertExternalIdentityLink(issuer, operatorSubject, operatorId)
        databaseClient.sql(
            """
            INSERT INTO platform_role_assignments (id, principal_id, role, assigned_at, assigned_by, actor_kind, version)
            VALUES (:id, :principalId, 'PLATFORM_OPERATOR', NOW(), :principalId, 'HUMAN', 0)
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID())
            .bind("principalId", operatorId)
            .fetch().rowsUpdated().awaitSingle()
        insertPrincipal(targetId, "identity-link-target")
        insertUserIdentity(targetId, "identity-link-target@example.test")
        TestOidcProvider.setIdentity(
            TestOidcProvider.ADMIN_CLIENT_ID,
            operatorSubject,
            "identity-link-operator@example.test",
        )
    }

    @When("the operator completes a local OIDC authorization-code login with fresh OTP MFA")
    fun operatorCompletesLocalOidcLogin() {
        val start = webTestClient.get()
            .uri("/oauth2/authorization/pt-admin")
            .exchange()
            .expectStatus().is3xxRedirection
            .expectBody().returnResult()
        val authorizationUri = URI.create(requireNotNull(start.responseHeaders.location).toString())
        sessionCookie = cookies(start).first { it.startsWith("SESSION=") }.substringBefore(';')
        val authorizationResponse = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
            .send(
                HttpRequest.newBuilder(authorizationUri).GET().build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        assertEquals(302, authorizationResponse.statusCode())
        val callbackUri = URI.create(
            requireNotNull(authorizationResponse.headers().firstValue("Location").orElse(null)),
        )
        val callback = webTestClient.get()
            .uri(callbackUri)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .exchange()
            .expectBody().returnResult()
        assertEquals(303, callback.status.value(), "OIDC callback did not complete login")
        sessionCookie = cookies(callback).firstOrNull { it.startsWith("SESSION=") }
            ?.substringBefore(';') ?: sessionCookie
        val profile = webTestClient.get()
            .uri("/api/auth/me")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody().returnResult()
        assertTrue(String(requireNotNull(profile.responseBody)).contains(operatorId.toString()))
        val adminSession = webTestClient.get()
            .uri("/api/admin/session")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody().returnResult()
        assertTrue(String(requireNotNull(adminSession.responseBody)).contains("PLATFORM_OPERATOR"))
    }

    @And("the authenticated operator provisions the target identity link")
    fun operatorProvisionsTargetIdentityLink() = runBlocking {
        acquireCsrf()
        submitLink("identity-link-first-${UUID.randomUUID()}")
        originalMapping = lookupMapping()
    }

    @And("the authenticated operator repeats the identity-link request")
    fun operatorRepeatsIdentityLinkRequest() {
        submitLink("identity-link-duplicate-${UUID.randomUUID()}")
    }

    @And("the authenticated operator submits an identity-link request with an invalid issuer")
    fun operatorSubmitsInvalidIssuerIdentityLinkRequest() {
        acquireCsrf()
        submitLink(
            "identity-link-invalid-issuer-${UUID.randomUUID()}",
            requestIssuer = "https://invalid.example.test/realm",
        )
    }

    @And("the authenticated operator submits an identity-link request without an idempotency key")
    fun operatorSubmitsIdentityLinkWithoutIdempotencyKey() {
        acquireCsrf()
        val result = webTestClient.post()
            .uri("/api/admin/users/$targetId/identity-link")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .header(csrfHeaderName, csrfToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("issuer" to issuer, "subject" to linkSubject))
            .exchange()
            .expectBody().returnResult()
        responseStatus = result.status.value()
        responseBody = result.responseBody?.toString(Charsets.UTF_8).orEmpty()
    }

    @Then("the identity-link problem detail code should be {string}")
    fun identityLinkProblemDetailCodeShouldBe(expected: String) {
        assertEquals(expected, json.readTree(responseBody).path("code").asText(), responseBody)
    }

    @And("identity-link persistence fails during provisioning")
    fun failIdentityLinkPersistence() = runBlocking {
        acquireCsrf()
        databaseClient.sql(
            """
            CREATE FUNCTION fail_identity_link_provisioning() RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'identity-link persistence unavailable';
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            CREATE TRIGGER fail_identity_link_provisioning
            BEFORE INSERT ON external_identity_links
            FOR EACH ROW EXECUTE FUNCTION fail_identity_link_provisioning()
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        persistenceFailureInstalled = true
        submitLink("identity-link-persistence-failure-${UUID.randomUUID()}")
    }

    @And("the operator loses USERS_MANAGE before identity-link provisioning")
    fun revokeOperatorManagePermission() = runBlocking {
        databaseClient.sql(
            "DELETE FROM platform_role_assignments WHERE principal_id = :operatorId AND role = 'PLATFORM_OPERATOR'",
        )
            .bind("operatorId", operatorId)
            .fetch().rowsUpdated().awaitSingle()
    }

    @And("the target principal is removed before identity-link provisioning")
    fun removeTargetBeforeIdentityLinkProvisioning() = runBlocking {
        databaseClient.sql("DELETE FROM user_identities WHERE principal_id = :targetId")
            .bind("targetId", targetId)
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM principals WHERE id = :targetId")
            .bind("targetId", targetId)
            .fetch().rowsUpdated().awaitSingle()
    }

    @And("the authenticated operator submits an identity-link request with an invalid subject")
    fun operatorSubmitsInvalidSubjectIdentityLinkRequest() {
        acquireCsrf()
        submitLink("identity-link-invalid-subject-${UUID.randomUUID()}", requestSubject = " ")
    }

    @Then("the identity-link response status should be {int}")
    fun identityLinkResponseStatusShouldBe(expected: Int) {
        assertEquals(expected, responseStatus, responseBody)
    }

    @And("the target external identity link should be persisted")
    fun targetExternalIdentityLinkShouldBePersisted() = runBlocking {
        assertEquals(targetId.toString(), lookupMapping())
    }

    @And("the original external identity link should remain unchanged")
    fun originalExternalIdentityLinkShouldRemainUnchanged() = runBlocking {
        assertEquals(targetId.toString(), originalMapping)
        assertEquals(originalMapping, lookupMapping())
    }

    @And("the target external identity link should not be persisted")
    fun targetExternalIdentityLinkShouldNotBePersisted() = runBlocking {
        assertEquals(null, lookupMapping())
    }

    @And("the identity-link invalid issuer audit evidence should be persisted")
    fun invalidIssuerAuditEvidenceShouldBePersisted() = runBlocking {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM platform_admin_audit_events
            WHERE operator_principal_id = :operatorId
              AND action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED'
              AND target_id = :targetId
              AND result = 'REJECTED'
              AND reason = 'invalid_identity'
              AND metadata::jsonb ->> 'issuer' = :issuer
              AND metadata::jsonb ->> 'subject' = :subject
            """.trimIndent(),
        )
            .bind("operatorId", operatorId)
            .bind("targetId", targetId.toString())
            .bind("issuer", "https://invalid.example.test/realm")
            .bind("subject", linkSubject)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        assertEquals(1L, count)
    }

    @And("the identity-link permission denial audit evidence should be persisted")
    fun permissionDenialAuditEvidenceShouldBePersisted() = runBlocking {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM platform_admin_audit_events
            WHERE operator_principal_id = :operatorId
              AND action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED'
              AND target_id = :targetId
              AND result = 'REJECTED'
              AND reason = 'permission_denied'
            """.trimIndent(),
        )
            .bind("operatorId", operatorId)
            .bind("targetId", targetId.toString())
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        assertEquals(1L, count)
    }

    @And("the identity-link missing target audit evidence should be persisted")
    fun missingTargetAuditEvidenceShouldBePersisted() = runBlocking {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM platform_admin_audit_events
            WHERE operator_principal_id = :operatorId
              AND action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED'
              AND target_id = :targetId
              AND result = 'REJECTED'
              AND reason = 'target_not_found'
            """.trimIndent(),
        )
            .bind("operatorId", operatorId)
            .bind("targetId", targetId.toString())
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        assertEquals(1L, count)
    }

    @And("the identity-link invalid subject audit evidence should be persisted")
    fun invalidSubjectAuditEvidenceShouldBePersisted() = runBlocking {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM platform_admin_audit_events
            WHERE operator_principal_id = :operatorId
              AND action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED'
              AND target_id = :targetId
              AND result = 'REJECTED'
              AND reason = 'invalid_identity'
              AND metadata::jsonb ->> 'issuer' = :issuer
              AND metadata::jsonb ->> 'subject' = :subject
            """.trimIndent(),
        )
            .bind("operatorId", operatorId)
            .bind("targetId", targetId.toString())
            .bind("issuer", issuer)
            .bind("subject", " ")
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        assertEquals(1L, count)
    }

    @And("the identity-link persistence failure audit evidence should be persisted")
    fun identityLinkPersistenceFailureAuditEvidenceShouldBePersisted() = runBlocking {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM platform_admin_audit_events
            WHERE operator_principal_id = :operatorId
              AND action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED'
              AND target_id = :targetId
              AND result = 'FAILED'
              AND reason = 'persistence_failure'
              AND metadata::jsonb ->> 'issuer' = :issuer
              AND metadata::jsonb ->> 'subject' = :subject
            """.trimIndent(),
        )
            .bind("operatorId", operatorId)
            .bind("targetId", targetId.toString())
            .bind("issuer", issuer)
            .bind("subject", linkSubject)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        assertEquals(1L, count)
    }

    @And("the identity-link audit evidence should be persisted")
    fun identityLinkAuditEvidenceShouldBePersisted() = runBlocking {
        val count = databaseClient.sql(
            """
            SELECT COUNT(*) FROM platform_admin_audit_events
            WHERE operator_principal_id = :operatorId
              AND action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED'
              AND target_id = :targetId
              AND result = 'REJECTED'
              AND reason = 'identity_conflict'
            """.trimIndent(),
        )
            .bind("operatorId", operatorId)
            .bind("targetId", targetId.toString())
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        assertEquals(1L, count)
    }

    private fun acquireCsrf() {
        val result = webTestClient.get()
            .uri("/api/auth/csrf")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody().returnResult()
        val csrf = json.readTree(requireNotNull(result.responseBody))
        csrfToken = csrf.path("token").asText()
        csrfHeaderName = csrf.path("headerName").asText()
        sessionCookie = cookies(result).firstOrNull { it.startsWith("SESSION=") }
            ?.substringBefore(';') ?: sessionCookie
    }

    private fun submitLink(
        idempotencyKey: String,
        requestIssuer: String = issuer,
        requestSubject: String = linkSubject,
    ) {
        val result = webTestClient.post()
            .uri("/api/admin/users/$targetId/identity-link")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .header(csrfHeaderName, csrfToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("issuer" to requestIssuer, "subject" to requestSubject))
            .exchange()
            .expectBody().returnResult()
        responseStatus = result.status.value()
        responseBody = result.responseBody?.toString(Charsets.UTF_8).orEmpty()
    }

    private fun cookies(result: EntityExchangeResult<*>): List<String> =
        result.responseHeaders[HttpHeaders.SET_COOKIE].orEmpty()

    private suspend fun insertPrincipal(id: UUID, subject: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, NULL, :subject)
            """.trimIndent(),
        ).bind("id", id).bind("subject", subject).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertUserIdentity(id: UUID, email: String) {
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES (:id, :email, :username, 'VERIFIED')
            """.trimIndent(),
        ).bind("id", id).bind("email", email).bind("username", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertExternalIdentityLink(linkIssuer: String, subject: String, principalId: UUID) {
        databaseClient.sql(
            """
            INSERT INTO external_identity_links (issuer, subject, principal_id, link_status)
            VALUES (:issuer, :subject, :principalId, 'APPROVED')
            """.trimIndent(),
        ).bind("issuer", linkIssuer).bind("subject", subject).bind("principalId", principalId)
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun lookupMapping(): String? = databaseClient.sql(
        "SELECT principal_id FROM external_identity_links WHERE issuer = :issuer AND subject = :subject",
    ).bind("issuer", issuer).bind("subject", linkSubject)
        .map { row, _ -> requireNotNull(row.get("principal_id", UUID::class.java)).toString() }
        .one().awaitSingleOrNull()

    private companion object {
        const val TARGET_ID = "00000000-0000-0000-0000-000000000001"
    }
}
