package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class R2dbcWorkspaceOwnershipRepositoryTest : DatabaseUnitTestBase() {

    override fun databaseName(): String = "ownership_lookup"

    private val repository by lazy {
        R2dbcWorkspaceOwnershipRepository(databaseClient)
    }

    @Test
    fun `stores and loads multiple owners per workspace`() = runTest {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedPrincipal("creator-1")
        seedWorkspace()

        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
                createdBy = "creator-1",
                createdAt = Instant.parse("2026-05-20T10:15:30Z"),
            ),
        )
        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-2",
                ownerPrincipalType = PrincipalType.USER,
                createdBy = "creator-1",
                createdAt = Instant.parse("2026-05-20T10:16:30Z"),
            ),
        )

        val owners = repository.findByWorkspaceId("workspace-1")

        assertEquals(2, owners.size)
        assertTrue(owners.any { it.ownerPrincipalId == "owner-1" && it.createdBy == "creator-1" })
        assertTrue(owners.any { it.ownerPrincipalId == "owner-2" && it.createdBy == "creator-1" })
    }

    @Test
    fun `removes a specific owner without affecting others`() = runTest {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedPrincipal("creator-1")
        seedWorkspace()
        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
                createdBy = "creator-1",
                createdAt = Instant.parse("2026-05-20T10:15:30Z"),
            ),
        )
        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-2",
                ownerPrincipalType = PrincipalType.USER,
                createdBy = "creator-1",
                createdAt = Instant.parse("2026-05-20T10:16:30Z"),
            ),
        )

        repository.remove("workspace-1", "owner-1")

        val remainingOwners = repository.findByWorkspaceId("workspace-1")
        assertEquals(1, remainingOwners.size)
        assertEquals("owner-2", remainingOwners.first().ownerPrincipalId)
    }

    private suspend fun seedPrincipal(principalId: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity) 
            VALUES (:id, 'USER', :subject, 'https://issuer.example', :displayIdentity)
            """.trimIndent()
        )
            .bind("id", principalId)
            .bind("subject", "subject-$principalId")
            .bind("displayIdentity", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedWorkspace() {
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')"
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
