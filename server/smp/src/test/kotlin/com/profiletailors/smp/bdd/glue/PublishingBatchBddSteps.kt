package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets

class PublishingBatchBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestPublishingResponse: EntityExchangeResult<ByteArray>? = null
    private var batchPublicationIds: List<String> = emptyList()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Before
    fun resetBatchPublishingState() {
        latestPublishingResponse = null
        batchPublicationIds = emptyList()
    }

    @When("the client batch-creates publications for valid targets")
    fun whenClientBatchCreatesPublicationsForValidTargets() = runBlocking {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "title" to "Batch publication",
                "scheduleMode" to "NOW",
                "targets" to listOf(
                    mapOf("socialAccountId" to "social-acc-1", "bodyText" to "First target"),
                    mapOf("socialAccountId" to "social-acc-1", "bodyText" to "Second target"),
                ),
            ),
        )
        latestPublishingResponse = webTestClient.post()
            .uri("${bddDatabaseSupport.publishingPublicationsPath()}/batch")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.VERIFIED_USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
        val results: List<Map<String, Any?>> = parseResponseField("results")
        batchPublicationIds = results.mapNotNull { it["publicationId"] as? String }
    }

    @When("the client batch-creates publications with one valid and one invalid target")
    fun whenClientBatchCreatesPublicationsWithOneInvalidTarget() = runBlocking {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "title" to "Batch publication",
                "scheduleMode" to "NOW",
                "targets" to listOf(
                    mapOf("socialAccountId" to "social-acc-1", "bodyText" to "Valid target"),
                    mapOf("socialAccountId" to "missing-account", "bodyText" to "Invalid target"),
                ),
            ),
        )
        latestPublishingResponse = webTestClient.post()
            .uri("${bddDatabaseSupport.publishingPublicationsPath()}/batch")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.VERIFIED_USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the batch publication response status should be {int}")
    fun thenBatchPublicationResponseStatusShouldBe(status: Int) {
        val response = latestPublishingResponse ?: error("No publishing response captured")
        val body = String(response.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        assertEquals(status, response.status.value()) {
            "Expected status $status but got ${response.status.value()}. Response body: $body"
        }
    }

    @Then("the batch publication status should be {string}")
    fun thenBatchPublicationStatusShouldBe(expectedStatus: String) {
        assertEquals(expectedStatus, parseResponseField<String>("status"))
    }

    @Then("the batch response should contain {int} created targets")
    fun thenBatchResponseShouldContainCreatedTargets(expectedCount: Int) {
        val results: List<Map<String, Any?>> = parseResponseField("results")
        assertEquals(expectedCount, results.count { it["status"] == "CREATED" })
        assertTrue(results.all { it["publicationId"] is String })
    }

    @Then("the batch response should contain one created and one failed target")
    fun thenBatchResponseShouldContainOneCreatedAndOneFailedTarget() {
        val results: List<Map<String, Any?>> = parseResponseField("results")
        assertEquals(1, results.count { it["status"] == "CREATED" })
        assertEquals(1, results.count { it["status"] == "BAD_REQUEST" })
    }

    @Then("the batch publication response should not contain provider data")
    fun thenBatchPublicationResponseShouldNotContainProviderData() {
        val body = responseBodyText()
        assertTrue(!body.contains("accessToken"), body)
        assertTrue(!body.contains("refreshToken"), body)
        assertTrue(!body.contains("clientSecret"), body)
    }

    private fun responseBodyText(): String =
        String(latestPublishingResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private inline fun <reified T> parseResponseField(field: String): T {
        val body = responseBodyText()
        val map: Map<String, Any?> = objectMapper.readValue(body)
        val raw = map[field]
        return when {
            raw == null -> error("Field '$field' is null in response: $body")
            raw is T -> raw
            else -> error(
                "Field '$field' has type ${raw::class.simpleName} " +
                    "but expected ${T::class.simpleName} in response: $body",
            )
        }
    }
}
