package com.profiletailors.smp.bdd.glue

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class PlatformHealthcheckBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    private var response: EntityExchangeResult<ByteArray>? = null

    @When("the healthcheck endpoint is called")
    fun callHealthcheck() {
        response = webTestClient.get()
            .uri("/api/health-check")
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the healthcheck response status should be {int}")
    fun healthcheckStatus(status: Int) {
        assertEquals(status, response().status.value())
    }

    @Then("the healthcheck response body should be {string}")
    fun healthcheckBody(expected: String) {
        val body = response().responseBody?.toString(Charsets.UTF_8).orEmpty()
        assertEquals(expected, body)
    }

    private fun response(): EntityExchangeResult<ByteArray> = requireNotNull(response) { "No response captured" }
}
