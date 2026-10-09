package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class ShortLinksBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private val objectMapper = jacksonObjectMapper()
    private var responseStatus: Int? = null
    private var redirectDestination: String? = null
    private var lastResponseBody: String? = null
    private var createdLinkId: UUID? = null
    private var clickedLinkId: UUID? = null
    private var unclickedLinkId: UUID? = null
    private var foreignLinkId: UUID? = null
    private var cacheMissShortCode: String? = null
    private var firstCreatedLinkId: UUID? = null
    private var workspaceMetricsCursor: String? = null
    private var firstMetricsPageIds: Set<String> = emptySet()
    private val workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111111").toString()
    private val otherWorkspaceId = UUID.fromString("22222222-2222-4222-8222-222222222222").toString()

    @Before("@shortlinks")
    fun resetShortLinksScenario() {
        responseStatus = null
        redirectDestination = null
        lastResponseBody = null
        createdLinkId = null
        clickedLinkId = null
        unclickedLinkId = null
        foreignLinkId = null
        cacheMissShortCode = null
        firstCreatedLinkId = null
        workspaceMetricsCursor = null
        firstMetricsPageIds = emptySet()
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    @Given("the short links workspace is prepared")
    fun prepareWorkspace() = runBlocking {
        bddDatabaseSupport.seedAuthenticatedUserWithWorkspace(workspaceId = workspaceId)
        bddDatabaseSupport.seedWorkspace(otherWorkspaceId)
        bddDatabaseSupport.seedWorkspaceMembership(BddDatabaseSupport.PRINCIPAL_ID, otherWorkspaceId)
    }

    @When("an authenticated user creates a short link in another workspace")
    fun createForeignWorkspaceLink() {
        val response = authenticatedRequest(otherWorkspaceId)
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"destinationUrl":"https://example.com/foreign"}""")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        foreignLinkId = response.responseBody?.let(objectMapper::readTree)
            ?.path("id")?.asText()?.takeIf(String::isNotBlank)?.let(UUID::fromString)
    }

    @When("the authenticated user lists workspace link metrics with limit {int}")
    fun listWorkspaceLinkMetrics(limit: Int) {
        val response = authenticatedRequest().get().uri("/api/v1/links?limit=$limit")
            .exchange().expectBody().returnResult()
        responseStatus = response.status.value()
        lastResponseBody = response.responseBody?.decodeToString()
        firstMetricsPageIds = response.responseBody?.let(objectMapper::readTree)?.path("links")
            ?.map { it.path("id").asText() }?.toSet().orEmpty()
        workspaceMetricsCursor = response.responseBody?.let(objectMapper::readTree)
            ?.path("nextCursor")?.takeIf { !it.isNull }?.asText()
    }

    @When("the authenticated user follows the workspace metrics cursor with limit {int}")
    fun followWorkspaceMetricsCursor(limit: Int) {
        val response = authenticatedRequest().get()
            .uri("/api/v1/links?limit=$limit&cursor=${requireNotNull(workspaceMetricsCursor)}")
            .exchange().expectBody().returnResult()
        responseStatus = response.status.value()
        lastResponseBody = response.responseBody?.decodeToString()
    }

    @Then("the workspace metrics page should contain exactly the created workspace link ids")
    fun assertWorkspaceMetricsContainOnlyOwnedLinks() {
        val links = objectMapper.readTree(requireNotNull(lastResponseBody)).path("links")
        val actualIds = links.map { it.path("id").asText() }.toSet()
        val expectedIds = setOf(requireNotNull(clickedLinkId), requireNotNull(unclickedLinkId))
            .map(UUID::toString).toSet()
        assertEquals(expectedIds, actualIds)
        assertEquals(false, actualIds.contains(requireNotNull(foreignLinkId).toString()))
    }

    @Then("the workspace metrics page should report {int} recorded redirect for the clicked link")
    fun assertClickedLinkRedirectCount(expected: Int) {
        assertWorkspaceLinkRedirectCount(requireNotNull(clickedLinkId), expected)
    }

    @Then("the workspace metrics page should report {int} recorded redirects for the unclicked link")
    fun assertUnclickedLinkRedirectCount(expected: Int) {
        assertWorkspaceLinkRedirectCount(requireNotNull(unclickedLinkId), expected)
    }

    private fun assertWorkspaceLinkRedirectCount(linkId: UUID, expected: Int) {
        val links = objectMapper.readTree(requireNotNull(lastResponseBody)).path("links")
        val link = links.first { it.path("id").asText() == linkId.toString() }
        assertEquals(expected, link.path("recordedRedirects").asInt())
    }

    @Then("the workspace metrics page should contain {int} link")
    fun assertWorkspaceMetricsCount(expected: Int) {
        val actual = objectMapper.readTree(requireNotNull(lastResponseBody)).path("links").size()
        assertEquals(expected, actual)
    }

    @Then("the workspace metrics page should include a continuation cursor")
    fun assertWorkspaceMetricsCursor() {
        assertEquals(true, !workspaceMetricsCursor.isNullOrBlank())
    }

    @Then("the workspace metrics page should include the created link ids from both pages")
    fun assertCreatedLinksAcrossPages() {
        val ids = objectMapper.readTree(requireNotNull(lastResponseBody)).path("links")
            .map { it.path("id").asText() }
        assertEquals(1, ids.size)
        assertEquals(emptySet<String>(), firstMetricsPageIds.intersect(ids.toSet()))
    }

    @When("a client requests metrics without authentication")
    fun requestMetricsWithoutAuthentication() {
        responseStatus = webTestClient.get()
            .uri("/api/v1/links/${UUID.fromString("0199b1ca-0000-7000-8000-000000000099")}/metrics")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("a client creates a link without authentication")
    fun createWithoutAuthentication() {
        responseStatus = webTestClient.post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"destinationUrl":"https://example.com"}""")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("an authenticated user creates a short link")
    fun createAuthenticatedLink() {
        val response = authenticatedRequest()
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"destinationUrl":"https://example.com/start"}""")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        if (responseStatus == 201) {
            createdLinkId =
                response.responseBody?.let(objectMapper::readTree)?.path("id")?.asText()?.let(UUID::fromString)
            if (clickedLinkId != null) unclickedLinkId = createdLinkId
        }
    }

    @When("an authenticated user creates a short link with idempotency key {string} and destination {string}")
    fun createLinkWithIdempotencyKey(idempotencyKey: String, destination: String) {
        val response = authenticatedRequest()
            .post()
            .uri("/api/v1/links")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"destinationUrl":"$destination"}""")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        lastResponseBody = response.responseBody?.decodeToString()
        val resourceId = response.responseBody
            ?.takeIf { responseStatus == 201 }
            ?.let(objectMapper::readTree)
            ?.path("id")
            ?.asText()
            ?.takeIf(String::isNotBlank)
            ?.let(UUID::fromString)
        if (resourceId != null && firstCreatedLinkId == null) firstCreatedLinkId = resourceId
        if (resourceId != null) createdLinkId = resourceId
    }

    @When(
        "the authenticated user repeats the short link creation with idempotency key {string} and destination {string}",
    )
    fun repeatLinkWithIdempotencyKey(idempotencyKey: String, destination: String) {
        createLinkWithIdempotencyKey(idempotencyKey, destination)
    }

    @When("an authenticated user creates a short link with alias {string}")
    fun createLinkWithAlias(alias: String) {
        val response = authenticatedRequest()
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                objectMapper.createObjectNode()
                    .put("destinationUrl", "https://example.com/cache")
                    .put("customAlias", alias),
            )
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        assertEquals(201, responseStatus, response.responseBody?.decodeToString())
        if (responseStatus == 201) {
            createdLinkId = response.responseBody
                ?.let(objectMapper::readTree)
                ?.path("id")
                ?.asText()
                ?.takeIf(String::isNotBlank)
                ?.let(UUID::fromString)
        }
    }

    @When("a client resolves the future alias {string}")
    fun resolveFutureAlias(alias: String) {
        responseStatus = webTestClient.get()
            .uri("/$alias")
            .header(HttpHeaders.HOST, "go.profiletailors.com")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("a client resolves the created short link")
    fun resolveCreatedShortLinkFromPublicEndpoint() = resolveCreatedShortLink()

    @When("a client resolves the unknown alias {string}")
    fun resolveUnknownAlias(alias: String) {
        responseStatus = webTestClient.get()
            .uri("/$alias")
            .header(HttpHeaders.HOST, "go.profiletailors.com")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @Then("the repeated short link should have the same resource ID")
    fun assertSameRepeatedResource() {
        assertEquals(firstCreatedLinkId, createdLinkId)
    }

    @Then("the short links error code should be {string}")
    fun assertShortLinksErrorCode(expectedCode: String) {
        val actualCode = objectMapper.readTree(requireNotNull(lastResponseBody)).path("code").asText()
        assertEquals(expectedCode, actualCode)
    }

    @When("a client resolves the created active short link")
    fun resolveCreatedShortLink() {
        clickedLinkId = createdLinkId
        val linkResponse = authenticatedRequest()
            .get()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}")
            .exchange()
            .expectBody()
            .returnResult()
        val shortCode = objectMapper.readTree(requireNotNull(linkResponse.responseBody).decodeToString())
            .path("shortCode")
            .asText()
        val response = webTestClient.get()
            .uri("/$shortCode")
            .header(HttpHeaders.HOST, "go.profiletailors.com")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        redirectDestination = response.responseHeaders.getFirst(HttpHeaders.LOCATION)
    }

    @When("the authenticated user requests metrics for the created short link")
    fun getCreatedLinkMetrics() {
        val response = authenticatedRequest()
            .get()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}/metrics")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        lastResponseBody = response.responseBody?.decodeToString()
    }

    @And("an authenticated user requests metrics for the created short link from another workspace")
    fun getCreatedLinkMetricsFromAnotherWorkspace() {
        responseStatus = authenticatedRequest(otherWorkspaceId)
            .get()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}/metrics")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @Then("the recorded redirect count should be {int}")
    fun assertRecordedRedirectCount(expected: Int) {
        val recordedRedirects = objectMapper.readTree(requireNotNull(lastResponseBody))
            .path("recordedRedirects")
            .asInt()
        assertEquals(expected, recordedRedirects)
    }

    @When("the authenticated user gets the created short link")
    fun getCreatedLink() {
        responseStatus = authenticatedRequest()
            .get()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("the authenticated user updates the created short link")
    fun updateCreatedLink() {
        updateCreatedLink("https://example.com/updated")
    }

    @When("the authenticated user expires the created short link")
    fun expireCreatedLink() {
        updateCreatedLink("https://example.com/start", "2020-01-01T00:00:00Z")
    }

    private fun createdShortCode(): String {
        val response = authenticatedRequest()
            .get()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}")
            .exchange()
            .expectBody()
            .returnResult()
        return objectMapper.readTree(requireNotNull(response.responseBody).decodeToString())
            .path("shortCode")
            .asText()
    }

    private fun resolveShortCode(shortCode: String) {
        val response = webTestClient.get()
            .uri("/$shortCode")
            .header(HttpHeaders.HOST, "go.profiletailors.com")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        redirectDestination = response.responseHeaders.getFirst(HttpHeaders.LOCATION)
    }

    private fun updateCreatedLink(destination: String, expiresAt: String? = null) {
        val body = if (expiresAt != null) {
            """{"destinationUrl":"$destination","expiresAt":"$expiresAt"}"""
        } else {
            """{"destinationUrl":"$destination"}"""
        }
        val response = authenticatedRequest()
            .patch()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}")
            .header("If-Match", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        lastResponseBody = response.responseBody?.decodeToString()
    }

    @When("the authenticated user disables the created short link")
    fun disableCreatedLink() {
        responseStatus = authenticatedRequest()
            .post()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}/disable")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("the authenticated user enables the created short link")
    fun enableCreatedLink() {
        responseStatus = authenticatedRequest()
            .post()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}/enable")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("the authenticated user deletes the created short link")
    fun deleteCreatedLink() {
        responseStatus = authenticatedRequest()
            .delete()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @And("an authenticated user requests the created short link from another workspace")
    fun getLinkFromAnotherWorkspace() {
        responseStatus = authenticatedRequest(otherWorkspaceId)
            .get()
            .uri("/api/v1/links/${requireNotNull(createdLinkId)}")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("a client resolves an unknown public short code")
    fun resolveUnknownPublicShortCode() {
        cacheMissShortCode = "QzX999zQ"
        responseStatus = webTestClient.get()
            .uri("/${requireNotNull(cacheMissShortCode)}")
            .header(HttpHeaders.HOST, "go.profiletailors.com")
            .exchange()
            .returnResult(ByteArray::class.java)
            .status.value()
    }

    @When("an authenticated user creates a short link for the resolved cache miss")
    fun createLinkForResolvedCacheMiss() {
        val alias = requireNotNull(cacheMissShortCode)
        val response = authenticatedRequest()
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"customAlias":"$alias","destinationUrl":"https://example.com/cache-miss"}""")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        lastResponseBody = response.responseBody?.decodeToString()
        val id = response.responseBody
            ?.takeIf { responseStatus == 201 }
            ?.let(objectMapper::readTree)
            ?.path("id")
            ?.asText()
            ?.takeIf(String::isNotBlank)
            ?.let(UUID::fromString)
        if (id != null) createdLinkId = id
    }

    @When("a client resolves the previously unknown public short code")
    fun resolvePreviouslyUnknownPublicShortCode() {
        val response = webTestClient.get()
            .uri("/${requireNotNull(cacheMissShortCode)}")
            .header(HttpHeaders.HOST, "go.profiletailors.com")
            .exchange()
            .expectBody()
            .returnResult()
        responseStatus = response.status.value()
        redirectDestination = response.responseHeaders.getFirst(HttpHeaders.LOCATION)
    }

    @Then("the short links redirect destination should be {string}")
    fun assertRedirectDestination(expected: String) {
        assertEquals(expected, redirectDestination)
    }

    @Then("the short links response status should be {int}")
    fun assertResponseStatus(expected: Int) {
        assertEquals(expected, responseStatus)
    }

    private fun authenticatedRequest(workspaceId: String = this.workspaceId) =
        webTestClient.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .defaultHeader(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .defaultHeader(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
            .build()
}
