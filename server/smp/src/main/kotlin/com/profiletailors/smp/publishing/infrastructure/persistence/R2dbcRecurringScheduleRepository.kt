package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.RecurrenceFrequency
import com.profiletailors.smp.publishing.domain.RecurrenceRule
import com.profiletailors.smp.publishing.domain.RecurringSchedule
import com.profiletailors.smp.publishing.domain.RecurringScheduleRepository
import com.profiletailors.smp.publishing.domain.RecurringScheduleStatus
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
@Suppress("StringLiteralDuplication")
class R2dbcRecurringScheduleRepository(private val databaseClient: DatabaseClient) : RecurringScheduleRepository {
    override suspend fun create(schedule: RecurringSchedule): RecurringSchedule = write(schedule, insert = true)
    override suspend fun update(schedule: RecurringSchedule): RecurringSchedule = write(schedule, insert = false)

    override suspend fun findByWorkspaceAndId(workspaceId: String, id: String): RecurringSchedule? = query(
        "WHERE workspace_id = :workspaceId AND id = :id",
        mapOf("workspaceId" to workspaceId, "id" to id),
    ).next().awaitSingleOrNull()

    override suspend fun findByWorkspace(workspaceId: String): List<RecurringSchedule> = query(
        "WHERE workspace_id = :workspaceId ORDER BY next_scheduled_at NULLS LAST, created_at DESC",
        mapOf("workspaceId" to workspaceId),
    ).collectList().awaitSingle()

    override suspend fun pauseByTemplatePost(workspaceId: String, templatePostId: String) {
        databaseClient.sql(
            "UPDATE recurring_schedules SET status = :paused, updated_at = CURRENT_TIMESTAMP " +
                "WHERE workspace_id = :workspaceId AND template_post_id = :templatePostId AND status = :active",
        ).bind("paused", RecurringScheduleStatus.PAUSED.name)
            .bind("workspaceId", workspaceId).bind("templatePostId", templatePostId)
            .bind("active", RecurringScheduleStatus.ACTIVE.name).fetch().rowsUpdated().awaitSingle()
    }

    override suspend fun delete(workspaceId: String, id: String): Boolean = databaseClient.sql(
        "UPDATE recurring_schedules SET status = :cancelled, updated_at = CURRENT_TIMESTAMP " +
            "WHERE workspace_id = :workspaceId AND id = :id AND status <> :existingCancelled",
    ).bind("cancelled", RecurringScheduleStatus.CANCELLED.name)
        .bind("existingCancelled", RecurringScheduleStatus.CANCELLED.name)
        .bind("workspaceId", workspaceId).bind("id", id)
        .fetch().rowsUpdated().awaitSingle() > 0

    private suspend fun write(schedule: RecurringSchedule, insert: Boolean): RecurringSchedule {
        val sql = if (insert) {
            """INSERT INTO recurring_schedules
                (id, workspace_id, created_by, template_post_id, frequency, recurrence_interval, days_of_week,
                 day_of_month, end_date, max_occurrences, timezone, next_scheduled_at, status, created_at, updated_at)
                VALUES (:id, :workspaceId, :createdBy, :templatePostId, :frequency, :interval, :daysOfWeek,
                 :dayOfMonth, :endDate, :maxOccurrences, :timezone, :nextScheduledAt, :status, :createdAt, :updatedAt)"""
        } else {
            """UPDATE recurring_schedules SET frequency=:frequency, recurrence_interval=:interval, days_of_week=:daysOfWeek,
                 day_of_month=:dayOfMonth, end_date=:endDate, max_occurrences=:maxOccurrences, timezone=:timezone,
                 next_scheduled_at=:nextScheduledAt, status=:status, updated_at=:updatedAt
               WHERE id=:id AND workspace_id=:workspaceId"""
        }
        val now = schedule.updatedAt ?: Instant.now()
        var spec = databaseClient.sql(sql)
            .bind("id", schedule.id).bind("workspaceId", schedule.workspaceId)
            .bind("frequency", schedule.recurrenceRule.frequency.name)
            .bind("interval", schedule.recurrenceRule.interval)
            .bind("daysOfWeek", schedule.recurrenceRule.daysOfWeek.sorted().joinToString(","))
            .bind("timezone", schedule.timezone).bind("status", schedule.status.name).bind("updatedAt", now)
        spec = bindNullable(spec, "dayOfMonth", schedule.recurrenceRule.dayOfMonth, java.lang.Integer::class.java)
        spec = bindNullable(spec, "endDate", schedule.recurrenceRule.endDate, LocalDate::class.java)
        spec =
            bindNullable(spec, "maxOccurrences", schedule.recurrenceRule.maxOccurrences, java.lang.Integer::class.java)
        spec = bindNullable(spec, "nextScheduledAt", schedule.nextScheduledAt, Instant::class.java)
        if (insert) {
            spec = spec.bind("createdBy", schedule.createdBy).bind("templatePostId", schedule.templatePostId)
                .bind("createdAt", schedule.createdAt ?: now)
        }
        val rowsUpdated = spec.fetch().rowsUpdated().awaitSingle()
        if (!insert && rowsUpdated == 0L) {
            throw IllegalArgumentException(
                "Recurring schedule ${schedule.id} not found or not owned by workspace ${schedule.workspaceId}",
            )
        }
        return schedule.copy(createdAt = schedule.createdAt ?: now, updatedAt = now)
    }

    private fun <T : Any> bindNullable(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: T?,
        type: Class<*>,
    ): DatabaseClient.GenericExecuteSpec = value?.let { spec.bind(name, it) } ?: spec.bindNull(name, type)

    private fun query(where: String, params: Map<String, Any>): Flux<RecurringSchedule> {
        var spec = databaseClient.sql(
            """SELECT id, workspace_id, created_by, template_post_id, frequency, recurrence_interval, days_of_week,
               day_of_month, end_date, max_occurrences, timezone, next_scheduled_at, status, created_at, updated_at
               FROM recurring_schedules $where""",
        )
        params.forEach { (key, value) -> spec = spec.bind(key, value) }
        return spec.map { row, _ ->
            RecurringSchedule(
                id = requireNotNull(row.get("id", String::class.java)),
                workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                createdBy = requireNotNull(row.get("created_by", String::class.java)),
                templatePostId = requireNotNull(row.get("template_post_id", String::class.java)),
                recurrenceRule = RecurrenceRule(
                    frequency = RecurrenceFrequency.valueOf(requireNotNull(row.get("frequency", String::class.java))),
                    interval = requireNotNull(row.get("recurrence_interval", java.lang.Integer::class.java)).toInt(),
                    daysOfWeek = row.get("days_of_week", String::class.java).orEmpty().split(",").filter {
                        it.isNotBlank()
                    }.map { it.toInt() }.toSet(),
                    dayOfMonth = row.get("day_of_month", java.lang.Integer::class.java)?.toInt(),
                    endDate = row.get("end_date", LocalDate::class.java),
                    maxOccurrences = row.get("max_occurrences", java.lang.Integer::class.java)?.toInt(),
                ),
                timezone = requireNotNull(row.get("timezone", String::class.java)),
                nextScheduledAt = row.get("next_scheduled_at", OffsetDateTime::class.java)?.toInstant(),
                status = RecurringScheduleStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                updatedAt = row.get("updated_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }.all()
    }
}
