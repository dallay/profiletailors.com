package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class R2dbcWorkspaceProvisioningServiceTest {
    private val databaseClient = mockk<DatabaseClient>()
    private val statements = mutableListOf<Map<String, Any>>()
    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val principalId = "e6ff7f1f-08ce-47e7-b6bd-6af25a207d05"
    private val service = R2dbcWorkspaceProvisioningService(databaseClient, Clock.fixed(now, ZoneOffset.UTC))

    @BeforeEach
    fun setUp() {
        every { databaseClient.sql(any<String>()) } answers {
            val bindings = mutableMapOf<String, Any>()
            val statement = mockk<DatabaseClient.GenericExecuteSpec>()
            statements.add(bindings)
            every { statement.bind(any<String>(), any()) } answers {
                bindings[firstArg<String>()] = secondArg<Any>()
                statement
            }
            every { statement.fetch().rowsUpdated() } returns Mono.just(1L)
            statement
        }
    }

    @Test
    fun `provisions a canonical UUID workspace shared by ownership and membership`() = runTest {
        val result = service.provisionDefaultWorkspace(principalId, "  Taylor  ")

        assertEquals(UUID.fromString(result.workspaceId).toString(), result.workspaceId)
        assertEquals(4, statements.size)
        assertEquals(UUID.fromString(result.workspaceId), statements[0]["id"])
        assertEquals(UUID.fromString(result.workspaceId), statements[1]["workspaceId"])
        assertEquals(UUID.fromString(result.workspaceId), statements[2]["workspaceId"])
        assertEquals("Taylor's Workspace", result.name)
        assertEquals(result.name, statements[0]["name"])
        assertEquals(WorkspaceMembershipStatus.ACTIVE, result.membershipStatus)
        assertEquals("ACTIVE", statements[0]["status"])
        assertEquals("ACTIVE", statements[2]["status"])
        assertEquals(UUID.fromString(principalId), statements[1]["ownerPrincipalId"])
        assertEquals(UUID.fromString(principalId), statements[1]["createdBy"])
        assertEquals(UUID.fromString(principalId), statements[2]["principalId"])
        assertEquals("USER", statements[1]["ownerPrincipalType"])
        assertEquals("USER", statements[2]["principalType"])
        statements.forEach { assertEquals(now, it["createdAt"]) }
    }

    @Test
    fun `provisions a canonical UUID membership reused for its owner role`() = runTest {
        val result = service.provisionDefaultWorkspace(principalId, "Taylor")
        val membershipId = statements[2].getValue("id").toString()

        assertEquals(UUID.fromString(membershipId).toString(), membershipId)
        assertNotEquals(result.workspaceId, membershipId)
        assertEquals(UUID.fromString(membershipId), statements[3]["membershipId"])
        assertEquals(UUID.fromString("6ce35f6c-2f1e-5702-9b59-d7a0e9402006"), statements[3]["roleId"])
    }

    @Test
    fun `generates independent identifiers for each provisioning call and preserves fallback name`() = runTest {
        val first = service.provisionDefaultWorkspace(principalId, " ")
        val second = service.provisionDefaultWorkspace(principalId, "Taylor")

        assertEquals("My's Workspace", first.name)
        assertNotEquals(first.workspaceId, second.workspaceId)
        assertNotEquals(statements[2]["id"], statements[6]["id"])
    }
}
