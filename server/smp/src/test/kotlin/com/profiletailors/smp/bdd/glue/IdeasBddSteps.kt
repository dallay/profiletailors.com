package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class IdeasBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestIdeasResponse: EntityExchangeResult<ByteArray>? = null
    private var currentIdeaId: String? = null
    private val objectMapper = jacksonObjectMapper()

    @Before
    fun resetIdeasState() {
        latestIdeasResponse = null
        currentIdeaId = null
    }

    @When("the client creates an idea with title {string}")
    fun createIdea(title: String) {
        val payload = mapOf(
            "title" to title,
            "notes" to "Captured in BDD",
            "tags" to listOf("launch", "q3"),
            "links" to listOf(mapOf("url" to "https://example.com", "label" to "source")),
            "columnId" to "raw",
        )
        val json = objectMapper.writeValueAsString(payload)
        latestIdeasResponse = webTestClient.post()
            .uri("/api/ideas")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()

        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val map: Map<String, Any?> = objectMapper.readValue(body)
        currentIdeaId = map["id"] as String?
    }

    @When("the client lists ideas")
    fun listIdeas() {
        latestIdeasResponse = webTestClient.get()
            .uri("/api/ideas")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Given("an existing idea in raw column")
    fun ensureIdeaExists() = runBlocking {
        if (currentIdeaId == null) {
            createIdea("Seed idea")
        }
    }

    @When("the client moves the idea to column {string} at position {int}")
    fun moveIdea(columnId: String, position: Int) {
        val ideaId = requireNotNull(currentIdeaId)
        val payload = mapOf(
            "columnId" to columnId,
            "orderInColumn" to position,
        )
        latestIdeasResponse = webTestClient.patch()
            .uri("/api/ideas/$ideaId/move")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client updates the idea title to {string}")
    fun updateIdeaTitle(title: String) {
        val ideaId = requireNotNull(currentIdeaId)
        val payload = mapOf("title" to title)
        latestIdeasResponse = webTestClient.patch()
            .uri("/api/ideas/$ideaId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client updates idea columns to {string}")
    fun updateColumns(columnsCsv: String) {
        val columns = columnsCsv.split(',').mapIndexed { index, name ->
            mapOf(
                "id" to name.trim().lowercase().replace(' ', '-'),
                "name" to name.trim(),
                "order" to index,
                "color" to null,
            )
        }
        val payload = mapOf("columns" to columns)

        latestIdeasResponse = webTestClient.put()
            .uri("/api/ideas/columns")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client converts the idea to a publication")
    fun convertIdea() {
        val ideaId = requireNotNull(currentIdeaId)
        latestIdeasResponse = webTestClient.post()
            .uri("/api/ideas/$ideaId/convert")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client associates the idea with publication {string}")
    fun associateIdea(publicationId: String) = runBlocking {
        if (publicationId.startsWith("pub-handoff-")) {
            try {
                bddDatabaseSupport.seedDraftPublication(
                    publicationId = publicationId,
                    socialAccountId = "social-acc-1",
                    title = "Handoff publication $publicationId",
                    bodyText = "Handoff body for $publicationId",
                )
            } catch (_: Exception) {
            }
        }
        val ideaId = requireNotNull(currentIdeaId)
        val payload = mapOf("convertedToPublicationId" to publicationId)
        latestIdeasResponse = webTestClient.patch()
            .uri("/api/ideas/$ideaId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the ideas response status should be {int}")
    fun assertStatus(code: Int) {
        assertEquals(code, latestIdeasResponse?.status?.value())
    }

    @Then("the ideas response should contain an ideaId")
    fun assertIdeaId() {
        assertNotNull(currentIdeaId)
    }

    @Then("the ideas response should contain at least {int} idea")
    fun assertIdeasCountAtLeast(minCount: Int) {
        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val payload: Map<String, Any?> = objectMapper.readValue(body)
        val ideas = payload["ideas"] as? List<*> ?: emptyList<Any>()
        assertTrue(ideas.size >= minCount)
    }

    @Then("the idea column should be {string}")
    fun assertIdeaColumn(columnId: String) {
        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val payload: Map<String, Any?> = objectMapper.readValue(body)
        assertEquals(columnId, payload["columnId"])
    }

    @Then("the idea title should be {string}")
    fun assertIdeaTitle(title: String) {
        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val payload: Map<String, Any?> = objectMapper.readValue(body)
        assertEquals(title, payload["title"])
    }

    @Then("the columns response should contain {int} columns")
    fun assertColumnsCount(count: Int) {
        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val payload: Map<String, Any?> = objectMapper.readValue(body)
        val columns = payload["columns"] as? List<*> ?: emptyList<Any>()
        assertEquals(count, columns.size)
    }

    @Then("the convert response should contain a publicationId")
    fun assertPublicationId() {
        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val payload: Map<String, Any?> = objectMapper.readValue(body)
        assertNotNull(payload["publicationId"])
    }

    @Then("the idea convertedToPublicationId should be {string}")
    fun assertConvertedId(expected: String) {
        val body = latestIdeasResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val payload: Map<String, Any?> = objectMapper.readValue(body)
        assertEquals(expected, payload["convertedToPublicationId"])
    }
}
