package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.contracts.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.application.query.ListAdminWaitlistEntriesQuery
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
class R2dbcAdminWaitlistQueryPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var adminWaitlistQuery: AdminWaitlistQuery

    private val waitlistId = "waitlist-a"
    private val entryId = "entry-1"
    private val joinedAt: Instant = Instant.parse("2026-07-01T10:00:00Z")
    private val invitationId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    override suspend fun seedScenario() {
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status, created_at, updated_at)
            VALUES (:id, 'launch', 'Launch', 'profile-tailors', 'ACTIVE', :joinedAt, :joinedAt)
            """.trimIndent(),
        )
            .bind("id", waitlistId)
            .bind("joinedAt", OffsetDateTime.ofInstant(joinedAt, ZoneOffset.UTC))
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source, locale,
               consent_early_access, consent_marketing, consent_version, status, joined_at,
               invited_at, converted_at, cancelled_at)
            VALUES
              (:id, :waitlistId, 'alice@example.com', 'alice@example.com', 'marketing', 'en',
               true, true, '1', 'PENDING', :joinedAt, NULL, NULL, NULL)
            """.trimIndent(),
        )
            .bind("id", entryId)
            .bind("waitlistId", waitlistId)
            .bind("joinedAt", OffsetDateTime.ofInstant(joinedAt, ZoneOffset.UTC))
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source, locale,
               consent_early_access, consent_marketing, consent_version, status, joined_at,
               invited_at, converted_at, cancelled_at)
            VALUES
              (:id, :waitlistId, 'bob@example.com', 'bob@example.com', 'organic', 'es',
               true, false, '1', 'INVITED', :joinedAt, :invitedAt, NULL, NULL)
            """.trimIndent(),
        )
            .bind("id", "entry-2")
            .bind("waitlistId", waitlistId)
            .bind("joinedAt", OffsetDateTime.ofInstant(joinedAt.plusSeconds(3600), ZoneOffset.UTC))
            .bind("invitedAt", OffsetDateTime.ofInstant(joinedAt.plusSeconds(7200), ZoneOffset.UTC))
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_invitations
              (id, waitlist_entry_id, token_hash, status, issued_at, expires_at,
               created_by, delivery_status, delivery_attempt_count, version)
            VALUES
              (:id, :entryId, 'hash-1', 'ACTIVE', :issuedAt, :expiresAt,
               :operatorId, 'PENDING', 0, 0)
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .bind("entryId", entryId)
            .bind("issuedAt", OffsetDateTime.ofInstant(joinedAt.plusSeconds(3600), ZoneOffset.UTC))
            .bind("expiresAt", OffsetDateTime.ofInstant(joinedAt.plusSeconds(604_800), ZoneOffset.UTC))
            .bind("operatorId", operatorId)
            .fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `list returns all entries with pagination metadata`() = runTest {
        val result = adminWaitlistQuery.list(
            ListAdminWaitlistEntriesQuery(page = 0, size = 10, sortField = "joinedAt", sortDirection = "asc"),
        )

        assertEquals(2, result.totalElements)
        assertEquals(2, result.items.size)
        assertEquals(entryId, result.items.first().id)
        assertEquals("launch", result.items.first().waitlistKey)
        assertEquals("alice@example.com", result.items.first().email)
    }

    @Test
    fun `list filters by status`() = runTest {
        val result = adminWaitlistQuery.list(
            ListAdminWaitlistEntriesQuery(page = 0, size = 10, status = "INVITED"),
        )

        assertEquals(1, result.totalElements)
        assertEquals("entry-2", result.items.single().id)
    }

    @Test
    fun `list filters by email`() = runTest {
        val result = adminWaitlistQuery.list(
            ListAdminWaitlistEntriesQuery(page = 0, size = 10, email = "BOB@example.com"),
        )

        assertEquals(1, result.totalElements)
        assertEquals("entry-2", result.items.single().id)
    }

    @Test
    fun `list supports pagination`() = runTest {
        val page1 = adminWaitlistQuery.list(ListAdminWaitlistEntriesQuery(page = 0, size = 1))
        val page2 = adminWaitlistQuery.list(ListAdminWaitlistEntriesQuery(page = 1, size = 1))

        assertEquals(1, page1.items.size)
        assertEquals(1, page2.items.size)
        assertEquals(2, page1.totalElements)
        assertEquals(2, page2.totalElements)
        assertTrue(page1.items.first().id != page2.items.first().id)
    }

    @Test
    fun `list rejects invalid page size`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            adminWaitlistQuery.list(ListAdminWaitlistEntriesQuery(page = 0, size = 0))
        }
    }

    @Test
    fun `findById returns entry detail with invitation history`() = runTest {
        val detail = adminWaitlistQuery.findById(entryId)

        assertNotNull(detail)
        assertEquals(entryId, detail!!.id)
        assertEquals("launch", detail.waitlistKey)
        assertEquals("alice@example.com", detail.email)
        assertEquals("alice@example.com", detail.normalizedEmail)
        assertEquals("PENDING", detail.status)
        assertEquals(joinedAt, detail.joinedAt)
        assertEquals("en", detail.preferredLocale)
        assertTrue(detail.earlyAccessConsent)
        assertTrue(detail.marketingConsent)
        assertEquals("1", detail.consentVersion)
        assertEquals("marketing", detail.source)
        assertEquals(1, detail.invitationHistory.size)
        assertEquals(invitationId, detail.invitationHistory.single().id)
        assertEquals(entryId, detail.invitationHistory.single().waitlistEntryId)
        assertEquals("ACTIVE", detail.invitationHistory.single().status)
        assertEquals(joinedAt.plusSeconds(3600), detail.invitationHistory.single().issuedAt)
        assertEquals("PENDING", detail.invitationHistory.single().deliveryStatus)
    }

    @Test
    fun `findById returns null for unknown entry`() = runTest {
        assertNull(adminWaitlistQuery.findById("does-not-exist"))
    }

    @Test
    fun `countByStatus groups entries by status`() = runTest {
        val result = adminWaitlistQuery.countByStatus()

        assertEquals(1, result["PENDING"])
        assertEquals(1, result["INVITED"])
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
