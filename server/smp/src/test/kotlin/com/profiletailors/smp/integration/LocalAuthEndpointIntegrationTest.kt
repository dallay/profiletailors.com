package com.profiletailors.smp.integration

import com.profiletailors.smp.governance.application.RecordConsentHandler
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.identity.application.EmailVerificationTokenHasher
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import com.profiletailors.smp.tenancy.infrastructure.R2dbcWorkspaceProvisioningService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "app.security.local-jwt.secret=integration-test-local-jwt-secret-1234567890",
        "app.security.local-jwt.issuer=http://localhost/profiletailors-local",
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.refresh-session.cookie-name=pt_refresh",
        "app.security.refresh-session.cookie-path=/api/auth",
        "app.identity.registration.enabled=true",
        "app.security.auth-rate-limit.enabled=false",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
    ],
)
@Import(
    IntegrationTestBase.SharedTestConfiguration::class,
    LocalAuthEndpointIntegrationTest.LocalAuthTestConfiguration::class,
)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalAuthEndpointIntegrationTest : PostgresIntegrationTestBase() {

    @LocalServerPort
    var serverPort: Int = 0

    @Autowired
    lateinit var reactiveJwtDecoder: ReactiveJwtDecoder

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    private fun allowedOrigin(): String = "http://localhost"

    override suspend fun seedScenario() {
        failWorkspaceProvisioning = false
        failConsentRecording = false

        // Seed the WORKSPACE_OWNER role required by R2dbcWorkspaceProvisioningService
        databaseClient.sql(
            "INSERT INTO roles (id, role_key, category) VALUES ('role-owner', 'owner', 'WORKSPACE')",
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun countRows(sql: String): Long = databaseClient.sql(sql)
        .map { row, _ -> (row.get(0) as Number).toLong() }
        .one()
        .awaitSingle()

    private suspend fun assertNoRegistrationArtifacts(email: String) {
        assertEquals(
            0,
            countRows("SELECT COUNT(*) FROM user_identities WHERE email = '$email'"),
        )
        assertEquals(
            0,
            countRows(
                """
                SELECT COUNT(*)
                FROM principals p
                WHERE p.subject = 'local:$email'
                """.trimIndent(),
            ),
        )
        assertEquals(
            0,
            countRows(
                """
                SELECT COUNT(*)
                FROM local_password_credentials c
                JOIN user_identities ui ON ui.principal_id = c.principal_id
                WHERE ui.email = '$email'
                """.trimIndent(),
            ),
        )
        assertEquals(
            0,
            countRows("SELECT COUNT(*) FROM email_verification_tokens WHERE email = '$email'"),
        )
        assertEquals(0, countRows("SELECT COUNT(*) FROM workspaces"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM workspace_ownerships"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM workspace_memberships"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM membership_roles"))
        assertEquals(
            0,
            countRows(
                """
                SELECT COUNT(*)
                FROM refresh_sessions rs
                JOIN principals p ON p.id = rs.principal_id
                WHERE p.subject = 'local:$email'
                """.trimIndent(),
            ),
        )
        assertEquals(0, countRows("SELECT COUNT(*) FROM consent_records"))
    }

    private suspend fun assertRegistrationArtifactsCreated(email: String) {
        assertEquals(
            1,
            countRows("SELECT COUNT(*) FROM user_identities WHERE email = '$email'"),
        )
        assertEquals(
            1,
            countRows(
                """
                SELECT COUNT(*)
                FROM principals p
                WHERE p.subject = 'local:$email'
                """.trimIndent(),
            ),
        )
        assertEquals(
            1,
            countRows(
                """
                SELECT COUNT(*)
                FROM local_password_credentials c
                JOIN user_identities ui ON ui.principal_id = c.principal_id
                WHERE ui.email = '$email'
                """.trimIndent(),
            ),
        )
        assertEquals(
            1,
            countRows("SELECT COUNT(*) FROM email_verification_tokens WHERE email = '$email'"),
        )
        assertEquals(1, countRows("SELECT COUNT(*) FROM workspaces"))
        assertEquals(1, countRows("SELECT COUNT(*) FROM workspace_ownerships"))
        assertEquals(1, countRows("SELECT COUNT(*) FROM workspace_memberships"))
        assertEquals(1, countRows("SELECT COUNT(*) FROM membership_roles"))
        assertEquals(
            1,
            countRows(
                """
                SELECT COUNT(*)
                FROM refresh_sessions rs
                JOIN principals p ON p.id = rs.principal_id
                WHERE p.subject = 'local:$email'
                """.trimIndent(),
            ),
        )
        // Verify consent records: two CONTRACT_ACCEPTANCE records should exist
        assertEquals(
            2,
            countRows("SELECT COUNT(*) FROM consent_records WHERE consent_type = 'CONTRACT_ACCEPTANCE'"),
        )
        assertEquals(
            1,
            countRows("SELECT COUNT(*) FROM consent_records WHERE purpose = 'age-eligibility.18-plus'"),
        )
        assertEquals(
            1,
            countRows("SELECT COUNT(*) FROM consent_records WHERE purpose = 'terms.acceptance'"),
        )
    }

    override fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM refresh_sessions",
        "DELETE FROM local_password_credentials",
        "DELETE FROM email_verification_tokens",
        "DELETE FROM audit_events",
        "DELETE FROM workspace_target_scopes",
        "DELETE FROM workspace_direct_grants",
        "DELETE FROM workspace_entitlements",
        "DELETE FROM membership_roles",
        "DELETE FROM role_permissions",
        "DELETE FROM roles",
        "DELETE FROM permissions",
        "DELETE FROM workspace_memberships",
        "DELETE FROM workspace_ownerships",
        "DELETE FROM consent_records",
        "DELETE FROM workspaces",
        "DELETE FROM service_account_credentials",
        "DELETE FROM user_identities",
        "DELETE FROM principals",
    )

    @Test
    fun `public capabilities is unauthenticated and exposes only registration availability`() {
        webTestClient.get()
            .uri("/api/capabilities/public")
            .exchange()
            .expectStatus().isOk
            .expectBody().json("""{"registrationEnabled":true}""", true)
    }

    @Test
    fun `non allow-listed public route still requires authentication`() {
        webTestClient.get()
            .uri("/api/auth/me")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `verifies email without authentication and returns session tokens`() {
        val email = "verify-success@example.com"
        val rawToken = "verify-success-token"
        kotlinx.coroutines.runBlocking {
            seedVerificationCandidate(
                email = email,
                rawToken = rawToken,
                expiresAt = Instant.parse("2099-01-01T00:00:00Z"),
            )
        }

        webTestClient.post()
            .uri("/api/auth/verify-email")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(mapOf("token" to rawToken))
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(API_V1_MEDIA_TYPE)
            .expectHeader().exists(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.email").isEqualTo(email)
            .jsonPath("$.emailStatus").isEqualTo("VERIFIED")

        kotlinx.coroutines.runBlocking {
            assertEquals(
                "VERIFIED",
                databaseClient.sql("SELECT email_status FROM user_identities WHERE email = :email")
                    .bind("email", email)
                    .map { row, _ -> row.get("email_status", String::class.java) ?: error("Missing email_status") }
                    .one()
                    .awaitSingle(),
            )
            assertEquals(
                1,
                countRows(
                    "SELECT COUNT(*) FROM email_verification_tokens WHERE email = '$email' AND used_at IS NOT NULL",
                ),
            )
        }
    }

    @Test
    fun `rejects invalid verification token with 400`() {
        webTestClient.post()
            .uri("/api/auth/verify-email")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(mapOf("token" to "invalid-token"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.title").isEqualTo("Invalid verification token")
            .jsonPath("$.detail").isEqualTo("Invalid verification token.")
    }

    @Test
    fun `rejects expired verification token with 400`() {
        val rawToken = "expired-token"
        kotlinx.coroutines.runBlocking {
            seedVerificationCandidate(
                email = "expired-token@example.com",
                rawToken = rawToken,
                expiresAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        }

        webTestClient.post()
            .uri("/api/auth/verify-email")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(mapOf("token" to rawToken))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.title").isEqualTo("Invalid verification token")
            .jsonPath("$.detail").isEqualTo("Invalid verification token.")
    }

    @Test
    fun `rejects used verification token with 400`() {
        val rawToken = "used-token"
        kotlinx.coroutines.runBlocking {
            seedVerificationCandidate(
                email = "used-token@example.com",
                rawToken = rawToken,
                expiresAt = Instant.parse("2099-01-01T00:00:00Z"),
                usedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )
        }

        webTestClient.post()
            .uri("/api/auth/verify-email")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(mapOf("token" to rawToken))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.title").isEqualTo("Invalid verification token")
            .jsonPath("$.detail").isEqualTo("Invalid verification token.")
    }

    @Test
    fun `resend verification remains permit all and returns 202`() {
        webTestClient.post()
            .uri("/api/auth/resend-verification")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(mapOf("email" to "newuser@example.com"))
            .exchange()
            .expectStatus().isAccepted
    }

    @Test
    fun `registers user then login succeeds with pending email status`() {
        // Step 1: Register
        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "verify@example.com",
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().isCreated

        // Step 2: Login with PENDING email now succeeds
        webTestClient.post()
            .uri("/api/auth/login")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(mapOf("email" to "verify@example.com", "password" to "password123"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.emailStatus").isEqualTo("PENDING")

        // The verification token was generated during registration.
        // This test verifies that login succeeds with PENDING email status.
    }

    @Test
    fun `registration failure during workspace provisioning rolls back prior writes`() {
        val email = "rollback@example.com"
        failWorkspaceProvisioning = true

        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().is5xxServerError

        kotlinx.coroutines.runBlocking {
            assertNoRegistrationArtifacts(email)
        }
    }

    @Test
    fun `registration failure during consent recording rolls back prior writes`() {
        val email = "consent-rollback@example.com"
        failConsentRecording = true

        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().is5xxServerError

        kotlinx.coroutines.runBlocking {
            assertNoRegistrationArtifacts(email)
        }
    }

    @Test
    fun `successful registration persists all expected records`() {
        val email = "success-artifacts@example.com"

        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().isCreated

        kotlinx.coroutines.runBlocking {
            assertRegistrationArtifactsCreated(email)
        }
    }

    @Test
    fun `returns current user profile for issued token`() {
        val registerResult = registerAndExtract()

        webTestClient.get()
            .uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${registerResult.accessToken}")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(API_V1_MEDIA_TYPE)
            .expectBody()
            .jsonPath("$.email").isEqualTo("owner@example.com")
            .jsonPath("$.username").isEqualTo("owner")
            .jsonPath("$.displayIdentity").isEqualTo("owner")
    }

    @Test
    fun `login returns jwt with emailStatus pending claim`() {
        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "pending-claims@example.com",
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().isCreated

        val loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "pending-claims@example.com",
                    "password" to "password123",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.emailStatus").isEqualTo("PENDING")
            .returnResult()

        val payload = String(loginResult.responseBody ?: error("Missing response body"))
        val accessToken = Regex("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"").find(payload)?.groupValues?.get(1)
            ?: error("Could not extract access token from response")

        val jwt = decodeJwt(accessToken)

        kotlin.test.assertEquals("PENDING", jwt.getClaim<String>("emailStatus"))
    }

    @Test
    fun `refresh returns jwt with emailStatus verified claim`() {
        val registerResult = registerAndExtract()

        val refreshResult = webTestClient.post()
            .uri("/api/auth/refresh")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
            .header(HttpHeaders.ORIGIN, allowedOrigin())
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(API_V1_MEDIA_TYPE)
            .expectHeader().exists(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.email").isEqualTo("owner@example.com")
            .jsonPath("$.emailStatus").isEqualTo("VERIFIED")
            .returnResult()

        val payload = String(refreshResult.responseBody ?: error("Missing response body"))
        val accessToken = Regex("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"").find(payload)?.groupValues?.get(1)
            ?: error("Could not extract access token from response")

        val jwt = decodeJwt(accessToken)

        kotlin.test.assertEquals("VERIFIED", jwt.getClaim<String>("emailStatus"))
    }

    @Test
    fun `refreshes session using refresh cookie`() {
        val registerResult = registerAndExtract()

        webTestClient.post()
            .uri("/api/auth/refresh")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
            .header(HttpHeaders.ORIGIN, allowedOrigin())
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(API_V1_MEDIA_TYPE)
            .expectHeader().exists(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.email").isEqualTo("owner@example.com")
    }

    @Test
    fun `logout invalidates refresh cookie`() {
        val registerResult = registerAndExtract()

        webTestClient.post()
            .uri("/api/auth/logout")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
            .header(HttpHeaders.ORIGIN, allowedOrigin())
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isNoContent

        webTestClient.post()
            .uri("/api/auth/refresh")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `refresh rejects cookie-authenticated request without trusted origin`() {
        val registerResult = registerAndExtract()

        webTestClient.post()
            .uri("/api/auth/refresh")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `logout rejects cookie-authenticated request with untrusted origin`() {
        val registerResult = registerAndExtract()

        webTestClient.post()
            .uri("/api/auth/logout")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
            .header(HttpHeaders.ORIGIN, "https://evil.example")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `rejects invalid password`() {
        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "badlogin@example.com",
                    "password" to "password123",
                    "confirmedAgeEligibility" to true,
                    "acceptedTermsVersion" to "terms-v1.0.0",
                ),
            )
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/api/auth/login")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "badlogin@example.com",
                    "password" to "wrongpass",
                ),
            )
            .exchange()
            .expectStatus().isUnauthorized
    }

    /**
     * Seeds a pre-verified user directly via DB to test the existing flow.
     * This simulates the migration behavior (existing users are VERIFIED).
     */
    private fun registerAndExtract(): RegisterResult {
        // Seed a user with VERIFIED status directly to test existing user flow
        kotlinx.coroutines.runBlocking {
            databaseClient.sql(
                """
                INSERT INTO principals (id, principal_type, subject, provider, display_identity)
                VALUES ('owner-1', 'USER', 'local:owner@example.com', NULL, 'owner')
                """.trimIndent(),
            ).fetch().rowsUpdated().block()!!

            databaseClient.sql(
                """
                INSERT INTO user_identities (principal_id, email, username, email_status)
                VALUES ('owner-1', 'owner@example.com', 'owner', 'VERIFIED')
                """.trimIndent(),
            ).fetch().rowsUpdated().block()!!

            val passwordHash = BCrypt.hashpw("password123", BCrypt.gensalt())
            databaseClient.sql(
                """
                INSERT INTO local_password_credentials (principal_id, password_hash)
                VALUES ('owner-1', :passwordHash)
                """.trimIndent(),
            )
                .bind("passwordHash", passwordHash)
                .fetch().rowsUpdated().block()!!
        }

        val result = webTestClient.post()
            .uri("/api/auth/login")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "owner@example.com",
                    "password" to "password123",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .returnResult()

        val payload = String(result.responseBody ?: error("Missing response body"))
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
            ?: error("Could not extract access token from response")
        val refreshCookie = result.responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
            ?: error("Missing refresh cookie")
        return RegisterResult(accessToken, refreshCookie.substringBefore(';'))
    }

    private data class RegisterResult(val accessToken: String, val refreshCookie: String)

    private suspend fun seedVerificationCandidate(
        email: String,
        rawToken: String,
        expiresAt: Instant,
        usedAt: Instant? = null,
    ) {
        val principalId = "principal-${email.substringBefore('@')}"
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:principalId, 'USER', :subject, NULL, :displayIdentity)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("subject", "local:$email")
            .bind("displayIdentity", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES (:principalId, :email, :username, 'PENDING')
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("email", email)
            .bind("username", email.substringBefore('@'))
            .fetch().rowsUpdated().awaitSingle()

        val tokenInsert = databaseClient.sql(
            """
            INSERT INTO email_verification_tokens (email, token_hash, expires_at, used_at)
            VALUES (:email, :tokenHash, :expiresAt, :usedAt)
            """.trimIndent(),
        )
            .bind("email", email)
            .bind("tokenHash", EmailVerificationTokenHasher.hash(rawToken))
            .bind("expiresAt", expiresAt)

        if (usedAt == null) {
            tokenInsert.bindNull("usedAt", Instant::class.java)
        } else {
            tokenInsert.bind("usedAt", usedAt)
        }
            .fetch().rowsUpdated().awaitSingle()
    }

    private fun decodeJwt(tokenValue: String): Jwt = reactiveJwtDecoder.decode(tokenValue).block()
        ?: error("Failed to decode JWT")

    @TestConfiguration
    class LocalAuthTestConfiguration {
        @Bean
        @Primary
        fun workspaceProvisioningService(realService: R2dbcWorkspaceProvisioningService): WorkspaceProvisioningService =
            WorkspaceProvisioningService { principalId, displayName ->
                val result = realService.provisionDefaultWorkspace(principalId, displayName)
                if (failWorkspaceProvisioning) {
                    error("Simulated workspace provisioning failure (post-write)")
                }
                result
            }

        @Bean
        @Primary
        fun recordConsentHandler(repository: ConsentRepository): RecordConsentHandler {
            val delegate = RecordConsentHandler(repository)
            return mockk {
                coEvery { handle(any()) } coAnswers {
                    if (failConsentRecording) {
                        error("Simulated consent recording failure")
                    }
                    delegate.handle(firstArg())
                }
            }
        }

        @Bean
        @Primary
        fun reactiveJwtDecoder(@Value("\${app.security.local-jwt.secret}") secret: String): ReactiveJwtDecoder =
            NimbusReactiveJwtDecoder
                .withSecretKey(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgresTestContainerSupport.newContainer("local_auth_e2e")

        @Volatile
        var failWorkspaceProvisioning: Boolean = false

        @Volatile
        var failConsentRecording: Boolean = false

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
