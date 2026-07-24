package com.profiletailors.smp.integration.support

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.infrastructure.metrics.StorageMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

abstract class ResourcePreviewEndpointTestBase : AuthorizationEndpointIntegrationTestSupport() {

    companion object {
        const val API_V1_MEDIA_TYPE = "application/vnd.api.v1+json"
        const val PRINCIPAL_ID = "principal-1"
        const val WORKSPACE_ID = "workspace-1"
        const val RESOURCE_ID = "resource-1"
        const val BEARER_TOKEN = "Bearer valid-token"
        const val WORKSPACE_HEADER = "X-Workspace-Id"
        const val RESOURCE_PREVIEW_PATH = "/api/authorization/resources/resource-1/preview"
        const val GET_RESOURCE_PREVIEW_QUERY =
            "com.profiletailors.smp.authorization.application.resource.getpreview.GetResourcePreviewQuery"
        const val PERMISSION_RESOURCE_READ = "workspace:resource:read"
        const val JSON_PATH_DETAIL = "$.detail"
    }

    /** Each subclass provides the JDBC URL for Liquibase baseline migrations. */

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `allows resource preview when base permission exists and scope matches target`() {
        seedMemberWithPreviewPermission()
        seedTargetScope(allowedTargetIdsJson = "[\"$RESOURCE_ID\"]")

        expectPreviewAllowed()
        assertAuthorizationFacts(listOf(expectedFact(AuthorizationReasonCode.ROLE_PERMISSION, allow = true)))
    }

    @Test
    fun `allows resource preview when base permission exists and no scope row`() {
        seedMemberWithPreviewPermission()

        expectPreviewAllowed()
        assertAuthorizationFacts(listOf(expectedFact(AuthorizationReasonCode.ROLE_PERMISSION, allow = true)))
    }

    @Test
    fun `denies resource preview when scope excludes target`() {
        seedMemberWithPreviewPermission()
        seedTargetScope(allowedTargetIdsJson = "[\"resource-2\"]")

        expectPreviewDenied("You do not have permission to perform this action.")
        assertAuthorizationFacts(listOf(expectedFact(AuthorizationReasonCode.SCOPE_REDUCED_TARGET, allow = false)))
    }

    @Test
    fun `denies resource preview when base permission is missing even if scope exists`() {
        seedMemberWithoutPreviewPermission()
        seedScopePermission()
        seedTargetScope(allowedTargetIdsJson = "[\"$RESOURCE_ID\"]")

        expectPreviewDenied("You do not have permission to perform this action.")
        assertAuthorizationFacts(listOf(expectedFact(AuthorizationReasonCode.MISSING_PERMISSION, allow = false)))
    }

    @Test
    fun `scope resolver remains narrow without wildcard or non-workspace behavior`() {
        seedMemberWithPreviewPermission()
        seedTargetScope(allowedTargetIdsJson = "[\"resource-*\"]")

        expectPreviewDenied("You do not have permission to perform this action.")
        assertAuthorizationFacts(listOf(expectedFact(AuthorizationReasonCode.SCOPE_REDUCED_TARGET, allow = false)))
    }

    // ── Assertions ─────────────────────────────────────────────────────────────

    protected fun assertAuthorizationFacts(expected: List<AuthorizationDecisionAuditFact>) {
        assertEquals(expected, auditHook.facts)
    }

    private fun expectPreviewAllowed() {
        previewRequest()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo(WORKSPACE_ID)
            .jsonPath("$.resourceId").isEqualTo(RESOURCE_ID)
            .jsonPath("$.principalId").isEqualTo(PRINCIPAL_ID)
            .jsonPath("$.previewAllowed").isEqualTo(true)
    }

    private fun expectPreviewDenied(detail: String) {
        previewRequest()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath(JSON_PATH_DETAIL)
            .isEqualTo(detail)
    }

    private fun previewRequest() = webTestClient.get()
        .uri(RESOURCE_PREVIEW_PATH)
        .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
        .header(WORKSPACE_HEADER, WORKSPACE_ID)
        .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
        .exchange()

    private fun expectedFact(reasonCode: AuthorizationReasonCode, allow: Boolean) = AuthorizationDecisionAuditFact(
        requestName = GET_RESOURCE_PREVIEW_QUERY,
        requestPath = RESOURCE_PREVIEW_PATH,
        permission = PERMISSION_RESOURCE_READ,
        principalId = PRINCIPAL_ID,
        workspaceId = WORKSPACE_ID,
        decision = if (allow) {
            com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW.name
        } else {
            com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name
        },
        reasonCode = reasonCode.name,
        roleKeys = listOf("member"),
    )

    // ── Seed helpers ───────────────────────────────────────────────────────────

    protected fun seedMemberWithPreviewPermission() {
        kotlinx.coroutines.runBlocking {
            seedPrincipalAndMembership()
            seedRolePermission(
                permissionId = "permission-resource-read",
                permissionKey = PERMISSION_RESOURCE_READ,
            )
        }
    }

    protected fun seedMemberWithoutPreviewPermission() {
        kotlinx.coroutines.runBlocking {
            seedPrincipalAndMembership()
            seedRolePermission(
                permissionId = "permission-members-manage",
                permissionKey = "workspace:members:manage",
            )
        }
    }

    private suspend fun seedPrincipalAndMembership() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('$PRINCIPAL_ID', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('$PRINCIPAL_ID', 'yuniel@example.com', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('$WORKSPACE_ID', 'Profile Tailors', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('membership-1', '$WORKSPACE_ID', '$PRINCIPAL_ID', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')",
        ).fetch().rowsUpdated().awaitSingle()
    }

    protected fun seedTargetScope(allowedTargetIdsJson: String) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql(
                """
                INSERT INTO workspace_target_scopes (
                    id, workspace_id, principal_id, principal_type, permission_id,
                    target_resource_type, allowed_target_ids_json
                ) VALUES (
                    'scope-1', '$WORKSPACE_ID', '$PRINCIPAL_ID', 'USER',
                    'permission-resource-read', 'RESOURCE', :allowedTargetIdsJson
                )
                """.trimIndent(),
            )
                .bind("allowedTargetIdsJson", allowedTargetIdsJson)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    protected fun seedScopePermission() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql(
                """
                INSERT INTO permissions (id, permission_key)
                VALUES ('permission-resource-read', '$PERMISSION_RESOURCE_READ')
                """.trimIndent(),
            )
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    // ── Shared @TestConfiguration ──────────────────────────────────────────────

    @TestConfiguration
    class SharedTestBeans {
        @Bean
        fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            when (token) {
                "valid-token" -> kotlinx.coroutines.reactor.mono {
                    Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("sub", "subject-123")
                        .claim("iss", "https://issuer.example")
                        .claim("preferred_username", "yuniel")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build()
                }

                else -> reactor.core.publisher.Mono.error(BadJwtException("Invalid token"))
            }
        }

        @Bean
        @Primary
        fun testAuditHook(): CapturingAuditHook = CapturingAuditHook()

        @Bean
        @Primary
        fun inMemoryFakeStorage(): Storage = object : Storage {
            private val objects = ConcurrentHashMap<String, ByteArray>()

            override suspend fun upload(
                bucket: String,
                key: String,
                content: Flow<ByteArray>,
                metadata: Map<String, String>,
            ) {
                val chunks = mutableListOf<ByteArray>()
                content.collect { chunks += it }
                objects["$bucket/$key"] = chunks.flatMap { it.toList() }.toByteArray()
            }

            override fun download(bucket: String, key: String): Flow<ByteArray> {
                val data = objects["$bucket/$key"]
                    ?: throw StorageObjectNotFoundException(bucket, key)
                return flowOf(data)
            }

            override suspend fun delete(bucket: String, key: String) {
                objects.remove("$bucket/$key")
            }

            override suspend fun list(bucket: String, prefix: String): List<String> = objects.keys
                .filter { it.startsWith("$bucket/$prefix") }
                .map { it.removePrefix("$bucket/") }

            override suspend fun exists(bucket: String, key: String): Boolean = objects.containsKey("$bucket/$key")

            override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
                val sourcePath = "$bucket/$sourceKey"
                val data = objects[sourcePath]
                    ?: throw IllegalStateException("copyObject: source not found: $sourceKey")
                objects["$bucket/$destKey"] = data
            }
        }

        @Bean
        fun noOpEventPublisher(): EventPublisher<BaseDomainEvent> = object : EventPublisher<BaseDomainEvent> {
            override suspend fun publish(event: BaseDomainEvent) {
                // discard
            }
        }

        @Bean
        @Primary
        fun storageApplicationService(
            inMemoryFakeStorage: Storage,
            noOpEventPublisher: EventPublisher<BaseDomainEvent>,
        ): StorageApplicationService = StorageApplicationService(
            storage = inMemoryFakeStorage,
            eventPublisher = noOpEventPublisher,
            metrics = StorageMetrics(SimpleMeterRegistry()),
        )
    }
}
