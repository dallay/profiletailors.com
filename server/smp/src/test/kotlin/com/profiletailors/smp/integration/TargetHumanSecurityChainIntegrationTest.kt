package com.profiletailors.smp.integration

import com.profiletailors.common.domain.authentication.CredentialType
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.infrastructure.security.IdentitySecurityChains
import com.profiletailors.smp.identity.infrastructure.security.ResolvedOidcUser
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.WebSession
import org.springframework.web.server.session.DefaultWebSessionManager
import org.springframework.web.server.session.WebSessionManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@ContextConfiguration(classes = [TestStorageConfiguration::class])
@Import(
    IntegrationTestBase.SharedTestConfiguration::class,
    TargetHumanSecurityChainIntegrationTest.TargetChainConfiguration::class,
)
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "platform.storage.default=attachments",
        "platform.storage.providers.attachments.type=local",
        "platform.storage.providers.attachments.base-path=./tmp/target-human-chain-test-storage",
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
class TargetHumanSecurityChainIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var webSessionManager: WebSessionManager

    private fun client(): WebTestClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:$port")
        .build()

    @Test
    fun `unauthenticated api request receives unauthorized without redirect`() {
        client().get().uri("/api/authorization/workspace-access/current")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `target chain rejects local bearer token`() {
        requestWithBearer("owner-token")
    }

    @Test
    fun `target chain rejects service account bearer token`() {
        requestWithBearer("service-account-token")
    }

    @Test
    fun `target chain rejects api key bearer format`() {
        requestWithBearer("ptk_lookup.secret-value")
    }

    @Test
    fun `target chain does not expose legacy local login`() {
        client().post().uri("/api/auth/login")
            .header("Content-Type", "application/json")
            .bodyValue("{}")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    @Test
    fun `authenticated bff session authorizes protected resource`() {
        val session = authenticatedSession()

        client().get().uri("/api/target-chain/protected")
            .cookie("SESSION", session.id)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String::class.java).isEqualTo("authorized")
    }

    @Test
    fun `mcp route remains handled by its credential specific chain`() {
        WebTestClient.bindToApplicationContext(applicationContext)
            .configureClient()
            .build()
            .mutateWith(csrf()).get().uri("/api/mcp")
            .cookie("SESSION", "target-chain-session")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(
                "WWW-Authenticate",
                """Bearer realm="mcp", resource="https://api.profiletailors.com/api/mcp"""",
            )
    }

    private fun requestWithBearer(token: String) {
        client().get().uri("/api/authorization/workspace-access/current")
            .header("Authorization", "Bearer $token")
            .header("X-Workspace-Id", "d3d9800f-120a-501e-ad01-401e54e8f766")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("Location")
    }

    private fun authenticatedSession(): WebSession {
        val manager = webSessionManager as DefaultWebSessionManager
        val store = manager.sessionStore
        val session = store.createWebSession().block() ?: error("Web session was not created")
        val now = Instant.now()
        val idToken = OidcIdToken(
            "test-id-token",
            now,
            now.plusSeconds(300),
            mapOf(
                "iss" to "https://keycloak.ahome.quest/realms/pt",
                "sub" to "bff-session-subject",
                "aud" to listOf("pt-app"),
            ),
        )
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val oidcUser = DefaultOidcUser(authorities, idToken)
        val authenticatedPrincipal = AuthenticatedPrincipal(
            PrincipalContext(
                principalId = "00000000-0000-0000-0000-000000000101",
                principalType = PrincipalType.USER,
                subject = "bff-session-subject",
                provider = "keycloak",
            ),
            credentialType = CredentialType.JWT,
            emailStatus = EmailStatus.VERIFIED,
        )
        val resolvedUser = ResolvedOidcUser(oidcUser, authenticatedPrincipal)
        val authentication = OAuth2AuthenticationToken(resolvedUser, authorities, "pt-app")
        session.attributes[WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME] =
            SecurityContextImpl(authentication)
        session.start()
        session.save().block()
        return session
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TargetChainConfiguration {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 60)
        fun targetHumanSecurityWebFilterChain(
            http: org.springframework.security.config.web.server.ServerHttpSecurity,
            identitySecurityChains: IdentitySecurityChains,
        ): SecurityWebFilterChain = identitySecurityChains.targetHumanChain(http)

        @Bean
        fun targetChainProtectedController() = TargetChainProtectedController()
    }

    @RestController
    class TargetChainProtectedController {
        @GetMapping("/api/target-chain/protected", produces = ["text/plain"])
        fun protectedResource(): String = AUTHORIZED_BODY

        private companion object {
            const val AUTHORIZED_BODY = "authorized"
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("target_human_auth_chain")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
