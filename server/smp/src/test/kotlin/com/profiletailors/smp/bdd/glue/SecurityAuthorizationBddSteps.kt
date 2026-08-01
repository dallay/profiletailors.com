package com.profiletailors.smp.bdd.glue

import io.cucumber.java.Before
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class SecurityAuthorizationBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    // Actuator endpoints run on the management server in the test profile
    // (management.server.port is fixed while the app uses a random port).
    @Value("\${local.management.port:8080}")
    private var managementPort: Int = 8080

    private var latestResponse: EntityExchangeResult<ByteArray>? = null

    @Before
    fun resetSecurityState() {
        latestResponse = null
    }

    @When("an unauthenticated client sends GET {string}")
    fun unauthenticatedGet(path: String) {
        val actuatorPath = path.startsWith("/actuator")
        val client = if (actuatorPath) {
            WebTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()
        } else {
            webTestClient
        }
        val get = client.get().uri(path)
        // Actuator produces Spring Boot media types, not the API media type
        if (!actuatorPath) {
            get.header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
        }
        latestResponse = get.exchange().expectBody().returnResult()
    }

    @Then("the security response status should be {int}")
    fun securityResponseStatusShouldBe(expectedStatus: Int) {
        val actual = requireNotNull(latestResponse) { "No security response captured" }
            .status.value()
        assertEquals(expectedStatus, actual)
    }
}
