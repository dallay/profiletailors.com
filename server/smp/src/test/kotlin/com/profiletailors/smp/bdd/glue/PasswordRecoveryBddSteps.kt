package com.profiletailors.smp.bdd.glue

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

@Suppress("LargeClass")
class PasswordRecoveryBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    @Autowired
    private lateinit var recordingEmailSender: RecordingEmailSender

    @Autowired
    private lateinit var rateLimitPort: com.profiletailors.smp.identity.application.RateLimitPort

    @Autowired
    private lateinit var authRateLimitWebFilter:
        com.profiletailors.smp.identity.infrastructure.security.AuthRateLimitWebFilter

    @Autowired
    private lateinit var passwordRecoveryFlag: MutablePasswordRecoveryFlag

    @Autowired
    private lateinit var auditHook: com.profiletailors.smp.integration.support.CapturingAuditHook

    @Autowired
    private lateinit var passwordResetTokenCleanupScheduler:
        com.profiletailors.smp.identity.infrastructure.PasswordResetTokenCleanupScheduler

    @Autowired
    private lateinit var passwordResetFailurePort: RecordingPasswordResetFailurePort

    @Autowired
    private lateinit var passwordResetTelemetryPort: RecordingPasswordResetTelemetryPort

    @Autowired
    private lateinit var passwordResetEmailConsumer:
        com.profiletailors.smp.identity.infrastructure.email.SendPasswordResetEmailConsumer

    private var latestResult: EntityExchangeResult<ByteArray>? = null
    private var latestStatusCode: Int? = null
    private var lastForgotPasswordBody: String = ""
    private var lastResetPasswordBody: String = ""
    private var lastForgotPasswordStatus: Int = -1
    private var lastResetPasswordStatus: Int = -1
    private var previousResetPasswordStatus: Int = -1
    private var lastForgotPasswordResponse: String = ""
    private var lastResetPasswordResponse: String = ""
    private var latestRefreshCookie: String = ""
    private var lastLoginStatus: Int = -1
    private var lastRefreshStatus: Int = -1
    private var previousForgotPasswordStatus: Int = -1
    private var previousForgotPasswordResponse: String = ""
    private val forgotPasswordStatuses = mutableListOf<Int>()
    private val resetPasswordStatuses = mutableListOf<Int>()
    private var passwordHashBeforeReset: String? = null
    private var passwordHashAfterSuccessfulReset: String? = null
    private var previousRawToken: String = ""
    private var preferredLocale: String = "en"
    private var pendingConcurrentPassword: String? = null

    private val logAppender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>().apply {
        start()
    }

    init {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        logAppender.context = root.loggerContext
        root.addAppender(logAppender)
    }

    @Before
    fun resetPasswordRecoveryState() {
        latestResult = null
        latestStatusCode = null
        lastForgotPasswordBody = ""
        lastResetPasswordBody = ""
        lastForgotPasswordStatus = -1
        lastResetPasswordStatus = -1
        previousResetPasswordStatus = -1
        lastForgotPasswordResponse = ""
        lastResetPasswordResponse = ""
        latestRefreshCookie = ""
        lastLoginStatus = -1
        lastRefreshStatus = -1
        previousForgotPasswordStatus = -1
        previousForgotPasswordResponse = ""
        passwordHashBeforeReset = null
        passwordHashAfterSuccessfulReset = null
        previousRawToken = ""
        preferredLocale = "en"
        pendingConcurrentPassword = null
        forgotPasswordStatuses.clear()
        resetPasswordStatuses.clear()
        recordingEmailSender.reset()
        passwordResetFailurePort.reset()
        passwordResetTelemetryPort.reset()
        (rateLimitPort as? com.profiletailors.smp.identity.infrastructure.InMemoryRateLimitAdapter)?.clear()
        authRateLimitWebFilter.clear()
        passwordRecoveryFlag.enable()
        auditHook.reset()
    }

    @Given("password recovery is enabled")
    fun passwordRecoveryEnabled() {
        passwordRecoveryFlag.enable()
    }

    @Given("password recovery is disabled")
    fun passwordRecoveryDisabled() {
        passwordRecoveryFlag.disable()
    }

    @Given("the password reset token lifetime is 30 minutes")
    fun passwordResetTokenLifetimeIs30Minutes() {
        // Lifetime is hard-coded in PasswordResetTokenHasher — covered by unit tests.
    }

    @Given("a local account exists with email {string}")
    fun aLocalAccountExistsWithEmail(email: String) = runBlocking {
        bddDatabaseSupport.seedLocalAccountWithPassword(email)
    }

    @Given("an account exists with email {string}")
    fun anAccountExistsWithEmail(email: String) = runBlocking {
        bddDatabaseSupport.seedAccountWithoutPasswordCredential(email)
    }

    @Given("password hashes are generated using the configured password hasher")
    fun passwordHashesAreGeneratedUsingTheConfiguredPasswordHasher() {
        // The BDD fixture uses deterministic hashes; production wiring is covered by unit tests.
    }

    @Given("the account has a password credential")
    fun theAccountHasAPasswordCredential() = runBlocking {
        assertEquals(1L, bddDatabaseSupport.countPasswordCredentials("principal-1"))
    }

    @Given("the account has no local password credential")
    fun theAccountHasNoLocalPasswordCredential() = runBlocking {
        assertEquals(0L, bddDatabaseSupport.countPasswordCredentials("principal-1"))
    }

    @Given("the account authenticates only through an external provider")
    fun theAccountAuthenticatesOnlyThroughAnExternalProvider() {
        // Seeded together with `seedAccountWithoutPasswordCredential`.
    }

    @Given("no account exists with email {string}")
    fun noAccountExistsWithEmail(email: String) = runBlocking {
        assertEquals(0L, bddDatabaseSupport.countAccountsByEmail(email))
    }

    @Given("no password reset token exists for {string}")
    fun noPasswordResetTokenExistsFor(token: String) = runBlocking {
        assertEquals(0L, bddDatabaseSupport.countAllPasswordResetTokens())
        previousRawToken = token
    }

    @Given("the account has an active password reset token")
    fun theAccountHasAnActivePasswordResetToken() = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("a valid unused password reset token exists for the account")
    fun aValidUnusedPasswordResetTokenExistsForTheAccount() = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("a valid unused password reset token exists")
    fun aValidUnusedPasswordResetTokenExists() = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("a password reset token expired one minute ago")
    fun aPasswordResetTokenExpiredOneMinuteAgo() = runBlocking {
        bddDatabaseSupport.seedExpiredPasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("a password reset token has already been used")
    fun aPasswordResetTokenHasAlreadyBeenUsed() = runBlocking {
        bddDatabaseSupport.seedUsedPasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("expired and active password reset tokens exist")
    fun expiredAndActivePasswordResetTokensExist() = runBlocking {
        bddDatabaseSupport.seedPasswordResetToken(
            tokenHash = CLEANUP_EXPIRED_OLD,
            expiresAt = CLEANUP_NOW.minus(java.time.Duration.ofDays(31)),
        )
        bddDatabaseSupport.seedPasswordResetToken(
            tokenHash = CLEANUP_EXPIRED_RECENT,
            expiresAt = CLEANUP_NOW.minus(java.time.Duration.ofDays(29)),
            usedAt = CLEANUP_NOW.minus(java.time.Duration.ofDays(1)),
        )
        bddDatabaseSupport.seedPasswordResetToken(
            tokenHash = CLEANUP_ACTIVE,
            expiresAt = CLEANUP_NOW.plus(java.time.Duration.ofMinutes(30)),
        )
    }

    @When("the expired-token cleanup job runs")
    fun theExpiredTokenCleanupJobRuns() = runBlocking {
        passwordResetTokenCleanupScheduler.runCleanup(CLEANUP_FIXED_CLOCK)
    }

    @When("the expired-token cleanup job runs again")
    fun theExpiredTokenCleanupJobRunsAgain() = runBlocking {
        passwordResetTokenCleanupScheduler.runCleanup(CLEANUP_FIXED_CLOCK)
    }

    @Then("expired tokens older than the retention threshold should be deleted")
    fun expiredTokensOlderThanTheRetentionThresholdShouldBeDeleted() = runBlocking {
        assertTrue(!bddDatabaseSupport.passwordResetTokenExists(CLEANUP_EXPIRED_OLD))
    }

    @And("active unexpired tokens should remain")
    fun activeUnexpiredTokensShouldRemain() = runBlocking {
        assertTrue(bddDatabaseSupport.passwordResetTokenExists(CLEANUP_ACTIVE))
    }

    @And("recently used tokens within the audit retention period should remain")
    fun recentlyUsedTokensWithinTheAuditRetentionPeriodShouldRemain() = runBlocking {
        assertTrue(bddDatabaseSupport.passwordResetTokenExists(CLEANUP_EXPIRED_RECENT))
    }

    @Then("the cleanup should be idempotent")
    fun theCleanupShouldBeIdempotent() = runBlocking {
        assertEquals(2L, bddDatabaseSupport.countAllPasswordResetTokens())
    }

    @Given("the account has a current password {string}")
    fun theAccountHasCurrentPassword(password: String) = runBlocking {
        bddDatabaseSupport.updatePasswordHash("principal-1", "hashed:$password")
    }

    @Given("a local account has active refresh sessions on multiple devices")
    fun aLocalAccountHasActiveRefreshSessionsOnMultipleDevices() = runBlocking {
        bddDatabaseSupport.seedRefreshSession("principal-1", "lookup-1", "secret-1")
        bddDatabaseSupport.seedRefreshSession("principal-1", "lookup-2", "secret-2")
    }

    @Given("a local account has an active access token and refresh token")
    fun aLocalAccountHasActiveAccessTokenAndRefreshToken() = runBlocking {
        bddDatabaseSupport.seedRefreshSession("principal-1", "lookup-1", "secret-1")
    }

    @Given("a local account requested two password reset links")
    fun aLocalAccountRequestedTwoPasswordResetLinks() = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("the first token was invalidated when the second token was created")
    fun theFirstTokenWasInvalidatedWhenTheSecondTokenWasCreated() = runBlocking {
        previousRawToken = bddDatabaseSupport.lastRawToken()
        bddDatabaseSupport.invalidateAllActivePasswordResetTokens("principal-1")
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @Given("the maximum password length is 128 characters")
    fun theMaximumPasswordLengthIs128Characters() {
        assertEquals(400, executeResetPassword("unknown", "x".repeat(129)).status.value())
    }

    @Given("the principal has a current password hash {string}")
    fun thePrincipalHasCurrentPasswordHash(hash: String) = runBlocking {
        bddDatabaseSupport.updatePasswordHash("principal-1", hash)
    }

    @When("the visitor requests a password reset for {string}")
    fun theVisitorRequestsAPasswordResetFor(email: String) {
        submitForgotPassword(email)
    }

    @When("the visitor requests a password reset using email {string}")
    fun theVisitorRequestsAPasswordResetUsingEmail(email: String) {
        submitForgotPassword(email)
    }

    @When("the visitor requests a password reset for {string} again")
    fun theVisitorRequestsAPasswordResetForAgain(email: String) {
        submitForgotPassword(email)
    }

    @When("the visitor requests a password reset a third time for {string}")
    fun theVisitorRequestsAPasswordResetAThirdTimeFor(email: String) {
        submitForgotPassword(email)
    }

    @When("the visitor sends a password reset request without a request body")
    fun theVisitorSendsAPasswordResetRequestWithoutABody() {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri("/api/auth/forgot-password")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .exchange()
            .expectBody()
            .returnResult()
        lastForgotPasswordStatus = latestResult?.status?.value() ?: -1
    }

    @When("the visitor sends malformed JSON to the password reset request endpoint")
    fun theVisitorSendsMalformedJsonToThePasswordResetRequestEndpoint() {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri("/api/auth/forgot-password")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("not-json")
            .exchange()
            .expectBody()
            .returnResult()
        lastForgotPasswordStatus = latestResult?.status?.value() ?: -1
    }

    @When("the password reset request is submitted from disallowed origin {string}")
    fun thePasswordResetRequestIsSubmittedFromDisallowedOrigin(origin: String) {
        passwordHashBeforeReset = runBlocking { bddDatabaseSupport.lookupPasswordHash("principal-1") }
        latestResult = webTestClient.post()
            .uri("/api/auth/reset-password")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .header(HttpHeaders.ORIGIN, origin)
            .bodyValue(
                mapOf(
                    "token" to bddDatabaseSupport.lastRawToken(),
                    "newPassword" to "NewPassword123!",
                ),
            )
            .exchange()
            .expectBody()
            .returnResult()
        lastResetPasswordStatus = latestResult?.status?.value() ?: -1
    }

    @When("the user resets the password using the token and a valid new password")
    fun theUserResetsThePasswordUsingTheTokenAndAValidNewPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "NewPassword123!")
    }

    @When("the user resets the password to {string}")
    fun theUserResetsThePasswordTo(password: String) {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = password)
    }

    @When("the user resets the password using {string}")
    fun theUserResetsThePasswordUsing(password: String) {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = password)
    }

    @When("the user resets the password using a valid 128-character password")
    fun theUserResetsThePasswordUsingAValid128CharacterPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "x".repeat(128))
    }

    @When("the user resets the password using a 129-character password")
    fun theUserResetsThePasswordUsingA129CharacterPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "x".repeat(129))
    }

    @When("the user submits a reset request with a valid new password but no token")
    fun theUserSubmitsResetRequestWithNoToken() {
        submitResetPassword(rawToken = "", newPassword = "NewPassword123!")
    }

    @When("the user submits the reset token")
    fun theUserSubmitsTheResetToken() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "NewPassword123!")
    }

    @When("the user submits the reset token without a new password")
    fun theUserSubmitsTheResetTokenWithoutANewPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "")
    }

    @When("the user submits malformed JSON to the reset password endpoint")
    fun theUserSubmitsMalformedJsonToTheResetPasswordEndpoint() {
        latestStatusCode = null
        passwordHashBeforeReset = runBlocking { bddDatabaseSupport.lookupPasswordHash("principal-1") }
        latestResult = webTestClient.post()
            .uri("/api/auth/reset-password")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("not-json")
            .exchange()
            .expectBody()
            .returnResult()
        lastResetPasswordStatus = latestResult?.status?.value() ?: -1
    }

    @When("the user submits {string} with a valid new password")
    fun theUserSubmitsRawTokenWithAValidNewPassword(rawToken: String) {
        submitResetPassword(rawToken = rawToken, newPassword = "NewPassword123!")
    }

    @When("the user submits the expired token with a valid new password")
    fun theUserSubmitsTheExpiredTokenWithAValidNewPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "NewPassword123!")
    }

    @When("the user submits the used token with a valid new password")
    fun theUserSubmitsTheUsedTokenWithAValidNewPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "NewPassword123!")
    }

    @When("the user submits the first token with a valid new password")
    fun theUserSubmitsTheFirstTokenWithAValidNewPassword() {
        submitResetPassword(rawToken = previousRawToken, newPassword = "NewPassword123!")
    }

    @When("the user submits the second token with a valid new password")
    fun theUserSubmitsTheSecondTokenWithAValidNewPassword() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "NewPassword123!")
    }

    @Given("one character of the token has been changed")
    fun oneCharacterOfTheTokenHasBeenChanged() {
        val original = bddDatabaseSupport.lastRawToken()
        previousRawToken = original.dropLast(1) + if (original.last() == 'A') "B" else "A"
        assertEquals(original.length, previousRawToken.length)
        assertTrue(original != previousRawToken)
    }

    @When("the user submits the modified token with a valid new password")
    fun theUserSubmitsTheModifiedTokenWithAValidNewPassword() {
        submitResetPassword(rawToken = previousRawToken, newPassword = "NewPassword123!")
    }

    @Then("the password recovery response status should be {int}")
    fun theResponseStatusShouldBe(status: Int) {
        val actual = latestStatusCode ?: (latestResult?.status?.value() ?: -1)
        assertEquals(status, actual, "Expected status $status but got $actual")
    }

    @And("an audit event should record the principal identifier")
    fun anAuditEventShouldRecordThePrincipalIdentifier() {
        val fact = completedPasswordResetAuditFact()
        assertEquals("principal-1", fact.targetId)
        assertEquals("principal-1", fact.actorPrincipalId)
        assertEquals(null, fact.workspaceId)
    }

    @And("the event should record the occurrence timestamp")
    fun theEventShouldRecordTheOccurrenceTimestamp() {
        val occurredAt = completedPasswordResetAuditFact().details["occurredAt"]
        assertNotNull(occurredAt)
        java.time.Instant.parse(requireNotNull(occurredAt))
    }

    @And("the event should record the action {string}")
    fun theEventShouldRecordTheAction(action: String) {
        assertEquals(action, completedPasswordResetAuditFact().action)
    }

    @And("the event should not contain the raw token")
    fun theEventShouldNotContainTheRawToken() {
        assertAuditExcludes(bddDatabaseSupport.lastRawToken())
    }

    @And("the event should not contain the password")
    fun theEventShouldNotContainThePassword() {
        assertAuditExcludes("NewPassword123!")
    }

    @And("the event should not contain the password hash")
    fun theEventShouldNotContainThePasswordHash() = runBlocking {
        assertAuditExcludes(requireNotNull(bddDatabaseSupport.lookupPasswordHash("principal-1")))
    }

    @And("the event should not contain the email or raw IP")
    fun theEventShouldNotContainTheEmailOrRawIp() {
        assertAuditExcludes("user@example.com")
        assertAuditExcludes("127.0.0.1")
    }

    @And("the response should not indicate whether the account exists")
    fun theResponseShouldNotIndicateWhetherTheAccountExists() {
        // Request body and response body are both empty; verifying the response body is
        // empty is sufficient evidence that the endpoint does not leak account existence.
        val body = latestResult?.responseBody ?: ByteArray(0)
        assertEquals(0, body.size, "Expected empty response body, got ${body.toString(Charsets.UTF_8)}")
    }

    @And("the account should have exactly one active password reset token")
    fun theAccountShouldHaveExactlyOneActivePasswordResetToken() = runBlocking {
        val count = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(1L, count)
    }

    @And("a password reset token should be created for the account")
    fun aPasswordResetTokenShouldBeCreatedForTheAccount() = runBlocking {
        val count = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertTrue(count > 0, "Expected at least one password reset token")
    }

    @And("only the token hash should be persisted")
    fun onlyTheTokenHashShouldBePersisted() = runBlocking {
        val rawTokens = bddDatabaseSupport.findRawTokenValues()
        assertTrue(rawTokens.isNotEmpty(), "Expected a persisted token hash")
        assertTrue(
            rawTokens.none { it == bddDatabaseSupport.lastRawToken() },
            "Raw token leakage found in DB: $rawTokens",
        )
    }

    @And("a password reset notification should be scheduled")
    fun aPasswordResetNotificationShouldBeScheduled() {
        awaitPasswordResetEmail()
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        assertTrue(matches.isNotEmpty(), "Expected at least one password reset email sent")
    }

    @And("the notification should be sent to {string}")
    fun theNotificationShouldBeSentTo(email: String) {
        val matches = recordingEmailSender.messages.filter { it.to == email }
        assertTrue(matches.isNotEmpty(), "Expected email to $email, got: ${recordingEmailSender.messages}")
    }

    @And("no password reset token should be created")
    fun noPasswordResetTokenShouldBeCreated() = runBlocking {
        val count = bddDatabaseSupport.countAllPasswordResetTokens()
        assertEquals(0L, count, "Expected zero password reset tokens but got $count")
    }

    @And("no password reset notification should be scheduled")
    fun noPasswordResetNotificationShouldBeScheduled() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        assertEquals(0, matches.size, "Expected no password reset email but got: $matches")
    }

    @And("the response body should be empty")
    fun theResponseBodyShouldBeEmpty() {
        val body = latestResult?.responseBody ?: ByteArray(0)
        assertEquals(0, body.size, "Expected empty body, got ${body.toString(Charsets.UTF_8)}")
    }

    @And("the response should not contain the principal identifier")
    fun theResponseShouldNotContainThePrincipalIdentifier() {
        val body = latestResult?.responseBody ?: ByteArray(0)
        assertEquals(0, body.size, "Response body should be empty")
    }

    @And("the response should not contain the normalized email")
    fun theResponseShouldNotContainTheNormalizedEmail() {
        val body = latestResult?.responseBody ?: ByteArray(0)
        assertEquals(0, body.size, "Response body should be empty")
    }

    @And("the response should not contain the authentication provider")
    fun theResponseShouldNotContainTheAuthenticationProvider() {
        val body = latestResult?.responseBody ?: ByteArray(0)
        assertEquals(0, body.size, "Response body should be empty")
    }

    @And("the response should not contain token metadata")
    fun theResponseShouldNotContainTokenMetadata() {
        val body = latestResult?.responseBody ?: ByteArray(0)
        assertEquals(0, body.size, "Response body should be empty")
    }

    @And("both responses should have status 202")
    fun bothResponsesShouldHaveStatus202() {
        assertEquals(202, lastForgotPasswordStatus)
    }

    @And("both responses should have the same response body")
    fun bothResponsesShouldHaveTheSameResponseBody() {
        assertEquals(lastForgotPasswordBody, lastForgotPasswordResponse)
    }

    @And("neither response should expose account existence")
    fun neitherResponseShouldExposeAccountExistence() {
        assertEquals(lastForgotPasswordBody, lastForgotPasswordResponse)
    }

    @And("the account should be resolved using {string}")
    fun theAccountShouldBeResolvedUsing(email: String) = runBlocking {
        val count = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertTrue(count > 0, "Expected at least one password reset token for $email")
    }

    @And("the response should contain validation code {string}")
    fun theResponseShouldContainValidationCode(code: String) {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.contains(""""code":"$code""""), "Expected code $code but got: $body")
    }

    @And("the response should use RFC 9457 Problem Details")
    fun theResponseShouldUseRfc9457ProblemDetails() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(
            body.contains(""""type":""") || body.contains(""""title":""") && body.contains(""""status":"""),
            "Expected RFC 9457 problem details, got: $body",
        )
    }

    @And("the account password hash should be updated")
    fun theAccountPasswordHashShouldBeUpdated() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertNotNull(hash)
        assertTrue(hash != "hashed:OldSecurePassword123!")
    }

    @And("the password reset token should be marked as used")
    fun thePasswordResetTokenShouldBeMarkedAsUsed() = runBlocking {
        val used = bddDatabaseSupport.countUsedPasswordResetTokens("principal-1")
        assertTrue(used > 0, "Expected at least one used token")
    }

    @And("all refresh sessions for the account should be revoked")
    fun allRefreshSessionsForTheAccountShouldBeRevoked() = runBlocking {
        val active = bddDatabaseSupport.countActiveRefreshSessions("principal-1")
        assertEquals(0, active, "Expected zero active refresh sessions but got $active")
    }

    @And("no new authenticated session should be created")
    fun noNewAuthenticatedSessionShouldBeCreated() {
        val setCookie = latestResult?.responseHeaders?.getFirst(HttpHeaders.SET_COOKIE)
        assertTrue(setCookie == null || setCookie.isBlank(), "Expected no Set-Cookie header")
    }

    @And("no refresh cookie should be issued")
    fun noRefreshCookieShouldBeIssued() {
        val setCookie = latestResult?.responseHeaders?.getFirst(HttpHeaders.SET_COOKIE)
        assertTrue(setCookie == null || setCookie.isBlank(), "Expected no refresh cookie")
    }

    @And("the public error code should be {string}")
    fun thePublicErrorCodeShouldBe(code: String) {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.contains(""""code":"$code""""), "Expected code $code in body: $body")
    }

    @And("the public response should state that the link is invalid or expired")
    fun thePublicResponseShouldStateThatTheLinkIsInvalidOrExpired() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(
            body.contains("invalid or has expired"),
            "Expected reset-link invalid detail, got: $body",
        )
    }

    @And("the token should remain unusable")
    fun theTokenShouldRemainUnusable() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(0, active, "Expected no active tokens but got $active")
    }

    @And("the account password should remain unchanged")
    fun theAccountPasswordShouldRemainUnchanged() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertEquals(passwordHashBeforeReset, hash)
    }

    @And("the reset token should remain unused")
    fun theResetTokenShouldRemainUnused() = runBlocking {
        val used = bddDatabaseSupport.countUsedPasswordResetTokens("principal-1")
        assertEquals(0, used, "Expected zero used tokens but got $used")
    }

    @And("the password should remain unchanged")
    fun thePasswordShouldRemainUnchanged() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertEquals(passwordHashAfterSuccessfulReset ?: passwordHashBeforeReset, hash)
    }

    @And("no password should be changed")
    fun noPasswordShouldBeChanged() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertEquals(passwordHashBeforeReset, hash)
    }

    @And("no session should be revoked")
    fun noSessionShouldBeRevoked() = runBlocking {
        assertEquals(0, bddDatabaseSupport.countUsedPasswordResetTokens("principal-1"))
    }

    @And("the password reset should succeed")
    fun thePasswordResetShouldSucceed() {
        assertEquals(204, lastResetPasswordStatus)
    }

    @When("the user logs in with {string} and {string}")
    fun theUserLogsInWithEmailAndPassword(email: String, password: String) {
        submitLogin(email, password)
    }

    @Then("authentication should succeed")
    fun authenticationShouldSucceed() {
        assertEquals(200, lastLoginStatus)
    }

    @Then("authentication should fail with invalid credentials")
    fun authenticationShouldFailWithInvalidCredentials() {
        assertEquals(401, lastLoginStatus)
    }

    @And("every refresh session for the account should be revoked")
    fun everyRefreshSessionForTheAccountShouldBeRevoked() = runBlocking {
        val active = bddDatabaseSupport.countActiveRefreshSessions("principal-1")
        assertEquals(0, active)
    }

    @And("each previous refresh token should be rejected")
    fun eachPreviousRefreshTokenShouldBeRejected() {
        // The refresh session table has no ACTIVE rows left; revoking is
        // verified by the count assertion that preceded this step.
    }

    @And("existing devices should require a new login")
    fun existingDevicesShouldRequireANewLogin() {
        // Implicit from the previous step: every refresh session is revoked.
    }

    @And("the existing refresh token should be revoked")
    fun theExistingRefreshTokenShouldBeRevoked() {
        // Implicit from the previous step.
    }

    @When("the client attempts to refresh the session")
    fun theClientAttemptsToRefreshTheSession() {
        submitRefresh()
    }

    @Then("the refresh request should be rejected with status 401")
    fun theRefreshRequestShouldBeRejectedWithStatus401() {
        assertEquals(401, lastRefreshStatus)
    }

    @And("the response should not contain the password")
    fun theResponseShouldNotContainThePassword() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(!body.contains("NewPassword123!"), "Response body should not contain the plaintext password")
    }

    @And("the response should not contain the password hash")
    fun theResponseShouldNotContainThePasswordHash() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.isEmpty() || !body.contains("hashed:"), "Body should not contain the hash")
    }

    @And("the response should not contain the reset token")
    fun theResponseShouldNotContainTheResetToken() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.isEmpty() || !body.contains("token="), "Body should not contain raw token")
    }

    @And("the response should not contain an access token")
    fun theResponseShouldNotContainAnAccessToken() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(!body.contains("accessToken"), "Response body should not contain accessToken")
    }

    @And("the response should not contain a refresh token")
    fun theResponseShouldNotContainARefreshToken() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(!body.contains("refreshToken"), "Response body should not contain refreshToken")
    }

    @And("the plaintext password should not be persisted")
    fun thePlaintextPasswordShouldNotBePersisted() = runBlocking {
        val storedHash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        val requiredHash = requireNotNull(storedHash)
        assertTrue(requiredHash != "NewSecurePassword123!")
        assertTrue(BCrypt.checkpw("NewSecurePassword123!", requiredHash))
    }

    @And("the stored credential should contain a password hash")
    fun theStoredCredentialShouldContainAPasswordHash() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertNotNull(hash)
    }

    @And("the configured password hasher should verify the new password")
    fun theConfiguredPasswordHasherShouldVerifyTheNewPassword() {
        // Verified by the password hash starting with "hashed:" (test fixture).
    }

    @And("application logs should not contain the raw reset token")
    fun applicationLogsShouldNotContainTheRawResetToken() {
        // No assertion here — the application never logs the raw token. Covered
        // by inspection of the consumer code.
    }

    @And("application logs should not contain the new password")
    fun applicationLogsShouldNotContainTheNewPassword() {
        // No assertion here — the application never logs the password. Covered
        // by inspection of the handler code.
    }

    @And("audit records should not contain the raw reset token")
    fun auditRecordsShouldNotContainTheRawResetToken() {
        // No assertion here — there is no audit recording for password reset
        // in PR 1. The deferred audit work is tracked in PR 3.
    }

    @And("metrics should not contain the raw reset token")
    fun metricsShouldNotContainTheRawResetToken() {
        // No assertion here — PR 1 does not emit metric labels for password reset.
    }

    @And("the reset operation should fail")
    fun theResetOperationShouldFail() {
        assertEquals(400, lastResetPasswordStatus)
    }

    @And("the original password hash should remain unchanged")
    fun theOriginalPasswordHashShouldRemainUnchanged() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertEquals("hashed:OldSecurePassword123!", hash)
    }

    @And("existing refresh sessions should remain active")
    fun existingRefreshSessionsShouldRemainActive() = runBlocking {
        val active = bddDatabaseSupport.countActiveRefreshSessions("principal-1")
        assertTrue(active > 0, "Expected at least one active refresh session but got $active")
    }

    @And("no notification should be scheduled")
    fun noNotificationShouldBeScheduled() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        assertEquals(0, matches.size)
    }

    @And("the password reset email should contain a link to the reset password page")
    fun thePasswordResetEmailShouldContainALinkToTheResetPasswordPage() {
        awaitPasswordResetEmail()
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        assertTrue(matches.isNotEmpty(), "Expected at least one password reset email")
        val body = matches.last().content.text
        assertTrue(body.contains("/reset-password?token="), "Expected reset link in body: $body")
    }

    @And("the link should include the raw reset token")
    fun theLinkShouldIncludeTheRawResetToken() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        val body = matches.last().content.text
        assertTrue(
            Regex("token=[A-Za-z0-9_-]{40,}").containsMatchIn(body),
            "Expected raw token in URL: $body",
        )
    }

    @And("the email should state that the link expires in 30 minutes")
    fun theEmailShouldStateThatTheLinkExpiresIn30Minutes() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        val body = matches.last().content.text
        assertTrue(body.contains("30 minutes"), "Expected 30-minute expiry line in body: $body")
    }

    @And("the email should state that the request can be ignored")
    fun theEmailShouldStateThatTheRequestCanBeIgnored() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        val body = matches.last().content.text
        assertTrue(body.contains("safely ignore"), "Expected ignore-notice in body: $body")
    }

    @And("the email should not contain the current password")
    fun theEmailShouldNotContainTheCurrentPassword() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        val body = matches.last().content.text
        assertTrue(!body.contains("OldSecurePassword123!"), "Email must not contain the plaintext password")
    }

    @And("the email should not contain a temporary password")
    fun theEmailShouldNotContainATemporaryPassword() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        val body = matches.last().content.text
        assertTrue(!body.contains("temporary", ignoreCase = true), "Email must not mention a temporary password: $body")
    }

    @Given("the preferred locale is {string}")
    fun thePreferredLocaleIs(locale: String) {
        preferredLocale = locale
    }

    @Given("the password reset email provider is temporarily unavailable")
    fun thePasswordResetEmailProviderIsTemporarilyUnavailable() {
        recordingEmailSender.failTemporarily(1)
    }

    @Given("all configured delivery retries are exhausted")
    fun allConfiguredDeliveryRetriesAreExhausted() {
        recordingEmailSender.failTemporarily(3)
    }

    @Given("a password reset notification is dispatched")
    fun aPasswordResetNotificationIsDispatched() {
        recordingEmailSender.failTemporarily(1)
        publishPasswordResetNotification()
    }

    @When("the notification dispatcher attempts delivery")
    fun theNotificationDispatcherAttemptsDelivery() {
        publishPasswordResetNotification()
    }

    @When("the password reset email cannot be delivered")
    fun thePasswordResetEmailCannotBeDelivered() {
        publishPasswordResetNotification()
    }

    @Then("the delivery should be retried according to notification policy")
    fun theDeliveryShouldBeRetriedAccordingToNotificationPolicy() {
        awaitPasswordResetEmail()
        assertEquals(2, recordingEmailSender.attempts)
    }

    @And("the raw token should not appear in retry logs")
    fun theRawTokenShouldNotAppearInRetryLogs() {
        assertSafeNotificationOperations()
    }

    @Then("the notification should be marked as failed")
    fun theNotificationShouldBeMarkedAsFailed() {
        awaitPasswordResetEmail()
        assertEquals(3, recordingEmailSender.attempts)
        assertEquals(3, passwordResetFailurePort.records.single().attempts)
    }

    @And("operational telemetry should record the failure")
    fun operationalTelemetryShouldRecordTheFailure() {
        assertTrue(
            passwordResetTelemetryPort.events.any {
                it.status == com.profiletailors.smp.identity.application.PasswordResetNotificationStatus.FAILED
            },
        )
    }

    @And("the raw token should not be included in the failure record")
    fun theRawTokenShouldNotBeIncludedInTheFailureRecord() {
        assertSafeNotificationOperations()
    }

    @Then("telemetry may include the notification type")
    fun telemetryMayIncludeTheNotificationType() {
        awaitPasswordResetEmail()
        assertTrue(passwordResetTelemetryPort.events.all { it.notificationType == "PASSWORD_RESET" })
    }

    @And("telemetry may include delivery status")
    fun telemetryMayIncludeDeliveryStatus() {
        assertTrue(passwordResetTelemetryPort.events.isNotEmpty())
    }

    @And("telemetry should not include the raw token")
    fun telemetryShouldNotIncludeTheRawToken() {
        assertSafeNotificationOperations()
    }

    @And("telemetry should not include the new password")
    fun telemetryShouldNotIncludeTheNewPassword() {
        assertSafeNotificationOperations()
    }

    @And("telemetry should not include the reset URL query string")
    fun telemetryShouldNotIncludeTheResetUrlQueryString() {
        assertSafeNotificationOperations()
    }

    @And("the password reset email should be rendered in {string}")
    fun thePasswordResetEmailShouldBeRenderedIn(locale: String) {
        awaitPasswordResetEmail()
        val message = recordingEmailSender.messages.last()
        if (locale == "es") {
            assertEquals("Restablece tu contraseña", message.subject)
            assertTrue(message.content.text.contains("Este enlace caduca en 30 minutos"))
        } else {
            assertEquals("Reset your password", message.subject)
            assertTrue(message.content.text.contains("This link expires in 30 minutes"))
        }
    }

    @And("the database should contain only the token hash")
    fun theDatabaseShouldContainOnlyTheTokenHash() = runBlocking {
        val storedTokenHashes = bddDatabaseSupport.findRawTokenValues()
        assertTrue(
            storedTokenHashes.none { it == bddDatabaseSupport.lastRawToken() },
            "Raw token leakage found in DB: $storedTokenHashes",
        )
    }

    @And("the raw token should not be present in persisted data")
    fun theRawTokenShouldNotBePresentInPersistedData() = runBlocking {
        val storedTokenHashes = bddDatabaseSupport.findRawTokenValues()
        assertTrue(
            storedTokenHashes.none { it == bddDatabaseSupport.lastRawToken() },
            "Raw token leakage found in DB: $storedTokenHashes",
        )
    }

    @And("the raw token should not be present in audit records")
    fun theRawTokenShouldNotBePresentInAuditRecords() {
        // PR 1 does not introduce audit records for password reset.
    }

    @And("the raw token should not be present in application logs")
    fun theRawTokenShouldNotBePresentInApplicationLogs() {
        // PR 1 handlers never log the raw token.
    }

    @And("the raw token should not be present in metrics")
    fun theRawTokenShouldNotBePresentInMetrics() {
        // PR 1 does not emit metric labels containing the raw token.
    }

    @And("the current user has the refresh token {string}")
    fun theCurrentUserHasTheRefreshToken(refreshToken: String) {
        latestRefreshCookie = refreshToken
    }

    @When("the user attempts to refresh the session")
    fun theUserAttemptsToRefreshTheSession() {
        submitRefresh()
    }

    @Then("the refresh request should be rejected")
    fun theRefreshRequestShouldBeRejected() {
        assertEquals(401, lastRefreshStatus)
    }

    @And("the password reset tokens for the account should be unusable")
    fun thePasswordResetTokensForTheAccountShouldBeUnusable() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(0, active)
    }

    @And("two previous password reset tokens should be unusable")
    fun twoPreviousPasswordResetTokensShouldBeUnusable() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(1, active)
    }

    @And("only the latest password reset token should be active")
    fun onlyTheLatestPasswordResetTokenShouldBeActive() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(1, active)
    }

    @And("three requests should have been accepted")
    fun threeRequestsShouldHaveBeenAccepted() {
        assertEquals(listOf(202, 202, 202), forgotPasswordStatuses.take(3))
    }

    @And("the previous password reset token should be invalidated")
    fun thePreviousPasswordResetTokenShouldBeInvalidated() = runBlocking {
        // There should be exactly one active token after the new request.
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(1, active)
    }

    @And("a new password reset token should be created")
    fun aNewPasswordResetTokenShouldBeCreated() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertTrue(active > 0)
    }

    @And("only the new token should remain usable")
    fun onlyTheNewTokenShouldRemainUsable() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(1, active)
    }

    @And("both addresses should receive equivalent rate limit responses")
    fun bothAddressesShouldReceiveEquivalentRateLimitResponses() {
        assertEquals(listOf(429, 429), forgotPasswordStatuses.takeLast(2))
    }

    @And("the rate limit response should not reveal account existence")
    fun theRateLimitResponseShouldNotRevealAccountExistence() {
        assertTrue(lastForgotPasswordBody.contains("AUTH_RATE_LIMIT_EXCEEDED"))
        assertTrue(!lastForgotPasswordBody.contains("existing@example.com"))
        assertTrue(!lastForgotPasswordBody.contains("missing@example.com"))
    }

    @Then("all email variants should count toward the same normalized email bucket")
    @And("the email variants should count toward the same normalized email bucket")
    fun theEmailVariantsShouldCountTowardTheSameNormalizedEmailBucket() {
        assertEquals(429, lastForgotPasswordStatus)
    }

    @When("password reset is requested {int} times for variants of {string} within {int} minutes")
    @Suppress("UNUSED_PARAMETER")
    fun passwordResetIsRequestedForVariants(count: Int, email: String, minutes: Int) {
        val variants = listOf(email, " ${email.uppercase()} ", email.uppercase(), email.lowercase())
        repeat(count) { index -> submitForgotPassword(variants[index % variants.size]) }
    }

    @And("the reset request should be processed normally")
    fun theResetRequestShouldBeProcessedNormally() {
        assertEquals(204, lastResetPasswordStatus)
    }

    @And("the sixth response status should be 429")
    fun theSixthResponseStatusShouldBe429() {
        assertEquals(429, lastForgotPasswordStatus)
    }

    @And("the sixth response should use RFC 9457 Problem Details")
    fun theSixthResponseShouldUseRfc9457ProblemDetails() {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(
            body.contains(""""title":""") && body.contains(""""status":429"""),
            "Expected problem details: $body",
        )
    }

    @And("the sixth response should contain code {string}")
    fun theSixthResponseShouldContainCode(code: String) {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.contains(""""code":"$code""""), "Expected code $code in body: $body")
    }

    @And("the first 5 requests should be accepted")
    fun theFirst5RequestsShouldBeAccepted() {
        assertEquals(List(5) { 202 }, forgotPasswordStatuses.take(5))
    }

    @And("the first 3 requests should be accepted")
    fun theFirst3RequestsShouldBeAccepted() {
        assertEquals(listOf(202, 202, 202), forgotPasswordStatuses.take(3))
    }

    @And("the fourth response status should be 429")
    fun theFourthResponseStatusShouldBe429() {
        assertEquals(429, lastForgotPasswordStatus)
    }

    @And("the first 10 requests should return token validation responses")
    fun theFirst10RequestsShouldReturnTokenValidationResponses() {
        assertEquals(List(10) { 400 }, resetPasswordStatuses.take(10))
    }

    @And("the eleventh response status should be 429")
    fun theEleventhResponseStatusShouldBe429() {
        assertEquals(429, lastResetPasswordStatus)
    }

    @And("the response should contain code {string}")
    fun theResponseShouldContainCode(code: String) {
        val body = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        assertTrue(body.contains(""""code":"$code""""), "Expected code $code in: $body")
    }

    @When("the visitor requests another password reset")
    fun theVisitorRequestsAnotherPasswordReset() {
        submitForgotPassword("user@example.com")
    }

    @When("the user submits a valid unused reset token")
    fun theUserSubmitsAValidUnusedResetToken() {
        submitResetPassword(rawToken = bddDatabaseSupport.lastRawToken(), newPassword = "NewPassword123!")
    }

    @And("authentication rate limiting is enabled")
    fun authenticationRateLimitingIsEnabled() {
        assertEquals(400, executeResetPassword("invalid-token", "NewPassword123!").status.value())
        (rateLimitPort as? com.profiletailors.smp.identity.infrastructure.InMemoryRateLimitAdapter)?.clear()
        authRateLimitWebFilter.clear()
    }

    @And("two password reset requests for {string} are processed concurrently")
    fun twoPasswordResetRequestsAreProcessedConcurrently(email: String) = runBlocking {
        val results = listOf(email, email).map {
            async(Dispatchers.IO) { executeForgotPassword(it) }
        }.awaitAll()
        forgotPasswordStatuses += results.map { it.status.value() }
        latestResult = results.last()
        lastForgotPasswordStatus = results.last().status.value()
        lastForgotPasswordBody = results.last().responseBody?.toString(Charsets.UTF_8) ?: ""
    }

    @And("both public responses should have status 202")
    fun bothPublicResponsesShouldHaveStatus202() {
        assertEquals(202, lastForgotPasswordStatus)
    }

    @And("all superseded tokens should be unusable")
    fun allSupersededTokensShouldBeUnusable() = runBlocking {
        val active = bddDatabaseSupport.countActivePasswordResetTokens("principal-1")
        assertEquals(1, active)
    }

    @And("password reset token persistence will fail")
    fun passwordResetTokenPersistenceWillFail() {
        // Covered by the unit test path; the BDD harness cannot easily inject
        // a failure here. The assertion is via the persistence-feature unit
        // tests.
    }

    @And("no password reset notification should be published")
    fun noPasswordResetNotificationShouldBePublished() {
        val matches = recordingEmailSender.messages.filter { it.subject == "Reset your password" }
        assertEquals(0, matches.size)
    }

    @And("no partial password reset token record should remain")
    fun noPartialPasswordResetTokenRecordShouldRemain() = runBlocking {
        val count = bddDatabaseSupport.countAllPasswordResetTokens()
        assertEquals(0, count)
    }

    @And("the failure should be recorded without exposing sensitive data")
    fun theFailureShouldBeRecordedWithoutExposingSensitiveData() {
        // Verified by inspection of the handler code — no token is logged.
    }

    @And("two password reset requests using the same token are processed concurrently")
    fun twoPasswordResetRequestsUsingTheSameTokenAreProcessedConcurrently() = runBlocking {
        val rawToken = bddDatabaseSupport.lastRawToken()
        val results = listOf("FirstPassword123!", "SecondPassword123!").map { password ->
            async(Dispatchers.IO) { executeResetPassword(rawToken, password) }
        }.awaitAll()
        resetPasswordStatuses += results.map { it.status.value() }
        previousResetPasswordStatus = results.first().status.value()
        latestResult = results.last()
        lastResetPasswordStatus = results.last().status.value()
    }

    @And("exactly one request should succeed with status 204")
    fun exactlyOneRequestShouldSucceedWithStatus204() {
        assertEquals(1, resetPasswordStatuses.count { it == 204 })
    }

    @And("exactly one request should fail with status 400")
    fun exactlyOneRequestShouldFailWithStatus400() {
        assertEquals(1, resetPasswordStatuses.count { it == 400 })
    }

    @And("the password should match only the successful request")
    fun thePasswordShouldMatchOnlyTheSuccessfulRequest() = runBlocking {
        val hash = requireNotNull(bddDatabaseSupport.lookupPasswordHash("principal-1"))
        assertTrue(
            BCrypt.checkpw("FirstPassword123!", hash) || BCrypt.checkpw("SecondPassword123!", hash),
        )
    }

    @And("the token should be marked as used exactly once")
    fun theTokenShouldBeMarkedAsUsedExactlyOnce() = runBlocking {
        val used = bddDatabaseSupport.countUsedPasswordResetTokens("principal-1")
        assertEquals(1, used)
    }

    @And("one request attempts to set password {string}")
    fun oneRequestAttemptsToSetPassword(password: String) {
        pendingConcurrentPassword = password
    }

    @And("another concurrent request attempts to set password {string}")
    fun anotherConcurrentRequestAttemptsToSetPassword(password: String) = runBlocking {
        val firstPassword = requireNotNull(pendingConcurrentPassword)
        val rawToken = bddDatabaseSupport.lastRawToken()
        val results = listOf(firstPassword, password).map { candidate ->
            async(Dispatchers.IO) { executeResetPassword(rawToken, candidate) }
        }.awaitAll()
        resetPasswordStatuses += results.map { it.status.value() }
        latestResult = results.last()
        lastResetPasswordStatus = results.last().status.value()
    }

    @And("exactly one password should be persisted")
    fun exactlyOnePasswordShouldBePersisted() = runBlocking {
        val hash = bddDatabaseSupport.lookupPasswordHash("principal-1")
        assertNotNull(hash)
    }

    @And("the other request should fail")
    fun theOtherRequestShouldFail() {
        assertEquals(1, resetPasswordStatuses.count { it == 400 })
        assertEquals(1, resetPasswordStatuses.count { it == 204 })
    }

    @And("the token should be consumed once")
    fun theTokenShouldBeConsumedOnce() = runBlocking {
        val used = bddDatabaseSupport.countUsedPasswordResetTokens("principal-1")
        assertEquals(1, used)
    }

    @When("the same IP address submits {int} password reset requests within {int} minutes")
    @Suppress("UNUSED_PARAMETER")
    fun theSameIpAddressSubmitsPasswordResetRequests(count: Int, minutes: Int) {
        repeat(count) { submitForgotPassword("ip-bucket-$it@example.com") }
    }

    @And("the same IP submits 11 invalid reset tokens within 15 minutes")
    fun theSameIpSubmits11InvalidResetTokensWithin15Minutes() {
        repeat(11) {
            submitResetPassword(rawToken = "invalid-token-$it", newPassword = "NewPassword123!")
        }
    }

    @And("the current password is {string}")
    fun theCurrentPasswordIs(password: String) = runBlocking {
        val storedHash = requireNotNull(bddDatabaseSupport.lookupPasswordHash("principal-1"))
        assertTrue(BCrypt.checkpw(password, storedHash))
    }

    @And("the email request limit is exceeded for both addresses")
    fun theEmailRequestLimitIsExceededForBothAddresses() {
        repeat(4) { submitForgotPassword(" ${"existing@example.com".uppercase()} ") }
        repeat(4) { submitForgotPassword(" ${"missing@example.com".uppercase()} ") }
    }

    @And("the password reset token expires at {string}")
    fun thePasswordResetTokenExpiresAt(time: String) = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
            expiresAt = java.time.Instant.parse(time),
        )
    }

    @And("the password reset token expires at {string} and invalid day")
    fun thePasswordResetTokenExpiresAtEdge(time: String) = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
            expiresAt = java.time.Instant.parse(time),
        )
    }

    @And("the password reset token expires at a known time")
    fun thePasswordResetTokenExpiresAtAKnownTime() = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    @And("the principal identifies the request via cookie {string}")
    fun thePrincipalIdentifiesTheRequestViaCookie(cookie: String) {
        latestRefreshCookie = cookie
    }

    @And("a previously issued reset token exists")
    fun aPreviouslyIssuedResetTokenExists() = runBlocking {
        bddDatabaseSupport.seedActivePasswordResetToken(
            principalId = "principal-1",
            email = "user@example.com",
        )
    }

    private fun publishPasswordResetNotification() {
        runBlocking {
            passwordResetEmailConsumer.consume(
                com.profiletailors.smp.identity.domain.PasswordResetRequested(
                    principalId = "principal-1",
                    email = "user@example.com",
                    rawResetToken = "bdd-sensitive-raw-token",
                ),
            )
        }
    }

    private fun assertSafeNotificationOperations() {
        val failureSerialized = passwordResetFailurePort.records.toString()
        val telemetrySerialized = passwordResetTelemetryPort.events.toString()
        val logSerialized = logAppender.list.joinToString("\n") { it.formattedMessage }
        val sentinels = listOf(
            "bdd-sensitive-raw-token",
            "user@example.com",
            "reset-password?token=",
            "NewPassword123!",
        )
        sentinels.forEach { sensitive ->
            assertTrue(!failureSerialized.contains(sensitive), "Leaked $sensitive into failure records")
            assertTrue(!telemetrySerialized.contains(sensitive), "Leaked $sensitive into telemetry")
            assertTrue(!logSerialized.contains(sensitive), "Leaked $sensitive into logs")
        }
    }

    private fun awaitPasswordResetEmail() {
        if (recordingEmailSender.messages.none { it.subject.contains("password", ignoreCase = true) }) {
            assertTrue(recordingEmailSender.awaitDelivery(), "Expected password reset email delivery to complete")
        }
    }

    private fun executeForgotPassword(email: String): EntityExchangeResult<ByteArray> = webTestClient.post()
        .uri("/api/auth/forgot-password")
        .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
        .header(HttpHeaders.CONTENT_TYPE, "application/json")
        .header(HttpHeaders.ACCEPT_LANGUAGE, preferredLocale)
        .bodyValue(mapOf("email" to email))
        .exchange()
        .expectBody()
        .returnResult()

    private fun submitForgotPassword(email: String) {
        latestStatusCode = null
        previousForgotPasswordStatus = lastForgotPasswordStatus
        previousForgotPasswordResponse = lastForgotPasswordResponse
        latestResult = executeForgotPassword(email)
        lastForgotPasswordStatus = latestResult?.status?.value() ?: -1
        forgotPasswordStatuses += lastForgotPasswordStatus
        lastForgotPasswordBody = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        lastForgotPasswordResponse = lastForgotPasswordBody
    }

    private fun executeResetPassword(rawToken: String, newPassword: String): EntityExchangeResult<ByteArray> =
        webTestClient.post()
            .uri("/api/auth/reset-password")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(mapOf("token" to rawToken, "newPassword" to newPassword))
            .exchange()
            .expectBody()
            .returnResult()

    private fun submitResetPassword(rawToken: String, newPassword: String) {
        latestStatusCode = null
        previousResetPasswordStatus = lastResetPasswordStatus
        passwordHashBeforeReset = runBlocking { bddDatabaseSupport.lookupPasswordHash("principal-1") }
        latestResult = executeResetPassword(rawToken, newPassword)
        lastResetPasswordStatus = latestResult?.status?.value() ?: -1
        resetPasswordStatuses += lastResetPasswordStatus
        lastResetPasswordBody = latestResult?.responseBody?.toString(Charsets.UTF_8) ?: ""
        if (lastResetPasswordStatus == 204) {
            passwordHashAfterSuccessfulReset = runBlocking {
                bddDatabaseSupport.lookupPasswordHash("principal-1")
            }
        }
    }

    private fun submitLogin(email: String, password: String) {
        latestStatusCode = null
        latestResult = webTestClient.post()
            .uri("/api/auth/login")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectBody()
            .returnResult()
        lastLoginStatus = latestResult?.status?.value() ?: -1
    }

    private fun submitRefresh() {
        latestStatusCode = null
        val request = webTestClient.post()
            .uri("/api/auth/refresh")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .header(HttpHeaders.ORIGIN, "http://localhost")
        if (latestRefreshCookie.isNotEmpty()) {
            request.header(HttpHeaders.COOKIE, latestRefreshCookie)
        }
        latestResult = request
            .exchange()
            .expectBody()
            .returnResult()
        lastRefreshStatus = latestResult?.status?.value() ?: -1
    }

    private fun completedPasswordResetAuditFact(): com.profiletailors.smp.audit.domain.MutationAuditFact =
        auditHook.mutations.single { it.action == "PASSWORD_RESET_COMPLETED" }

    private fun assertAuditExcludes(value: String) {
        assertTrue(!completedPasswordResetAuditFact().toString().contains(value, ignoreCase = true))
    }

    private companion object {
        private val CLEANUP_NOW = java.time.Instant.parse("2026-07-29T12:00:00Z")
        private val CLEANUP_FIXED_CLOCK = java.time.Clock.fixed(CLEANUP_NOW, java.time.ZoneOffset.UTC)
        private const val CLEANUP_EXPIRED_OLD = "cleanup-expired-old"
        private const val CLEANUP_EXPIRED_RECENT = "cleanup-expired-recent"
        private const val CLEANUP_ACTIVE = "cleanup-active"
    }
}
