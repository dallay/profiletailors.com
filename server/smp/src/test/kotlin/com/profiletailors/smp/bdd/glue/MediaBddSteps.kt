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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets
import java.util.UUID

class MediaBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestMediaResponse: EntityExchangeResult<ByteArray>? = null
    private var latestAssetId: String? = null
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Before
    fun resetMediaState() {
        latestMediaResponse = null
        latestAssetId = null
    }

    // ---------------------------------------------------------------------------
    // Given
    // ---------------------------------------------------------------------------

    @Given("a media asset exists")
    fun givenMediaAssetExists() = runBlocking {
        bddDatabaseSupport.seedMediaAsset()
        latestAssetId = BddDatabaseSupport.MEDIA_ASSET_ID
    }

    // ---------------------------------------------------------------------------
    // When
    // ---------------------------------------------------------------------------

    @When("the client registers a media asset with file hash {string} size {long} and type {string}")
    fun whenClientRegistersMediaAsset(fileHash: String, fileSizeBytes: Long, mediaType: String) = runBlocking {
        val assetId = UUID.randomUUID().toString()
        val body = mapOf(
            "fileHash" to fileHash,
            "fileSizeBytes" to fileSizeBytes,
            "declaredMediaType" to mediaType,
            "originalFilename" to "bdd-upload.jpg",
        )
        val json = objectMapper.writeValueAsString(body)
        latestMediaResponse = webTestClient.put()
            .uri("${bddDatabaseSupport.mediaAssetsPath()}/$assetId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
        latestAssetId = assetId
    }

    @When("the client requests the media asset")
    fun whenClientRequestsMediaAsset() {
        val assetId = requireNotNull(latestAssetId) { "No asset ID available from previous step" }
        latestMediaResponse = webTestClient.get()
            .uri("${bddDatabaseSupport.mediaAssetsPath()}/$assetId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client lists media assets")
    fun whenClientListsMediaAssets() {
        latestMediaResponse = webTestClient.get()
            .uri(bddDatabaseSupport.mediaAssetsPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client deletes the media asset")
    fun whenClientDeletesMediaAsset() {
        val assetId = requireNotNull(latestAssetId) { "No asset ID available from previous step" }
        latestMediaResponse = webTestClient.delete()
            .uri("${bddDatabaseSupport.mediaAssetsPath()}/$assetId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests the deleted media asset")
    fun whenClientRequestsDeletedMediaAsset() {
        whenClientRequestsMediaAsset()
    }

    // ---------------------------------------------------------------------------
    // Then
    // ---------------------------------------------------------------------------

    @Then("the media response status should be {int}")
    fun thenMediaResponseStatusShouldBe(status: Int) {
        val response = latestMediaResponse ?: error("No media response captured")
        val body = String(response.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        val actualStatus = response.status.value()
        System.err.println("MEDIA_BDD: expected=$status actual=$actualStatus body='$body'")
        assertEquals(status, actualStatus) {
            "Expected status $status but got $actualStatus. Response body: $body"
        }
    }

    @Then("the response should contain an assetId")
    fun thenResponseShouldContainAssetId() {
        val body = mediaResponseBodyText()
        val assetId: String? = parseMediaResponseField<String>("assetId")
        assertNotNull(assetId, "Expected assetId in response: $body")
        latestAssetId = assetId ?: latestAssetId
    }

    @Then("the asset status should be {string}")
    fun thenAssetStatusShouldBe(expectedStatus: String) {
        val actualStatus: String = parseMediaResponseField("status")
        assertEquals(expectedStatus, actualStatus)
    }

    @Then("the response should contain the media type {string}")
    fun thenResponseShouldContainMediaType(expectedMediaType: String) {
        val actualMediaType: String = parseMediaResponseField("mediaType")
        assertEquals(expectedMediaType, actualMediaType)
    }

    @Then("the response should contain {int} asset")
    fun thenResponseShouldContainAssets(expectedCount: Int) {
        val assets: List<*> = parseMediaResponseField<List<*>>("assets")
        assertEquals(expectedCount, assets.size) {
            "Expected $expectedCount asset(s) but got ${assets.size} in: ${mediaResponseBodyText()}"
        }
    }

    @Then("the response should contain {int} assets")
    fun thenResponseShouldContainMultipleAssets(expectedCount: Int) {
        val assets: List<*> = parseMediaResponseField<List<*>>("assets")
        assertEquals(expectedCount, assets.size) {
            "Expected $expectedCount asset(s) but got ${assets.size} in: ${mediaResponseBodyText()}"
        }
    }

    @Then("the asset should be marked as deleted")
    fun thenAssetShouldBeMarkedAsDeleted() {
        val deleted: Boolean = parseMediaResponseField("deleted")
        assertTrue(deleted) { "Expected deleted=true in response: ${mediaResponseBodyText()}" }
    }

    @Then("the media asset should be deleted from the database")
    fun thenMediaAssetShouldBeDeletedFromDatabase() = runBlocking {
        val assetId = requireNotNull(latestAssetId) { "No asset ID available from previous step" }
        val status = bddDatabaseSupport.findMediaAssetStatus(assetId)
        assertEquals("DELETED", status) { "Expected asset $assetId status DELETED but found $status" }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun mediaResponseBodyText(): String =
        String(latestMediaResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private inline fun <reified T> parseMediaResponseField(field: String): T {
        val body = mediaResponseBodyText()
        return try {
            val map: Map<String, Any?> = objectMapper.readValue(body)
            val raw = map[field]
            when {
                raw == null && null is T ->
                    @Suppress("UNCHECKED_CAST")
                    null as T
                raw == null -> error("Field '$field' is null in response: $body")
                raw is T -> raw
                else -> error(
                    "Field '$field' has type ${raw::class.simpleName} " +
                        "but expected ${T::class.simpleName} in response: $body",
                )
            }
        } catch (e: Exception) {
            error("Failed to parse field '$field' from response: $body. Error: ${e.message}")
        }
    }
}
