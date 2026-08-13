package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.bdd.SocialContentBddState
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

class SocialContentCalendarCursorBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    @Autowired
    private lateinit var transactionalOperator: org.springframework.transaction.reactive.TransactionalOperator

    @Autowired
    private lateinit var state: SocialContentBddState

    private var latestResponse: EntityExchangeResult<ByteArray>? = null
    private var lastNextCursor: String? = null
    private val objectMapper = jacksonObjectMapper()

    @Before("@social-content-calendar")
    fun resetCalendarCursorState() {
        latestResponse = null
        lastNextCursor = null
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    /**
     * Resets the social-content calendar BDD state and database for a scenario.
     */
    @Given("the social-content calendar cursor BDD state is reset")
    fun givenSocialContentCalendarCursorBddStateIsReset() {
        state.reset()
        state.content.useProductionReaderForCalendar = true
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    /**
     * Seeds an imported, published LinkedIn Page post in the specified workspace.
     *
     * @param workspaceId The workspace in which to create the post.
     * @param publishedAt The post publication timestamp in ISO-8601 format.
     */
    @Given(
        "an imported LinkedIn Page post exists in workspace {string} via the production repository " +
            "with publishedAt {string}",
    )
    fun givenImportedLinkedInPagePostViaProductionRepository(workspaceId: String, publishedAt: String) {
        runBlocking {
            transactionalOperator.transactional(
                mono {
                    bddDatabaseSupport.seedWorkspace(workspaceId)
                    val socialAccountId = "page-$workspaceId"
                    bddDatabaseSupport.ensureSocialAccount(
                        accountId = socialAccountId,
                        connectionId = "conn-$workspaceId",
                        workspaceId = workspaceId,
                        provider = "LINKEDIN",
                        providerAccountId = "linkedin-$workspaceId",
                        accountKind = "ORGANIZATION_PAGE",
                        displayName = "Page for $workspaceId",
                    )
                    val publishedInstant = Instant.parse(publishedAt)
                    val post = SocialPost(
                        scope = WorkspaceScope(workspaceId),
                        provider = SocialProvider.LINKEDIN,
                        actorId = socialAccountId,
                        externalPostId = ExternalPostId("post-$workspaceId-${UUID.randomUUID().toString().take(8)}"),
                        publishedAt = publishedInstant,
                        lastModifiedAt = publishedInstant,
                        body = "Imported page content for $workspaceId",
                        origin = PostOrigin.EXTERNAL_OR_UNKNOWN,
                        lifecycle = PostLifecycle.PUBLISHED,
                        expiresAt = publishedInstant.plusSeconds(172_800),
                    )
                    bddDatabaseSupport.seedSocialContentPost(post)
                },
            ).awaitSingle()
        }
    }

    /**
     * Requests the social-content calendar for a date range with a result limit.
     *
     * @param from The start of the calendar date range.
     * @param to The end of the calendar date range.
     * @param limit The maximum number of calendar items to request.
     */
    @When(
        "the cursor client requests social-content calendar from {string} to {string} with limit {int}",
    )
    fun whenClientRequestsCalendarWithLimit(from: String, to: String, limit: Int) {
        latestResponse = socialContentCalendarGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to&limit=$limit",
            BddDatabaseSupport.WORKSPACE_ID,
        )
        extractNextCursor()
    }

    /**
     * Requests the next social-content calendar page using the cursor from the previous response.
     *
     * @param from The start of the calendar date range.
     * @param to The end of the calendar date range.
     * @throws AssertionError If the previous response did not contain a cursor.
     */
    @When(
        "the cursor client requests social-content calendar from {string} to {string} " +
            "with the last received cursor",
    )
    fun whenClientRequestsCalendarWithLastCursor(from: String, to: String) {
        val cursor = lastNextCursor
        assertNotNull(cursor, "No nextCursor was received from the previous response")
        latestResponse = socialContentCalendarGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to&cursor=$cursor",
            BddDatabaseSupport.WORKSPACE_ID,
        )
        extractNextCursor()
    }

    /**
     * Requests the social-content calendar for a date range using a cursor and workspace.
     *
     * @param from The start of the calendar range.
     * @param to The end of the calendar range.
     * @param cursor The pagination cursor.
     * @param workspaceId The workspace identifier.
     */
    @When(
        "the client requests social-content calendar from {string} to {string} " +
            "with cursor {string} for workspace {string}",
    )
    fun whenClientRequestsCalendarWithCursorForForeignWorkspace(
        from: String,
        to: String,
        cursor: String,
        workspaceId: String,
    ) {
        latestResponse = socialContentCalendarGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to&cursor=$cursor",
            workspaceId,
        )
    }

    /**
     * Requests the social-content calendar for the specified date range using a cursor.
     *
     * @param from The start of the calendar range.
     * @param to The end of the calendar range.
     * @param cursor The pagination cursor to use.
     */
    @When(
        "the cursor client requests social-content calendar from {string} to {string} " +
            "with cursor {string}",
    )
    fun whenClientRequestsCalendarWithCursor(from: String, to: String, cursor: String) {
        latestResponse = socialContentCalendarGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to&cursor=$cursor",
            BddDatabaseSupport.WORKSPACE_ID,
        )
    }

    /**
     * Verifies that the social-content calendar contains the expected number of posts.
     *
     * @param expectedCount The expected number of posts.
     */
    @Then("the social-content calendar should contain {int} posts")
    fun thenCalendarShouldContainCount(expectedCount: Int) {
        assertCalendarContainsCount(expectedCount)
    }

    /**
     * Verifies that the social-content calendar contains one post.
     */
    @Then("the social-content calendar should contain 1 post")
    fun thenCalendarShouldContainOnePost() {
        assertCalendarContainsCount(1)
    }

    /**
     * Verifies that the latest cursor-based social-content response has the expected HTTP status.
     *
     * @param status The expected HTTP status code.
     */
    @Then("the cursor social-content response status should be {int}")
    fun thenCursorResponseStatusShouldBe(status: Int) {
        assertEquals(status, latestResponse?.status?.value(), responseBody())
    }

    /**
     * Verifies that the cursor problem response contains the expected denial code.
     *
     * @param denial The expected denial code.
     */
    @Then("the cursor social-content problem should contain denial {string}")
    fun thenCursorProblemShouldContainDenial(denial: String) {
        assertEquals(denial, json().path("errorCode").asText(), responseBody())
    }

    /**
     * Verifies that the calendar response contains the expected number of items.
     *
     * @param expectedCount The expected number of calendar items.
     */
    private fun assertCalendarContainsCount(expectedCount: Int) {
        val actualCount = json().path("items").size()
        assertEquals(expectedCount, actualCount, responseBody())
    }

    /**
     * Verifies that the social-content calendar response contains a nonblank next cursor.
     */
    @Then("the cursor social-content response should contain a nextCursor")
    fun thenResponseShouldContainNextCursor() {
        val cursorNode = json().path("nextCursor")
        assertFalse(cursorNode.isMissingNode || cursorNode.isNull, "Expected nextCursor but got: ${responseBody()}")
        assertTrue(cursorNode.asText().isNotBlank(), "nextCursor should not be blank")
    }

    /**
     * Verifies that the social-content calendar response does not contain a next cursor.
     */
    @Then("the cursor social-content response should not contain a nextCursor")
    fun thenResponseShouldNotContainNextCursor() {
        val cursorNode = json().path("nextCursor")
        assertTrue(cursorNode.isMissingNode || cursorNode.isNull, "Expected no nextCursor but got: ${responseBody()}")
    }

    /**
     * Stores the response's next-page cursor, or clears it when no cursor is present.
     */
    private fun extractNextCursor() {
        val cursorNode = json().path("nextCursor")
        lastNextCursor = if (cursorNode.isMissingNode || cursorNode.isNull) null else cursorNode.asText()
    }

    /**
     * Executes an authenticated GET request for the social-content calendar.
     *
     * @param uri The request URI.
     * @param workspaceId The workspace identifier to include in the request.
     * @return The raw HTTP response body and response metadata.
     */
    private fun socialContentCalendarGet(uri: String, workspaceId: String): EntityExchangeResult<ByteArray> {
        val request = webTestClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
        return request.exchange().expectBody().returnResult()
    }

    /**
 * Decodes the latest response body as UTF-8 text.
 *
 * @return The decoded response body, or an empty string when no response body is available.
 */
private fun responseBody(): String = String(latestResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    /**
 * Parses the current response body as JSON.
 *
 * @return The parsed JSON response.
 */
private fun json(): JsonNode = objectMapper.readTree(responseBody())
}
