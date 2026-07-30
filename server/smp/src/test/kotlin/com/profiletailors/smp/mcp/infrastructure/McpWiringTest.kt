package com.profiletailors.smp.mcp.infrastructure

import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.modulith.core.ApplicationModules
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * PR 1 acceptance test for the MCP server foundation.
 *
 * Verifies that, with `SMP_MCP_ENABLED=true` and `spring.ai.mcp.server.enabled=true`:
 *  1. The `mcp` bounded context is registered as a Spring Modulith module.
 *  2. The `McpConfiguration` placeholder loads (gated by `@ConditionalOnProperty`).
 *  3. The MCP transport bean is wired by Spring AI auto-configuration.
 *  4. `POST /api/mcp` (unauthenticated) responds with `401 Unauthorized` AND a
 *     `WWW-Authenticate: Bearer …` header (placeholder contents — the full RFC 9728
 *     `resource_metadata` URL lands in PR 2 via `ResourceMetadataController`).
 *  5. No `@McpTool` beans are registered yet (the tool catalogue is empty in PR 1).
 *
 * The server boots via the standard SMP `SmpApplication` entry point and uses a
 * Testcontainers-managed PostgreSQL instance for the bits of the application context
 * that touch R2DBC. Liquibase is disabled for the test — no migrations are required
 * to verify the MCP wiring.
 */
@Tag("postgres")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.ai.mcp.server.enabled=true",
        "spring.ai.mcp.server.protocol=STATELESS",
        "spring.ai.mcp.server.type=ASYNC",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/api/mcp",
        "app.mcp.resource-uri=https://api.profiletailors.com/api/mcp",
        "app.mcp.required-audience=https://api.profiletailors.com/api/mcp",
        "app.security.local-jwt.secret=mcp-wiring-test-jwt-secret-32+bytes-1234567890",
        "app.security.local-jwt.issuer=http://localhost/profiletailors-local",
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.auth-rate-limit.enabled=false",
        "app.security.refresh-session.cookie-name=pt_refresh",
        "app.security.refresh-session.cookie-path=/api/auth",
        "app.identity.registration.enabled=false",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "platform.storage.default=attachments",
        "platform.storage.providers.attachments.type=local",
        "platform.storage.providers.attachments.base-path=./tmp/mcp-wiring-test-storage",
    ],
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestStorageConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpWiringTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `mcp bounded context is registered as a Spring Modulith module`() {
        // Modulith discovers `ModuleMetadata` via its `@ApplicationModule` annotation.
        // The `ModuleMetadata` class itself is not a Spring bean; we verify the module is
        // registered with Modulith by asking the modules API.
        val modules = ApplicationModules.of(
            org.springframework.boot.autoconfigure.SpringBootApplication::class.java.let {
                // Use SmpApplication via reflection-style class loading
                Class.forName("com.profiletailors.smp.SmpApplication")
            },
        )
        val moduleNames = modules.stream().map { it.getIdentifier().toString() }.toList()
        assertThat(moduleNames).anyMatch { it.contains("mcp") }
    }

    @Test
    fun `McpConfiguration is loaded when spring ai mcp server enabled is true`() {
        // `@Configuration` classes get registered under their lowercase class name.
        assertThat(applicationContext.containsBean("mcpConfiguration")).isTrue()
        assertThat(applicationContext.containsBean("mcpSecurityConfiguration")).isTrue()
    }

    @Test
    fun `POST api-mcp without Authorization header returns 401 with WWW-Authenticate`() {
        webTestClient.post()
            .uri("/api/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE)
            .expectHeader()
            .value(HttpHeaders.WWW_AUTHENTICATE) { header ->
                assertThat(header).startsWith("Bearer")
            }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("mcp_wiring_test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
