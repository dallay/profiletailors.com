package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

private const val ADMIN_BEARER = "Bearer $BDD_ADMIN_TOKEN"
private const val API_V1 = "application/vnd.api.v1+json"
private const val STALE_JOBS_PATH = "/api/admin/publishing/stale-jobs"

@Suppress("TooManyFunctions")
class PublishingStaleJobsBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private val json = ObjectMapper()

    private var lastResponse: EntityExchangeResult<ByteArray>? = null
    private var currentPublicationId: String? = null

    @Before("@publishing-stale")
    fun resetState() = runBlocking {
        lastResponse = null
        currentPublicationId = null
        // AuthorizationBddSteps owns the unqualified database reset for every
        // BDD scenario. Do not reset here: resetDatabase clears platform role
        // assignments before the background can authenticate the operator.
    }

    @Given("a queued publication exists for {string}")
    fun queuedPublicationExists(publicationId: String) = runBlocking {
        bddDatabaseSupport.seedWorkspace()
        val socialAccountId = ensureSocialAccount()
        bddDatabaseSupport.seedQueuedPublication(
            publicationId = publicationId,
            socialAccountId = socialAccountId,
            title = "Stale Publication",
            bodyText = "Stale publication body",
        )
        currentPublicationId = publicationId
    }

    @Given("a publication job has been claimed by worker {string} with a stale lease")
    fun publicationJobClaimedWithStaleLease(workerId: String) = runBlocking {
        val publicationId = requireNotNull(currentPublicationId) {
            "Step requires 'a queued publication exists for ...' to run first"
        }
        val jobId = "pjob-stale-${UUID.randomUUID()}"
        val claimedAt = Instant.now().minus(java.time.Duration.ofMinutes(30))
        val leaseExpiresAt = Instant.now().minus(java.time.Duration.ofMinutes(20))
        databaseClient.sql(
            """
            INSERT INTO publication_jobs (
                id, publication_id, workspace_id, status, due_at, priority_rank,
                attempt_count, max_attempts, claimed_by_worker, claimed_at,
                lease_expires_at, created_at
            ) VALUES (
                :id, :publicationId, :workspaceId, 'CLAIMED', :dueAt,
                10, 1, 3, :workerId, :claimedAt, :leaseExpiresAt, :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", jobId)
            .bind("publicationId", publicationId)
            .bind("workspaceId", BddDatabaseSupport.WORKSPACE_ID)
            .bind("dueAt", claimedAt)
            .bind("workerId", workerId)
            .bind("claimedAt", claimedAt)
            .bind("leaseExpiresAt", leaseExpiresAt)
            .bind("createdAt", Instant.now())
            .fetch().rowsUpdated().awaitSingle()
    }

    @When("the platform operator requests the stale publication jobs endpoint")
    fun operatorRequestsStaleJobs() {
        lastResponse = requestStaleJobs("leaseStaleThreshold=PT5M&limit=50")
    }

    @When("an unauthenticated principal requests the stale publication jobs endpoint")
    fun unauthenticatedPrincipalRequestsStaleJobs() {
        lastResponse = requestStaleJobs(query = "leaseStaleThreshold=PT5M&limit=50", bearer = null)
    }

    @When("the platform operator requests stale publication jobs with threshold {string}")
    fun operatorRequestsStaleJobsWithThreshold(threshold: String) {
        lastResponse = requestStaleJobs("leaseStaleThreshold=$threshold&limit=50")
    }

    @When("the platform operator requests stale publication jobs with limit {string}")
    fun operatorRequestsStaleJobsWithLimit(limit: String) {
        lastResponse = requestStaleJobs("leaseStaleThreshold=PT5M&limit=$limit")
    }

    private fun requestStaleJobs(query: String, bearer: String? = ADMIN_BEARER): EntityExchangeResult<ByteArray> {
        val request = webTestClient.get()
            .uri("$STALE_JOBS_PATH?$query")
            .header(HttpHeaders.ACCEPT, API_V1)
        if (bearer != null) request.header(HttpHeaders.AUTHORIZATION, bearer)
        return request.exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()
    }

    @Then("the stale jobs response status should be {int}")
    fun staleJobsResponseStatusShouldBe(expected: Int) {
        assertEquals(expected, lastResponse?.status?.value(), "Unexpected response status")
    }

    @Then("the stale jobs admin response code should be {string}")
    fun staleJobsAdminResponseCodeShouldBe(expected: String) {
        val body = lastResponseJson()
        val code = body.path("properties").path("code").asText().ifBlank {
            body.path("code").asText()
        }
        assertEquals(expected, code)
    }

    @Then("the stale jobs response should contain a jobId for {string}")
    fun staleResponseContainsJobIdForPublication(publicationId: String) {
        val body = lastResponseJson()
        val jobIds = body.path("staleJobs").toList().mapNotNull { node ->
            node.path("publicationId").asText().takeIf { it == publicationId }
                ?.let { node.path("jobId").asText() }
        }
        assertTrue(jobIds.isNotEmpty(), "Expected stale jobs response to contain publicationId=$publicationId")
        assertTrue(jobIds.all { it.isNotBlank() }, "Expected every jobId to be non-blank")
    }

    @Then("the stale jobs response should contain the workspaceId for the publication")
    fun staleResponseContainsWorkspaceId() {
        val body = lastResponseJson()
        val first = body.path("staleJobs").firstOrNull()
        assertNotNull(first, "Expected the stale jobs response to contain at least one entry")
        assertEquals(BddDatabaseSupport.WORKSPACE_ID, first!!.path("workspaceId").asText())
    }

    @Then("the stale jobs entry should expose ageSeconds greater than or equal to 0")
    fun staleResponseContainsNonNegativeAge() {
        val body = lastResponseJson()
        val first = body.path("staleJobs").firstOrNull()
        assertNotNull(first, "Expected the first stale job to be present")
        val age = first!!.path("ageSeconds").asLong()
        assertTrue(age >= 0L, "Expected ageSeconds >= 0, got $age")
    }

    @Then("the stale jobs entry should expose suggestedAction {string}")
    fun staleResponseContainsSuggestedAction(expected: String) {
        val body = lastResponseJson()
        val first = body.path("staleJobs").firstOrNull()
        assertNotNull(first)
        assertEquals(expected, first!!.path("suggestedAction").asText())
    }

    @Then("the publication status should remain {string}")
    fun publicationStatusRemains(expected: String) = runBlocking {
        val publicationId = requireNotNull(currentPublicationId)
        val status: String = databaseClient.sql(
            "SELECT status FROM publications WHERE id = :id",
        )
            .bind("id", publicationId)
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(expected, status, "Publication must not be auto-transitioned by stale visibility")
    }

    @Then("the publication published_at column should be null")
    fun publicationPublishedAtIsNull() = runBlocking {
        val publicationId = requireNotNull(currentPublicationId)
        val hasPublishedAt: Boolean = databaseClient.sql(
            "SELECT published_at FROM publications WHERE id = :id",
        )
            .bind("id", publicationId)
            .map { row, _ -> row.get("published_at", OffsetDateTime::class.java) != null }
            .one()
            .awaitSingle()
        assertFalse(hasPublishedAt, "Stale visibility MUST NOT mark the publication as published")
    }

    @Then("the stale jobs response body should be safe-shaped")
    fun staleResponseBodyIsSafeShaped() {
        val raw = lastResponse?.responseBody?.toString(Charsets.UTF_8) ?: ""
        // tokens / bearer strings
        assertTrue(!raw.contains("token="), "Response contains token=. Body=$raw")
        assertTrue(!raw.contains("Bearer "), "Response contains Bearer. Body=$raw")
        // URLs / paths
        assertTrue(!raw.contains("https://"), "Response contains https://. Body=$raw")
        assertTrue(!raw.contains("bucket/"), "Response contains bucket/. Body=$raw")
        // full workspace UUID form: workspace-<8-4-4-4-12> (8.4.4.4.12)
        assertTrue(
            !Regex("""workspace-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")
                .containsMatchIn(raw),
            "Response contains workspace-<UUID>. Body=$raw",
        )
        // exception-like substrings
        assertTrue(!raw.contains("Exception"), "Response contains Exception. Body=$raw")
        assertTrue(!raw.contains("Error"), "Response contains Error. Body=$raw")
    }

    @Then("the stale jobs response total should be 0")
    fun staleResponseTotalIsZero() {
        val body = lastResponseJson()
        assertEquals(0, body.path("total").asInt(), "Expected total=0")
    }

    @Then("the stale jobs response staleJobs list should be empty")
    fun staleResponseListIsEmpty() {
        val body = lastResponseJson()
        assertTrue(body.path("staleJobs").isArray, "Expected staleJobs to be an array")
        assertEquals(0, body.path("staleJobs").size(), "Expected staleJobs list to be empty")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun ensureSocialAccount(): String {
        val existing: String? = databaseClient.sql(
            "SELECT id FROM social_accounts WHERE id = 'social-acc-1'",
        )
            .map { row, _ -> row.get("id", String::class.java) as String }
            .one()
            .awaitSingleOrNull()
        if (existing != null) return existing
        bddDatabaseSupport.seedSocialConnection("social-conn-stale", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            accountId = "social-acc-1",
            connectionId = "social-conn-stale",
            provider = "LINKEDIN",
            providerAccountId = "linkedin-stale-profile",
            accountKind = "PERSONAL_PROFILE",
            displayName = "Stale Profile",
        )
        return "social-acc-1"
    }

    private fun lastResponseJson(): JsonNode {
        val body = lastResponse?.responseBody
            ?: error("No response captured yet — call a @When step first")
        return json.readTree(body)
    }

    private fun JsonNode.firstOrNull(): JsonNode? = if (this.isArray && this.size() > 0) this.get(0) else null
}
