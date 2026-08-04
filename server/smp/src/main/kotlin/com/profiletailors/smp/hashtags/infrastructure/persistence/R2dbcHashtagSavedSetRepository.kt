package com.profiletailors.smp.hashtags.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.profiletailors.smp.hashtags.domain.HashtagSavedSet
import com.profiletailors.smp.hashtags.domain.HashtagSavedSetRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

private const val BIND_WORKSPACE_ID = "workspaceId"

@Repository
class R2dbcHashtagSavedSetRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : HashtagSavedSetRepository {

    override suspend fun listByWorkspace(workspaceId: String): List<HashtagSavedSet> = databaseClient.sql(
        """
            SELECT id, workspace_id, name, hashtags_json, created_at, updated_at
            FROM hashtag_saved_sets
            WHERE workspace_id = :workspaceId
            ORDER BY created_at ASC
        """.trimIndent(),
    )
        .bind(BIND_WORKSPACE_ID, workspaceId)
        .map { row, _ -> mapRow(row) }
        .all()
        .collectList()
        .awaitSingle()

    override suspend fun findByWorkspaceAndId(workspaceId: String, setId: String): HashtagSavedSet? =
        databaseClient.sql(
            """
            SELECT id, workspace_id, name, hashtags_json, created_at, updated_at
            FROM hashtag_saved_sets
            WHERE workspace_id = :workspaceId AND id = :setId
            """.trimIndent(),
        )
            .bind(BIND_WORKSPACE_ID, workspaceId)
            .bind("setId", setId)
            .map { row, _ -> mapRow(row) }
            .one()
            .awaitSingleOrNull()

    override suspend fun create(set: HashtagSavedSet): HashtagSavedSet {
        databaseClient.sql(
            """
            INSERT INTO hashtag_saved_sets (id, workspace_id, name, hashtags_json, created_at, updated_at)
            VALUES (:id, :workspaceId, :name, :hashtagsJson, :createdAt, :updatedAt)
            """.trimIndent(),
        )
            .bind("id", set.id)
            .bind(BIND_WORKSPACE_ID, set.workspaceId)
            .bind("name", set.name)
            .bind("hashtagsJson", objectMapper.writeValueAsString(set.hashtags))
            .bind("createdAt", set.createdAt)
            .bind("updatedAt", set.updatedAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return set
    }

    override suspend fun delete(workspaceId: String, setId: String): Boolean {
        val rows = databaseClient.sql(
            "DELETE FROM hashtag_saved_sets WHERE workspace_id = :workspaceId AND id = :setId",
        )
            .bind(BIND_WORKSPACE_ID, workspaceId)
            .bind("setId", setId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return rows > 0
    }

    private fun mapRow(row: io.r2dbc.spi.Row): HashtagSavedSet {
        val createdAt = row.get("created_at", OffsetDateTime::class.java)!!.toInstant()
        val updatedAt = row.get("updated_at", OffsetDateTime::class.java)!!.toInstant()
        val hashtagsJson = requireNotNull(row.get("hashtags_json", String::class.java))
        return HashtagSavedSet(
            id = requireNotNull(row.get("id", String::class.java)),
            workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
            name = requireNotNull(row.get("name", String::class.java)),
            hashtags = objectMapper.readValue(hashtagsJson),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
