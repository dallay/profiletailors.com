package com.profiletailors.smp.ideas.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.ideas.domain.Idea
import com.profiletailors.smp.ideas.domain.IdeaBoardConfig
import com.profiletailors.smp.ideas.domain.IdeaBoardConfigRepository
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.ideas.domain.IdeaLink
import com.profiletailors.smp.ideas.domain.IdeaRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcIdeaRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : IdeaRepository {
    override suspend fun listByWorkspace(workspaceId: String): List<Idea> = databaseClient.sql(
        """
        SELECT id, workspace_id, title, notes, tags_json, links_json, column_id,
               order_in_column, converted_to_publication_id, created_at, updated_at
        FROM ideas
        WHERE workspace_id = :workspaceId
        ORDER BY column_id ASC, order_in_column ASC, created_at ASC
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .map { row, _ -> mapIdeaRow(row) }
        .all()
        .collectList()
        .awaitSingle()

    override suspend fun findByWorkspaceAndId(workspaceId: String, ideaId: String): Idea? = databaseClient.sql(
        """
        SELECT id, workspace_id, title, notes, tags_json, links_json, column_id,
               order_in_column, converted_to_publication_id, created_at, updated_at
        FROM ideas
        WHERE workspace_id = :workspaceId AND id = :ideaId
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("ideaId", ideaId)
        .map { row, _ -> mapIdeaRow(row) }
        .one()
        .awaitSingleOrNull()

    override suspend fun create(idea: Idea): Idea {
        databaseClient.sql(
            """
            INSERT INTO ideas (
              id, workspace_id, title, notes, tags_json, links_json,
              column_id, order_in_column, converted_to_publication_id,
              created_at, updated_at
            ) VALUES (
              :id, :workspaceId, :title, :notes, :tagsJson, :linksJson,
              :columnId, :orderInColumn, :convertedToPublicationId,
              :createdAt, :updatedAt
            )
            """.trimIndent(),
        )
            .bind("id", idea.id)
            .bind("workspaceId", idea.workspaceId)
            .bind("title", idea.title)
            .bindNullable("notes", idea.notes, String::class.java)
            .bind("tagsJson", objectMapper.writeValueAsString(idea.tags))
            .bind("linksJson", objectMapper.writeValueAsString(idea.links))
            .bind("columnId", idea.columnId)
            .bind("orderInColumn", idea.orderInColumn)
            .bindNullable("convertedToPublicationId", idea.convertedToPublicationId, String::class.java)
            .bind("createdAt", idea.createdAt)
            .bind("updatedAt", idea.updatedAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return idea
    }

    override suspend fun update(idea: Idea): Idea {
        databaseClient.sql(
            """
            UPDATE ideas
            SET title = :title,
                notes = :notes,
                tags_json = :tagsJson,
                links_json = :linksJson,
                column_id = :columnId,
                order_in_column = :orderInColumn,
                converted_to_publication_id = :convertedToPublicationId,
                updated_at = :updatedAt
            WHERE workspace_id = :workspaceId
              AND id = :id
            """.trimIndent(),
        )
            .bind("id", idea.id)
            .bind("workspaceId", idea.workspaceId)
            .bind("title", idea.title)
            .bindNullable("notes", idea.notes, String::class.java)
            .bind("tagsJson", objectMapper.writeValueAsString(idea.tags))
            .bind("linksJson", objectMapper.writeValueAsString(idea.links))
            .bind("columnId", idea.columnId)
            .bind("orderInColumn", idea.orderInColumn)
            .bindNullable("convertedToPublicationId", idea.convertedToPublicationId, String::class.java)
            .bind("updatedAt", idea.updatedAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return idea
    }

    override suspend fun delete(workspaceId: String, ideaId: String): Boolean = databaseClient.sql(
        "DELETE FROM ideas WHERE workspace_id = :workspaceId AND id = :ideaId",
    )
        .bind("workspaceId", workspaceId)
        .bind("ideaId", ideaId)
        .fetch()
        .rowsUpdated()
        .awaitSingle() > 0

    private fun mapIdeaRow(row: io.r2dbc.spi.Row): Idea {
        val tagsJson = row.get("tags_json", String::class.java) ?: "[]"
        val linksJson = row.get("links_json", String::class.java) ?: "[]"

        return Idea(
            id = requireNotNull(row.get("id", String::class.java)),
            workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
            title = requireNotNull(row.get("title", String::class.java)),
            notes = row.get("notes", String::class.java),
            tags = objectMapper.readValue(tagsJson, object : com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}),
            links = objectMapper.readValue(linksJson, object : com.fasterxml.jackson.core.type.TypeReference<List<IdeaLink>>() {}),
            columnId = requireNotNull(row.get("column_id", String::class.java)),
            orderInColumn = requireNotNull(row.get("order_in_column", Integer::class.java)).toInt(),
            convertedToPublicationId = row.get("converted_to_publication_id", String::class.java),
            createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
            updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
        )
    }
}

@Repository
class R2dbcIdeaBoardConfigRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : IdeaBoardConfigRepository {
    override suspend fun findByWorkspace(workspaceId: String): IdeaBoardConfig? = databaseClient.sql(
        "SELECT workspace_id, columns_json FROM idea_board_configs WHERE workspace_id = :workspaceId",
    )
        .bind("workspaceId", workspaceId)
        .map { row, _ ->
            IdeaBoardConfig(
                workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                columns = objectMapper.readValue(
                    row.get("columns_json", String::class.java) ?: "[]",
                    object : com.fasterxml.jackson.core.type.TypeReference<List<IdeaColumn>>() {},
                ),
            )
        }
        .one()
        .awaitSingleOrNull()

    override suspend fun upsert(config: IdeaBoardConfig): IdeaBoardConfig {
        val columnsJson = objectMapper.writeValueAsString(config.columns)
        databaseClient.sql(
            """
            INSERT INTO idea_board_configs (workspace_id, columns_json, updated_at)
            VALUES (:workspaceId, :columnsJson, :updatedAt)
            ON CONFLICT (workspace_id) DO UPDATE
            SET columns_json = EXCLUDED.columns_json,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .bind("workspaceId", config.workspaceId)
            .bind("columnsJson", columnsJson)
            .bind("updatedAt", Instant.now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return config
    }
}

private fun DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: Any?,
    type: Class<*>,
): DatabaseClient.GenericExecuteSpec =
    if (value == null) bindNull(name, type) else bind(name, value)
