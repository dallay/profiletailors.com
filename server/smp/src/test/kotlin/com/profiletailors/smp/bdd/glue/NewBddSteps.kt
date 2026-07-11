package com.profiletailors.smp.bdd.glue

import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestResult: EntityExchangeResult<ByteArray>? = null

    @When("the client refreshes the local user session with an invalid cookie")
    fun whenTheClientRefreshesTheLocalUserSessionWithAnInvalidCookie() {
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthRefreshPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .cookie("pt_refresh", "invalid-token")
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Given("the verification token for {string} is {string}")
    fun givenTheVerificationTokenForEmailIs(email: String, token: String) = runBlocking<Unit> {
        bddDatabaseSupport.seedVerificationToken(email, token)
    }

    @When("the client verifies the email with token {string}")
    fun whenTheClientVerifiesTheEmailWithToken(token: String) {
        latestResult = webTestClient.post()
            .uri("/api/auth/verify-email")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(mapOf("token" to token))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the latest response should contain {string}")
    fun thenTheLatestResponseShouldContain(expected: String) {
        val body = String(latestResult?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        assertTrue(body.contains(expected), "Expected response body to contain '$expected' but was: $body")
    }

    @And("the latest response status should be {int}")
    fun andTheLatestResponseStatusShouldBe(status: Int) {
        assertEquals(status, latestResult?.status?.value())
    }

    @When("the user fills the email input with a new unique email")
    fun whenUserFillsUniqueEmail() {
        // Doc-only
    }

    @When("the user fills the password input with {string}")
    fun whenUserFillsPassword(@Suppress("UnusedParameter") password: String) {
        // Doc-only
    }

    @When("the user clicks the submit button")
    fun whenUserClicksSubmit() {
        // Doc-only
    }

    @Then("the API request should have {string}")
    fun thenApiRequestShouldHaveHeader(header: String) {
        val result = requireNotNull(latestResult) { "No request has been made yet" }
        val headerParts = header.split(":", limit = 2)
        require(headerParts.size == 2) { "Header must be in format 'Name: Value'" }
        val headerName = headerParts[0].trim()
        val expectedValue = headerParts[1].trim()
        val actualValue = result.requestHeaders.getFirst(headerName)
        assertEquals(expectedValue, actualValue, "Expected header '$headerName' to be '$expectedValue' but was '$actualValue'")
    }

    @Then("the API response should not contain a {string} field")
    fun thenApiResponseShouldNotContainField(field: String) {
        val body = String(latestResult?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        assertFalse(body.contains("\"$field\":"), "Response should not contain field $field")
    }

    @Then("the API response should not contain {string}")
    fun thenApiResponseShouldNotContain(text: String) {
        val body = String(latestResult?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        assertFalse(body.contains(text), "Response should not contain $text")
    }

    @Given("the email {string} already exists")
    fun givenEmailExists(email: String) = runBlocking<Unit> {
        bddDatabaseSupport.seedAuthenticatedUserWithWorkspace(email = email)
    }
}
