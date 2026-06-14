package com.profiletailors.smp.integration.support

import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialCommand
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialHandler
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import java.time.Instant

/**
 * Abstract base for workspace-access-summary endpoint integration tests.
 *
 * Subclasses only need to wire the Spring context (H2 vs Postgres) and
 * implement the three Liquibase coordinate methods.
 */
@Suppress("LargeClass")
abstract class WorkspaceAccessSummaryEndpointTestBase : AuthorizationEndpointIntegrationTestSupport() {

    @Autowired
    protected lateinit var replaceApiKeyCredentialHandler: ReplaceApiKeyCredentialHandler

    override fun additionalCleanupStatements(): List<String> = listOf(
        "DELETE FROM api_key_credentials",
        "DELETE FROM service_account_credentials",
    )

    companion object {
        private const val API_V1_MEDIA_TYPE = "application/vnd.api.v1+json"
        private const val GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN =
            "com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery"
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `returns workspace access summary for entitled authorized member`() {
        seedAuthorizedMember(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")
            .jsonPath("$.roles[0]").isEqualTo("member")
            .jsonPath("$.permissions[0]").isEqualTo("workspace:access:read")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW.name,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies entitled member without required permission`() {
        seedMemberWithoutPermission(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `workspace access summary remains unaffected by target scopes`() {
        seedAuthorizedMember(entitled = true)
        seedTargetScope(allowedTargetIdsJson = "[\"resource-99\"]")

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW.name,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `allows entitled member through persisted direct allow grant`() {
        seedMemberWithoutPermission(entitled = true)
        seedDirectGrant(effect = "ALLOW", permissionId = "permission-1", permissionKey = "workspace:access:read")

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW.name,
                    reasonCode = AuthorizationReasonCode.DIRECT_ALLOW.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `direct deny overrides entitled role based allow`() {
        seedAuthorizedMember(entitled = true)
        seedDirectGrant(
            effect = "DENY",
            permissionId = "permission-1",
            permissionKey = "workspace:access:read",
            insertPermission = false,
        )

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.DIRECT_DENY.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `expired direct grant is ignored on entitled member`() {
        seedMemberWithoutPermission(entitled = true)
        seedDirectGrant(
            effect = "ALLOW",
            permissionId = "permission-1",
            permissionKey = "workspace:access:read",
            expiresAt = "2026-05-15T09:00:00Z",
        )

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `returns workspace access summary for entitled authorized service account`() {
        seedAuthorizedServiceAccount(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("service-principal-1")
            .jsonPath("$.roles[0]").isEqualTo("member")
            .jsonPath("$.permissions[0]").isEqualTo("workspace:access:read")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "service-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW.name,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies entitled service account without required permission`() {
        seedServiceAccountWithoutPermission(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "service-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `rejects revoked service-account credential before authorization executes`() {
        seedAuthorizedServiceAccount(credentialStatus = "REVOKED", entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isUnauthorized

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "service-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                    roleKeys = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `returns workspace access summary for entitled authorized api key principal`() {
        seedAuthorizedApiKeyPrincipal(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("api-key-principal-1")
            .jsonPath("$.roles[0]").isEqualTo("member")
            .jsonPath("$.permissions[0]").isEqualTo("workspace:access:read")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW.name,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies entitled api key principal without required permission`() {
        seedApiKeyPrincipalWithoutPermission(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `rejects revoked api key credential before authorization executes`() {
        seedAuthorizedApiKeyPrincipal(credentialStatus = "REVOKED", entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isUnauthorized

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                    roleKeys = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `rejects inactive api key credential before authorization executes`() {
        seedAuthorizedApiKeyPrincipal(credentialStatus = "INACTIVE", entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isUnauthorized

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                    roleKeys = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `denies authorized principal when workspace entitlement is missing`() {
        seedAuthorizedMember(entitled = false)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = GET_CURRENT_WORKSPACE_ACCESS_SUMMARY_QUERY_FQCN,
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY.name,
                    reasonCode = AuthorizationReasonCode.MISSING_ENTITLEMENT.name,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `allows old api key before replacement and new api key after replacement`() {
        seedAuthorizedApiKeyPrincipal(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo("api-key-principal-1")

        val replacement = kotlinx.coroutines.runBlocking {
            replaceApiKeyCredentialHandler.handle(
                ReplaceApiKeyCredentialCommand("api-key-cred-1"),
            )
        }

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${replacement.successorPlaintextApiKey}")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.ACCEPT, API_V1_MEDIA_TYPE)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo("api-key-principal-1")

        val rows = kotlinx.coroutines.runBlocking { readApiKeyRows() }
        assertEquals(2, rows.size)
        val predecessor = rows.first { it.id == "api-key-cred-1" }
        val successor = rows.first { it.id == replacement.successorCredentialReference }
        assertEquals("INACTIVE", predecessor.status)
        assertEquals(replacement.successorCredentialReference, predecessor.replacedByCredentialId)
        assertEquals("ACTIVE", successor.status)
        assertEquals("api-key-cred-1", successor.replacedCredentialId)
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    private fun assertAuthorizationFacts(expected: List<AuthorizationDecisionAuditFact>) {
        assertEquals(expected, auditHook.facts)
    }

    // ── Seed helpers ──────────────────────────────────────────────────────────

    protected fun seedAuthorizedMember(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            seedUserPrincipal()
            seedWorkspaceAndRole(principalId = "principal-1", principalType = "USER", entitled = entitled)
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
        }
    }

    protected fun seedMemberWithoutPermission(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            seedUserPrincipal()
            seedWorkspaceAndRole(principalId = "principal-1", principalType = "USER", entitled = entitled)
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
        }
    }

    protected fun seedAuthorizedServiceAccount(credentialStatus: String = "ACTIVE", entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            seedPrincipal(
                principalId = "service-principal-1",
                principalType = "SERVICE_ACCOUNT",
                subject = "service-account-subject",
                provider = "https://issuer.example",
                displayIdentity = "scheduler-bot",
            )
            seedWorkspaceAndRole(principalId = "service-principal-1", principalType = "SERVICE_ACCOUNT", entitled = entitled)
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
            seedServiceAccountCredential(status = credentialStatus)
        }
    }

    protected fun seedServiceAccountWithoutPermission(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            seedPrincipal(
                principalId = "service-principal-1",
                principalType = "SERVICE_ACCOUNT",
                subject = "service-account-subject",
                provider = "https://issuer.example",
                displayIdentity = "scheduler-bot",
            )
            seedWorkspaceAndRole(principalId = "service-principal-1", principalType = "SERVICE_ACCOUNT", entitled = entitled)
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
            seedServiceAccountCredential(status = "ACTIVE")
        }
    }

    protected fun seedAuthorizedApiKeyPrincipal(credentialStatus: String = "ACTIVE", entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            seedPrincipal(
                principalId = "api-key-principal-1",
                principalType = "API_KEY",
                subject = "api-key-subject",
                provider = null,
                displayIdentity = "integration-key",
            )
            seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY", entitled = entitled)
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
            seedApiKeyCredential(status = credentialStatus)
        }
    }

    protected fun seedApiKeyPrincipalWithoutPermission(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            seedPrincipal(
                principalId = "api-key-principal-1",
                principalType = "API_KEY",
                subject = "api-key-subject",
                provider = null,
                displayIdentity = "integration-key",
            )
            seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY", entitled = entitled)
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
            seedApiKeyCredential(status = "ACTIVE")
        }
    }

    private suspend fun seedWorkspaceAndRole(principalId: String, principalType: String, entitled: Boolean = false) {
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE', NULL)").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', :principalId, :principalType, 'ACTIVE')")
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')").fetch().rowsUpdated().awaitSingle()
        if (entitled) {
            seedWorkspaceEntitlement()
        }
    }

    private suspend fun seedUserPrincipal() {
        seedPrincipal(
            principalId = "principal-1",
            principalType = "USER",
            subject = "subject-123",
            provider = "https://issuer.example",
            displayIdentity = "yuniel",
        )
        databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedPrincipal(
        principalId: String,
        principalType: String,
        subject: String,
        provider: String?,
        displayIdentity: String,
    ) {
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES (:principalId, :principalType, :subject, :provider, :displayIdentity)",
        )
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .bind("subject", subject)
            .let { spec -> if (provider == null) spec.bindNull("provider", String::class.java) else spec.bind("provider", provider) }
            .bind("displayIdentity", displayIdentity)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedWorkspaceEntitlement() {
        databaseClient.sql(
            "INSERT INTO workspace_entitlements (id, workspace_id, entitlement_key, enabled) VALUES ('entitlement-1', 'workspace-1', 'workspace.access.summary', TRUE)",
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected fun seedTargetScope(allowedTargetIdsJson: String) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-resource-read', 'workspace:resource:read')")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            databaseClient.sql(
                "INSERT INTO workspace_target_scopes (id, workspace_id, principal_id, principal_type, permission_id, target_resource_type, allowed_target_ids_json) VALUES ('scope-legacy-1', 'workspace-1', 'principal-1', 'USER', 'permission-resource-read', 'RESOURCE', :allowedTargetIdsJson)",
            )
                .bind("allowedTargetIdsJson", allowedTargetIdsJson)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private suspend fun seedServiceAccountCredential(status: String) {
        databaseClient.sql("INSERT INTO service_account_credentials (id, principal_id, provider, credential_reference, status, revoked_at) VALUES ('svc-cred-row-1', 'service-principal-1', 'https://issuer.example', 'svc-cred-1', :status, :revokedAt)")
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z")) else spec.bindNull("revokedAt", Instant::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedApiKeyCredential(status: String) {
        val verifier = org.springframework.security.crypto.bcrypt.BCrypt.hashpw("secret-value", org.springframework.security.crypto.bcrypt.BCrypt.gensalt())
        databaseClient.sql("INSERT INTO api_key_credentials (id, principal_id, lookup_key, key_prefix, secret_verifier, status, revoked_at, replaced_by_credential_id, replaced_credential_id, replaced_at) VALUES ('api-key-cred-1', 'api-key-principal-1', 'ptk_lookup', 'ptk_lookup', :verifier, :status, :revokedAt, NULL, NULL, NULL)")
            .bind("verifier", verifier)
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z")) else spec.bindNull("revokedAt", Instant::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun readApiKeyRows(): List<ApiKeyCredentialRow> =
        databaseClient.sql("SELECT id, status, replaced_by_credential_id, replaced_credential_id FROM api_key_credentials ORDER BY id")
            .map { row, _ ->
                ApiKeyCredentialRow(
                    id = requireNotNull(row.get("id", String::class.java)),
                    status = requireNotNull(row.get("status", String::class.java)),
                    replacedByCredentialId = row.get("replaced_by_credential_id", String::class.java),
                    replacedCredentialId = row.get("replaced_credential_id", String::class.java),
                )
            }
            .all()
            .collectList()
            .awaitSingle()

    private data class ApiKeyCredentialRow(
        val id: String,
        val status: String,
        val replacedByCredentialId: String?,
        val replacedCredentialId: String?,
    )

    protected fun seedDirectGrant(
        effect: String,
        permissionId: String,
        permissionKey: String,
        expiresAt: String? = null,
        insertPermission: Boolean = true,
    ) {
        kotlinx.coroutines.runBlocking {
            if (insertPermission) {
                databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)")
                    .bind("id", permissionId)
                    .bind("permissionKey", permissionKey)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
            }

            databaseClient.sql("INSERT INTO workspace_direct_grants (id, workspace_id, principal_id, principal_type, permission_id, effect, expires_at, conditions_json) VALUES (:id, :workspaceId, :principalId, :principalType, :permissionId, :effect, :expiresAt, :conditionsJson)")
                .bind("id", "grant-$effect-$permissionId")
                .bind("workspaceId", "workspace-1")
                .bind("principalId", "principal-1")
                .bind("principalType", "USER")
                .bind("permissionId", permissionId)
                .bind("effect", effect)
                .let { spec ->
                    if (expiresAt == null) spec.bindNull("expiresAt", Instant::class.java) else spec.bind("expiresAt", Instant.parse(expiresAt))
                }
                .bindNull("conditionsJson", String::class.java)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    // ── Shared @TestConfiguration ─────────────────────────────────────────────

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
                        .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
                        .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
                        .build()
                }

                "service-account-token" -> kotlinx.coroutines.reactor.mono {
                    Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("sub", "service-account-subject")
                        .claim("iss", "https://issuer.example")
                        .claim("principal_type", "SERVICE_ACCOUNT")
                        .claim("credential_reference", "svc-cred-1")
                        .claim("jti", "jwt-service-1")
                        .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
                        .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
                        .build()
                }

                "ptk_lookup.secret-value" -> reactor.core.publisher.Mono.error(BadJwtException("API key is handled by API-key authentication path"))

                else -> reactor.core.publisher.Mono.error(BadJwtException("Invalid token"))
            }
        }


        @Bean
        @Primary
        fun testAuditHook(): AuditHook = CapturingAuditHook()
    }
}
