@file:Suppress("MaxLineLength", "MagicNumber", "StringLiteralDuplication", "TooManyFunctions", "LongMethod")

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
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcBulkImportJobRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : BulkImportJobRepository {
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
        val existing = databaseClient.sql("SELECT id FROM bulk_import_jobs WHERE id = :id")
            .bind("id", job.id)
            .map<String> { r, _ -> requireNotNull(r.get("id", String::class.java)) }
            .one()
            .awaitSingleOrNull()
        if (existing != null) {
            databaseClient.sql(
                """
                UPDATE bulk_import_jobs
                SET status = :status, total_rows = :totalRows, scheduled_count = :scheduledCount, failed_count = :failedCount, updated_at = :updatedAt
                WHERE id = :id
                """.trimIndent(),
            )
                .bind("status", job.status.name)
                .bind("totalRows", job.totalRows)
                .bind("scheduledCount", job.scheduledCount)
                .bind("failedCount", job.failedCount)
                .bind("updatedAt", job.updatedAt)
                .bind("id", job.id)
                .fetch().rowsUpdated().awaitSingle()
        } else {
            databaseClient.sql(
                """
                INSERT INTO bulk_import_jobs (id, workspace_id, principal_id, idempotency_key, status, total_rows, scheduled_count, failed_count, csv_hash, created_at, updated_at)
                VALUES (:id, :workspaceId, :principalId, :idempotencyKey, :status, :totalRows, :scheduledCount, :failedCount, :csvHash, :createdAt, :updatedAt)
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
        }
        return job
    }

    override suspend fun saveRows(rows: List<BulkImportRow>) {
        if (rows.isEmpty()) return
        val chunkSize = 100
        for (chunk in rows.chunked(chunkSize)) {
            for (row in chunk) {
                val errorsJson = objectMapper.writeValueAsString(row.errors)
                val mediaUrlsText = row.mediaUrls.joinToString(",")
                databaseClient.sql(
                    """
                    INSERT INTO bulk_import_rows (id, job_id, row_index, status, publication_id, errors, body_text, scheduled_for, media_urls, has_conflict, created_at)
                    VALUES (:id, :jobId, :rowIndex, :status, :publicationId, CAST(:errors AS jsonb), :bodyText, :scheduledFor, :mediaUrls, :hasConflict, :createdAt)
                    """.trimIndent(),
                )
                    .bind("id", row.id)
                    .bind("jobId", row.jobId)
                    .bind("rowIndex", row.rowIndex)
                    .bind("status", row.status.name)
                    .bindNullable("publicationId", row.publicationId)
                    .bind("errors", errorsJson)
                    .bindNullable("bodyText", row.bodyText)
                    .bindNullable("scheduledFor", row.scheduledFor)
                    .bindNullable("mediaUrls", mediaUrlsText.ifBlank { null })
                    .bind("hasConflict", row.hasConflict)
                    .bind("createdAt", Instant.now())
                    .fetch().rowsUpdated().awaitSingle()
            }
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
        } catch (_: Exception) {
            emptyList()
        }
        val mediaUrlsRaw = get("media_urls", String::class.java) ?: ""
        val mediaUrls = if (mediaUrlsRaw.isBlank()) emptyList() else mediaUrlsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
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

    private fun DatabaseClient.GenericExecuteSpec.bindNullable(name: String, value: String?): DatabaseClient.GenericExecuteSpec =
        if (value != null) bind(name, value) else bindNull(name, String::class.java)

    private fun DatabaseClient.GenericExecuteSpec.bindNullable(name: String, value: Instant?): DatabaseClient.GenericExecuteSpec =
        if (value != null) bind(name, value) else bindNull(name, Instant::class.java)
}
