package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.model.AdminAuditEventSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminAuditQuery
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.query.ListAdminAuditEventsQuery
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcAdminAuditRepository(private val databaseClient: DatabaseClient) :
    AdminAuditQuery,
    AdministrativeAuditPublisher {

    override suspend fun publish(event: AdminAuditEvent) {
        databaseClient.sql(INSERT_EVENT)
            .bind("eventId", event.eventId)
            .bind("occurredAt", OffsetDateTime.ofInstant(event.occurredAt, ZoneOffset.UTC))
            .bind("operatorPrincipalId", event.operatorPrincipalId)
            .bind("operatorPlatformRoles", event.operatorPlatformRoles.map { it.name }.toTypedArray())
            .bind("action", event.action.name)
            .bind("targetType", event.targetType)
            .bind("targetId", event.targetId)
            .bind("result", event.result.name)
            .bindNullable("reason", event.reason, String::class.java)
            .bindNullable("correlationId", event.correlationId, String::class.java)
            .bindNullable("requestId", event.requestId, String::class.java)
            .bindNullable("sourceIpHash", event.sourceIpHash, String::class.java)
            .bindNullable("userAgentSummary", event.userAgentSummary, String::class.java)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun list(query: ListAdminAuditEventsQuery): PagedResult<AdminAuditEventSummary> {
        validatePagination(query.page, query.size)

        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        query.operatorPrincipalId?.let {
            conditions += "operator_principal_id = :operatorId"
            params["operatorId"] = it
        }
        query.action?.let {
            conditions += "action = :action"
            params["action"] = it
        }
        query.targetType?.let {
            conditions += "target_type = :targetType"
            params["targetType"] = it
        }
        query.targetId?.let {
            conditions += "target_id = :targetId"
            params["targetId"] = it
        }
        query.result?.let {
            conditions += "result = :result"
            params["result"] = it
        }
        query.correlationId?.let {
            conditions += "correlation_id = :correlationId"
            params["correlationId"] = it
        }
        query.occurredFrom?.let {
            conditions += "occurred_at >= :occurredFrom"
            params["occurredFrom"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }
        query.occurredTo?.let {
            conditions += "occurred_at <= :occurredTo"
            params["occurredTo"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }

        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val offset = query.page * query.size

        val countSql = "SELECT COUNT(*) FROM platform_admin_audit_events $where"
        val dataSql = """
            SELECT event_id, occurred_at, operator_principal_id, operator_platform_roles,
                   action, target_type, target_id, result, reason, correlation_id, request_id
            FROM platform_admin_audit_events $where
            ORDER BY occurred_at DESC LIMIT :size OFFSET :offset
        """.trimIndent()

        val countSpec = params.entries.fold(databaseClient.sql(countSql)) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }
        val dataSpec = params.entries.fold(
            databaseClient.sql(dataSql).bind("size", query.size).bind("offset", offset)
        ) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }

        val total = countSpec.map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        val items = dataSpec.map { row, _ -> row.toSummary() }.all().collectList().awaitSingle()

        return PagedResult.of(items, query.page, query.size, total)
    }

    override suspend fun findById(eventId: UUID): AdminAuditEventSummary? = databaseClient.sql(SELECT_BY_ID)
        .bind("eventId", eventId)
        .map { row, _ -> row.toSummary() }
        .one()
        .awaitSingleOrNull()

    private fun Readable.toSummary() = AdminAuditEventSummary(
        eventId = requireNotNull(get("event_id", UUID::class.java)),
        occurredAt = requireNotNull(get("occurred_at", OffsetDateTime::class.java)).toInstant(),
        operatorPrincipalId = requireNotNull(get("operator_principal_id", UUID::class.java)),
        operatorPlatformRoles = requireNotNull(get("operator_platform_roles", Array<String>::class.java)).toList(),
        action = requireNotNull(get("action", String::class.java)),
        targetType = requireNotNull(get("target_type", String::class.java)),
        targetId = requireNotNull(get("target_id", String::class.java)),
        result = requireNotNull(get("result", String::class.java)),
        reason = get("reason", String::class.java),
        correlationId = get("correlation_id", String::class.java),
        requestId = get("request_id", String::class.java),
    )

    companion object {
        private const val INSERT_EVENT = """
            INSERT INTO platform_admin_audit_events
              (event_id, occurred_at, operator_principal_id, operator_platform_roles,
               action, target_type, target_id, result, reason, correlation_id, request_id,
               source_ip_hash, user_agent_summary)
            VALUES
              (:eventId, :occurredAt, :operatorPrincipalId, :operatorPlatformRoles,
               :action, :targetType, :targetId, :result, :reason, :correlationId, :requestId,
               :sourceIpHash, :userAgentSummary)
        """
        private const val SELECT_BY_ID = """
            SELECT event_id, occurred_at, operator_principal_id, operator_platform_roles,
                   action, target_type, target_id, result, reason, correlation_id, request_id
            FROM platform_admin_audit_events WHERE event_id = :eventId
        """
    }
}
