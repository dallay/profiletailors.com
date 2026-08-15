package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.bdd.SocialContentBddState
import com.profiletailors.smp.bdd.bddImportedPost
import com.profiletailors.smp.bdd.bddOrganizationPageActor
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidence
import com.profiletailors.smp.publishing.domain.SocialProvider
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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets

class SocialContentBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var state: SocialContentBddState

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestResponse: EntityExchangeResult<ByteArray>? = null
    private var lastNextCursor: String? = null
    private val objectMapper = jacksonObjectMapper()

    @Before("@linkedin-social-content")
    fun resetSocialContentState() {
        latestResponse = null
        lastNextCursor = null
        state.reset()
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    @Given("the default social-content feature gates are disabled")
    fun givenDefaultSocialContentFeatureGatesAreDisabled() = Unit

    /**
     * Resets the social-content BDD state and database.
     */
    @Given("the social-content BDD state is reset")
    fun givenSocialContentBddStateIsReset() {
        state.reset()
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    /**
     * Configures the BDD state with an active personal LinkedIn account as the only social-content account.
     */
    @Given("a personal LinkedIn social account is the only social-content account")
    fun givenPersonalLinkedInSocialAccountIsOnlySocialContentAccount() {
        state.gates.importEnabled = true
        state.socialAccounts[BddDatabaseSupport.WORKSPACE_ID to "personal-profile-1"] = SocialAccount(
            id = "personal-profile-1",
            socialConnectionId = "personal-connection-1",
            workspaceId = BddDatabaseSupport.WORKSPACE_ID,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "linkedin-profile-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Personal Profile",
            status = SocialConnectionStatus.ACTIVE,
        )
    }

    @Given("an approved LinkedIn organization page actor exists for social-content sync")
    fun givenApprovedLinkedInOrganizationPageActorExists() {
        state.gates.importEnabled = true
        val actor = bddOrganizationPageActor(BddDatabaseSupport.WORKSPACE_ID)
        state.actors.put(actor)
        state.approvalEvidence.put(
            SocialContentApprovalEvidence(
                workspaceId = actor.scope.value,
                socialAccountId = actor.socialAccountId,
                roleState = ActorRoleState.ADMIN,
                grantedScopes = actor.grantedScopes,
                communityManagementApproved = true,
                apiVersion = "202606",
                retentionPolicyVersion = "2026-01",
            ),
        )
    }

    @Given("an imported LinkedIn Page post exists in workspace {string}")
    fun givenImportedLinkedInPagePostExists(workspaceId: String) {
        state.content.seed(bddImportedPost(workspaceId))
    }

    @When("the client requests social-content sync for actor {string} without authorization")
    fun whenClientRequestsSyncWithoutAuthorization(actorId: String) {
        latestResponse = socialContentPost(
            uri = "/api/publishing/social-content/sync",
            authorization = null,
            workspaceId = BddDatabaseSupport.WORKSPACE_ID,
            body = """{"actorId":"$actorId"}""",
        )
    }

    @When("the client requests social-content sync for actor {string} without workspace context")
    fun whenClientRequestsSyncWithoutWorkspace(actorId: String) {
        latestResponse = socialContentPost(
            uri = "/api/publishing/social-content/sync",
            authorization = BddDatabaseSupport.USER_BEARER,
            workspaceId = null,
            body = """{"actorId":"$actorId"}""",
        )
    }

    @When("the client requests social-content sync for actor {string}")
    fun whenClientRequestsSync(actorId: String) {
        latestResponse = socialContentPost(
            uri = "/api/publishing/social-content/sync",
            authorization = BddDatabaseSupport.USER_BEARER,
            workspaceId = BddDatabaseSupport.WORKSPACE_ID,
            body = """{"actorId":"$actorId"}""",
        )
    }

    /**
     * Requests the social-content calendar for the specified date range.
     *
     * @param from The start date of the calendar range.
     * @param to The end date of the calendar range.
     */
    @When("the client requests social-content calendar from {string} to {string}")
    fun whenClientRequestsCalendar(from: String, to: String) {
        latestResponse = socialContentGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to",
            BddDatabaseSupport.WORKSPACE_ID,
        )
    }

    /**
     * Requests the social-content calendar for a date range using the specified pagination cursor.
     *
     * @param from The start date of the calendar range.
     * @param to The end date of the calendar range.
     * @param cursor The pagination cursor.
     */
    @When("the client requests social-content calendar from {string} to {string} with cursor {string}")
    fun whenClientRequestsCalendarWithCursor(from: String, to: String, cursor: String) {
        latestResponse = socialContentGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to&cursor=$cursor",
            BddDatabaseSupport.WORKSPACE_ID,
        )
    }

    /**
     * Requests the social-content calendar for a date range with a maximum item limit.
     *
     * @param from The start date of the calendar range.
     * @param to The end date of the calendar range.
     * @param limit The maximum number of calendar items to request.
     */
    @When("the client requests social-content calendar from {string} to {string} with limit {int}")
    fun whenClientRequestsCalendarWithLimit(from: String, to: String, limit: Int) {
        latestResponse = socialContentGet(
            "/api/publishing/social-content/calendar?from=$from&to=$to&limit=$limit",
            BddDatabaseSupport.WORKSPACE_ID,
        )
        extractNextCursor()
    }

    /**
     * Requests social-content post details for the specified external post.
     *
     * @param externalPostId The external identifier of the post to retrieve.
     */
    @When("the client requests social-content post detail for {string}")
    fun whenClientRequestsPostDetail(externalPostId: String) {
        latestResponse = socialContentGet(
            "/api/publishing/social-content/posts/$externalPostId",
            BddDatabaseSupport.WORKSPACE_ID,
        )
    }

    @When("the client requests social-content post detail for {string} in workspace {string}")
    fun whenClientRequestsPostDetailInWorkspace(externalPostId: String, workspaceId: String) {
        latestResponse = socialContentGet(
            "/api/publishing/social-content/posts/$externalPostId",
            workspaceId,
        )
    }

    @Then("the social-content response status should be {int}")
    fun thenResponseStatusShouldBe(status: Int) {
        assertEquals(status, latestResponse?.status?.value(), responseBody())
    }

    @Then("the social-content problem should contain denial {string}")
    fun thenProblemShouldContainDenial(denial: String) {
        assertTrue(responseBody().contains(denial), responseBody())
    }

    @Then("the social-content provider should have received {int} calls")
    fun thenProviderShouldHaveReceivedCalls(expected: Int) {
        assertEquals(expected, state.provider.calls())
    }

    @Then("the social-content response should contain actorId {string}")
    fun thenResponseShouldContainActorId(actorId: String) {
        assertEquals(actorId, json().path("actorId").asText())
    }

    @Then("the social-content response should contain status {string}")
    fun thenResponseShouldContainStatus(status: String) {
        assertEquals(status, json().path("status").asText())
    }

    @Then("the social-content calendar should contain post {string}")
    fun thenCalendarShouldContainPost(externalPostId: String) {
        assertTrue(json().path("items").any { externalPostIdOf(it) == externalPostId }, responseBody())
    }

    @Then("the social-content calendar item {string} should have mutationAllowed false")
    fun thenCalendarItemShouldBeImmutable(externalPostId: String) {
        val item = json().path("items").first { externalPostIdOf(it) == externalPostId }
        assertFalse(item.path("mutationAllowed").asBoolean())
    }

    @Then("the social-content calendar item {string} should have origin {string}")
    fun thenCalendarItemShouldHaveOrigin(externalPostId: String, origin: String) {
        val item = json().path("items").first { externalPostIdOf(it) == externalPostId }
        assertEquals(origin, item.path("origin").asText())
    }

    @Then("the social-content response should contain externalPostId {string}")
    fun thenResponseShouldContainExternalPostId(externalPostId: String) {
        assertEquals(externalPostId, externalPostIdOf(json()))
    }

    @Then("the social-content response should contain mutationAllowed false")
    fun thenResponseShouldContainMutationAllowedFalse() {
        assertFalse(json().path("mutationAllowed").asBoolean())
    }

    @Then("the social-content reader should have received {int} calls")
    fun thenReaderShouldHaveReceivedCalls(expected: Int) {
        assertEquals(expected, state.content.readerCalls())
    }

    @Then("the social-content reader should receive cursor {string}")
    fun thenReaderShouldReceiveCursor(cursor: String) {
        assertEquals(cursor, state.content.lastCursor()?.value)
    }

    private fun socialContentPost(
        uri: String,
        authorization: String?,
        workspaceId: String?,
        body: String,
    ): EntityExchangeResult<ByteArray> {
        var request = webTestClient.post()
            .uri(uri)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
        if (authorization != null) request = request.header(HttpHeaders.AUTHORIZATION, authorization)
        if (workspaceId != null) request = request.header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
        return request.bodyValue(body).exchange().expectBody().returnResult()
    }

    private fun socialContentGet(uri: String, workspaceId: String?): EntityExchangeResult<ByteArray> {
        var request = webTestClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
        if (workspaceId != null) request = request.header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
        return request.exchange().expectBody().returnResult()
    }

    private fun responseBody(): String = String(latestResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    /**
     * Parses the latest response body as JSON.
     *
     * @return The parsed JSON response.
     */
    private fun json(): JsonNode = objectMapper.readTree(responseBody())

    /**
     * Extracts the next pagination cursor from the response and stores it for later assertions.
     */
    private fun extractNextCursor() {
        val cursorNode = json().path("nextCursor")
        lastNextCursor = if (cursorNode.isMissingNode || cursorNode.isNull) null else cursorNode.asText()
    }

    /**
     * Extracts the external post ID from a JSON node.
     *
     * @param node The JSON node containing the external post ID.
     * @return The external post ID value.
     */
    private fun externalPostIdOf(node: JsonNode): String = node.path("externalPostId").let {
        if (it.isObject) it.path("value").asText() else it.asText()
    }
}
