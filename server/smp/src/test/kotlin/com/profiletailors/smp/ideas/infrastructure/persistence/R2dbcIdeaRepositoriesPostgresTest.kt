package com.profiletailors.smp.ideas.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.ideas.domain.Idea
import com.profiletailors.smp.ideas.domain.IdeaBoardConfig
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class R2dbcIdeaRepositoriesPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private val ideas by lazy { R2dbcIdeaRepository(databaseClient, jacksonObjectMapper()) }
    private val boards by lazy { R2dbcIdeaBoardConfigRepository(databaseClient, jacksonObjectMapper()) }

    @Test
    fun `persists JSON fields and isolates ideas by workspace`() = runTest {
        seedWorkspace(WORKSPACE_1)
        seedWorkspace(WORKSPACE_2)
        val saved = ideas.create(idea(WORKSPACE_1, IDEA_1))

        assertEquals(saved, ideas.findByWorkspaceAndId(WORKSPACE_1, IDEA_1))
        assertTrue(ideas.findByWorkspaceAndId(WORKSPACE_2, IDEA_1) == null)
        assertEquals(listOf("#testing"), ideas.listByWorkspace(WORKSPACE_1).single().tags)
        assertEquals("Docs", ideas.listByWorkspace(WORKSPACE_1).single().links.single().label)
    }

    @Test
    fun `maps nullable JSON and publication fields and returns missing workspace rows as null`() = runTest {
        seedWorkspace(WORKSPACE_1)
        val minimal = idea(WORKSPACE_1, IDEA_MINIMAL).copy(
            notes = null,
            tags = emptyList(),
            links = emptyList(),
            convertedToPublicationId = null,
        )
        ideas.create(minimal)

        val loaded = ideas.findByWorkspaceAndId(WORKSPACE_1, IDEA_MINIMAL)
        assertEquals(null, loaded?.notes)
        assertEquals(emptyList<String>(), loaded?.tags)
        assertEquals(emptyList<com.profiletailors.smp.ideas.domain.IdeaLink>(), loaded?.links)
        assertEquals(null, loaded?.convertedToPublicationId)
        assertEquals(null, ideas.findByWorkspaceAndId(WORKSPACE_1, MISSING))
    }

    @Test
    fun `updates deletes and upserts board configuration`() = runTest {
        seedWorkspace(WORKSPACE_1)
        val original = idea(WORKSPACE_1, IDEA_1)
        ideas.create(original)
        val updated = original.copy(title = "Updated", orderInColumn = 1)

        ideas.update(updated)
        assertEquals("Updated", ideas.findByWorkspaceAndId(WORKSPACE_1, IDEA_1)?.title)
        assertTrue(ideas.delete(WORKSPACE_1, IDEA_1))
        assertTrue(!ideas.delete(WORKSPACE_1, IDEA_1))

        val config = IdeaBoardConfig(WORKSPACE_1, listOf(IdeaColumn("raw", "Raw", order = 0)))
        boards.upsert(config)
        boards.upsert(config.copy(columns = listOf(IdeaColumn("done", "Done", order = 0))))
        assertEquals("done", boards.findByWorkspace(WORKSPACE_1)?.columns?.single()?.id)
    }

    private fun idea(workspaceId: String, id: String) = Idea(
        id = id,
        workspaceId = workspaceId,
        title = "Idea",
        notes = "Notes",
        tags = listOf("#testing"),
        links = listOf(com.profiletailors.smp.ideas.domain.IdeaLink("https://example.com", "Docs")),
        columnId = "raw",
        orderInColumn = 0,
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
        private const val IDEA_1 = "55be1fec-33ff-54ff-a037-e800e338ae4b"
        private const val IDEA_MINIMAL = "98362b9f-099a-5ed0-8219-ec8825711570"
        private const val MISSING = "3ed94c3a-fb01-58d8-9803-9c4e8f476a05"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("ideas_repositories")
    }
}
