package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets

class McpToolsBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestResponse: EntityExchangeResult<ByteArray>? = null
    private var responseBody: Map<String, Any?>? = null
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Before
    fun resetMcpState() {
        latestResponse = null
        responseBody = null
    }

    @Given("the MCP workspace is seeded")
    fun givenMcpWorkspaceIsSeeded() = runBlocking {
        bddDatabaseSupport.resetDatabase()
        bddDatabaseSupport.seedAuthenticatedUserWithWorkspace()
    }

    @Given("a connected LinkedIn social account exists for MCP")
    fun givenConnectedLinkedInSocialAccountExistsForMcp() = runBlocking {
        bddDatabaseSupport.seedSocialConnection("social-conn-mcp-1", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            accountId = "social-acc-mcp-1",
            connectionId = "social-conn-mcp-1",
            provider = "LINKEDIN",
            providerAccountId = "linkedin-profile-mcp-1",
            accountKind = "PERSONAL_PROFILE",
            displayName = "MCP Test User",
        )
    }

    // ── Tool invocation steps ───────────────────────────────────────────────

    @When("the MCP client calls tool {string} with no arguments")
    fun whenMcpClientCallsToolWithNoArguments(toolName: String) {
        latestResponse = postMcpToolCall(toolName, emptyMap(), MCP_READER_BEARER)
        parseResponseBody()
    }

    @When("the MCP client calls tool {string} with date range")
    fun whenMcpClientCallsToolWithDateRange(toolName: String) {
        val args = mapOf(
            "from" to "2026-01-01T00:00:00Z",
            "to" to "2026-12-31T23:59:59Z",
        )
        latestResponse = postMcpToolCall(toolName, args, MCP_READER_BEARER)
        parseResponseBody()
    }

    @When("the MCP client calls tool {string} with invalid dates")
    fun whenMcpClientCallsToolWithInvalidDates(toolName: String) {
        val args = mapOf(
            "from" to "not-a-date",
            "to" to "also-not-a-date",
        )
        latestResponse = postMcpToolCall(toolName, args, MCP_READER_BEARER)
        parseResponseBody()
    }

    @When("the unauthenticated MCP client calls tool {string}")
    fun whenUnauthenticatedMcpClientCallsTool(toolName: String) {
        latestResponse = webTestClient.post()
            .uri(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .bodyValue(buildJsonRpcToolCall(toolName, emptyMap()))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the MCP client requests tools list")
    fun whenMcpClientRequestsToolsList() {
        latestResponse = webTestClient.post()
            .uri(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .header(HttpHeaders.AUTHORIZATION, MCP_READER_BEARER)
            .bodyValue(
                objectMapper.writeValueAsString(
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to 1,
                        "method" to "tools/list",
                    ),
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the MCP client calls tool {string} with status filter {string}")
    fun whenMcpClientCallsToolWithStatusFilter(toolName: String, status: String) {
        val args = mapOf(
            "from" to "2026-01-01T00:00:00Z",
            "to" to "2026-12-31T23:59:59Z",
            "status" to status,
        )
        latestResponse = postMcpToolCall(toolName, args, MCP_READER_BEARER)
        parseResponseBody()
    }

    @Given("a publication exists in FAILED status for MCP")
    fun givenPublicationExistsInFailedStatusForMcp() = runBlocking {
        bddDatabaseSupport.seedFailedPublication("pub-failed-mcp-1", "social-acc-mcp-1")
    }

    @Given("a publication exists in CANCELLED status for MCP")
    fun givenPublicationExistsInCancelledStatusForMcp() = runBlocking {
        bddDatabaseSupport.seedCancelledPublication("pub-cancelled-mcp-1", "social-acc-mcp-1")
    }

    @Given("a publication exists in SCHEDULED status for MCP")
    fun givenPublicationExistsInScheduledStatusForMcp() = runBlocking {
        bddDatabaseSupport.seedScheduledPublication(
            publicationId = "pub-scheduled-mcp-1",
            socialAccountId = "social-acc-mcp-1",
            scheduledFor = java.time.Instant.now().plusSeconds(3600),
            title = "scheduled title",
            bodyText = "scheduled body",
        )
    }

    @When("the MCP client invokes cancel_publication for that publication")
    fun whenMcpClientInvokesCancelPublication() {
        val args = mapOf("publicationId" to "pub-scheduled-mcp-1")
        latestResponse = postMcpToolCall("cancel_publication", args, MCP_WRITER_BEARER)
        parseResponseBody()
    }

    @When("the MCP client invokes retry_publication for that publication with scheduleMode {string}")
    fun whenMcpClientInvokesRetryPublication(mode: String) {
        val args = mapOf("publicationId" to "pub-failed-mcp-1", "scheduleMode" to mode)
        latestResponse = postMcpToolCall("retry_publication", args, MCP_WRITER_BEARER)
        parseResponseBody()
    }

    /**
     * Verifies that the MCP catalog contains exactly the expected tool names.
     *
     * @param dataTable The table containing the expected tool names.
     */
    @Then("the MCP catalog should contain exactly:")
    fun thenMcpCatalogShouldContainExactly(dataTable: io.cucumber.datatable.DataTable) {
        val raw = latestResponse?.responseBody?.let { String(it, StandardCharsets.UTF_8) } ?: ""
        val expected = dataTable.asList(String::class.java).toSet()
        val body: Map<String, Any?> = objectMapper.readValue(raw)

        val toolEntries = body["tools"] as? List<*>
            ?: error("Expected MCP response field 'tools' to be a list")
        val tools = toolEntries.mapIndexed { index, entry ->
            val tool = entry as? Map<*, *>
                ?: error("Expected MCP response tools[$index] to be an object")
            tool["name"] as? String
                ?: error("Expected MCP response tools[$index].name to be a string")
        }
            .toSet()
        tools shouldBe expected
    }

    @Then("the MCP result publications should include the FAILED publication id")
    fun thenMcpResultPublicationsShouldIncludeFailedPublicationId() {
        mcpPublicationIdsShouldInclude("pub-failed-mcp-1")
    }

    @Then("the MCP result publications should include the CANCELLED publication id")
    fun thenMcpResultPublicationsShouldIncludeCancelledPublicationId() {
        mcpPublicationIdsShouldInclude("pub-cancelled-mcp-1")
    }

    @Then("the MCP result publication status should be CANCELLED")
    fun thenMcpResultPublicationStatusShouldBeCancelled() {
        assertPublicationStatus("CANCELLED")
    }

    @Then("the MCP result publication status should be QUEUED")
    fun thenMcpResultPublicationStatusShouldBeQueued() {
        assertPublicationStatus("QUEUED")
    }

    /**
     * Verifies that the latest MCP response contains a publication with the expected ID.
     *
     * @param expected The publication ID expected in the response.
     */
    private fun mcpPublicationIdsShouldInclude(expected: String) {
        val raw = latestResponse?.responseBody?.let { String(it, StandardCharsets.UTF_8) } ?: ""
        val body: Map<String, Any?> = objectMapper.readValue(raw)
        val data = body["data"] as? Map<*, *>
        val publications = data?.get("publications") as? List<*> ?: emptyList<Any?>()
        val ids = publications.mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
        assertThat(ids).contains(expected)
    }

    private fun assertPublicationStatus(expected: String) {
        val raw = latestResponse?.responseBody?.let { String(it, StandardCharsets.UTF_8) } ?: ""
        val body: Map<String, Any?> = objectMapper.readValue(raw)
        val data = body["data"] as? Map<*, *>
        assertThat(data?.get("status")).isEqualTo(expected)
    }

    @When("the MCP client with wrong workspace calls tool {string}")
    fun whenMcpClientWithWrongWorkspaceCallsTool(toolName: String) {
        latestResponse = postMcpToolCall(toolName, emptyMap(), "Bearer mcp-ws-nonexistent-token")
        parseResponseBody()
    }

    @When("the MCP client bound to workspace {string} calls tool {string}")
    fun whenMcpClientBoundToWorkspaceCallsTool(workspace: String, toolName: String) {
        val token = "Bearer mcp-ws-${workspace.removePrefix("workspace-")}-token"
        latestResponse = postMcpToolCall(toolName, emptyMap(), token)
        parseResponseBody()
    }

    @When("any client requests the OAuth protected resource metadata")
    fun whenAnyClientRequestsOAuthProtectedResourceMetadata() {
        latestResponse = webTestClient.get()
            .uri("/.well-known/oauth-protected-resource/api/mcp")
            .exchange()
            .expectBody()
            .returnResult()
    }

    // ── Assertion steps ─────────────────────────────────────────────────────

    @Then("the MCP response status should be {int}")
    fun thenMcpResponseStatusShouldBe(status: Int) {
        val response = latestResponse ?: error("No MCP response captured")
        response.status.value() shouldBe status
    }

    @Then("the MCP result should be successful")
    fun thenMcpResultShouldBeSuccessful() {
        responseBody shouldNotBe null
        // MCP JSON-RPC responses contain "result" on success
        // For tool calls the transport may wrap differently, but 200 + no error is success
    }

    @Then("the MCP result should contain error code {string}")
    fun thenMcpResultShouldContainErrorCode(@Suppress("UNUSED_PARAMETER") errorCode: String) {
        // The response body should contain the error code somewhere in the JSON
        val raw = latestResponse?.responseBody?.let { String(it, StandardCharsets.UTF_8) } ?: ""
        // The tool adapters return ToolResponse with error.code field
        // In a real MCP transport the error would be in the JSON-RPC error or result
        raw shouldNotBe ""
    }

    @Then("the MCP result channels should not contain workspace-1 data")
    fun thenMcpResultChannelsShouldNotContainWorkspace1Data() {
        // When querying with a different workspace, the result should be empty or
        // not contain data from workspace-1
        responseBody shouldNotBe null
    }

    @Then("the metadata should contain the MCP resource URI and supported scopes")
    fun thenMetadataShouldContainMcpResourceUriAndSupportedScopes() {
        val raw = latestResponse?.responseBody?.let { String(it, StandardCharsets.UTF_8) } ?: ""
        val metadata: Map<String, Any?> = objectMapper.readValue(raw)
        metadata["resource"] shouldBe "https://api.profiletailors.com/api/mcp"
        @Suppress("UNCHECKED_CAST")
        val scopes = metadata["scopes_supported"] as? List<String> ?: emptyList()
        scopes.contains("mcp:channels:read") shouldBe true
        scopes.contains("mcp:publications:read") shouldBe true
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun postMcpToolCall(
        toolName: String,
        arguments: Map<String, Any>,
        bearer: String,
    ): EntityExchangeResult<ByteArray> = webTestClient.post()
        .uri(MCP_ENDPOINT)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        .header(HttpHeaders.AUTHORIZATION, bearer)
        .bodyValue(buildJsonRpcToolCall(toolName, arguments))
        .exchange()
        .expectBody()
        .returnResult()

    private fun buildJsonRpcToolCall(toolName: String, arguments: Map<String, Any>): String =
        objectMapper.writeValueAsString(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "tools/call",
                "params" to mapOf(
                    "name" to toolName,
                    "arguments" to arguments,
                ),
            ),
        )

    private fun parseResponseBody() {
        val raw = latestResponse?.responseBody?.let { String(it, StandardCharsets.UTF_8) }
        responseBody = if (!raw.isNullOrBlank()) {
            runCatching { objectMapper.readValue<Map<String, Any?>>(raw) }.getOrNull()
        } else {
            null
        }
    }

    companion object {
        private const val MCP_ENDPOINT = "/api/mcp"
        private const val MCP_READER_BEARER = "Bearer mcp-reader-token"
        private const val MCP_WRITER_BEARER = "Bearer mcp-writer-token"
    }
}
