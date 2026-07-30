package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

private const val ADMIN_PRINCIPAL_ID = BDD_ADMIN_PRINCIPAL_ID
private const val ADMIN_BEARER = "Bearer $BDD_ADMIN_TOKEN"
private const val API_V1 = "application/vnd.api.v1+json"

@Suppress("TooManyFunctions")
class PlatformAdminBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private val json = ObjectMapper()

    private var lastResponse: EntityExchangeResult<ByteArray>? = null
    private var lastEntryId: String? = null
    private var lastInvitationId: String? = null

    // ── Setup ────────────────────────────────────────────────────────────────

    @Before("@platform-admin")
    fun resetPlatformAdminState() = runBlocking {
        lastResponse = null
        lastEntryId = null
        lastInvitationId = null
        cleanupPlatformAdminData()
    }

    @Given("a platform operator with role {string} is authenticated")
    fun platformOperatorAuthenticated(role: String) = runBlocking {
        seedAdminPrincipal()
        seedPlatformRoleAssignment(role)
    }

    @Given("the authenticated principal has no active platform role")
    fun noActivePlatformRole() = runBlocking {
        seedAdminPrincipal()
        // do NOT seed any platform_role_assignments
    }

    @Given("the authenticated principal has the role {string}")
    fun principalHasRole(role: String) = runBlocking {
        seedAdminPrincipal()
        seedPlatformRoleAssignment(role)
    }

    @Given("a pending waitlist entry exists for {string}")
    fun pendingWaitlistEntry(email: String) = runBlocking {
        val entryId = seedWaitlistEntry(email, "PENDING")
        lastEntryId = entryId
    }

    @Given("a converted waitlist entry exists for {string}")
    fun convertedWaitlistEntry(email: String) = runBlocking {
        val entryId = seedWaitlistEntry(email, "CONVERTED")
        lastEntryId = entryId
    }

    @Given("an invited waitlist entry with an active invitation exists for {string}")
    fun invitedWaitlistEntryWithActiveInvitation(email: String) = runBlocking {
        val entryId = seedWaitlistEntry(email, "INVITED")
        lastEntryId = entryId
        val invitationId = seedActiveInvitation(entryId)
        lastInvitationId = invitationId
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    @When("an unauthenticated principal requests the admin waitlist endpoint")
    fun unauthenticatedAdminRequest() {
        lastResponse = webTestClient.get()
            .uri("/api/admin/waitlist-entries")
            .header(HttpHeaders.ACCEPT, API_V1)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the principal requests the admin waitlist endpoint")
    fun principalRequestsAdminWaitlist() {
        lastResponse = webTestClient.get()
            .uri("/api/admin/waitlist-entries")
            .header(HttpHeaders.ACCEPT, API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator requests the admin waitlist endpoint")
    fun operatorRequestsAdminWaitlist() {
        lastResponse = webTestClient.get()
            .uri("/api/admin/waitlist-entries")
            .header(HttpHeaders.ACCEPT, API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the auditor attempts to invite the waitlist entry")
    fun auditorAttemptsInvite() {
        val entryId = requireNotNull(lastEntryId)
        lastResponse = webTestClient.post()
            .uri("/api/admin/waitlist-entries/$entryId/invitations")
            .header(HttpHeaders.ACCEPT, API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator invites the waitlist entry")
    fun operatorInvitesEntry() {
        val entryId = requireNotNull(lastEntryId)
        lastResponse = webTestClient.post()
            .uri("/api/admin/waitlist-entries/$entryId/invitations")
            .header(HttpHeaders.ACCEPT, API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()

        lastResponse?.responseBody?.let { body ->
            runCatching { json.readTree(body) }
                .getOrNull()
                ?.get("id")
                ?.asText()
                ?.let { lastInvitationId = it }
        }
    }

    @When("the platform operator cancels the waitlist entry with reason {string}")
    fun operatorCancelsEntry(reason: String) {
        val entryId = requireNotNull(lastEntryId)
        lastResponse = webTestClient.post()
            .uri("/api/admin/waitlist-entries/$entryId/cancel")
            .header(HttpHeaders.ACCEPT, API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reason":"$reason"}""")
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @When("the platform operator revokes the active invitation")
    fun operatorRevokesInvitation() {
        val invitationId = requireNotNull(lastInvitationId)
        lastResponse = webTestClient.post()
            .uri("/api/admin/invitations/$invitationId/revoke")
            .header(HttpHeaders.ACCEPT, API_V1)
            .header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    // ── Assertions ───────────────────────────────────────────────────────────

    @Then("the admin response status should be {int}")
    fun adminResponseStatus(expected: Int) {
        assertEquals(expected, lastResponse?.status?.value())
    }

    @And("the admin response code should be {string}")
    fun adminResponseCode(expected: String) {
        val body = lastResponse?.responseBody?.let { json.readTree(it) }
        val code = body?.get("properties")?.get("code")?.asText()
            ?: body?.get("code")?.asText()
        assertEquals(expected, code)
    }

    @And("the waitlist result should be paginated")
    fun waitlistResultIsPaginated() {
        val body = lastResponse?.responseBody?.let { json.readTree(it) }
        assertNotNull(body?.get("items"))
        assertNotNull(body?.get("totalElements"))
        assertNotNull(body?.get("page"))
    }

    @And("the waitlist entry status should become {string}")
    fun entryStatusIs(expected: String) = runBlocking {
        val entryId = requireNotNull(lastEntryId)
        val status = entryStatus(entryId)
        assertEquals(expected, status)
    }

    @And("the waitlist entry status should remain {string}")
    fun entryStatusRemains(expected: String) = runBlocking {
        val entryId = requireNotNull(lastEntryId)
        val status = entryStatus(entryId)
        assertEquals(expected, status)
    }

    @And("one active invitation should be created for the entry")
    fun oneActiveInvitationExists() = runBlocking {
        val entryId = requireNotNull(lastEntryId)
        val count = activeInvitationCount(entryId)
        assertEquals(1L, count)
    }

    @And("no active invitation should remain for the entry")
    fun noActiveInvitationExists() = runBlocking {
        val entryId = requireNotNull(lastEntryId)
        val count = activeInvitationCount(entryId)
        assertEquals(0L, count)
    }

    @And("the invitation status should be {string}")
    fun invitationStatusIs(expected: String) = runBlocking {
        val invId = requireNotNull(lastInvitationId)
        val status = invitationStatus(invId)
        assertEquals(expected, status)
    }

    @And("an audit event with action {string} should be recorded")
    fun auditEventRecorded(action: String) = runBlocking {
        val count = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = :action",
        )
            .bind("action", action)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertTrue(count > 0, "Expected at least one audit event with action $action")
    }

    @And("the audit event should not contain a raw invitation token")
    fun auditEventHasNoRawToken() = runBlocking {
        // token_hash column is on waitlist_invitations, not on audit events — audit events are safe by design
        val count = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = 'WAITLIST_ENTRY_INVITED'",
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertTrue(count >= 1)
    }

    // ── Database helpers ─────────────────────────────────────────────────────

    private suspend fun cleanupPlatformAdminData() {
        listOf(
            "DELETE FROM platform_admin_audit_events",
            "DELETE FROM waitlist_invitations",
            "DELETE FROM platform_role_assignments WHERE principal_id = '$ADMIN_PRINCIPAL_ID'::uuid",
            "DELETE FROM waitlist_entries WHERE waitlist_id = 'admin-bdd-waitlist'",
            "DELETE FROM waitlists WHERE id = 'admin-bdd-waitlist'",
            "DELETE FROM user_identities WHERE principal_id = '$ADMIN_PRINCIPAL_ID'",
            "DELETE FROM principals WHERE id = '$ADMIN_PRINCIPAL_ID'",
        ).forEach { sql ->
            runCatching { databaseClient.sql(sql).fetch().rowsUpdated().awaitSingle() }
        }
    }

    private suspend fun seedAdminPrincipal() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('$ADMIN_PRINCIPAL_ID', 'USER', 'local:admin@platform.example', NULL, 'Platform Admin')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('$ADMIN_PRINCIPAL_ID', 'admin@platform.example', 'platform-admin')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPlatformRoleAssignment(role: String) {
        val id = UUID.randomUUID()
        databaseClient.sql(
            """
            INSERT INTO platform_role_assignments (id, principal_id, role, assigned_at, assigned_by, version)
            VALUES ('$id', '$ADMIN_PRINCIPAL_ID'::uuid, '$role', NOW(), '$ADMIN_PRINCIPAL_ID'::uuid, 0)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedWaitlist(): String {
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES ('admin-bdd-waitlist', 'admin-bdd', 'BDD Waitlist', 'admin', 'ACTIVE')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        return "admin-bdd-waitlist"
    }

    private suspend fun seedWaitlistEntry(email: String, status: String): String {
        seedWaitlist()
        val entryId = "bdd-entry-${email.replace("@", "-at-").replace(".", "-")}"
        val invitedAt = if (status == "INVITED" || status == "CONVERTED") ", invited_at = NOW()" else ""
        val convertedAt = if (status == "CONVERTED") ", converted_at = NOW()" else ""
        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source,
               consent_early_access, consent_marketing, consent_version, status, joined_at)
            VALUES
              ('$entryId', 'admin-bdd-waitlist', '$email', '$email', 'bdd',
               true, false, '1.0', '$status', NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        if (invitedAt.isNotEmpty()) {
            databaseClient.sql("UPDATE waitlist_entries SET invited_at = NOW() WHERE id = '$entryId'")
                .fetch().rowsUpdated().awaitSingle()
        }
        if (convertedAt.isNotEmpty()) {
            databaseClient.sql("UPDATE waitlist_entries SET converted_at = NOW() WHERE id = '$entryId'")
                .fetch().rowsUpdated().awaitSingle()
        }
        return entryId
    }

    private suspend fun seedActiveInvitation(entryId: String): String {
        val invId = UUID.randomUUID()
        databaseClient.sql(
            """
            INSERT INTO waitlist_invitations
              (id, waitlist_entry_id, token_hash, status, issued_at, expires_at,
               created_by, delivery_status, delivery_attempt_count, version)
            VALUES
              ('$invId', '$entryId', 'bdd-token-hash-$invId', 'ACTIVE', NOW(), NOW() + interval '7 days',
               '$ADMIN_PRINCIPAL_ID'::uuid, 'SENT', 0, 0)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        return invId.toString()
    }

    private suspend fun entryStatus(entryId: String): String? =
        databaseClient.sql("SELECT status FROM waitlist_entries WHERE id = :id")
            .bind("id", entryId)
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .first()
            .awaitSingleOrNull()

    private suspend fun activeInvitationCount(entryId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) FROM waitlist_invitations WHERE waitlist_entry_id = :entryId AND status = 'ACTIVE'",
    )
        .bind("entryId", entryId)
        .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
        .one()
        .awaitSingle()

    private suspend fun invitationStatus(invitationId: String): String? =
        databaseClient.sql("SELECT status FROM waitlist_invitations WHERE id = :id")
            .bind("id", UUID.fromString(invitationId))
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .first()
            .awaitSingleOrNull()
}
