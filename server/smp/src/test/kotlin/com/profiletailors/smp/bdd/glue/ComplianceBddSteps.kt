package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets

class ComplianceBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestResponse: EntityExchangeResult<ByteArray>? = null
    private var releaseGateBody: Map<String, Any?>? = null
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Before
    fun resetState() {
        latestResponse = null
        releaseGateBody = null
    }

    @Given("an authenticated user exists")
    fun givenAuthenticatedUserExists() = runBlocking {
        bddDatabaseSupport.seedJwtAuthenticatedUserWithWorkspace("VERIFIED")
    }

    @When("the client queries the release gate for the release {string}")
    fun whenClientQueriesReleaseGate(release: String) {
        val response = webTestClient.get()
            .uri("/api/governance/compliance/release-gate?release=$release")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
        latestResponse = response
        val body = String(response.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        releaseGateBody = if (body.isNotBlank()) objectMapper.readValue(body) else null
    }

    @Then("the release gate response status should be {int}")
    fun thenReleaseGateResponseStatusShouldBe(status: Int) {
        val response = latestResponse ?: error("No response captured")
        assertEquals(status, response.status.value())
    }

    @Then("the release gate status should be {string}")
    fun thenReleaseGateStatusShouldBe(expectedStatus: String) {
        val body = releaseGateBody ?: error("No release gate body captured")
        assertEquals(expectedStatus, body["gateStatus"])
    }
}
