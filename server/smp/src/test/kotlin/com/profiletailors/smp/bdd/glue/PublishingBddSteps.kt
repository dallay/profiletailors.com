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
import java.time.Instant

class PublishingBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    @Autowired
    private lateinit var providerCatalogPolicyControl: BddProviderCatalogPolicyControl

    private var latestPublishingResponse: EntityExchangeResult<ByteArray>? = null
    private var latestPublicationId: String? = null
    private var currentSocialConnectionId: String? = null
    private var currentSocialAccountId: String? = null
    private var currentPublicationId: String? = null
    private var currentRecurringScheduleId: String? = null
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Before
    fun resetPublishingState() {
        latestPublishingResponse = null
        latestPublicationId = null
        currentSocialConnectionId = null
        currentSocialAccountId = null
        currentPublicationId = null
        currentRecurringScheduleId = null
        providerCatalogPolicyControl.reset()
    }

    @Given("a connected LinkedIn social account exists")
    fun givenConnectedLinkedInSocialAccountExists() = runBlocking {
        bddDatabaseSupport.seedWorkspace()
        bddDatabaseSupport.seedSocialConnection("social-conn-1", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            accountId = "social-acc-1",
            connectionId = "social-conn-1",
            provider = "LINKEDIN",
            providerAccountId = "linkedin-profile-1",
            accountKind = "PERSONAL_PROFILE",
            displayName = "Yuniel Acosta",
        )
        currentSocialConnectionId = "social-conn-1"
        currentSocialAccountId = "social-acc-1"
    }

    @Given("a draft publication exists")
    fun givenDraftPublicationExists() = runBlocking {
        if (currentSocialAccountId == null) {
            givenConnectedLinkedInSocialAccountExists()
        }
        val socialAccountId = requireNotNull(currentSocialAccountId) {
            "No social account seeded. Ensure 'a connected LinkedIn social account exists' step runs first."
        }
        bddDatabaseSupport.seedDraftPublication(
            publicationId = "pub-bdd-draft-1",
            socialAccountId = socialAccountId,
            title = "Draft Post",
            bodyText = "Draft body",
        )
        currentPublicationId = "pub-bdd-draft-1"
    }

    @Given("a scheduled publication exists")
    fun givenScheduledPublicationExists() = runBlocking {
        if (currentSocialAccountId == null) {
            givenConnectedLinkedInSocialAccountExists()
        }
        val socialAccountId = requireNotNull(currentSocialAccountId) {
            "No social account seeded. Ensure 'a connected LinkedIn social account exists' step runs first."
        }
        bddDatabaseSupport.seedScheduledPublication(
            publicationId = "pub-bdd-scheduled-1",
            socialAccountId = socialAccountId,
            scheduledFor = Instant.parse("2026-08-01T12:00:00Z"),
            title = "Scheduled Post",
            bodyText = "Scheduled body",
        )
        currentPublicationId = "pub-bdd-scheduled-1"
    }

    @Given("a draft and a scheduled publication exist")
    fun givenDraftAndScheduledPublicationsExist() = runBlocking {
        if (currentSocialAccountId == null) {
            givenConnectedLinkedInSocialAccountExists()
        }
        val socialAccountId = requireNotNull(currentSocialAccountId) {
            "No social account seeded. Ensure 'a connected LinkedIn social account exists' step runs first."
        }
        bddDatabaseSupport.seedDraftPublication(
            publicationId = "pub-bdd-draft-2",
            socialAccountId = socialAccountId,
            title = "Draft Post 2",
            bodyText = "Draft body 2",
        )
        bddDatabaseSupport.seedScheduledPublication(
            publicationId = "pub-bdd-scheduled-2",
            socialAccountId = socialAccountId,
            scheduledFor = Instant.parse("2026-08-01T14:00:00Z"),
            title = "Scheduled Post 2",
            bodyText = "Scheduled body 2",
        )
    }

    @When("the client creates a publication with title {string} and body {string}")
    fun whenClientCreatesPublication(title: String?, body: String?) = runBlocking {
        if (currentSocialAccountId == null) {
            givenConnectedLinkedInSocialAccountExists()
        }
        val bodyMap = mutableMapOf<String, Any?>(
            "socialAccountId" to currentSocialAccountId,
            "bodyText" to body,
            "scheduleMode" to "NOW",
        )
        if (title != null) bodyMap["title"] = title
        val json = objectMapper.writeValueAsString(bodyMap)
        latestPublishingResponse = webTestClient.post()
            .uri(bddDatabaseSupport.publishingPublicationsPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client creates a scheduled publication for {string} with title {string} and body {string}")
    fun whenClientCreatesScheduledPublication(scheduledFor: String, title: String?, body: String?) = runBlocking {
        if (currentSocialAccountId == null) {
            givenConnectedLinkedInSocialAccountExists()
        }
        val bodyMap = mutableMapOf<String, Any?>(
            "socialAccountId" to currentSocialAccountId,
            "bodyText" to body,
            "scheduleMode" to "SCHEDULED_AT",
            "scheduledFor" to scheduledFor,
        )
        if (title != null) bodyMap["title"] = title
        val json = objectMapper.writeValueAsString(bodyMap)
        latestPublishingResponse = webTestClient.post()
            .uri(bddDatabaseSupport.publishingPublicationsPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client edits the publication with new title {string}")
    fun whenClientEditsPublication(newTitle: String) = runBlocking {
        val pubId = currentPublicationId ?: extractPublicationIdFromResponse()
        val bodyMap = mapOf<String, Any?>(
            "socialAccountId" to currentSocialAccountId,
            "title" to newTitle,
            "bodyText" to "Updated body",
            "scheduleMode" to "NOW",
        )
        val json = objectMapper.writeValueAsString(bodyMap)
        latestPublishingResponse = webTestClient.patch()
            .uri("${bddDatabaseSupport.publishingPublicationsPath()}/$pubId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client cancels the publication")
    fun whenClientCancelsPublication() = runBlocking {
        val pubId = currentPublicationId ?: extractPublicationIdFromResponse()
        latestPublishingResponse = webTestClient.post()
            .uri("${bddDatabaseSupport.publishingPublicationsPath()}/$pubId/cancel")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client deletes the publication")
    fun whenClientDeletesPublication() = runBlocking {
        val pubId = currentPublicationId ?: extractPublicationIdFromResponse()
        latestPublishingResponse = webTestClient.delete()
            .uri("${bddDatabaseSupport.publishingPublicationsPath()}/$pubId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client quick-creates a publication for {string} with title {string} and body {string}")
    fun whenClientQuickCreatesPublication(scheduledFor: String, title: String?, body: String?) = runBlocking {
        if (currentSocialAccountId == null) {
            givenConnectedLinkedInSocialAccountExists()
        }
        val bodyMap = mutableMapOf<String, Any?>(
            "socialAccountId" to currentSocialAccountId,
            "scheduledFor" to scheduledFor,
        )
        if (title != null) bodyMap["title"] = title
        if (body != null) bodyMap["bodyText"] = body
        val json = objectMapper.writeValueAsString(bodyMap)
        latestPublishingResponse = webTestClient.post()
            .uri("${bddDatabaseSupport.publishingPublicationsPath()}/quick-create")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client lists publications")
    fun whenClientListsPublications() {
        latestPublishingResponse = webTestClient.get()
            .uri(bddDatabaseSupport.publishingPublicationsPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client creates a daily recurring schedule")
    fun whenClientCreatesDailyRecurringSchedule() {
        val templatePostId = requireNotNull(currentPublicationId)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "templatePostId" to templatePostId,
                "frequency" to "daily",
                "interval" to 1,
                "startsAt" to "2026-08-02T09:00:00Z",
                "endDate" to "2026-08-04",
                "timezone" to "UTC",
            ),
        )
        latestPublishingResponse = webTestClient.post()
            .uri(bddDatabaseSupport.recurringSchedulesPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
        currentRecurringScheduleId = extractJsonString("id")
    }

    @When("the client pauses the recurring schedule")
    fun whenClientPausesRecurringSchedule() {
        val scheduleId = requireNotNull(currentRecurringScheduleId)
        val body = objectMapper.writeValueAsString(mapOf("status" to "paused"))
        latestPublishingResponse = webTestClient.patch()
            .uri("${bddDatabaseSupport.recurringSchedulesPath()}/$scheduleId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client lists recurring schedules")
    fun whenClientListsRecurringSchedules() {
        latestPublishingResponse = webTestClient.get()
            .uri(bddDatabaseSupport.recurringSchedulesPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the recurring response should contain a schedule id")
    fun thenRecurringResponseShouldContainScheduleId() {
        assertNotNull(currentRecurringScheduleId)
        assertTrue(requirePublishingResponseBody().contains("\"id\":"))
    }

    @Then("the recurring response status should be {string}")
    fun thenRecurringResponseStatusShouldBe(status: String) {
        assertTrue(requirePublishingResponseBody().contains(""""status":"${status.uppercase()}"""))
    }

    @Then("at least {int} recurring publications should be scheduled")
    fun thenAtLeastRecurringPublicationsShouldBeScheduled(expected: Int) = runBlocking {
        assertTrue(bddDatabaseSupport.countScheduledPublications() >= expected)
    }

    private fun extractJsonString(field: String): String? {
        val body = requirePublishingResponseBody()
        return Regex(""""$field":"([^"]+)"""").find(body)?.groupValues?.get(1)
    }

    private fun requirePublishingResponseBody(): String = String(
        requireNotNull(latestPublishingResponse).responseBody ?: error("Missing response body"),
        StandardCharsets.UTF_8,
    )

    @When("the client lists connected channels")
    fun whenClientListsConnectedChannels() {
        latestPublishingResponse = webTestClient.get()
            .uri(bddDatabaseSupport.publishingChannelsPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client lists configured providers")
    fun whenClientListsConfiguredProviders() {
        latestPublishingResponse = webTestClient.get()
            .uri(bddDatabaseSupport.publishingChannelProvidersPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Given("LinkedIn provider policy is hidden for the workspace")
    fun givenLinkedInProviderPolicyIsHiddenForWorkspace() {
        providerCatalogPolicyControl.setHidden(BddDatabaseSupport.WORKSPACE_ID)
    }

    @Given("LinkedIn provider policy is entitlement locked for the workspace")
    fun givenLinkedInProviderPolicyIsEntitlementLockedForWorkspace() {
        providerCatalogPolicyControl.setEntitlementLocked(BddDatabaseSupport.WORKSPACE_ID)
    }

    @Given("LinkedIn provider policy is capacity locked for the workspace")
    fun givenLinkedInProviderPolicyIsCapacityLockedForWorkspace() {
        providerCatalogPolicyControl.setCapacityLocked(BddDatabaseSupport.WORKSPACE_ID)
    }

    @Given("LinkedIn provider policy is available for workspace {string}")
    fun givenLinkedInProviderPolicyIsAvailableForWorkspace(workspaceId: String) {
        providerCatalogPolicyControl.setAvailable(workspaceId)
    }

    @Given("LinkedIn provider policy is entitlement locked for workspace {string}")
    fun givenLinkedInProviderPolicyIsEntitlementLockedForWorkspace(workspaceId: String) {
        providerCatalogPolicyControl.setEntitlementLocked(workspaceId)
    }

    @When("the client lists configured providers for workspace {string}")
    fun whenClientListsConfiguredProvidersForWorkspace(workspaceId: String) {
        latestPublishingResponse = webTestClient.get()
            .uri(bddDatabaseSupport.publishingChannelProvidersPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client initiates a LinkedIn connection")
    fun whenClientInitiatesLinkedInConnection() {
        latestPublishingResponse = initiateLinkedInConnection(
            authorization = BddDatabaseSupport.VERIFIED_USER_BEARER,
            workspaceId = BddDatabaseSupport.WORKSPACE_ID,
        )
    }

    @When("the unauthenticated client initiates a LinkedIn connection")
    fun whenUnauthenticatedClientInitiatesLinkedInConnection() {
        latestPublishingResponse =
            initiateLinkedInConnection(authorization = null, workspaceId = BddDatabaseSupport.WORKSPACE_ID)
    }

    @When("the client initiates a LinkedIn connection without workspace context")
    fun whenClientInitiatesLinkedInConnectionWithoutWorkspaceContext() {
        latestPublishingResponse = initiateLinkedInConnection(
            authorization = BddDatabaseSupport.VERIFIED_USER_BEARER,
            workspaceId = null,
        )
    }

    @Then("the publishing response status should be {int}")
    fun thenPublishingResponseStatusShouldBe(status: Int) {
        val response = latestPublishingResponse ?: error("No publishing response captured")
        val body = String(response.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        val actualStatus = response.status.value()
        System.err.println("DEBUG_STATUS_CHECK: expected=$status, actual=$actualStatus, body=$body")
        assertEquals(status, actualStatus) {
            "Expected status $status but got $actualStatus. Response body: $body"
        }
    }

    @Then("the response should contain a publicationId")
    fun thenResponseShouldContainPublicationId() {
        val body = publishingResponseBodyText()
        val publicationId = parsePublishingResponseField<String>("publicationId")
        assertNotNull(publicationId, "Expected publicationId in response: $body")
        latestPublicationId = publicationId
    }

    @Then("the publication status should be {string}")
    fun thenPublicationStatusShouldBe(expectedStatus: String) {
        val actualStatus: String = parsePublishingResponseField("status")
        assertEquals(expectedStatus, actualStatus)
    }

    @Then("the response title should be {string}")
    fun thenResponseTitleShouldBe(expectedTitle: String) {
        val actualTitle: String? = parsePublishingResponseField("title")
        assertEquals(expectedTitle, actualTitle)
    }

    @Then("the response should contain {int} publications")
    fun thenResponseShouldContainPublications(expectedCount: Int) {
        val body = publishingResponseBodyText()
        val total = parsePublishingResponseField<Int>("total")
        assertEquals(expectedCount, total)
    }

    @Then("the channels list should be empty")
    fun thenChannelsListShouldBeEmpty() {
        val channels: List<*> = parsePublishingResponseField("channels")
        assertTrue(channels.isEmpty()) {
            "Expected empty channels list but got: $channels (body: ${publishingResponseBodyText()})"
        }
    }

    @Then("the channels list should contain the existing LinkedIn channel")
    fun thenChannelsListShouldContainExistingLinkedInChannel() {
        val channels: List<Map<String, Any?>> = parsePublishingResponseField("channels")
        assertTrue(channels.any { it["provider"] == "LINKEDIN" }) {
            "Expected the existing LinkedIn channel but got: $channels (body: ${publishingResponseBodyText()})"
        }
    }

    @Then("the providers list should contain {string}")
    fun thenProvidersListShouldContain(provider: String) {
        val providers: List<Map<String, Any?>> = parsePublishingResponseField("providers")
        assertTrue(providers.any { it["name"] == provider }) {
            "Expected '$provider' in providers list but got: $providers (body: ${publishingResponseBodyText()})"
        }
    }

    @Then("the catalog should contain available LinkedIn personal profile without secrets or plans")
    fun thenCatalogShouldContainAvailableLinkedInPersonalProfileWithoutSecretsOrPlans() {
        val providers: List<Map<String, Any?>> = parsePublishingResponseField("providers")
        val linkedIn = providers.single { it["provider"] == "linkedin" }

        assertEquals(listOf("PERSONAL_PROFILE"), linkedIn["accountKinds"])
        assertEquals("AVAILABLE", linkedIn["state"])
        assertEquals(null, linkedIn["reason"])
        assertEquals(null, linkedIn["channelLimit"])
        assertEquals(true, linkedIn["canConnectMore"])
        assertTrue(!publishingResponseBodyText().contains("clientSecret"))
        assertTrue(!publishingResponseBodyText().contains("plan"))
    }

    @Then("the catalog should omit LinkedIn")
    fun thenCatalogShouldOmitLinkedIn() {
        val providers: List<Map<String, Any?>> = parsePublishingResponseField("providers")
        assertTrue(providers.none { it["provider"] == "linkedin" }) {
            "Expected LinkedIn to be omitted but got: $providers (body: ${publishingResponseBodyText()})"
        }
    }

    @Then("the catalog should contain LinkedIn locked for {string}")
    fun thenCatalogShouldContainLinkedInLockedFor(reason: String) {
        val linkedIn = linkedInCatalogItem()
        assertEquals("LOCKED", linkedIn["state"])
        assertEquals(reason, linkedIn["reason"])
    }

    @Then("the catalog should contain LinkedIn locked for {string} with {int} connected channel")
    fun thenCatalogShouldContainLinkedInLockedForWithConnectedChannel(reason: String, connectedChannelCount: Int) {
        thenCatalogShouldContainLinkedInLockedFor(reason)
        assertEquals(connectedChannelCount, linkedInCatalogItem()["connectedChannelCount"])
        assertEquals(false, linkedInCatalogItem()["canConnectMore"])
    }

    @Then("the OAuth denial should report {string} without authorization details")
    fun thenOAuthDenialShouldReportReasonWithoutAuthorizationDetails(reason: String) {
        val body = publishingResponseBodyText()
        assertTrue(body.contains(reason)) { "Expected policy reason $reason in response: $body" }
        assertTrue(!body.contains("authorizationUrl")) { "OAuth denial must not expose an authorization URL: $body" }
        assertTrue(!body.contains("\"state\"")) { "OAuth denial must not expose OAuth state: $body" }
    }

    private fun extractPublicationIdFromResponse(): String =
        latestPublicationId ?: error("No publication ID available from previous response")

    private fun publishingResponseBodyText(): String =
        String(latestPublishingResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private fun linkedInCatalogItem(): Map<String, Any?> {
        val providers: List<Map<String, Any?>> = parsePublishingResponseField("providers")
        return providers.single { it["provider"] == "linkedin" }
    }

    private fun initiateLinkedInConnection(
        authorization: String?,
        workspaceId: String?,
    ): EntityExchangeResult<ByteArray> {
        var request = webTestClient.post()
            .uri("/api/publishing/linkedin/connections/initiate")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization)
        }
        if (workspaceId != null) {
            request = request.header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
        }
        return request
            .bodyValue("""{"redirectUri":"https://app.example.com/callback"}""")
            .exchange()
            .expectBody()
            .returnResult()
    }

    private inline fun <reified T> parsePublishingResponseField(field: String): T {
        val body = publishingResponseBodyText()
        return try {
            val map: Map<String, Any?> = objectMapper.readValue(body)
            val raw = map[field]
            when {
                raw == null && null is T ->
                    @Suppress("UNCHECKED_CAST")
                    null
                        as T
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
