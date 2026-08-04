package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class HashtagsBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    private var latestResponse: EntityExchangeResult<ByteArray>? = null
    private var currentSetId: String? = null
    private val objectMapper = jacksonObjectMapper()

    @Before
    fun resetHashtagsState() {
        latestResponse = null
        currentSetId = null
    }

    @When("the client analyzes content {string}")
    fun analyzeContent(content: String) {
        val payload = mapOf("content" to content)
        latestResponse = webTestClient.post()
            .uri("/api/hashtags/analyze")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client fetches trending hashtags")
    fun fetchTrendingHashtags() {
        latestResponse = webTestClient.get()
            .uri("/api/hashtags/trending")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client saves a hashtag set named {string} with hashtags {string}")
    fun saveHashtagSet(name: String, hashtagsCsv: String) {
        val hashtags = if (hashtagsCsv.isBlank()) {
            emptyList()
        } else {
            hashtagsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
        }
        val payload = mapOf("name" to name, "hashtags" to hashtags)
        latestResponse = webTestClient.post()
            .uri("/api/hashtags/saved-sets")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .exchange()
            .expectBody()
            .returnResult()

        val body = latestResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        runCatching {
            val map: Map<String, Any?> = objectMapper.readValue(body)
            currentSetId = map["id"] as? String
        }
    }

    @Given("a saved hashtag set {string} with hashtags {string} exists")
    fun ensureSavedSetExists(name: String, hashtagsCsv: String) {
        saveHashtagSet(name, hashtagsCsv)
        val body = latestResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val map: Map<String, Any?> = objectMapper.readValue(body)
        currentSetId = map["id"] as? String
    }

    @When("the client lists saved hashtag sets")
    fun listSavedSets() {
        latestResponse = webTestClient.get()
            .uri("/api/hashtags/saved-sets")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client deletes the saved hashtag set")
    fun deleteHashtagSet() {
        val setId = requireNotNull(currentSetId) { "No set to delete — save one first" }
        latestResponse = webTestClient.delete()
            .uri("/api/hashtags/saved-sets/$setId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the hashtags response status should be {int}")
    fun verifyHashtagsResponseStatus(expectedStatus: Int) {
        val status = latestResponse?.status?.value()
        assertEquals(expectedStatus, status, "Unexpected HTTP status")
    }

    @Then("the hashtags response should contain at least {int} suggestion(s)")
    fun verifySuggestionsCount(minCount: Int) {
        val body: Map<String, Any?> = parseBody()

        @Suppress("UNCHECKED_CAST")
        val suggestions = body["suggestedHashtags"] as? List<*> ?: emptyList<Any>()
        assertTrue(suggestions.size >= minCount, "Expected at least $minCount suggestions, got ${suggestions.size}")
    }

    @Then("the suggestions should include detected topics")
    fun verifyDetectedTopics() {
        val body: Map<String, Any?> = parseBody()

        @Suppress("UNCHECKED_CAST")
        val topics = body["detectedTopics"] as? List<*> ?: emptyList<Any>()
        assertTrue(topics.isNotEmpty(), "Expected at least one detected topic")
    }

    @Then("the trending hashtags response should contain at least {int} hashtag(s)")
    fun verifyTrendingCount(minCount: Int) {
        val body: Map<String, Any?> = parseBody()

        @Suppress("UNCHECKED_CAST")
        val hashtags = body["hashtags"] as? List<*> ?: emptyList<Any>()
        assertTrue(hashtags.size >= minCount, "Expected at least $minCount trending hashtags, got ${hashtags.size}")
    }

    @Then("the saved set response should contain a setId")
    fun verifySavedSetId() {
        val body: Map<String, Any?> = parseBody()
        assertNotNull(body["id"], "Expected a setId in the response")
    }

    @Then("the saved set name should be {string}")
    fun verifySavedSetName(expectedName: String) {
        val body: Map<String, Any?> = parseBody()
        assertEquals(expectedName, body["name"], "Saved set name mismatch")
    }

    @Then("the saved sets response should contain at least {int} set(s)")
    fun verifySavedSetsCount(minCount: Int) {
        val body: Map<String, Any?> = parseBody()

        @Suppress("UNCHECKED_CAST")
        val sets = body["sets"] as? List<*> ?: emptyList<Any>()
        assertTrue(sets.size >= minCount, "Expected at least $minCount sets, got ${sets.size}")
    }

    @Then("the saved hashtags should all start with {string}")
    fun verifySavedHashtagsNormalized(prefix: String) {
        val body: Map<String, Any?> = parseBody()

        @Suppress("UNCHECKED_CAST")
        val hashtags = body["hashtags"] as? List<*> ?: emptyList<Any>()
        assertTrue(hashtags.isNotEmpty(), "Expected hashtags in the response")
        hashtags.forEach { tag ->
            assertTrue(tag.toString().startsWith(prefix), "Hashtag '$tag' should start with '$prefix'")
        }
    }

    private fun parseBody(): Map<String, Any?> {
        val raw = latestResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        return objectMapper.readValue(raw)
    }
}
