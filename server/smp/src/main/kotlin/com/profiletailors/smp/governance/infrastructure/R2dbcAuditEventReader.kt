package com.profiletailors.smp.governance.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.governance.domain.AuditEventCursor
import com.profiletailors.smp.governance.domain.AuditEventItem
import com.profiletailors.smp.governance.domain.AuditEventReader
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcAuditEventReader(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : AuditEventReader {
    override suspend fun readWorkspaceEvents(
        workspaceId: String,
        targetType: String?,
        action: String?,
        eventType: String?,
        actorPrincipalId: String?,
        createdAfter: Instant?,
        createdBefore: Instant?,
        cursor: AuditEventCursor?,
        limit: Int,
    ): List<AuditEventItem> {
        val sql = buildSqlQuery(targetType, action, eventType, actorPrincipalId, createdAfter, createdBefore, cursor)
        val spec = bindParameters(
            sql,
            workspaceId,
            targetType,
            action,
            eventType,
            actorPrincipalId,
            createdAfter,
            createdBefore,
            cursor,
            limit,
        )

        return spec.map { row, _ -> mapRow(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    private fun buildSqlQuery(
        targetType: String?,
        action: String?,
        eventType: String?,
        actorPrincipalId: String?,
        createdAfter: Instant?,
        createdBefore: Instant?,
        cursor: AuditEventCursor?,
    ): String = buildString {
        append(
            """
            SELECT id, event_type, action, request_name, request_path, permission,
                   actor_principal_id, workspace_id, target_type, target_id, outcome,
                   reason_code, role_keys_json, details_json, created_at
            FROM audit_events
            WHERE workspace_id = :workspaceId
            """.trimIndent(),
        )
        if (targetType != null) append(" AND target_type = :targetType")
        if (action != null) append(" AND action = :action")
        if (eventType != null) append(" AND event_type = :eventType")
        if (actorPrincipalId != null) append(" AND actor_principal_id = :actorPrincipalId")
        if (createdAfter != null) append(" AND created_at >= :createdAfter")
        if (createdBefore != null) append(" AND created_at <= :createdBefore")
        if (cursor != null) {
            append(" AND (created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id < :cursorId))")
        }
        append(" ORDER BY created_at DESC, id DESC LIMIT :limit")
    }

    private fun bindParameters(
        sql: String,
        workspaceId: String,
        targetType: String?,
        action: String?,
        eventType: String?,
        actorPrincipalId: String?,
        createdAfter: Instant?,
        createdBefore: Instant?,
        cursor: AuditEventCursor?,
        limit: Int,
    ): DatabaseClient.GenericExecuteSpec {
        var spec = databaseClient.sql(sql)
            .bind("workspaceId", workspaceId)
            .bind("limit", limit)

        if (targetType != null) spec = spec.bind("targetType", targetType)
        if (action != null) spec = spec.bind("action", action)
        if (eventType != null) spec = spec.bind("eventType", eventType)
        if (actorPrincipalId != null) spec = spec.bind("actorPrincipalId", actorPrincipalId)
        if (createdAfter != null) spec = spec.bind("createdAfter", createdAfter)
        if (createdBefore != null) spec = spec.bind("createdBefore", createdBefore)
        if (cursor != null) {
            spec = spec
                .bind("cursorCreatedAt", cursor.createdAt)
                .bind("cursorId", cursor.id)
        }
        return spec
    }

    private fun mapRow(row: io.r2dbc.spi.Row): AuditEventItem = AuditEventItem(
        id = requireNotNull(row.get("id", String::class.java)),
        eventType = requireNotNull(row.get("event_type", String::class.java)),
        action = row.get("action", String::class.java),
        requestName = row.get("request_name", String::class.java),
        requestPath = row.get("request_path", String::class.java),
        permission = row.get("permission", String::class.java),
        actorPrincipalId = row.get("actor_principal_id", String::class.java),
        workspaceId = row.get("workspace_id", String::class.java),
        targetType = row.get("target_type", String::class.java),
        targetId = row.get("target_id", String::class.java),
        outcome = row.get("outcome", String::class.java),
        reasonCode = row.get("reason_code", String::class.java),
        roleKeys = decodeList(row.get("role_keys_json", String::class.java)),
        details = decodeMap(row.get("details_json", String::class.java)),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
    )

    private fun decodeList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, LIST_TYPE)
    }

    private fun decodeMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return objectMapper.readValue(json, MAP_TYPE)
    }

    companion object {
        private val LIST_TYPE = object : TypeReference<List<String>>() {}
        private val MAP_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
