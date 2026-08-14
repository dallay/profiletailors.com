package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.time.temporal.ChronoUnit

class AnalyticsBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestAnalyticsResponse: EntityExchangeResult<ByteArray>? = null
    private var currentSocialAccountId: String? = null
    private val objectMapper = jacksonObjectMapper()

    @Before
    fun resetAnalyticsState() {
        latestAnalyticsResponse = null
        currentSocialAccountId = null
    }

    @Given("a published publication exists")
    fun givenPublishedPublicationExists() = runBlocking {
        bddDatabaseSupport.seedWorkspace()
        bddDatabaseSupport.seedSocialConnection("social-conn-analytics-1", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            accountId = "social-acc-analytics-1",
            connectionId = "social-conn-analytics-1",
            provider = "LINKEDIN",
            providerAccountId = "linkedin-analytics-1",
            accountKind = "PERSONAL_PROFILE",
            displayName = "Analytics Test Account",
        )
        currentSocialAccountId = "social-acc-analytics-1"
        bddDatabaseSupport.seedPublishedPublication(
            publicationId = "pub-analytics-1",
            socialAccountId = "social-acc-analytics-1",
            title = "Analytics Test Post",
            bodyText = "Post body for analytics testing",
            publishedAt = Instant.now().minus(7, ChronoUnit.DAYS),
        )
    }

    @When("the client requests analytics overview")
    fun clientRequestsAnalyticsOverview() {
        latestAnalyticsResponse = webTestClient.get()
            .uri("/api/analytics/overview")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests analytics overview from {string} to {string}")
    fun clientRequestsAnalyticsOverviewForRange(startDate: String, endDate: String) {
        latestAnalyticsResponse = webTestClient.get()
            .uri("/api/analytics/overview?startDate=$startDate&endDate=$endDate")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests post analytics")
    fun clientRequestsPostAnalytics() {
        latestAnalyticsResponse = webTestClient.get()
            .uri("/api/analytics/posts")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests best posting times")
    fun clientRequestsBestTimes() {
        latestAnalyticsResponse = webTestClient.get()
            .uri("/api/analytics/best-times")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client exports analytics as CSV")
    fun clientExportsAnalytics() {
        latestAnalyticsResponse = webTestClient.post()
            .uri("/api/analytics/export")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the analytics response status should be {int}")
    fun analyticsResponseStatusShouldBe(status: Int) {
        assertEquals(status, latestAnalyticsResponse?.status?.value())
    }

    @And("the overview contains {word}")
    fun overviewContainsField(field: String) {
        val body = latestAnalyticsResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        val map: Map<String, Any?> = objectMapper.readValue(body)
        assertTrue(map.containsKey(field), "Expected field '$field' in overview response")
    }

    @And("the overview period start is {string}")
    fun overviewPeriodStartIs(expectedDate: String) {
        val map = responseAsMap()
        val period = map["period"] as? Map<*, *>
        assertEquals(expectedDate, period?.get("startDate")?.toString())
    }

    @And("the overview period end is {string}")
    fun overviewPeriodEndIs(expectedDate: String) {
        val map = responseAsMap()
        val period = map["period"] as? Map<*, *>
        assertEquals(expectedDate, period?.get("endDate")?.toString())
    }

    @And("the post analytics list contains at least {int} post")
    fun postAnalyticsListContainsAtLeast(count: Int) {
        val map = responseAsMap()
        val posts = map["posts"] as? List<*>
        assertNotNull(posts)
        assertTrue(posts?.size ?: 0 >= count, "Expected at least $count posts")
    }

    @And("each post has postId and publishedAt")
    fun eachPostHasRequiredFields() {
        val map = responseAsMap()
        val posts = map["posts"] as? List<*> ?: return
        posts.forEach { post ->
            val postMap = post as? Map<*, *>
            if (postMap != null) {
                assertNotNull(postMap["postId"], "postId missing from post")
                assertNotNull(postMap["publishedAt"], "publishedAt missing from post")
            }
        }
    }

    @And("the best times response contains a slots array")
    fun bestTimesContainsSlotsArray() {
        val map = responseAsMap()
        val slots = map["slots"]
        assertNotNull(slots, "Expected 'slots' field in best-times response")
        assertTrue(slots is List<*>)
    }

    @And("the response content type is {string}")
    fun responseContentTypeIs(contentType: String) {
        val actual = latestAnalyticsResponse?.responseHeaders?.contentType?.toString() ?: ""
        assertTrue(actual.startsWith(contentType), "Expected content-type '$contentType' but got '$actual'")
    }

    @And("the CSV contains the header row")
    fun csvContainsHeaderRow() {
        val body = latestAnalyticsResponse?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.startsWith("date,platform,title"), "Expected CSV header row")
    }

    @And("the overview totalImpressions is {int}")
    fun overviewTotalImpressionsIs(expected: Int) {
        val map = responseAsMap()
        val actual = (map["totalImpressions"] as? Number)?.toInt() ?: -1
        assertEquals(expected, actual)
    }

    @And("the overview totalEngagements is {int}")
    fun overviewTotalEngagementsIs(expected: Int) {
        val map = responseAsMap()
        val actual = (map["totalEngagements"] as? Number)?.toInt() ?: -1
        assertEquals(expected, actual)
    }

    private fun responseAsMap(): Map<String, Any?> {
        val body = latestAnalyticsResponse?.responseBody?.toString(Charsets.UTF_8) ?: "{}"
        return objectMapper.readValue(body)
    }
}
