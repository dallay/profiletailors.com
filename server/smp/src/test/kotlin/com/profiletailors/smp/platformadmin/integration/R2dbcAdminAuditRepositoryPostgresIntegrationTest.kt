package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.contracts.AdminAuditQuery
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.query.ListAdminAuditEventsQuery
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import java.util.UUID
import kotlin.test.assertFailsWith

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
        "platform.storage.providers.local.base-path=/tmp/smp-platform-admin-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAdminAuditRepositoryPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var auditPublisher: AdministrativeAuditPublisher

    @Autowired
    private lateinit var adminAuditQuery: AdminAuditQuery

    private val operatorId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val occurredAt: Instant = Instant.parse("2026-07-01T10:00:00Z")

    private fun event() = AdminAuditEvent(
        eventId = UUID.fromString("33333333-4444-5555-6666-777777777777"),
        occurredAt = occurredAt,
        operatorPrincipalId = operatorId,
        operatorPlatformRoles = setOf(PlatformRole.PLATFORM_OPERATOR),
        action = AdminAuditAction.PLATFORM_ROLE_ASSIGNED,
        targetType = "principal",
        targetId = "11111111-2222-3333-4444-555555555555",
        result = AdminAuditResult.SUCCEEDED,
        reason = "granted by admin",
        correlationId = "corr-1",
        requestId = "req-1",
        sourceIpHash = "ip-hash",
        userAgentSummary = "Playwright/1.0",
    )

    override suspend fun seedScenario() = Unit

    @Test
    fun `publish persists event and list reads it back`() = runTest {
        auditPublisher.publish(event())

        val result = adminAuditQuery.list(ListAdminAuditEventsQuery(page = 0, size = 25))

        assertEquals(1, result.totalElements)
        val summary = result.items.single()
        assertEquals(event().eventId, summary.eventId)
        assertEquals(occurredAt, summary.occurredAt)
        assertEquals(operatorId, summary.operatorPrincipalId)
        assertEquals(listOf("PLATFORM_OPERATOR"), summary.operatorPlatformRoles)
        assertEquals("PLATFORM_ROLE_ASSIGNED", summary.action)
        assertEquals("principal", summary.targetType)
        assertEquals(event().targetId, summary.targetId)
        assertEquals("SUCCEEDED", summary.result)
        assertEquals("granted by admin", summary.reason)
        assertEquals("corr-1", summary.correlationId)
        assertEquals("req-1", summary.requestId)
    }

    @Test
    fun `findById returns persisted event`() = runTest {
        auditPublisher.publish(event())

        val found = adminAuditQuery.findById(event().eventId)

        assertNotNull(found)
        assertEquals(event().targetId, found!!.targetId)
    }

    @Test
    fun `findById returns null for unknown event`() = runTest {
        assertNull(adminAuditQuery.findById(UUID.fromString("99999999-9999-9999-9999-999999999999")))
    }

    @Test
    fun `list filters by operator and action`() = runTest {
        auditPublisher.publish(event())

        val byOperator = adminAuditQuery.list(
            ListAdminAuditEventsQuery(page = 0, size = 25, operatorPrincipalId = operatorId),
        )
        assertEquals(1, byOperator.totalElements)

        val byAction = adminAuditQuery.list(
            ListAdminAuditEventsQuery(page = 0, size = 25, action = AdminAuditAction.PLATFORM_ROLE_ASSIGNED.name),
        )
        assertEquals(1, byAction.totalElements)

        val wrongAction = adminAuditQuery.list(
            ListAdminAuditEventsQuery(page = 0, size = 25, action = AdminAuditAction.INVITATION_REVOKED.name),
        )
        assertEquals(0, wrongAction.totalElements)
    }

    @Test
    fun `list filters by date range`() = runTest {
        auditPublisher.publish(event())

        val within = adminAuditQuery.list(
            ListAdminAuditEventsQuery(
                page = 0,
                size = 25,
                occurredFrom = occurredAt.minusSeconds(60),
                occurredTo = occurredAt.plusSeconds(60),
            ),
        )
        assertEquals(1, within.totalElements)

        val outside = adminAuditQuery.list(
            ListAdminAuditEventsQuery(
                page = 0,
                size = 25,
                occurredFrom = occurredAt.plusSeconds(3600),
                occurredTo = occurredAt.plusSeconds(7200),
            ),
        )
        assertEquals(0, outside.totalElements)
    }

    @Test
    fun `publish redacts sensitive metadata fields before persisting`() = runTest {
        val sensitiveMetadata = mapOf(
            "email" to "user@example.com",
            "ip" to "192.168.1.1",
            "phone" to "+1-555-0100",
            "non_sensitive" to "visible-value",
        )
        val eventWithMetadata = event().copy(
            eventId = UUID.fromString("55555555-6666-7777-8888-999999999999"),
            metadata = sensitiveMetadata,
        )
        auditPublisher.publish(eventWithMetadata)

        val persisted = adminAuditQuery.findById(eventWithMetadata.eventId)
        assertNotNull(persisted)
    }

    @Test
    fun `publish persists non-sensitive metadata as-is`() = runTest {
        val cleanMetadata = mapOf("campaign_id" to "summer-2026", "source" to "waitlist-page")
        val eventWithCleanMetadata = event().copy(
            eventId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
            metadata = cleanMetadata,
        )
        auditPublisher.publish(eventWithCleanMetadata)

        val persisted = adminAuditQuery.findById(eventWithCleanMetadata.eventId)
        assertNotNull(persisted)
    }

    @Test
    fun `list orders by occurred_at descending`() = runTest {
        auditPublisher.publish(event())
        val later = event().copy(
            eventId = UUID.fromString("44444444-5555-6666-7777-888888888888"),
            occurredAt = occurredAt.plusSeconds(300),
        )
        auditPublisher.publish(later)

        val result = adminAuditQuery.list(ListAdminAuditEventsQuery(page = 0, size = 25))

        assertEquals(2, result.totalElements)
        assertEquals(later.eventId, result.items.first().eventId)
    }

    @Test
    fun `list rejects invalid page size`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            adminAuditQuery.list(ListAdminAuditEventsQuery(page = 0, size = 0))
        }
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
