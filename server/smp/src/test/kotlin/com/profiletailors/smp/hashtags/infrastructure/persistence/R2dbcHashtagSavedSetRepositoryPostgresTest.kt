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
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class R2dbcHashtagSavedSetRepositoryPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private val repository by lazy { R2dbcHashtagSavedSetRepository(databaseClient, jacksonObjectMapper()) }

    @Test
    fun `persists JSON hashtags and isolates saved sets by workspace`() = runTest {
        seedWorkspace(WORKSPACE_1)
        seedWorkspace(WORKSPACE_2)
        repository.create(savedSet(WORKSPACE_1, SET_1))

        assertEquals(listOf("#testing", "#quality"), repository.listByWorkspace(WORKSPACE_1).single().hashtags)
        assertTrue(repository.findByWorkspaceAndId(WORKSPACE_2, SET_1) == null)
        assertTrue(repository.delete(WORKSPACE_1, SET_1))
        assertFalse(repository.delete(WORKSPACE_1, SET_1))
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
            .bind("id", UUID.fromString(id))
            .bind("name", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val WORKSPACE_2 = "57af0326-afba-57ec-83e4-15ec87ecc8bc"
        private const val SET_1 = "298962b2-35c5-508a-ac20-ba8a9e1c4409"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("hashtags_repositories")
    }
}
