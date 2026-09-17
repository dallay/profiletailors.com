package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.identity.application.PrincipalLifecycle
import com.profiletailors.smp.identity.application.PrincipalStatusTransition
import com.profiletailors.smp.identity.application.PrincipalVersionConflictException
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.contracts.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.query.ListAdminUsersQuery
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
class R2dbcAdminUserQueryPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var userQuery: AdminUserQuery

    @Autowired
    private lateinit var platformRoleAssignmentRepository: PlatformRoleAssignmentRepository

    @Autowired
    private lateinit var principalLifecycleService: PrincipalLifecycle

    override suspend fun seedScenario() {
        seedPrincipal("user-1")
        seedUserIdentity("user-1", "user1@example.com", "user1")
        seedPrincipal("user-2")
        seedUserIdentity("user-2", "user2@example.com", "user2")
        seedWorkspace("ws-1", "Workspace One")
        seedWorkspaceMembership("m-1", "ws-1", "user-1")

        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('svc-1', 'SERVICE', 'subject-svc-1', 'https://issuer.example', 'svc-1')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username) VALUES ('svc-1', 'svc1@example.com', 'svc1')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO platform_role_assignments " +
                "(id, principal_id, role, assigned_at, assigned_by, version) " +
                "VALUES (:id, CAST(:principalId AS UUID), 'PLATFORM_OPERATOR', " +
                "CURRENT_TIMESTAMP, CAST(:assignedBy AS UUID), 0)",
        )
            .bind("id", UUID.randomUUID())
            .bind("principalId", "00000000-0000-0000-0000-000000000001")
            .bind("assignedBy", "00000000-0000-0000-0000-000000000002")
            .fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `list returns all users with identity data`() = runTest {
        val result = userQuery.list(ListAdminUsersQuery())

        assertEquals(2L, result.totalElements)
        assertEquals(2, result.items.size)
        val user1 = result.items.first { it.principalId == "user-1" }
        assertEquals(1, user1.workspaceCount)
        assertEquals("user1@example.com", user1.email)
        assertEquals("user-1", user1.displayIdentity)
        assertEquals("USER", user1.principalType)
        assertEquals(UserAccountState.ACTIVE, user1.accountState)
        assertNotNull(user1.emailStatus)
        assertNotNull(user1.createdAt)
    }

    @Test
    fun `list filters by status`() = runTest {
        val users = userQuery.list(ListAdminUsersQuery(status = "ACTIVE"))
        assertEquals(2L, users.totalElements)
        assertTrue(users.items.all { it.accountState == UserAccountState.ACTIVE })

        databaseClient.sql(
            "UPDATE principals SET account_state = 'DISABLED' WHERE id = 'user-2'",
        ).fetch().rowsUpdated().awaitSingle()
        val disabled = userQuery.list(ListAdminUsersQuery(status = "DISABLED"))
        assertEquals(1L, disabled.totalElements)
        assertEquals("user-2", disabled.items.single().principalId)
    }

    @Test
    fun `list filters by email`() = runTest {
        val result = userQuery.list(ListAdminUsersQuery(email = "user2@example.com"))
        assertEquals(1L, result.totalElements)
        assertEquals("user-2", result.items.single().principalId)
    }

    @Test
    fun `list paginates and sorts by email`() = runTest {
        val firstPage = userQuery.list(
            ListAdminUsersQuery(page = 0, size = 2, sortField = "email", sortDirection = "asc"),
        )
        assertEquals(2, firstPage.items.size)
        assertEquals(2L, firstPage.totalElements)
        assertTrue(!firstPage.hasNext)
        val firstEmails = firstPage.items.mapNotNull { it.email }
        assertEquals(firstEmails.sorted(), firstEmails)
    }

    @Test
    fun `list rejects invalid page and size`() = runTest {
        assertFailsWith<IllegalArgumentException> { userQuery.list(ListAdminUsersQuery(page = -1)) }
        assertFailsWith<IllegalArgumentException> { userQuery.list(ListAdminUsersQuery(size = 0)) }
        assertFailsWith<IllegalArgumentException> { userQuery.list(ListAdminUsersQuery(size = 101)) }
    }

    @Test
    fun `findById returns user detail`() = runTest {
        val user = userQuery.findById("user-1")
        assertNotNull(user)
        assertEquals("user1@example.com", user!!.email)
        assertEquals("user-1", user.displayIdentity)
        assertEquals("USER", user.principalType)
        assertEquals(UserAccountState.ACTIVE, user.accountState)
        assertEquals(1, user.workspaceMemberships.size)
        assertEquals("Workspace One", user.workspaceMemberships.single().workspaceName)
        assertEquals(emptyList<String>(), user.platformRoles)
        assertNotNull(user.emailStatus)
        assertNotNull(user.createdAt)
    }

    @Test
    fun `findById returns null for unknown user`() = runTest {
        assertNull(userQuery.findById("missing"))
    }

    @Test
    fun `findById maps persisted status and version`() = runTest {
        val user = requireNotNull(userQuery.findById("user-1"))

        assertEquals("ACTIVE", user.status)
        assertEquals(0, user.version)
    }

    @Test
    fun `deactivate rejects stale version`() = runTest {
        assertFailsWith<PrincipalVersionConflictException> {
            principalLifecycleService.deactivate("svc-1", 5)
        }
    }

    @Test
    fun `deactivate and reactivate lifecycle bumps version`() = runTest {
        assertEquals(
            PrincipalStatusTransition.TRANSITIONED,
            principalLifecycleService.deactivate("user-2", 0),
        )

        val deactivated = requireNotNull(userQuery.findById("user-2"))
        assertEquals("DEACTIVATED", deactivated.status)
        assertEquals(1, deactivated.version)

        assertEquals(
            PrincipalStatusTransition.TRANSITIONED,
            principalLifecycleService.reactivate("user-2", 1),
        )

        val reactivated = requireNotNull(userQuery.findById("user-2"))
        assertEquals("ACTIVE", reactivated.status)
        assertEquals(2, reactivated.version)
    }

    @Test
    fun `findById maps platform roles for uuid principal`() = runTest {
        seedPrincipal("00000000-0000-0000-0000-000000000001")
        seedUserIdentity("00000000-0000-0000-0000-000000000001", "operator@example.com", "operator")

        val user = requireNotNull(userQuery.findById("00000000-0000-0000-0000-000000000001"))

        assertEquals(listOf("PLATFORM_OPERATOR"), user.platformRoles)
    }

    @Test
    fun `findWorkspacesByPrincipalId returns memberships`() = runTest {
        val memberships = userQuery.findWorkspacesByPrincipalId("user-1")
        assertEquals(1, memberships.size)
        assertEquals("ws-1", memberships.single().workspaceId)
        assertEquals("Workspace One", memberships.single().workspaceName)
        assertEquals("ACTIVE", memberships.single().membershipStatus)
        assertNotNull(memberships.single().joinedAt)
    }

    @Test
    fun `findWorkspacesByPrincipalId returns empty for user without workspaces`() = runTest {
        assertEquals(0, userQuery.findWorkspacesByPrincipalId("user-2").size)
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
