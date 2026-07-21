package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse
import com.profiletailors.smp.governance.domain.AuditEventPage
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets
import java.time.Instant

@Suppress("LargeClass")
class AuthorizationBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var auditHook: CapturingAuditHook

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    @Autowired
    private lateinit var recordingEmailSender: RecordingEmailSender

    private var latestResult: EntityExchangeResult<ByteArray>? = null
    private var latestStatusCode: Int? = null
    private var latestLocalAuthSession: BddDatabaseSupport.LocalAuthSession? = null
    private var latestApiKeyReplacementState: BddDatabaseSupport.ApiKeyCredentialReplacementState? = null
    private var latestAuditEventsResponse: WorkspaceAuditEventsResponse? = null
    private var latestOwnershipResponse: WorkspaceOwnershipResult? = null
    private var latestMembershipStatusResponse: WorkspaceMembershipStatusResult? = null
    private var pendingRegistrationEmail: String? = null
    private var pendingRegistrationResult: EntityExchangeResult<ByteArray>? = null
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private var latestPublishingResponse: EntityExchangeResult<ByteArray>? = null
    private var latestPublicationId: String? = null
    private var currentSocialConnectionId: String? = null
    private var currentSocialAccountId: String? = null
    private var currentPublicationId: String? = null

    @Before
    fun resetScenarioState() {
        latestResult = null
        latestStatusCode = null
        latestLocalAuthSession = null
        latestApiKeyReplacementState = null
        latestAuditEventsResponse = null
        latestOwnershipResponse = null
        latestMembershipStatusResponse = null
        pendingRegistrationEmail = null
        pendingRegistrationResult = null
        latestPublishingResponse = null
        latestPublicationId = null
        currentSocialConnectionId = null
        currentSocialAccountId = null
        currentPublicationId = null
        recordingEmailSender.reset()
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
            registerLocalUserLegacy(email = "owner@example.com")
        }
    }

    @Given("a previously registered local user session exists without verified email")
    fun givenPreviouslyRegisteredLocalUserSessionExistsWithoutVerifiedEmail() {
        registerLocalUser(email = "pending-media@example.com", verifyEmail = false, login = true)
        runBlocking { bddDatabaseSupport.markEmailPENDING("pending-media@example.com") }
    }

    @Given("a previously registered local user session exists with workspace membership")
    fun givenPreviouslyRegisteredLocalUserSessionExistsWithWorkspaceMembership() {
        givenPreviouslyRegisteredLocalUserSessionExists()
    }

    @Given("a previously registered local user exists")
    fun givenPreviouslyRegisteredLocalUserExists() {
        registerLocalUser(email = "owner@example.com", login = false, verifyEmail = true)
    }

    @Given("a previously registered local user exists with email {string}")
    fun givenPreviouslyRegisteredLocalUserExistsWithEmail(email: String) {
        registerLocalUser(email = email, login = false, verifyEmail = true)
    }

    @Given("an existing user with email {string}")
    fun givenExistingUserWithEmail(email: String) {
        registerLocalUser(email = email, login = false, verifyEmail = false)
    }

    @Given("the user has no active session")
    fun givenUserHasNoActiveSession() {
        latestLocalAuthSession = null
    }

    @Given("the user has an expired session")
    fun givenUserHasExpiredSession() {
        latestLocalAuthSession = BddDatabaseSupport.LocalAuthSession(
            accessToken = "expired-token",
            refreshCookie = "refresh_token=invalid; Path=/api/auth",
        )
    }

    @When("the client registers a local user")
    fun whenClientRegistersLocalUser() {
        registerLocalUser(email = "yuniel@example.com", login = false)
    }

    @When("the client registers a local user without verifying email")
    fun whenClientRegistersLocalUserWithoutVerifyingEmail() {
        registerLocalUser(email = "yuniel@example.com", verifyEmail = false, login = false)
    }

    @When("the client requests the current authenticated user profile")
    fun whenClientRequestsCurrentUserProfile() {
        latestStatusCode = null
        latestResult = webTestClient.get()
            .uri(bddDatabaseSupport.currentUserProfilePath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @Given("the pending user has an active workspace membership")
    fun givenPendingUserHasActiveWorkspaceMembership() = runBlocking {
        bddDatabaseSupport.seedJwtAuthenticatedUserWithWorkspace(emailStatus = "PENDING")
    }

    @Given("the verified user has an active workspace membership")
    fun givenVerifiedUserHasActiveWorkspaceMembership() = runBlocking {
        bddDatabaseSupport.seedJwtAuthenticatedUserWithWorkspace(emailStatus = "VERIFIED")
    }

    @And("the current user profile should include emailStatus {string}")
    fun andCurrentUserProfileShouldIncludeEmailStatus(emailStatus: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"emailStatus\":\"$emailStatus\""), body)
    }

    @And("the verification email sender should have received {int} message for {string}")
    fun andVerificationEmailSenderShouldHaveReceivedMessages(count: Int, email: String) {
        val matches = recordingEmailSender.messages.filter { it.to == email }
        assertEquals(
            count,
            matches.size,
            "Expected $count message(s) to $email, got ${matches.size}: ${recordingEmailSender.messages}",
        )
        assertTrue(matches.isNotEmpty(), "Expected at least one verification email")
        val latest = matches.last()
        assertTrue(
            latest.subject.contains("Verify", ignoreCase = true),
            "Expected verification subject, got '${latest.subject}'",
        )
    }

    @When("the client attempts to register a media asset")
    fun whenClientAttemptsToRegisterMediaAsset() {
        val workspaceId = BddDatabaseSupport.WORKSPACE_ID
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.mediaAssetsPath())
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, workspaceId)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "sourceType" to "UPLOADED",
                    "mediaType" to "image/png",
                    "originalFilename" to "e2e-bdd-asset.png",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()
    }

    @And("the problem response should include code {string}")
    fun andProblemResponseShouldIncludeCode(code: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains(""""code":"$code""""), body)
    }

    @And("no media asset should be persisted")
    fun andNoMediaAssetShouldBePersisted() = runBlocking {
        val count: Long = bddDatabaseSupport.countMediaAssets()
        assertEquals(0L, count, "Expected zero media_assets rows, found $count")
    }

    @And("one media asset should be persisted")
    fun andOneMediaAssetShouldBePersisted() = runBlocking {
        val count: Long = bddDatabaseSupport.countMediaAssets()
        assertEquals(1L, count, "Expected one media_assets row, found $count")
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

    @When("the client resends the verification email for {string}")
    fun whenClientResendsVerificationEmail(email: String) {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthResendPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(mapOf("email" to email))
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

    @When("the client logs out without a valid session")
    fun whenClientLogsOutWithoutValidSession() {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthLogoutPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .exchange()
            .expectBody()
            .returnResult()
    }

    @When("the user submits invalid credentials")
    fun whenUserSubmitsInvalidCredentials() {
        submitLogin(email = "owner@example.com", password = "wrongpassword")
    }

    @When("the user submits credentials for {string}")
    fun whenUserSubmitsCredentialsFor(email: String) {
        submitLogin(email = email, password = "password123")
    }

    @When("the user submits credentials with email {string}")
    fun whenUserSubmitsCredentialsWithEmail(email: String) {
        submitLogin(email = email, password = "password123")
    }

    @When("the client registers with email {string}")
    fun whenClientRegistersWithEmail(email: String) {
        submitRegistration(email = email)
    }

    @When("the client registers with password {string}")
    fun whenClientRegistersWithPassword(password: String) {
        submitRegistration(email = "newuser@example.com", password = password)
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

    @And("the auth response should include either email {string} or {string}")
    fun andAuthResponseShouldIncludeEitherEmail(email1: String, email2: String) {
        val body = requireResponseBodyText()
        assertTrue(
            body.contains(""""email":"$email1"""") || body.contains(""""email":"$email2""""),
            body,
        )
    }

    @And("the latest response body should include accessToken")
    fun andLatestResponseBodyShouldIncludeAccessToken() {
        val body = requireResponseBodyText()
        assertTrue(Regex("\"accessToken\":\"[^\"]+\"").containsMatchIn(body), body)
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

    @And("the auth response should include emailStatus {string}")
    fun andAuthResponseShouldIncludeEmailStatus(emailStatus: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains("\"emailStatus\":\"$emailStatus\""), body)
    }

    @And("the response should not set a refresh cookie")
    fun andResponseShouldNotSetRefreshCookie() {
        val cookie = requireLatestResult().responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
        assertTrue(cookie.isNullOrBlank(), "Expected no Set-Cookie header for registration")
    }

    @And("the problem response should include detail {string}")
    fun andProblemResponseShouldIncludeDetail(detail: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains(""""detail":"$detail""""), body)
    }

    @And("the response body should contain {string}")
    fun andResponseBodyShouldContain(text: String) {
        val body = requireResponseBodyText()
        assertTrue(body.contains(text), body)
    }

    @Then("the response should contain a workspaceId")
    fun thenResponseShouldContainWorkspaceId() {
        val body = requireResponseBodyText()
        assertTrue(body.contains(""""workspaceId":"""), body)
    }

    @Then("the email in the response should be normalized to lowercase")
    fun thenEmailInResponseShouldBeNormalizedToLowercase() {
        val body = requireResponseBodyText()
        assertTrue(Regex(""""email":"[a-z@.]+"""").containsMatchIn(body), body)
        assertFalse(body.contains(""""email":"Test@Example.COM""""), body)
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
        assertTrue(
            body.contains("\"returned\":$count"),
            "Expected body to contain '\"returned\":$count' but got: $body",
        )
    }

    @Given("a stubbed workspace ownership response is configured")
    fun givenStubbedWorkspaceOwnershipResponseIsConfigured() {
        latestOwnershipResponse = WorkspaceOwnershipResult("workspace-1", listOf("owner-1", "owner-2"))
    }

    @When("the client transfers workspace ownership to principal {string}")
    fun whenTheClientTransfersWorkspaceOwnershipToPrincipal(principalId: String) = runBlocking {
        val response = requireNotNull(latestOwnershipResponse) { "Expected a stubbed ownership response" }
        val controller = WorkspaceOwnershipController(object : Mediator {
            override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse =
                error("Not used")
            override suspend fun <TCommand : Command> send(command: TCommand) = error("Not used")
            override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
                assertEquals(TransferWorkspaceOwnershipCommand(targetPrincipalId = principalId), command)
                @Suppress("UNCHECKED_CAST")
                return response as TResult
            }
            override suspend fun <T : Notification> publish(notification: T) = error("Not used")
            override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) =
                error("Not used")
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
        val response =
            requireNotNull(latestMembershipStatusResponse) { "Expected a stubbed membership status response" }
        val controller = WorkspaceMembershipController(object : Mediator {
            override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse =
                error("Not used")
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
            override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) =
                error("Not used")
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

    @Given("an authorized workspace member exists")
    fun givenAuthorizedWorkspaceMemberExists() = runBlocking {
        bddDatabaseSupport.seedEntitledAuthorizedMember()
    }

    @Given("a connected LinkedIn social account exists")
    fun givenConnectedLinkedInSocialAccountExists() = runBlocking {
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
        bddDatabaseSupport.seedDraftPublication(
            publicationId = "pub-bdd-draft-1",
            socialAccountId = currentSocialAccountId!!,
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
        bddDatabaseSupport.seedScheduledPublication(
            publicationId = "pub-bdd-scheduled-1",
            socialAccountId = currentSocialAccountId!!,
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
        bddDatabaseSupport.seedDraftPublication(
            publicationId = "pub-bdd-draft-2",
            socialAccountId = currentSocialAccountId!!,
            title = "Draft Post 2",
            bodyText = "Draft body 2",
        )
        bddDatabaseSupport.seedScheduledPublication(
            publicationId = "pub-bdd-scheduled-2",
            socialAccountId = currentSocialAccountId!!,
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

    @Then("the publishing response status should be {int}")
    fun thenPublishingResponseStatusShouldBe(status: Int) {
        val response = latestPublishingResponse ?: error("No publishing response captured")
        assertEquals(status, response.status.value())
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
        val body = publishingResponseBodyText()
        assertTrue(body.contains("\"channels\":[]") || body.contains("\"channels\": []"), body)
    }

    @Then("the providers list should contain {string}")
    fun thenProvidersListShouldContain(provider: String) {
        val body = publishingResponseBodyText()
        assertTrue(body.contains(""""name":"$provider""""), body)
    }

    private fun extractPublicationIdFromResponse(): String =
        latestPublicationId ?: error("No publication ID available from previous response")

    private fun publishingResponseBodyText(): String =
        String(latestPublishingResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private inline fun <reified T> parsePublishingResponseField(field: String): T {
        val body = publishingResponseBodyText()
        return try {
            val map: Map<String, Any?> = objectMapper.readValue(body)
            @Suppress("UNCHECKED_CAST")
            map[field] as T
                ?: error("Field '$field' is null in response: $body")
        } catch (e: Exception) {
            error("Failed to parse field '$field' from response: $body. Error: ${e.message}")
        }
    }

    private fun submitLogin(email: String, password: String) {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthLoginPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectBody()
            .returnResult()
    }

    private fun submitRegistration(email: String, password: String = "password123") {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthRegisterPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to password,
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()
    }

    private fun registerLocalUser(email: String, verifyEmail: Boolean = true, login: Boolean = true) {
        // Step 1: Register — returns 201 with RegistrationResult, no tokens, PENDING
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthRegisterPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()

        // Capture register response so Then steps can assert on it.
        pendingRegistrationResult = latestResult
        pendingRegistrationEmail = email
        captureLocalAuthSessionFrom(requireLatestResult(), "registration")

        // Step 2: Login to get tokens (handled before verify so pending users can also log in)
        if (login) {
            runBlocking { performLogin(email) }
        }

        // Step 3: Mark email as verified in DB (BDD shortcut for pre-existing scenarios)
        if (verifyEmail) {
            runBlocking { bddDatabaseSupport.markEmailVerified(email) }
        }

        // Restore register result so Then steps check the registration response, not the login response
        latestResult = pendingRegistrationResult
    }

    private fun registerLocalUserLegacy(email: String, verifyEmail: Boolean = true) {
        // Used by the original 'Registration creates an unverified user with session tokens' flow.
        // Preserved verbatim to keep that scenario aligned with the prior behavior.
        latestLocalAuthSession = null
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthRegisterPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()
        pendingRegistrationResult = latestResult
        pendingRegistrationEmail = email
        captureLocalAuthSessionFrom(requireLatestResult(), "registration")

        if (!verifyEmail) return
        runBlocking { bddDatabaseSupport.markEmailVerified(email) }

        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthLoginPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()

        val body = requireResponseBodyText()
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("Missing access token in login response")
        val refreshCookie = requireLatestResult().responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
            ?.substringBefore(';')
            ?: error("Missing refresh cookie in login response")
        latestLocalAuthSession = BddDatabaseSupport.LocalAuthSession(accessToken, refreshCookie)
    }

    private fun captureLocalAuthSessionFrom(result: EntityExchangeResult<ByteArray>, source: String) {
        val body = String(result.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        val status = result.status.value()
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("Missing access token in $source response (status=$status, body=$body)")
        val refreshCookie = result.responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
            ?.substringBefore(';')
            ?: error("Missing refresh cookie in $source response")
        latestLocalAuthSession = BddDatabaseSupport.LocalAuthSession(accessToken, refreshCookie)
    }

    private suspend fun performLogin(email: String) {
        val loginResult = webTestClient.post()
            .uri(bddDatabaseSupport.localAuthLoginPath())
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()

        val status = loginResult.status.value()
        if (status != 200) {
            val responseBody = String(
                loginResult.responseBody ?: ByteArray(0),
                StandardCharsets.UTF_8,
            )
            error("Login expected 200 but was $status: $responseBody")
        }
        val body = String(loginResult.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("Missing access token in login response")
        val refreshCookie = loginResult.responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
            ?.substringBefore(';')
            ?: error("Missing refresh cookie in login response")
        latestLocalAuthSession = BddDatabaseSupport.LocalAuthSession(accessToken, refreshCookie)
    }

    private fun requireLatestResult(): EntityExchangeResult<ByteArray> =
        checkNotNull(latestResult) { "No HTTP result has been captured for the current scenario" }

    private fun requireResponseBodyText(): String =
        String(requireLatestResult().responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    private fun requireLatestAuthorizationFact() =
        auditHook.facts.lastOrNull().also { assertNotNull(it, "Expected at least one authorization audit fact") }!!
}
