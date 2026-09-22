package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.OffsetDateTime
import java.util.UUID

private const val TAKEDOWN_API_V1 = "application/vnd.api.v1+json"
private const val TAKEDOWN_ADMIN_BEARER = "Bearer $BDD_ADMIN_TOKEN"
private const val TAKEDOWN_PATH = "/api/admin/takedown-reports"

class TakedownAdminBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var state: PlatformAdminScenarioState

    private val json = ObjectMapper()
    private var lastIdempotencyKey: String? = null

    @Before("@platform-takedown")
    fun resetState() = runBlocking {
        databaseClient.sql("DELETE FROM takedown_reports").fetch().rowsUpdated().awaitSingle()
        state.lastResponse = null
        state.takedownReportIds.clear()
        lastIdempotencyKey = null
    }

    @Given("a takedown report exists with status {string} and workspace {string}")
    fun seedTakedownReport(status: String, workspaceId: String) = runBlocking {
        val reportId = "bdd-takedown-${UUID.randomUUID()}"
        val principalId = UUID.randomUUID().toString()
        val assetId = "bdd-asset-${UUID.randomUUID()}"
        val reporterEmail = "reporter+${UUID.randomUUID()}@example.com"

        seedReporterPrincipal(principalId, reporterEmail)
        seedOwnedWorkspace(workspaceId, principalId)
        insertTakedownReport(reportId, workspaceId, assetId, principalId, reporterEmail, status)

        state.takedownReportIds["current"] = reportId
    }

    private suspend fun seedReporterPrincipal(principalId: String, reporterEmail: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:principalId, 'USER', :subject, NULL, 'BDD Reporter')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).bind("principalId", principalId)
            .bind("subject", "local:reporter-$principalId@example.com")
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES (:principalId, :email, :username, 'VERIFIED')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).bind("principalId", principalId)
            .bind("email", reporterEmail)
            .bind("username", "bdd-reporter")
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedOwnedWorkspace(workspaceId: String, principalId: String) {
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES (:wsId, 'BDD Workspace', 'ACTIVE', 'flask')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).bind("wsId", workspaceId).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by)
            VALUES (:wsId, :principalId, 'USER', :principalId)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).bind("wsId", workspaceId).bind("principalId", principalId).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertTakedownReport(
        reportId: String,
        workspaceId: String,
        assetId: String,
        principalId: String,
        reporterEmail: String,
        status: String,
    ) {
        val decided = status == "DISMISSED" || status == "APPROVED"
        var insert = databaseClient.sql(
            """
            INSERT INTO takedown_reports
              (report_id, workspace_id, asset_id, reported_by_id, reason,
               status, reporter_email, media_reference_url, created_at, updated_at,
               reviewed_by_id, reviewed_at, rejection_reason)
            VALUES (:reportId, :wsId, :assetId, :reportedById,
                    'BDD takedown report reason', :status, :reporterEmail,
                    'https://example.com/media/bdd-asset', NOW(), NOW(),
                    :reviewedById, :reviewedAt, :rejectionReason)
            ON CONFLICT (report_id) DO UPDATE SET status = :status
            """.trimIndent(),
        )
            .bind("reportId", reportId)
            .bind("wsId", workspaceId)
            .bind("assetId", assetId)
            .bind("reportedById", principalId)
            .bind("status", status)
            .bind("reporterEmail", reporterEmail)
        insert = if (decided) {
            insert.bind("reviewedById", principalId)
                .bind("reviewedAt", OffsetDateTime.now())
        } else {
            insert.bindNull("reviewedById", String::class.java)
                .bindNull("reviewedAt", OffsetDateTime::class.java)
        }
        insert = if (status == "DISMISSED") {
            insert.bind("rejectionReason", "BDD dismissal reason")
        } else {
            insert.bindNull("rejectionReason", String::class.java)
        }
        insert.fetch().rowsUpdated().awaitSingle()
    }

    @When("an unauthenticated platform operator queries takedown reports")
    fun queryTakedownUnauthenticated() {
        state.lastResponse = webTestClient.get()
            .uri(TAKEDOWN_PATH)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries takedown reports")
    fun queryTakedownReports() {
        state.lastResponse = webTestClient.get()
            .uri(TAKEDOWN_PATH)
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries takedown reports with status filter {string}")
    fun queryTakedownWithStatus(status: String) {
        state.lastResponse = webTestClient.get()
            .uri { it.path(TAKEDOWN_PATH).queryParam("status", status).build() }
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries takedown reports with workspace filter {string}")
    fun queryTakedownWithWorkspace(workspaceId: String) {
        state.lastResponse = webTestClient.get()
            .uri { it.path(TAKEDOWN_PATH).queryParam("workspaceId", workspaceId).build() }
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator queries takedown reports with page {int} and size {int}")
    fun queryTakedownPaginated(page: Int, size: Int) {
        state.lastResponse = webTestClient.get()
            .uri { it.path(TAKEDOWN_PATH).queryParam("page", page).queryParam("size", size).build() }
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator views the takedown report")
    fun viewTakedownReport() {
        val id = state.takedownReportIds["current"] ?: return
        state.lastResponse = webTestClient.get()
            .uri("$TAKEDOWN_PATH/$id")
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator views the non-existent takedown report")
    fun viewNonExistentTakedownReport() {
        state.lastResponse = webTestClient.get()
            .uri("$TAKEDOWN_PATH/does-not-exist-${UUID.randomUUID()}")
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator approves the takedown report")
    fun approveTakedownReport() {
        val id = state.takedownReportIds["current"] ?: return
        val key = lastIdempotencyKey ?: "approve-key-${UUID.randomUUID()}"
        state.lastResponse = webTestClient.post()
            .uri("$TAKEDOWN_PATH/$id/approve")
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .header("Idempotency-Key", key)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator approves the takedown report with idempotency key {string}")
    fun approveTakedownReportWithKey(key: String) {
        lastIdempotencyKey = key
        val id = state.takedownReportIds["current"] ?: return
        state.lastResponse = webTestClient.post()
            .uri("$TAKEDOWN_PATH/$id/approve")
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .header("Idempotency-Key", key)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator approves the takedown report without idempotency key")
    fun approveWithoutIdempotencyKey() {
        val id = state.takedownReportIds["current"] ?: return
        state.lastResponse = webTestClient.post()
            .uri("$TAKEDOWN_PATH/$id/approve")
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the platform operator rejects the takedown report with reason {string}")
    fun rejectTakedownReport(reason: String) {
        val id = state.takedownReportIds["current"] ?: return
        val key = lastIdempotencyKey ?: "reject-key-${UUID.randomUUID()}"
        state.lastResponse = webTestClient.post()
            .uri("$TAKEDOWN_PATH/$id/reject")
            .header(HttpHeaders.AUTHORIZATION, TAKEDOWN_ADMIN_BEARER)
            .header(HttpHeaders.ACCEPT, TAKEDOWN_API_V1)
            .header("Idempotency-Key", key)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(mapOf("rejectionReason" to reason))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the takedown response status should be {int}")
    fun assertStatus(status: Int) {
        val response = state.lastResponse!!
        assertEquals(status, response.status.value())
    }

    @Then("the takedown result should contain at least 1 report")
    fun assertAtLeastOne() {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("items")
        assertTrue(data.isArray, "Expected data to be an array")
        assertTrue(data.size() >= 1, "Expected at least 1 report")
    }

    @Then("the takedown result should be empty")
    fun assertTakedownEmpty() {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("items")
        assertTrue(data.isArray, "Expected data to be an array")
        assertEquals(0, data.size(), "Expected empty array")
    }

    @Then("all takedown reports should have status {string}")
    fun assertAllStatus(expectedStatus: String) {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("items")
        assertTrue(data.isArray, "Expected data to be an array")
        for (i in 0 until data.size()) {
            val report = data[i]
            assertEquals(expectedStatus, report.get("status").asText())
        }
    }

    @Then("all takedown reports should have workspace {string}")
    fun assertAllWorkspace(expectedWorkspace: String) {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        val data = result.get("items")
        assertTrue(data.isArray, "Expected data to be an array")
        for (i in 0 until data.size()) {
            val report = data[i]
            assertEquals(expectedWorkspace, report.get("workspaceId").asText())
        }
    }

    @Then("the report should include an asset status field")
    fun assertAssetStatusField() {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        assertTrue(result.has("assetStatus"), "Expected assetStatus field in report")
    }

    @Then("the report status should be {string}")
    fun assertReportStatus(expectedStatus: String) {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        assertEquals(expectedStatus, result.get("status").asText())
    }

    @Then("the report rejection reason should be {string}")
    fun assertRejectionReason(expectedReason: String) {
        val response = state.lastResponse!!
        val body = response.responseBodyContent?.decodeToString() ?: ""
        val result = json.readTree(body)
        assertEquals(expectedReason, result.get("rejectionReason").asText())
    }
}
