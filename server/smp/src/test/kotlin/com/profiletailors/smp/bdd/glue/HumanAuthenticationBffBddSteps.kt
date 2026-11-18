package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.Before
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class HumanAuthenticationBffBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    private var response: EntityExchangeResult<ByteArray>? = null

    @Before
    fun clearResponse() {
        response = null
    }

    @When("an unauthenticated client requests the protected BFF profile endpoint")
    fun requestProtectedBffProfile() {
        response = webTestClient.get()
            .uri("/api/auth/me")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("an unauthenticated client requests the BFF CSRF token")
    fun requestBffCsrfToken() {
        response = webTestClient.get()
            .uri("/api/auth/csrf")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the BFF response status should be {int}")
    fun responseStatusShouldBe(status: Int) {
        assertEquals(status, requireResponse().status.value())
    }

    @Then("the BFF response should not redirect to OIDC")
    fun responseShouldNotRedirectToOidc() {
        val headers = requireResponse().responseHeaders
        assertFalse(headers[HttpHeaders.LOCATION].orEmpty().any { it.contains("/oauth2/") })
    }

    @Then("the BFF response should not be an HTML login page")
    fun responseShouldNotBeHtmlLoginPage() {
        val contentType = requireResponse().responseHeaders.contentType
        assertFalse(contentType?.isCompatibleWith(org.springframework.http.MediaType.TEXT_HTML) == true)
    }

    @Then("the BFF CSRF response should include a non-empty token")
    fun csrfResponseShouldIncludeToken() {
        val token = csrfJson().path("token")
        assertTrue(token.isTextual && token.asText().isNotBlank())
    }

    @Then("the BFF CSRF response header name should be {string}")
    fun csrfResponseShouldIncludeHeaderName(headerName: String) {
        assertEquals(headerName, csrfJson().path("headerName").asText())
    }

    private fun csrfJson() = ObjectMapper().readTree(requireResponse().responseBody)

    private fun requireResponse(): EntityExchangeResult<ByteArray> =
        requireNotNull(response) { "No BFF response captured" }
}
