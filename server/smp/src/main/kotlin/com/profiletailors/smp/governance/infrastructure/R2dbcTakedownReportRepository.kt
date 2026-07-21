package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Suppress("StringLiteralDuplication")
@Repository
internal class R2dbcTakedownReportRepository(private val databaseClient: DatabaseClient) : TakedownReportRepository {

    override suspend fun save(report: TakedownReport): TakedownReport {
        var spec = databaseClient.sql(
            """
            INSERT INTO takedown_reports (
                report_id, workspace_id, asset_id, reported_by_id, reason, status,
                rejection_reason, reviewed_by_id, reviewed_at, reporter_email,
                media_reference_url, created_at, updated_at
            ) VALUES (
                :reportId, :workspaceId, :assetId, :reportedById, :reason, :status,
                :rejectionReason, :reviewedById, :reviewedAt, :reporterEmail,
                :mediaReferenceUrl, :createdAt, :updatedAt
            )
            ON CONFLICT (report_id) DO UPDATE SET
                status = EXCLUDED.status,
                rejection_reason = EXCLUDED.rejection_reason,
                reviewed_by_id = EXCLUDED.reviewed_by_id,
                reviewed_at = EXCLUDED.reviewed_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .bind("reportId", report.reportId)
            .bind("workspaceId", report.workspaceId)
            .bind("assetId", report.assetId)
            .bind("reportedById", report.reportedById)
            .bind("reason", report.reason)
            .bind("status", report.status.name)
            .bind("reporterEmail", report.reporterEmail)
            .bind("createdAt", OffsetDateTime.ofInstant(report.createdAt, ZoneOffset.UTC))
            .bind("updatedAt", OffsetDateTime.ofInstant(report.updatedAt, ZoneOffset.UTC))

        spec = bindNullable(spec, "rejectionReason", report.rejectionReason)
        spec = bindNullable(spec, "reviewedById", report.reviewedById)
        spec = bindNullableInstant(spec, "reviewedAt", report.reviewedAt)
        spec = bindNullable(spec, "mediaReferenceUrl", report.mediaReferenceUrl)

        spec.then().awaitSingleOrNull()
        return report
    }

    override suspend fun findById(workspaceId: String, reportId: String): TakedownReport? = databaseClient.sql(
        """
        $SELECT_COLUMNS
        FROM takedown_reports
        WHERE workspace_id = :workspaceId AND report_id = :reportId
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("reportId", reportId)
        .map { row, _ -> rowToTakedownReport(row) }
        .one()
        .awaitSingleOrNull()

    override suspend fun findExisting(workspaceId: String, assetId: String, reportedById: String): TakedownReport? =
        databaseClient.sql(
            """
            $SELECT_COLUMNS
            FROM takedown_reports
            WHERE workspace_id = :workspaceId AND asset_id = :assetId AND reported_by_id = :reportedById
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("assetId", assetId)
            .bind("reportedById", reportedById)
            .map { row, _ -> rowToTakedownReport(row) }
            .one()
            .awaitSingleOrNull()

    override fun findByWorkspace(workspaceId: String, status: TakedownReportStatus?): Flow<TakedownReport> {
        val query = if (status != null) {
            databaseClient.sql(
                """
                $SELECT_COLUMNS
                FROM takedown_reports
                WHERE workspace_id = :workspaceId AND status = :status
                ORDER BY created_at DESC
                """.trimIndent(),
            )
                .bind("workspaceId", workspaceId)
                .bind("status", status.name)
        } else {
            databaseClient.sql(
                """
                $SELECT_COLUMNS
                FROM takedown_reports
                WHERE workspace_id = :workspaceId
                ORDER BY created_at DESC
                """.trimIndent(),
            )
                .bind("workspaceId", workspaceId)
        }

        return query.map { row, _ -> rowToTakedownReport(row) }
            .all()
            .asFlow()
    }

    private fun rowToTakedownReport(row: Row): TakedownReport = TakedownReport(
        reportId = requireNotNull(row.get("report_id", String::class.java)),
        workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
        assetId = requireNotNull(row.get("asset_id", String::class.java)),
        reportedById = requireNotNull(row.get("reported_by_id", String::class.java)),
        reason = requireNotNull(row.get("reason", String::class.java)),
        status = TakedownReportStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
        rejectionReason = row.get("rejection_reason", String::class.java),
        reviewedById = row.get("reviewed_by_id", String::class.java),
        reviewedAt = row.get("reviewed_at", OffsetDateTime::class.java)?.toInstant(),
        reporterEmail = requireNotNull(row.get("reporter_email", String::class.java)),
        mediaReferenceUrl = row.get("media_reference_url", String::class.java),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
    )

    private fun bindNullable(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: String?,
    ): DatabaseClient.GenericExecuteSpec =
        if (value != null) spec.bind(name, value) else spec.bindNull(name, String::class.java)

    private fun bindNullableInstant(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: Instant?,
    ): DatabaseClient.GenericExecuteSpec = if (value != null) {
        spec.bind(name, OffsetDateTime.ofInstant(value, ZoneOffset.UTC))
    } else {
        spec.bindNull(name, OffsetDateTime::class.java)
    }

    companion object {
        private val SELECT_COLUMNS = """
            SELECT report_id, workspace_id, asset_id, reported_by_id, reason, status,
                   rejection_reason, reviewed_by_id, reviewed_at, reporter_email,
                   media_reference_url, created_at, updated_at
        """.trimIndent()
    }
}
