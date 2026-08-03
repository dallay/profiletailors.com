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

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class R2dbcIdeaRepositoriesPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private val ideas by lazy { R2dbcIdeaRepository(databaseClient, jacksonObjectMapper()) }
    private val boards by lazy { R2dbcIdeaBoardConfigRepository(databaseClient, jacksonObjectMapper()) }

    @Test
    fun `persists JSON fields and isolates ideas by workspace`() = runTest {
        seedWorkspace("workspace-1")
        seedWorkspace("workspace-2")
        val saved = ideas.create(idea("workspace-1", "idea-1"))

        assertEquals(saved, ideas.findByWorkspaceAndId("workspace-1", "idea-1"))
        assertTrue(ideas.findByWorkspaceAndId("workspace-2", "idea-1") == null)
        assertEquals(listOf("#testing"), ideas.listByWorkspace("workspace-1").single().tags)
        assertEquals("Docs", ideas.listByWorkspace("workspace-1").single().links.single().label)
    }

    @Test
    fun `maps nullable JSON and publication fields and returns missing workspace rows as null`() = runTest {
        seedWorkspace("workspace-1")
        val minimal = idea("workspace-1", "idea-minimal").copy(
            notes = null,
            tags = emptyList(),
            links = emptyList(),
            convertedToPublicationId = null,
        )
        ideas.create(minimal)

        val loaded = ideas.findByWorkspaceAndId("workspace-1", "idea-minimal")
        assertEquals(null, loaded?.notes)
        assertEquals(emptyList<String>(), loaded?.tags)
        assertEquals(emptyList<com.profiletailors.smp.ideas.domain.IdeaLink>(), loaded?.links)
        assertEquals(null, loaded?.convertedToPublicationId)
        assertEquals(null, ideas.findByWorkspaceAndId("workspace-1", "missing"))
    }

    @Test
    fun `updates deletes and upserts board configuration`() = runTest {
        seedWorkspace("workspace-1")
        val original = idea("workspace-1", "idea-1")
        ideas.create(original)
        val updated = original.copy(title = "Updated", orderInColumn = 1)

        ideas.update(updated)
        assertEquals("Updated", ideas.findByWorkspaceAndId("workspace-1", "idea-1")?.title)
        assertTrue(ideas.delete("workspace-1", "idea-1"))
        assertTrue(!ideas.delete("workspace-1", "idea-1"))

        val config = IdeaBoardConfig("workspace-1", listOf(IdeaColumn("raw", "Raw", order = 0)))
        boards.upsert(config)
        boards.upsert(config.copy(columns = listOf(IdeaColumn("done", "Done", order = 0))))
        assertEquals("done", boards.findByWorkspace("workspace-1")?.columns?.single()?.id)
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
            .bind("id", id)
            .bind("name", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("ideas_repositories")
    }
}
