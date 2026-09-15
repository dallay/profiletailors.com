package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.contracts.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.application.query.ListAdminDirectInvitationsQuery
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "platform.storage.default=local",
        "platform.storage.providers.local.type=local",
        "platform.storage.providers.local.base-path=/tmp/smp-platform-admin-invitation-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAdminInvitationQueryPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var invitationQuery: AdminInvitationQuery

    override suspend fun seedScenario() {
        seedPrincipal("inv-issuer")
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) " +
                "VALUES ('ws-inv', 'Invitation Workspace', 'ACTIVE', NULL) ON CONFLICT DO NOTHING",
        ).fetch().rowsUpdated().awaitSingle()
        seedDirectInvitation(
            email = "active@example.com",
            status = "ACTIVE",
            createdAt = Instant.parse("2026-03-01T10:00:00Z"),
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        )
        seedDirectInvitation(
            email = "expired@example.com",
            status = "ACTIVE",
            createdAt = Instant.parse("2026-01-01T10:00:00Z"),
            expiresAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        seedDirectInvitation(
            email = "revoked@example.com",
            status = "REVOKED",
            createdAt = Instant.parse("2026-02-01T10:00:00Z"),
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        )
    }

    @Test
    fun `unfiltered list projects expired effective status`() = runTest {
        val result = invitationQuery.list(ListAdminDirectInvitationsQuery(page = 0, size = 25))

        assertEquals(3, result.totalElements)
        val byEmail = result.items.associateBy { it.email }
        assertEquals("ACTIVE", byEmail["active@example.com"]?.status)
        assertEquals("EXPIRED", byEmail["expired@example.com"]?.status)
        assertEquals("REVOKED", byEmail["revoked@example.com"]?.status)
    }

    @Test
    fun `expired filter matches naturally expired invitations`() = runTest {
        val result = invitationQuery.list(ListAdminDirectInvitationsQuery(page = 0, size = 25, status = "EXPIRED"))

        assertEquals(1, result.totalElements)
        assertEquals("expired@example.com", result.items.single().email)
    }

    @Test
    fun `active filter excludes naturally expired invitations`() = runTest {
        val result = invitationQuery.list(ListAdminDirectInvitationsQuery(page = 0, size = 25, status = "ACTIVE"))

        assertEquals(1, result.totalElements)
        assertEquals("active@example.com", result.items.single().email)
    }

    @Test
    fun `list orders newest first`() = runTest {
        val result = invitationQuery.list(ListAdminDirectInvitationsQuery(page = 0, size = 25))

        assertEquals(
            listOf("active@example.com", "revoked@example.com", "expired@example.com"),
            result.items.map { it.email },
        )
    }

    private suspend fun seedDirectInvitation(email: String, status: String, createdAt: Instant, expiresAt: Instant) {
        databaseClient.sql(
            """
            INSERT INTO invitations (
                id, source, source_reference_id, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at, target
            ) VALUES (
                :id, 'DIRECT', NULL, 'ws-inv', :email,
                :candidateKey, :tokenHash, :status, 'inv-issuer', :createdAt, :expiresAt, 'EXISTING_WORKSPACE'
            )
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID())
            .bind("email", email)
            .bind("candidateKey", "key-$email")
            .bind("tokenHash", "hash-$email")
            .bind("status", status)
            .bind("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
            .bind("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer()

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
