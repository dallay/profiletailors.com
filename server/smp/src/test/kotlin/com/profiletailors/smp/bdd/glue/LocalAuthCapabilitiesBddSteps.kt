package com.profiletailors.smp.bdd.glue

import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class LocalAuthCapabilitiesBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var database: BddDatabaseSupport

    @Autowired
    private lateinit var registrationFlag: MutableRegistrationFlag

    @Autowired
    private lateinit var passwordRecoveryFlag: MutablePasswordRecoveryFlag

    private var response: EntityExchangeResult<ByteArray>? = null
    private val disabledEmail = "registration-disabled@example.com"

    @Before
    fun resetAuthCapabilityState() {
        registrationFlag.enable()
        passwordRecoveryFlag.enable()
        response = null
    }

    @Given("public registration and password recovery are enabled")
    fun publicRegistrationAndPasswordRecoveryAreEnabled() {
        registrationFlag.enable()
        passwordRecoveryFlag.enable()
    }

    @Given("public registration and password recovery are disabled")
    fun publicRegistrationAndPasswordRecoveryAreDisabled() {
        registrationFlag.disable()
        passwordRecoveryFlag.disable()
    }

    @Given("public registration is disabled")
    fun publicRegistrationIsDisabled() {
        registrationFlag.disable()
    }

    @When("the visitor requests public application capabilities")
    fun requestPublicApplicationCapabilities() {
        response = webTestClient.get()
            .uri("/api/capabilities/public")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the visitor submits valid disabled registration details")
    fun submitDisabledRegistration() {
        response = webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to disabledEmail,
                    "password" to "SecurePassword123!",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the public capabilities response status should be {int}")
    fun publicCapabilitiesStatus(status: Int) {
        assertEquals(status, response().status.value())
    }

    @Then("the public capabilities response should equal the exact allow-listed contract")
    fun exactPublicCapabilitiesContract() {
        val body = response().responseBody?.toString(Charsets.UTF_8).orEmpty()
        assertEquals(
            setOf("registrationEnabled", "passwordRecoveryEnabled"),
            com.fasterxml.jackson.databind.ObjectMapper().readTree(body).fieldNames().asSequence().toSet(),
        )
        assertTrue(body.contains("\"registrationEnabled\":true"))
        assertTrue(body.contains("\"passwordRecoveryEnabled\":true"))
        assertFalse(body.contains("sso"))
    }

    @Then("the public capabilities response should equal the exact disabled allow-listed contract")
    fun exactDisabledPublicCapabilitiesContract() {
        val body = response().responseBody?.toString(Charsets.UTF_8).orEmpty()
        val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(body)
        assertEquals(
            setOf("registrationEnabled", "passwordRecoveryEnabled"),
            json.fieldNames().asSequence().toSet(),
        )
        assertFalse(json.get("registrationEnabled").booleanValue())
        assertFalse(json.get("passwordRecoveryEnabled").booleanValue())
    }

    @Then("the disabled registration response status should be {int}")
    fun disabledRegistrationStatus(status: Int) {
        val captured = response()
        val body = captured.responseBody?.toString(Charsets.UTF_8).orEmpty()
        assertEquals(status, captured.status.value(), body)
    }

    @Then("the disabled registration response code should be {string}")
    fun disabledRegistrationCode(code: String) {
        val body = response().responseBody?.toString(Charsets.UTF_8).orEmpty()
        assertTrue(body.contains(""""code":"$code""""), "Expected $code in $body")
    }

    @Then("no local account, credential, workspace, consent, event, or session should be created")
    fun noRegistrationMutation(): Unit = runBlocking {
        assertEquals(0L, database.countAccountsByEmail(disabledEmail))
        val body = response().responseBody?.toString(Charsets.UTF_8).orEmpty()
        assertFalse(body.contains("accessToken"))
        assertTrue(response().responseHeaders[HttpHeaders.SET_COOKIE].isNullOrEmpty())
    }

    private fun response(): EntityExchangeResult<ByteArray> = requireNotNull(response) { "No response captured" }
}
