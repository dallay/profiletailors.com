package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private const val CONFIGURATION_API_V1 = "application/vnd.api.v1+json"
private const val CONFIGURATION_ADMIN_BEARER = "Bearer $BDD_ADMIN_TOKEN"
private const val REGISTRATION_MODE_PATH = "/api/admin/configuration/registration-mode"

class ConfigurationBddSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var state: PlatformAdminScenarioState

    private val json = ObjectMapper()

    private var concurrentResponses: List<EntityExchangeResult<ByteArray>> = emptyList()

    @Given("the registration mode is currently {string}")
    fun registrationModeIsCurrently(mode: String) = runBlocking {
        databaseClient.sql(
            "UPDATE platform_operational_config SET config_value = :mode, version = version + 1, " +
                "updated_at = CURRENT_TIMESTAMP WHERE config_key = 'registration.mode'",
        )
            .bind("mode", mode)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @When("the platform operator requests the current registration mode")
    @When("the principal requests the current registration mode")
    @When("a new request reads the current registration mode")
    fun requestsCurrentRegistrationMode() {
        state.lastResponse = getRegistrationMode()
    }

    @When("the owner changes the registration mode to {string}")
    fun ownerChangesRegistrationMode(mode: String) {
        state.lastResponse = changeRegistrationMode(mode, "owner-change-${UUID.randomUUID()}")
    }

    @When("the owner attempts to change the registration mode to {string}")
    fun ownerAttemptsToChangeRegistrationMode(mode: String) {
        state.lastResponse = changeRegistrationMode(mode, "owner-attempt-${UUID.randomUUID()}")
    }

    @When("the platform operator attempts to change the registration mode to {string}")
    fun operatorAttemptsToChangeRegistrationMode(mode: String) {
        state.lastResponse = changeRegistrationMode(mode, "operator-attempt-${UUID.randomUUID()}")
    }

    @When("two owners concurrently change the registration mode to {string} and {string}")
    fun twoOwnersConcurrentlyChangeRegistrationMode(modeA: String, modeB: String) {
        val executor = Executors.newFixedThreadPool(2)
        val keyA = "concurrent-a-${UUID.randomUUID()}"
        val keyB = "concurrent-b-${UUID.randomUUID()}"
        try {
            val futureA = executor.submit(Callable { changeRegistrationMode(modeA, keyA) })
            val futureB = executor.submit(Callable { changeRegistrationMode(modeB, keyB) })
            concurrentResponses = listOf(futureA.get(), futureB.get())
        } finally {
            executor.shutdown()
        }
    }

    @Then("the registration mode response should be {string}")
    fun registrationModeResponseShouldBe(expected: String) {
        val body = state.lastResponse?.responseBody?.let { json.readTree(it) }
        assertEquals(expected, body?.get("mode")?.asText())
    }

    @Then("the registration mode response should not disclose a mode value")
    fun registrationModeResponseShouldNotDiscloseAModeValue() {
        val body = state.lastResponse?.responseBody?.let { json.readTree(it) }
        assertNull(body?.get("mode"))
    }

    @Then("the persisted registration mode should be {string}")
    fun persistedRegistrationModeShouldBe(expected: String) = runBlocking {
        assertEquals(expected, currentPersistedMode())
    }

    @Then("the persisted registration mode should be {string} or {string}")
    fun persistedRegistrationModeShouldBeEither(optionA: String, optionB: String) = runBlocking {
        val actual = currentPersistedMode()
        assertTrue(actual == optionA || actual == optionB, "Expected $optionA or $optionB but was $actual")
    }

    @Then("both concurrent registration mode changes should return 200")
    fun bothConcurrentRegistrationModeChangesShouldReturn200() {
        assertEquals(2, concurrentResponses.size)
        concurrentResponses.forEach { assertEquals(200, it.status.value()) }
    }

    @Then("a {string} {string} audit event should be recorded")
    fun auditEventShouldBeRecorded(action: String, result: String) = runBlocking {
        val count = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events " +
                "WHERE action = :action AND result = :result AND target_type = 'CONFIGURATION' " +
                "AND target_id = 'registration.mode'",
        )
            .bind("action", action)
            .bind("result", result)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertTrue(count > 0, "Expected at least one $action/$result audit event")
    }

    @Then("each concurrent write should be audited with a consistent previous and new mode")
    fun eachConcurrentWriteShouldBeAuditedConsistently() = runBlocking {
        val transitions = databaseClient.sql(
            "SELECT metadata FROM platform_admin_audit_events " +
                "WHERE action = 'CONFIGURATION_CHANGED' AND result = 'SUCCEEDED' " +
                "AND target_id = 'registration.mode' ORDER BY occurred_at ASC",
        )
            .map { row, _ -> requireNotNull(row.get("metadata", String::class.java)) }
            .all()
            .collectList()
            .awaitSingle()
            .map { json.readTree(it) }

        assertEquals(2, transitions.size)
        val first = transitions[0]
        val second = transitions[1]
        assertEquals("OPEN", first.get("previousMode").asText())
        assertEquals(second.get("previousMode").asText(), first.get("newMode").asText())
    }

    private fun getRegistrationMode(): EntityExchangeResult<ByteArray> = webTestClient.get()
        .uri(REGISTRATION_MODE_PATH)
        .header(HttpHeaders.ACCEPT, CONFIGURATION_API_V1)
        .header(HttpHeaders.AUTHORIZATION, CONFIGURATION_ADMIN_BEARER)
        .exchange()
        .expectBody(ByteArray::class.java)
        .returnResult()

    private fun changeRegistrationMode(mode: String, idempotencyKey: String): EntityExchangeResult<ByteArray> =
        webTestClient.post()
            .uri(REGISTRATION_MODE_PATH)
            .header(HttpHeaders.ACCEPT, CONFIGURATION_API_V1)
            .header(HttpHeaders.AUTHORIZATION, CONFIGURATION_ADMIN_BEARER)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("mode" to mode))
            .exchange()
            .expectBody(ByteArray::class.java)
            .returnResult()

    private suspend fun currentPersistedMode(): String? = databaseClient.sql(
        "SELECT config_value FROM platform_operational_config WHERE config_key = 'registration.mode'",
    )
        .map { row, _ -> requireNotNull(row.get("config_value", String::class.java)) }
        .first()
        .awaitSingleOrNull()
}
