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

    @Given("the social-content calendar cursor BDD state is reset")
    fun givenSocialContentCalendarCursorBddStateIsReset() {
        state.reset()
        state.content.useProductionReaderForCalendar = true
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

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

    @Then("the social-content calendar should contain {int} posts")
    fun thenCalendarShouldContainCount(expectedCount: Int) {
        assertCalendarContainsCount(expectedCount)
    }

    @Then("the social-content calendar should contain 1 post")
    fun thenCalendarShouldContainOnePost() {
        assertCalendarContainsCount(1)
    }

    @Then("the cursor social-content response status should be {int}")
    fun thenCursorResponseStatusShouldBe(status: Int) {
        assertEquals(status, latestResponse?.status?.value(), responseBody())
    }

    @Then("the cursor social-content problem should contain denial {string}")
    fun thenCursorProblemShouldContainDenial(denial: String) {
        assertEquals(denial, json().path("errorCode").asText(), responseBody())
    }

    private fun assertCalendarContainsCount(expectedCount: Int) {
        val actualCount = json().path("items").size()
        assertEquals(expectedCount, actualCount, responseBody())
    }

    @Then("the cursor social-content response should contain a nextCursor")
    fun thenResponseShouldContainNextCursor() {
        val cursorNode = json().path("nextCursor")
        assertFalse(cursorNode.isMissingNode || cursorNode.isNull, "Expected nextCursor but got: ${responseBody()}")
        assertTrue(cursorNode.asText().isNotBlank(), "nextCursor should not be blank")
    }

    @Then("the cursor social-content response should not contain a nextCursor")
    fun thenResponseShouldNotContainNextCursor() {
        val cursorNode = json().path("nextCursor")
        assertTrue(cursorNode.isMissingNode || cursorNode.isNull, "Expected no nextCursor but got: ${responseBody()}")
    }

    private fun extractNextCursor() {
        val cursorNode = json().path("nextCursor")
        lastNextCursor = if (cursorNode.isMissingNode || cursorNode.isNull) null else cursorNode.asText()
    }

    private fun socialContentCalendarGet(uri: String, workspaceId: String): EntityExchangeResult<ByteArray> {
        val request = webTestClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
        return request.exchange().expectBody().returnResult()
    }

    private fun responseBody(): String = String(latestResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private fun json(): JsonNode = objectMapper.readTree(responseBody())
}
