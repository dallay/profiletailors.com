@file:Suppress(
    "MaxLineLength",
    "ktlint:standard:max-line-length",
    "MagicNumber",
    "StringLiteralDuplication",
    "TooManyFunctions",
    "LongMethod",
)

package com.profiletailors.smp.publishing.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.BulkImportJob
import com.profiletailors.smp.publishing.domain.BulkImportJobRepository
import com.profiletailors.smp.publishing.domain.BulkImportRow
import com.profiletailors.smp.publishing.domain.BulkJobStatus
import com.profiletailors.smp.publishing.domain.BulkRowStatus
import com.profiletailors.smp.publishing.domain.ImportError
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcBulkImportJobRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : BulkImportJobRepository {
    private val logger = LoggerFactory.getLogger(javaClass)
    override suspend fun findByIdempotencyKey(idempotencyKey: String): BulkImportJob? = databaseClient.sql(
        """
        SELECT id, workspace_id, principal_id, idempotency_key, csv_hash, status, total_rows, scheduled_count, failed_count, created_at, updated_at
        FROM bulk_import_jobs
        WHERE idempotency_key = :key
        """.trimIndent(),
    )
        .bind("key", idempotencyKey)
        .map { row, _ -> row.toBulkJob() }
        .one()
        .awaitSingleOrNull()

    override suspend fun findByWorkspaceAndId(workspaceId: String, jobId: String): BulkImportJob? = databaseClient.sql(
        """
        SELECT id, workspace_id, principal_id, idempotency_key, csv_hash, status, total_rows, scheduled_count, failed_count, created_at, updated_at
        FROM bulk_import_jobs
        WHERE workspace_id = :workspaceId AND id = :jobId
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("jobId", jobId)
        .map { row, _ -> row.toBulkJob() }
        .one()
        .awaitSingleOrNull()

    override suspend fun save(job: BulkImportJob): BulkImportJob {
        try {
            databaseClient.sql(
                """
                INSERT INTO bulk_import_jobs (id, workspace_id, principal_id, idempotency_key, status, total_rows, scheduled_count, failed_count, csv_hash, created_at, updated_at)
                VALUES (:id, :workspaceId, :principalId, :idempotencyKey, :status, :totalRows, :scheduledCount, :failedCount, :csvHash, :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, total_rows = EXCLUDED.total_rows, scheduled_count = EXCLUDED.scheduled_count, failed_count = EXCLUDED.failed_count, updated_at = EXCLUDED.updated_at
                """.trimIndent(),
            )
                .bind("id", job.id)
                .bind("workspaceId", job.workspaceId)
                .bind("principalId", job.principalId)
                .bind("idempotencyKey", job.idempotencyKey)
                .bind("status", job.status.name)
                .bind("totalRows", job.totalRows)
                .bind("scheduledCount", job.scheduledCount)
                .bind("failedCount", job.failedCount)
                .bind("csvHash", job.csvHash)
                .bind("createdAt", job.createdAt)
                .bind("updatedAt", job.updatedAt)
                .fetch().rowsUpdated().awaitSingle()
        } catch (ex: org.springframework.dao.DataAccessException) {
            if (ex.message?.contains("duplicate", ignoreCase = true) == true) {
                throw IllegalStateException("Bulk job conflict for id ${job.id}", ex)
            }
            throw ex
        }
        return job
    }

    override suspend fun saveRows(rows: List<BulkImportRow>) {
        if (rows.isEmpty()) return
        val now = clock.instant()
        for (chunk in rows.chunked(100)) {
            val valuesClause = chunk.joinToString(", ") {
                "(:id${it.rowIndex}, :jobId${it.rowIndex}, :rowIndex${it.rowIndex}, :status${it.rowIndex}, :publicationId${it.rowIndex}, CAST(:errors${it.rowIndex} AS jsonb), :bodyText${it.rowIndex}, :scheduledFor${it.rowIndex}, :mediaUrls${it.rowIndex}, :hasConflict${it.rowIndex}, :createdAt${it.rowIndex})"
            }
            val sql = "INSERT INTO bulk_import_rows (id, job_id, row_index, status, publication_id, errors, body_text, scheduled_for, media_urls, has_conflict, created_at) VALUES $valuesClause"
            var spec = databaseClient.sql(sql)
            for (row in chunk) {
                val errorsJson = objectMapper.writeValueAsString(row.errors)
                val mediaUrlsText = row.mediaUrls.joinToString(",")
                spec = spec.bind("id${row.rowIndex}", row.id)
                    .bind("jobId${row.rowIndex}", row.jobId)
                    .bind("rowIndex${row.rowIndex}", row.rowIndex)
                    .bind("status${row.rowIndex}", row.status.name)
                    .bindNullable("publicationId${row.rowIndex}", row.publicationId)
                    .bind("errors${row.rowIndex}", errorsJson)
                    .bindNullable("bodyText${row.rowIndex}", row.bodyText)
                    .bindNullable("scheduledFor${row.rowIndex}", row.scheduledFor)
                    .bindNullable("mediaUrls${row.rowIndex}", mediaUrlsText.ifBlank { null })
                    .bind("hasConflict${row.rowIndex}", row.hasConflict)
                    .bind("createdAt${row.rowIndex}", now)
            }
            spec.fetch().rowsUpdated().awaitSingle()
        }
    }

    override suspend fun findRows(jobId: String): List<BulkImportRow> = databaseClient.sql(
        """
        SELECT id, job_id, row_index, status, publication_id, errors, body_text, scheduled_for, media_urls, has_conflict
        FROM bulk_import_rows
        WHERE job_id = :jobId
        ORDER BY row_index ASC
        """.trimIndent(),
    )
        .bind("jobId", jobId)
        .map { row, _ -> row.toBulkRow() }
        .all()
        .collectList()
        .awaitSingle()

    private fun Readable.toBulkJob(): BulkImportJob = BulkImportJob(
        id = requireNotNull(get("id", String::class.java)),
        workspaceId = requireNotNull(get("workspace_id", String::class.java)),
        principalId = requireNotNull(get("principal_id", String::class.java)),
        idempotencyKey = requireNotNull(get("idempotency_key", String::class.java)),
        csvHash = requireNotNull(get("csv_hash", String::class.java)),
        status = BulkJobStatus.valueOf(requireNotNull(get("status", String::class.java))),
        totalRows = requireNotNull(get("total_rows", Int::class.javaObjectType)),
        scheduledCount = requireNotNull(get("scheduled_count", Int::class.javaObjectType)),
        failedCount = requireNotNull(get("failed_count", Int::class.javaObjectType)),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(get("updated_at", OffsetDateTime::class.java)).toInstant(),
    )

    private fun Readable.toBulkRow(): BulkImportRow {
        val errorsJson = get("errors", String::class.java) ?: "[]"
        val errors: List<ImportError> = try {
            objectMapper.readValue(errorsJson, object : TypeReference<List<ImportError>>() {})
        } catch (ex: com.fasterxml.jackson.core.JsonProcessingException) {
            logger.warn("Failed to deserialize bulk row errors: $errorsJson", ex)
            emptyList()
        }
        val mediaUrlsRaw = get("media_urls", String::class.java) ?: ""
        val mediaUrls = if (mediaUrlsRaw.isBlank()) {
            emptyList()
        } else {
            mediaUrlsRaw.split(",").map {
                it.trim()
            }.filter { it.isNotBlank() }
        }
        return BulkImportRow(
            id = requireNotNull(get("id", String::class.java)),
            jobId = requireNotNull(get("job_id", String::class.java)),
            rowIndex = requireNotNull(get("row_index", Int::class.javaObjectType)),
            status = BulkRowStatus.valueOf(requireNotNull(get("status", String::class.java))),
            errors = errors,
            publicationId = get("publication_id", String::class.java),
            bodyText = get("body_text", String::class.java),
            scheduledFor = get("scheduled_for", OffsetDateTime::class.java)?.toInstant(),
            mediaUrls = mediaUrls,
            hasConflict = get("has_conflict", java.lang.Boolean::class.java)?.booleanValue() ?: false,
        )
    }

    private fun DatabaseClient.GenericExecuteSpec.bindNullable(
        name: String,
        value: String?,
    ): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, String::class.java)

    private fun DatabaseClient.GenericExecuteSpec.bindNullable(
        name: String,
        value: Instant?,
    ): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, Instant::class.java)
}
