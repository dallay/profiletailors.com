package com.profiletailors.smp.integration

import com.profiletailors.smp.identity.infrastructure.security.AdminMfaFilter
import com.profiletailors.smp.identity.infrastructure.security.ApiKeyAuthenticationWebFilter
import com.profiletailors.smp.identity.infrastructure.security.LocalPrincipalOidcUserService
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.core.ResolvableType
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.util.UriComponentsBuilder
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@ContextConfiguration(classes = [TestStorageConfiguration::class])
@Import(IntegrationTestBase.SharedTestConfiguration::class)
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "platform.storage.default=attachments",
        "platform.storage.providers.attachments.type=local",
        "platform.storage.providers.attachments.base-path=./tmp/legacy-auth-chain-test-storage",
        "media.storage.bucket=attachments",
        "spring.ai.mcp.server.enabled=true",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/api/mcp",
        "spring.security.oauth2.client.provider.test.authorization-uri=http://localhost/keycloak/authorize",
        "spring.security.oauth2.client.provider.test.token-uri=http://localhost/keycloak/token",
        "spring.security.oauth2.client.provider.test.user-info-uri=http://localhost/keycloak/userinfo",
        "spring.security.oauth2.client.provider.test.user-name-attribute=sub",
        "spring.security.oauth2.client.provider.test.jwk-set-uri=http://localhost/keycloak/certs",
        "spring.security.oauth2.client.registration.pt-app.provider=test",
        "spring.security.oauth2.client.registration.pt-admin.provider=test",
        "spring.security.oauth2.client.registration.pt-app.client-secret=test-app-secret",
        "spring.security.oauth2.client.registration.pt-admin.client-secret=test-admin-secret",
    ],
)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class LegacyMachineAuthenticationChainIntegrationTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @LocalServerPort
    private var port: Int = 0

    @MockitoSpyBean
    private lateinit var adminMfaFilter: AdminMfaFilter

    @Test
    fun `admin mfa authorization filter runs once in the configured application filter chain`() {
        client().get().uri("/api/auth/csrf").exchange().expectStatus().isOk()

        assertEquals(1, mockingDetails(adminMfaFilter).invocations.count { it.method.name == "filter" })
    }

    @Test
    fun `local principal oidc user service is selected by the configured application context`() {
        val service = applicationContext.getBeanProvider<Any>(
            ResolvableType.forClassWithGenerics(
                ReactiveOAuth2UserService::class.java,
                OidcUserRequest::class.java,
                OidcUser::class.java,
            ),
        ).getObject()

        assertEquals(LocalPrincipalOidcUserService::class.java, service.javaClass)
    }

    @Test
    fun `pt-app authorization route redirects to local provider with fixed callback`() {
        val location = authorizationLocation("pt-app")

        assertEquals("http://localhost/keycloak/authorize", location.substringBefore('?'))
        assertEquals(
            "https://api.profiletailors.com/login/oauth2/code/pt-app",
            UriComponentsBuilder.fromUriString(location).build().queryParams.getFirst("redirect_uri"),
        )
    }

    @Test
    fun `pt-admin authorization route redirects to local provider with fixed callback`() {
        val location = authorizationLocation("pt-admin")

        assertEquals("http://localhost/keycloak/authorize", location.substringBefore('?'))
        assertEquals(
            "https://api.profiletailors.com/login/oauth2/code/pt-admin",
            UriComponentsBuilder.fromUriString(location).build().queryParams.getFirst("redirect_uri"),
        )
    }

    @Test
    fun `unauthenticated json api request returns unauthorized without redirect`() {
        val result = client().get().uri("/api/authorization/workspace-access/current")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()

        result.expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `arbitrary protected api route does not start oidc login`() {
        val result = client().get().uri("/api/not-a-login-route")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()

        result.expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `unauthenticated html request to an api route does not redirect`() {
        client().get().uri("/api/not-a-login-route")
            .header("Accept", "text/html")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `csrf endpoint returns token and creates secure host only session cookie`() {
        val result = client().get().uri("/api/auth/csrf").exchange()

        result.expectStatus().isOk()
            .expectBody()
            .jsonPath("$.headerName").isEqualTo("X-CSRF-TOKEN")
            .jsonPath("$.token").value<String> { assertNotNull(it) }

        val sessionCookie = result.returnResult(String::class.java).responseHeaders.getFirst("Set-Cookie")

        assertNotNull(sessionCookie)
        val actualSessionCookie = requireNotNull(sessionCookie)
        assertEquals(true, actualSessionCookie.startsWith("SESSION="))
        assertEquals(true, actualSessionCookie.contains("Path=/"))
        assertEquals(true, actualSessionCookie.contains("Secure"))
        assertEquals(true, actualSessionCookie.lowercase().contains("httponly"), actualSessionCookie)
        assertEquals(true, actualSessionCookie.lowercase().contains("samesite=lax"))
        assertEquals(false, actualSessionCookie.lowercase().contains("domain="))
    }

    @Test
    fun `session cookie and bearer credential are rejected by the configured security chain`() {
        client().get().uri("/api/auth/csrf")
            .cookie("SESSION", "session-and-bearer")
            .header("Authorization", "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `logout rejects a session cookie request without csrf token`() {
        client().post().uri("/api/auth/logout")
            .cookie("SESSION", "session-without-csrf")
            .exchange()
            .expectStatus().isForbidden()
    }

    @Test
    fun `session cookie alone does not substitute for mcp bearer authentication`() {
        val result = WebTestClient.bindToApplicationContext(applicationContext)
            .configureClient()
            .build()
            .mutateWith(csrf())
            .get().uri("/api/mcp")
            .cookie("SESSION", "cookie-only-characterization")
            .exchange()

        result.expectStatus().isUnauthorized()
            .expectHeader().valueEquals(
                "WWW-Authenticate",
                """Bearer realm="mcp", resource="https://api.profiletailors.com/api/mcp"""",
            )
    }

    @Test
    fun `legacy local login remains reachable before cutover`() {
        client().post().uri("/api/auth/login")
            .header("Content-Type", "application/json")
            .bodyValue("{}")
            .exchange()
            .expectStatus().isBadRequest
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `legacy bearer and api key machine credentials remain handled by the active chain before cutover`() {
        val activeFilterTypes: List<Class<*>> = applicationContext
            .getBean("securityWebFilterChain", SecurityWebFilterChain::class.java)
            .webFilters
            .toIterable()
            .map { it.javaClass }
            .toList()

        assertTrue(
            activeFilterTypes.any { it == AuthenticationWebFilter::class.java },
            "active chain must keep bearer authentication before cutover: $activeFilterTypes",
        )
        assertTrue(
            activeFilterTypes.any { it == ApiKeyAuthenticationWebFilter::class.java },
            "active chain must keep api key authentication before cutover: $activeFilterTypes",
        )
    }

    @Test
    fun `oidc callback error does not establish an authenticated session`() {
        val result = client().get().uri("/login/oauth2/code/pt-app?error=access_denied&state=invalid")
            .exchange()

        result.expectStatus().is3xxRedirection()
            .expectHeader().doesNotExist("Set-Cookie")
    }

    private fun authorizationLocation(registrationId: String): String {
        val result = client().get().uri("/oauth2/authorization/$registrationId").exchange()
        result.expectStatus().is3xxRedirection()
        return result.returnResult(String::class.java).responseHeaders.getFirst("Location")
            ?: error("OIDC initiation response must contain a Location header")
    }

    private fun client(): WebTestClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:$port")
        .build()

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("legacy_auth_chain")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
