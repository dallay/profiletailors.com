package com.profiletailors.smp.administrative.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEvent
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEventRepository
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcAdministrativeAuditEventRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : AdministrativeAuditEventRepository {

    override suspend fun save(event: AdministrativeAuditEvent): AdministrativeAuditEvent {
        val metadataJson = objectMapper.writeValueAsString(event.metadata)
        databaseClient.sql(INSERT)
            .bind("id", event.id)
            .bind("actorId", event.actorId)
            .bind("actorType", event.actorType)
            .bind("action", event.action)
            .bind("targetId", event.targetId)
            .bind("targetType", event.targetType)
            .bindNullableString("correlationId", event.correlationId)
            .bind("metadata", metadataJson)
            .bind("occurredAt", OffsetDateTime.ofInstant(event.occurredAt, ZoneOffset.UTC))
            .then()
            .awaitSingle()
        return event
    }

    override suspend fun findById(id: UUID): AdministrativeAuditEvent? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id)
        .map { row, _ -> row.toEvent() }
        .one()
        .awaitSingleOrNull()

    override suspend fun findByActor(actorId: UUID): List<AdministrativeAuditEvent> =
        databaseClient.sql(SELECT_BY_ACTOR)
            .bind("actorId", actorId)
            .map { row, _ -> row.toEvent() }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun findByTarget(targetType: String, targetId: String): List<AdministrativeAuditEvent> =
        databaseClient.sql(SELECT_BY_TARGET)
            .bind("targetType", targetType)
            .bind("targetId", targetId)
            .map { row, _ -> row.toEvent() }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun findByCorrelationId(correlationId: String): List<AdministrativeAuditEvent> =
        databaseClient.sql(SELECT_BY_CORRELATION)
            .bind("correlationId", correlationId)
            .map { row, _ -> row.toEvent() }
            .all()
            .collectList()
            .awaitSingle()

    @Suppress("UNCHECKED_CAST")
    private fun Readable.toEvent(): AdministrativeAuditEvent {
        val metadataJson: String = requireNotNull(get("metadata", String::class.java))
        val metadata: Map<String, String> = objectMapper.readValue(metadataJson, Map::class.java) as Map<String, String>
        return AdministrativeAuditEvent(
            id = requireNotNull(get("id", UUID::class.java)),
            actorId = requireNotNull(get("actor_id", UUID::class.java)),
            actorType = requireNotNull(get("actor_type", String::class.java)),
            action = requireNotNull(get("action", String::class.java)),
            targetId = requireNotNull(get("target_id", String::class.java)),
            targetType = requireNotNull(get("target_type", String::class.java)),
            correlationId = get("correlation_id", String::class.java),
            metadata = metadata,
            occurredAt = requireNotNull(get("occurred_at", OffsetDateTime::class.java)).toInstant(),
        )
    }

    companion object {
        private const val COLUMNS = """
            id, actor_id, actor_type, action, target_id, target_type,
            correlation_id, metadata, occurred_at
        """
        private const val SELECT_BY_ID = "SELECT $COLUMNS FROM administrative_audit_events WHERE id = :id"
        private const val SELECT_BY_ACTOR = """
            SELECT $COLUMNS FROM administrative_audit_events
            WHERE actor_id = :actorId ORDER BY occurred_at DESC
        """
        private const val SELECT_BY_TARGET = """
            SELECT $COLUMNS FROM administrative_audit_events
            WHERE target_type = :targetType AND target_id = :targetId ORDER BY occurred_at DESC
        """
        private const val SELECT_BY_CORRELATION = """
            SELECT $COLUMNS FROM administrative_audit_events
            WHERE correlation_id = :correlationId ORDER BY occurred_at DESC
        """
        private const val INSERT = """
            INSERT INTO administrative_audit_events (
                id, actor_id, actor_type, action, target_id, target_type,
                correlation_id, metadata, occurred_at
            ) VALUES (
                :id, :actorId, :actorType, :action, :targetId, :targetType,
                :correlationId, :metadata, :occurredAt
            )
        """
    }
}

private fun DatabaseClient.GenericExecuteSpec.bindNullableString(
    name: String,
    value: String?,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, String::class.java)
