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
    private var cacheMissShortCode: String? = null
    private var firstCreatedLinkId: UUID? = null
    private val workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111111").toString()
    private val otherWorkspaceId = UUID.fromString("22222222-2222-4222-8222-222222222222").toString()

    @Before("@shortlinks")
    fun resetShortLinksScenario() {
        responseStatus = null
        redirectDestination = null
        lastResponseBody = null
        createdLinkId = null
        cacheMissShortCode = null
        firstCreatedLinkId = null
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    @Given("the short links workspace is prepared")
    fun prepareWorkspace() = runBlocking {
        bddDatabaseSupport.seedAuthenticatedUserWithWorkspace(workspaceId = workspaceId)
        bddDatabaseSupport.seedWorkspace(otherWorkspaceId)
        bddDatabaseSupport.seedWorkspaceMembership(BddDatabaseSupport.PRINCIPAL_ID, otherWorkspaceId)
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
        createdLinkId = response.responseBody?.let(objectMapper::readTree)?.path("id")?.asText()?.let(UUID::fromString)
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
