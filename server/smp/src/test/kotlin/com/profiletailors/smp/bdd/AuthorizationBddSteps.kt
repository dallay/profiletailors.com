package com.profiletailors.smp.bdd.glue

import java.nio.charset.StandardCharsets
import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.governance.application.AuditEventPage
import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsQuery
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse
import com.profiletailors.smp.governance.infrastructure.http.AuditEventController
import com.profiletailors.smp.integration.support.CapturingAuditHook
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipCommand
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipResult
import com.profiletailors.smp.tenancy.infrastructure.http.WorkspaceMembershipController
import com.profiletailors.smp.tenancy.infrastructure.http.WorkspaceMembershipStatusRequest
import com.profiletailors.smp.tenancy.infrastructure.http.WorkspaceOwnerRequest
import com.profiletailors.smp.tenancy.infrastructure.http.WorkspaceOwnershipController
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

class AuthorizationBddSteps(
) {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var auditHook: CapturingAuditHook

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    private var latestResult: EntityExchangeResult<ByteArray>? = null
    private var latestStatusCode: Int? = null
    private var latestLocalAuthSession: BddDatabaseSupport.LocalAuthSession? = null
    private var latestApiKeyReplacementState: BddDatabaseSupport.ApiKeyCredentialReplacementState? = null
    private var latestAuditEventsResponse: WorkspaceAuditEventsResponse? = null
    private var latestOwnershipResponse: WorkspaceOwnershipResult? = null
    private var latestMembershipStatusResponse: WorkspaceMembershipStatusResult? = null

    @Before
    fun resetScenarioState() {
        latestResult = null
        latestStatusCode = null
        latestLocalAuthSession = null
        latestApiKeyReplacementState = null
        latestAuditEventsResponse = null
        latestOwnershipResponse = null
        latestMembershipStatusResponse = null
        auditHook.reset()
        runBlocking { bddDatabaseSupport.resetDatabase() }
    }

    @Given("an entitled workspace member with the required workspace access permission")
    fun givenEntitledAuthorizedMember() = runBlocking {
        bddDatabaseSupport.seedEntitledAuthorizedMember()
    }

    @Given("an entitled workspace member without the required workspace access permission")
    fun givenEntitledUnauthorizedMember() = runBlocking {
        bddDatabaseSupport.seedEntitledMemberWithoutAccessPermission()
    }

    @Given("an authenticated user principal exists without any workspace membership or roles")
    fun givenAuthenticatedUserPrincipalExistsWithoutAnyWorkspaceMembershipOrRoles() = runBlocking {
        bddDatabaseSupport.seedAuthenticatedPrincipalOnly()
    }

    @And("an explicit direct {string} grant exists for the required permission")
    fun andAnExplicitDirectGrantExistsForTheRequiredPermission(effect: String) = runBlocking {
        bddDatabaseSupport.seedDirectGrant(effect, BddDatabaseSupport.WORKSPACE_ACCESS_PERMISSION)
    }

    @Given("a workspace member with the resource preview base permission")
    fun givenMemberWithPreviewPermission() = runBlocking {
        bddDatabaseSupport.seedMemberWithPreviewPermission()
    }

    @Given("a workspace member with the audit events read permission")
    fun givenMemberWithAuditEventsReadPermission() = runBlocking {
        bddDatabaseSupport.seedMemberWithAuditReadPermission()
    }

    @And("the member has a target scope that allows resource {string}")
    fun andMemberHasTargetScope(resourceId: String) = runBlocking {
        bddDatabaseSupport.seedTargetScope(resourceId)
    }

    @When("the client requests the current workspace access summary with a valid JWT")
    fun whenClientRequestsCurrentWorkspaceAccessSummary() {
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.accessSummaryPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests the current workspace access summary with a valid JWT but without workspace header")
    fun whenClientRequestsCurrentWorkspaceAccessSummaryWithoutWorkspaceHeader() {
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.accessSummaryPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests the preview for resource {string} with a valid JWT")
    fun whenClientRequestsResourcePreview(resourceId: String) {
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.resourcePreviewPath(resourceId))
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Then("the response status should be {int}")
    fun thenResponseStatusShouldBe(status: Int) {
        val actual = latestStatusCode ?: requireLatestResult().status.value()
        assertEquals(status, actual)
    }

    @And("the response workspaceId should be {string}")
    fun andResponseWorkspaceIdShouldBe(workspaceId: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"workspaceId\":\"$workspaceId\""), body)
    }

    @And("the response principalId should be {string}")
    fun andResponsePrincipalIdShouldBe(principalId: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"principalId\":\"$principalId\""), body)
    }

    @And("the response should include role {string}")
    fun andResponseShouldIncludeRole(role: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"roles\":[\"$role\"]"), body)
    }

    @And("the response should include permission {string}")
    fun andResponseShouldIncludePermission(permission: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"permissions\":[\"$permission\"]"), body)
    }

    @And("the resource preview response workspaceId should be {string}")
    fun andResourcePreviewWorkspaceIdShouldBe(workspaceId: String) {
        andResponseWorkspaceIdShouldBe(workspaceId)
    }

    @And("the resource preview response principalId should be {string}")
    fun andResourcePreviewPrincipalIdShouldBe(principalId: String) {
        andResponsePrincipalIdShouldBe(principalId)
    }

    @And("the resource preview response resourceId should be {string}")
    fun andResourcePreviewResourceIdShouldBe(resourceId: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"resourceId\":\"$resourceId\""), body)
    }

    @And("the resource preview response previewAllowed should be true")
    fun andResourcePreviewAllowedShouldBeTrue() {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"previewAllowed\":true"), body)
    }

    @And("the latest authorization decision should be allow because {string}")
    fun andLatestAuthorizationDecisionShouldBeAllow(reasonCode: String) {
        val fact = requireLatestAuthorizationFact()
        assertEquals(AuthorizationDecision.ALLOW.name, fact.decision)
        assertEquals(reasonCode, fact.reasonCode)
    }

    @And("the latest authorization decision should be deny because {string}")
    fun andLatestAuthorizationDecisionShouldBeDeny(reasonCode: String) {
        val fact = requireLatestAuthorizationFact()
        assertEquals(AuthorizationDecision.DENY.name, fact.decision)
        assertEquals(reasonCode, fact.reasonCode)
    }

    @Given("a browser submits valid local registration details")
    fun givenBrowserSubmitsValidLocalRegistrationDetails() {
        latestLocalAuthSession = null
    }

    @Given("a previously registered local user session exists")
    fun givenPreviouslyRegisteredLocalUserSessionExists() {
        if (latestLocalAuthSession == null) {
            registerLocalUser(email = "owner@example.com", username = "owner")
        }
    }

    @When("the client registers a local user")
    fun whenClientRegistersLocalUser() {
        registerLocalUser(email = "yuniel@example.com", username = "yuniel")
    }

    @When("the client refreshes the local user session")
    fun whenClientRefreshesLocalUserSession() {
        val session = requireNotNull(latestLocalAuthSession) { "Expected a previously registered local auth session" }
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthRefreshPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, session.refreshCookie)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client logs out the local user session")
    fun whenClientLogsOutLocalUserSession() {
        val session = requireNotNull(latestLocalAuthSession) { "Expected a previously registered local auth session" }
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthLogoutPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.COOKIE, session.refreshCookie)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @And("the auth response should include an access token")
    fun andAuthResponseShouldIncludeAccessToken() {
        val body = requireResponseBodyText()
        assertTrue(Regex("\"accessToken\":\"[^\"]+\"").containsMatchIn(body), body)
    }

    @And("the auth response should include email {string}")
    fun andAuthResponseShouldIncludeEmail(email: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"email\":\"$email\""), body)
    }

    @And("the response should set a refresh cookie")
    fun andResponseShouldSetRefreshCookie() {
        val cookie = requireLatestResult().responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
        assertTrue(!cookie.isNullOrBlank(), "Expected Set-Cookie header with refresh cookie")
    }

    @And("the response should clear the refresh cookie")
    fun andResponseShouldClearRefreshCookie() {
        val cookie = requireLatestResult().responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
        assertTrue(!cookie.isNullOrBlank() && cookie.contains("Max-Age=0"), "Expected cleared refresh cookie")
    }

    @Given("an entitled authorized service-account principal exists")
    fun givenEntitledAuthorizedServiceAccountPrincipalExists() = runBlocking {
        bddDatabaseSupport.seedAuthorizedServiceAccount(entitled = true)
    }

    @Given("an entitled authorized service-account principal exists with revoked credential state")
    fun givenEntitledAuthorizedServiceAccountPrincipalExistsWithRevokedCredentialState() = runBlocking {
        bddDatabaseSupport.seedAuthorizedServiceAccount(entitled = true, credentialStatus = "REVOKED")
    }

    @Given("an entitled authorized API-key principal exists")
    fun givenEntitledAuthorizedApiKeyPrincipalExists() = runBlocking {
        bddDatabaseSupport.seedAuthorizedApiKeyPrincipal(entitled = true)
    }

    @When("the client requests the current workspace access summary with a service-account bearer token")
    fun whenClientRequestsCurrentWorkspaceAccessSummaryWithServiceAccountBearerToken() {
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.accessSummaryPath())
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the client requests the current workspace access summary with the predecessor API key")
    fun whenClientRequestsCurrentWorkspaceAccessSummaryWithPredecessorApiKey() {
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.accessSummaryPath())
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the active API key is replaced")
    fun whenTheActiveApiKeyIsReplaced() = runBlocking {
        latestApiKeyReplacementState = bddDatabaseSupport.replaceActiveApiKeyCredential()
    }

    @When("the client requests the current workspace access summary with the successor API key")
    fun whenClientRequestsCurrentWorkspaceAccessSummaryWithSuccessorApiKey() {
        val successor = requireNotNull(latestApiKeyReplacementState) { "Expected a replaced API key state" }
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.accessSummaryPath())
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${successor.successorPlaintextApiKey}")
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Given("a stubbed audit events response is configured")
    fun givenStubbedAuditEventsResponseIsConfigured() {
        latestAuditEventsResponse = WorkspaceAuditEventsResponse(
            workspaceId = BddDatabaseSupport.WORKSPACE_ID,
            items = emptyList(),
            page = AuditEventPage(
                cursor = "cursor-token",
                limit = 10,
                returned = 0,
                hasMore = false,
                nextCursor = null,
            ),
        )
    }

    @When("the client queries workspace audit events with filters and pagination")
    fun whenTheClientQueriesWorkspaceAuditEventsWithFiltersAndPagination() = runBlocking {
        // Seed audit event that matches the query filters
        bddDatabaseSupport.seedAuditEventRecords()
        
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri { builder ->
                builder.path("/api/governance/audit-events")
                    .queryParam("targetType", "WORKSPACE_OWNER")
                    .queryParam("action", "workspace.owner.add")
                    .queryParam("eventType", "MUTATION")
                    .queryParam("actorPrincipalId", "owner-1")
                    .queryParam("limit", 10)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @And("the audit events response workspaceId should be {string}")
    fun andAuditEventsResponseWorkspaceIdShouldBe(workspaceId: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"workspaceId\":\"$workspaceId\""), body)
    }

    @And("the audit events response returned count should be {int}")
    fun andAuditEventsResponseReturnedCountShouldBe(count: Int) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"returned\":$count"), "Expected body to contain '\"returned\":$count' but got: $body")
    }

    @Given("a stubbed workspace ownership response is configured")
    fun givenStubbedWorkspaceOwnershipResponseIsConfigured() {
        latestOwnershipResponse = WorkspaceOwnershipResult("workspace-1", listOf("owner-1", "owner-2"))
    }

    @When("the client transfers workspace ownership to principal {string}")
    fun whenTheClientTransfersWorkspaceOwnershipToPrincipal(principalId: String) = runBlocking {
        val response = requireNotNull(latestOwnershipResponse) { "Expected a stubbed ownership response" }
        val controller = WorkspaceOwnershipController(object : Mediator {
            override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = error("Not used")
            override suspend fun <TCommand : Command> send(command: TCommand) = error("Not used")
            override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
                assertEquals(TransferWorkspaceOwnershipCommand(targetPrincipalId = principalId), command)
                @Suppress("UNCHECKED_CAST")
                return response as TResult
            }
            override suspend fun <T : Notification> publish(notification: T) = error("Not used")
            override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) = error("Not used")
        })
        latestOwnershipResponse = controller.transferOwnership(WorkspaceOwnerRequest(principalId))
        latestStatusCode = 200
    }

    @Then("the tenancy response status should be {int}")
    fun thenTheTenancyResponseStatusShouldBe(status: Int) {
        thenResponseStatusShouldBe(status)
    }

    @And("the ownership response workspaceId should be {string}")
    fun andTheOwnershipResponseWorkspaceIdShouldBe(workspaceId: String) {
        assertEquals(workspaceId, requireNotNull(latestOwnershipResponse).workspaceId)
    }

    @And("the ownership response should include owner principal {string}")
    fun andTheOwnershipResponseShouldIncludeOwnerPrincipal(principalId: String) {
        assertTrue(requireNotNull(latestOwnershipResponse).ownerPrincipalIds.contains(principalId))
    }

    @Given("a stubbed workspace membership status response is configured")
    fun givenStubbedWorkspaceMembershipStatusResponseIsConfigured() {
        latestMembershipStatusResponse = WorkspaceMembershipStatusResult(
            workspaceId = "workspace-1",
            principalId = "member-2",
            status = WorkspaceMembershipStatus.SUSPENDED,
        )
    }

    @When("the client updates workspace membership status for principal {string} to {string}")
    fun whenTheClientUpdatesWorkspaceMembershipStatusForPrincipal(principalId: String, status: String) = runBlocking {
        val response = requireNotNull(latestMembershipStatusResponse) { "Expected a stubbed membership status response" }
        val controller = WorkspaceMembershipController(object : Mediator {
            override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = error("Not used")
            override suspend fun <TCommand : Command> send(command: TCommand) = error("Not used")
            override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
                assertEquals(
                    UpdateWorkspaceMembershipStatusCommand(
                        targetPrincipalId = principalId,
                        targetStatus = WorkspaceMembershipStatus.valueOf(status),
                    ),
                    command,
                )
                @Suppress("UNCHECKED_CAST")
                return response as TResult
            }
            override suspend fun <T : Notification> publish(notification: T) = error("Not used")
            override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) = error("Not used")
        })
        latestMembershipStatusResponse = controller.updateMembershipStatus(
            principalId,
            WorkspaceMembershipStatusRequest(status),
        )
        latestStatusCode = 200
    }

    @And("the membership status response workspaceId should be {string}")
    fun andTheMembershipStatusResponseWorkspaceIdShouldBe(workspaceId: String) {
        assertEquals(workspaceId, requireNotNull(latestMembershipStatusResponse).workspaceId)
    }

    @And("the membership status response principalId should be {string}")
    fun andTheMembershipStatusResponsePrincipalIdShouldBe(principalId: String) {
        assertEquals(principalId, requireNotNull(latestMembershipStatusResponse).principalId)
    }

    @And("the membership status response status should be {string}")
    fun andTheMembershipStatusResponseStatusShouldBe(status: String) {
        assertEquals(status, requireNotNull(latestMembershipStatusResponse).status.name)
    }

    private fun registerLocalUser(email: String, username: String) {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthRegisterPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                    "username" to username,
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()

        val body = requireResponseBodyText()
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("Missing access token in registration response")
        val refreshCookie = requireLatestResult().responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
            ?.substringBefore(';')
            ?: error("Missing refresh cookie in registration response")
        latestLocalAuthSession = BddDatabaseSupport.LocalAuthSession(accessToken, refreshCookie)
    }

    private fun requireLatestResult(): EntityExchangeResult<ByteArray> =
        checkNotNull(latestResult) { "No HTTP result has been captured for the current scenario" }

    private fun requireResponseBodyText(): String =
        String(requireLatestResult().responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private fun requireLatestAuthorizationFact() =
        auditHook.facts.lastOrNull().also { assertNotNull(it, "Expected at least one authorization audit fact") }!!
}
