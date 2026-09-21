package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.redactPayload
import com.profiletailors.smp.platformadmin.application.contracts.NotificationAdminQuery
import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.NotificationFilters
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class R2dbcNotificationAdminQueryAdapter(private val databaseClient: DatabaseClient) : NotificationAdminQuery {

    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun list(filters: NotificationFilters, page: Int, size: Int): PagedResult<NotificationSummary> {
        validatePagination(page, size)

        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        filters.status?.let {
            conditions += "n.status = :status"
            params["status"] = it
        }
        filters.channel?.let {
            conditions += "n.channel = :channel"
            params["channel"] = it
        }
        filters.templateId?.let {
            conditions += "n.template_id = :templateId"
            params["templateId"] = it
        }
        filters.recipient?.let {
            conditions += "LOWER(n.recipient) LIKE LOWER(:recipient)"
            params["recipient"] = "%$it%"
        }
        filters.createdFrom?.let {
            conditions += "n.created_at >= :createdFrom"
            params["createdFrom"] = it.atOffset(ZoneOffset.UTC)
        }
        filters.createdTo?.let {
            conditions += "n.created_at <= :createdTo"
            params["createdTo"] = it.atOffset(ZoneOffset.UTC)
        }

        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val offset = page.toLong() * size

        val countSql = "SELECT COUNT(*) FROM notifications n $where"
        val dataSql = """
            SELECT n.id, n.channel, n.recipient, n.template_id, n.status,
                   n.error_message, n.created_at, n.sent_at, n.failed_at, n.payload
            FROM notifications n
            $where
            ORDER BY n.created_at DESC
            LIMIT :size OFFSET :offset
        """.trimIndent()

        val countSpec = params.entries.fold(databaseClient.sql(countSql)) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }
        val dataSpec = params.entries.fold(
            databaseClient.sql(dataSql).bind("size", size).bind("offset", offset),
        ) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }

        val total = countSpec.map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        val items = dataSpec.map { row, _ -> row.toNotificationSummary() }.all().collectList().awaitSingle()

        return PagedResult.of(items, page, size, total)
    }

    override suspend fun findById(notificationId: NotificationId): NotificationSummary? {
        val sql = """
            SELECT n.id, n.channel, n.recipient, n.template_id, n.status,
                   n.error_message, n.created_at, n.sent_at, n.failed_at, n.payload
            FROM notifications n
            WHERE n.id = :id
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", notificationId.value)
            .map { row, _ -> row.toNotificationSummary() }
            .one()
            .awaitSingleOrNull()
    }

    private fun Readable.toNotificationSummary(): NotificationSummary {
        val payloadJson = get("payload", String::class.java) ?: "{}"
        val payloadMap: Map<String, Any?> = try {
            objectMapper.readValue(payloadJson, object : TypeReference<Map<String, Any?>>() {})
        } catch (e: JsonProcessingException) {
            logger.warn("Failed to parse notification payload JSON: {}", e.message)
            emptyMap()
        }

        val redactedPayload = redactPayload(payloadMap)

        return NotificationSummary(
            id = get("id", String::class.java) ?: "",
            channel = get("channel", String::class.java) ?: "",
            templateId = get("template_id", String::class.java) ?: "",
            recipient = get("recipient", String::class.java) ?: "",
            status = get("status", String::class.java) ?: "",
            errorMessage = get("error_message", String::class.java),
            createdAt = getOffsetDateTime("created_at")?.toInstant() ?: Instant.EPOCH,
            sentAt = getOffsetDateTime("sent_at")?.toInstant(),
            failedAt = getOffsetDateTime("failed_at")?.toInstant(),
            redactedPayload = redactedPayload,
        )
    }

    private fun Readable.getOffsetDateTime(column: String): OffsetDateTime? = get(column, OffsetDateTime::class.java)
}
