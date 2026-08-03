package com.profiletailors.smp.hashtags.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.hashtags.domain.HashtagSavedSet
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class R2dbcHashtagSavedSetRepositoryPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private val repository by lazy { R2dbcHashtagSavedSetRepository(databaseClient, jacksonObjectMapper()) }

    @Test
    fun `persists JSON hashtags and isolates saved sets by workspace`() = runTest {
        seedWorkspace("workspace-1")
        seedWorkspace("workspace-2")
        repository.create(savedSet("workspace-1", "set-1"))

        assertEquals(listOf("#testing", "#quality"), repository.listByWorkspace("workspace-1").single().hashtags)
        assertTrue(repository.findByWorkspaceAndId("workspace-2", "set-1") == null)
        assertTrue(repository.delete("workspace-1", "set-1"))
        assertFalse(repository.delete("workspace-1", "set-1"))
    }

    private fun savedSet(workspaceId: String, id: String) = HashtagSavedSet(
        id = id,
        workspaceId = workspaceId,
        name = "Engineering",
        hashtags = listOf("#testing", "#quality"),
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private suspend fun seedWorkspace(id: String) {
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES (:id, :name, 'ACTIVE', NULL)",
        )
            .bind("id", id)
            .bind("name", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("hashtags_repositories")
    }
}
