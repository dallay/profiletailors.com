package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
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
        "platform.storage.providers.local.base-path=/tmp/smp-platform-admin-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcPlatformRoleAssignmentRepositoryPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var roleRepository: PlatformRoleAssignmentRepository

    private val operatorId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val principalId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val assignedAt: Instant = Instant.parse("2026-07-01T10:00:00Z")

    override suspend fun seedScenario() {
        databaseClient.sql(
            """
            INSERT INTO platform_role_assignments
              (id, principal_id, role, assigned_at, assigned_by, revoked_at, revoked_by, version)
            VALUES
              ('22222222-3333-4444-5555-666666666666', :principalId, 'SUPPORT_AGENT', :assignedAt, :assignedBy, NULL, NULL, 0)
            """.trimIndent(),
        )
            .bind("principalId", principalId)
            .bind("assignedAt", java.sql.Timestamp.from(assignedAt))
            .bind("assignedBy", operatorId)
            .fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `save persists assignment and findById reads it back`() = runTest {
        val assignment = PlatformRoleAssignment(
            id = PlatformRoleAssignmentId.generate(),
            principalId = principalId,
            role = PlatformRole.AUDITOR,
            assignedAt = assignedAt,
            assignedBy = operatorId,
        )

        val saved = roleRepository.save(assignment)

        assertEquals(assignment.id, saved.id)
        assertEquals(0L, saved.version)
        assertTrue(saved.isActive)

        val found = roleRepository.findById(assignment.id)
        assertNotNull(found)
        assertEquals(principalId, found!!.principalId)
        assertEquals(PlatformRole.AUDITOR, found.role)
        assertEquals(operatorId, found.assignedBy)
        assertEquals(assignedAt, found.assignedAt)
        assertNull(found.revokedAt)
    }

    @Test
    fun `findById returns null for unknown assignment`() = runTest {
        assertNull(roleRepository.findById(PlatformRoleAssignmentId.generate()))
    }

    @Test
    fun `findActiveByPrincipalId returns only active assignments`() = runTest {
        val revokedAssignment = PlatformRoleAssignment(
            id = PlatformRoleAssignmentId.generate(),
            principalId = principalId,
            role = PlatformRole.PLATFORM_OPERATOR,
            assignedAt = assignedAt,
            assignedBy = operatorId,
        )
        roleRepository.save(revokedAssignment)
        roleRepository.update(revokedAssignment.revoke(assignedAt.plusSeconds(60), operatorId))

        val result = roleRepository.findActiveByPrincipalId(principalId)

        assertEquals(1, result.size)
        assertEquals(PlatformRole.SUPPORT_AGENT, result.single().role)
    }

    @Test
    fun `findAllActive returns only active assignments across principals`() = runTest {
        val otherPrincipal = UUID.fromString("99999999-8888-7777-6666-555555555555")
        val revokedAssignment = PlatformRoleAssignment(
            id = PlatformRoleAssignmentId.generate(),
            principalId = otherPrincipal,
            role = PlatformRole.PLATFORM_OPERATOR,
            assignedAt = assignedAt,
            assignedBy = operatorId,
        )
        roleRepository.save(revokedAssignment)
        roleRepository.update(revokedAssignment.revoke(assignedAt.plusSeconds(60), operatorId))

        val result = roleRepository.findAllActive()

        assertTrue(result.none { it.id == revokedAssignment.id })
        assertTrue(result.any { it.role == PlatformRole.SUPPORT_AGENT })
    }

    @Test
    fun `update revokes assignment and increments version`() = runTest {
        val assignment = PlatformRoleAssignment(
            id = PlatformRoleAssignmentId.generate(),
            principalId = principalId,
            role = PlatformRole.AUDITOR,
            assignedAt = assignedAt,
            assignedBy = operatorId,
        )
        roleRepository.save(assignment)

        val revoked = roleRepository.update(assignment.revoke(assignedAt.plusSeconds(120), operatorId))

        assertEquals(1L, revoked.version)
        assertEquals(assignedAt.plusSeconds(120), revoked.revokedAt)
        assertEquals(operatorId, revoked.revokedBy)
        assertTrue(!revoked.isActive)
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
