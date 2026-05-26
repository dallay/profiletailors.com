package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.support.CapturingAuditHook
import com.profiletailors.smp.integration.support.IntegrationTestBase
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///local_auth?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:local_auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "spring.main.allow-bean-definition-overriding=true",
        "app.security.local-jwt.secret=integration-test-local-jwt-secret-1234567890",
        "app.security.local-jwt.issuer=http://localhost/profiletailors-local",
        "app.security.refresh-session.cookie-name=pt_refresh",
        "app.security.refresh-session.cookie-path=/api/auth",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
    ],
)
@Import(LocalAuthEndpointIntegrationTest.H2ConnectionFactoryConfiguration::class)
class LocalAuthEndpointIntegrationTest : IntegrationTestBase() {

    override fun databaseName(): String = "local_auth"

    override suspend fun seedScenario() = Unit

    override fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM refresh_sessions",
        "DELETE FROM local_password_credentials",
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
        "DELETE FROM workspaces",
        "DELETE FROM service_account_credentials",
        "DELETE FROM user_identities",
        "DELETE FROM principals",
    )

    @Test
    fun `registers and logs in user with local credentials plus refresh cookie`() {
        val registerResponse = webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "yuniel@example.com",
                    "password" to "password123",
                    "username" to "yuniel",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(API_V1_MEDIA_TYPE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.email").isEqualTo("yuniel@example.com")
            .returnResult()

        val refreshCookie = registerResponse.responseHeaders.getFirst(HttpHeaders.SET_COOKIE)
        require(!refreshCookie.isNullOrBlank())

        webTestClient.post()
            .uri("/api/auth/login")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "yuniel@example.com",
                    "password" to "password123",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(API_V1_MEDIA_TYPE)
            .expectHeader().exists(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.tokenType").isEqualTo("Bearer")
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
    fun `refreshes session using refresh cookie`() {
        val registerResult = registerAndExtract()

        webTestClient.post()
            .uri("/api/auth/refresh")
            .header(HttpHeaders.COOKIE, registerResult.refreshCookie)
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
    fun `rejects invalid password`() {
        webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "badlogin@example.com",
                    "password" to "password123",
                ),
            )
            .exchange()
            .expectStatus().isOk

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

    private fun registerAndExtract(): RegisterResult {
        val result = webTestClient.post()
            .uri("/api/auth/register")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .bodyValue(
                mapOf(
                    "email" to "owner@example.com",
                    "password" to "password123",
                    "username" to "owner",
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

    private data class RegisterResult(
        val accessToken: String,
        val refreshCookie: String,
    )

    @TestConfiguration
    class H2ConnectionFactoryConfiguration {
        @Bean
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("local_auth")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build(),
        )

        @Bean
        @Primary
        fun testAuditHook(): CapturingAuditHook = CapturingAuditHook()

        @Bean
        @Primary
        fun localAuthJwtDecoder(
            @Value("\${app.security.local-jwt.secret}") secret: String,
        ): ReactiveJwtDecoder = NimbusReactiveJwtDecoder
            .withSecretKey(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
    }
}
