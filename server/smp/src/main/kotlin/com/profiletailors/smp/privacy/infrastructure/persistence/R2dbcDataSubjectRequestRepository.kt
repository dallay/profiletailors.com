package com.profiletailors.smp.privacy.infrastructure.persistence

import com.profiletailors.smp.privacy.domain.DataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequestId
import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import com.profiletailors.smp.privacy.domain.DataSubjectRequestStatus
import com.profiletailors.smp.privacy.domain.RequestType
import io.r2dbc.spi.Readable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * R2DBC implementation of [DataSubjectRequestRepository].
 *
 * Maps between the domain [DataSubjectRequest] aggregate and the
 * `data_subject_requests` table. Uses PostgreSQL-compatible UPSERT
 * for the [save] method.
 *
 * @since 1.0.0
 */
@Repository
class R2dbcDataSubjectRequestRepository(
    private val databaseClient: DatabaseClient,
) : DataSubjectRequestRepository {

    override suspend fun save(request: DataSubjectRequest) {
        databaseClient.sql(
            """
            INSERT INTO data_subject_requests (
                id, request_type, status, requested_by, requested_by_email,
                workspace_id, notes, correction_data, result_ref, rejection_reason,
                created_at, updated_at, completed_at, expires_at
            ) VALUES (
                :id, :requestType, :status, :requestedBy, :requestedByEmail,
                :workspaceId, :notes, :correctionData, :resultRef, :rejectionReason,
                :createdAt, :updatedAt, :completedAt, :expiresAt
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                result_ref = EXCLUDED.result_ref,
                rejection_reason = EXCLUDED.rejection_reason,
                updated_at = EXCLUDED.updated_at,
                completed_at = EXCLUDED.completed_at
            """.trimIndent(),
        )
            .bind("id", request.id.value)
            .bind("requestType", request.requestType.name)
            .bind("status", request.status.name)
            .bind("requestedBy", request.requestedBy)
            .bind("requestedByEmail", request.requestedByEmail)
            .bindNullable("workspaceId", request.workspaceId, String::class.java)
            .bindNullable("notes", request.notes, String::class.java)
            .bindNullable("correctionData", request.correctionData, String::class.java)
            .bindNullable("resultRef", request.resultRef, String::class.java)
            .bindNullable("rejectionReason", request.rejectionReason, String::class.java)
            .bind("createdAt", OffsetDateTime.ofInstant(request.createdAt, ZoneOffset.UTC))
            .bind("updatedAt", OffsetDateTime.ofInstant(request.updatedAt, ZoneOffset.UTC))
            .bindNullable("completedAt", request.completedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }, OffsetDateTime::class.java)
            .bind("expiresAt", OffsetDateTime.ofInstant(request.expiresAt, ZoneOffset.UTC))
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun findById(id: String): DataSubjectRequest? = databaseClient.sql(
        """
        SELECT id, request_type, status, requested_by, requested_by_email,
               workspace_id, notes, correction_data, result_ref, rejection_reason,
               created_at, updated_at, completed_at, expires_at
        FROM data_subject_requests
        WHERE id = :id
        """.trimIndent(),
    )
        .bind("id", id)
        .map { row, _ -> rowToRequest(row) }
        .one()
        .awaitSingleOrNull()

    override suspend fun findByRequester(principalId: String): List<DataSubjectRequest> = databaseClient.sql(
        """
        SELECT id, request_type, status, requested_by, requested_by_email,
               workspace_id, notes, correction_data, result_ref, rejection_reason,
               created_at, updated_at, completed_at, expires_at
        FROM data_subject_requests
        WHERE requested_by = :principalId
        ORDER BY created_at DESC
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .map { row, _ -> rowToRequest(row) }
        .all()
        .asFlow()
        .toList()

    override suspend fun findByStatus(status: DataSubjectRequestStatus): List<DataSubjectRequest> = databaseClient.sql(
        """
        SELECT id, request_type, status, requested_by, requested_by_email,
               workspace_id, notes, correction_data, result_ref, rejection_reason,
               created_at, updated_at, completed_at, expires_at
        FROM data_subject_requests
        WHERE status = :status
        ORDER BY created_at DESC
        """.trimIndent(),
    )
        .bind("status", status.name)
        .map { row, _ -> rowToRequest(row) }
        .all()
        .asFlow()
        .toList()

    override suspend fun findExpired(before: Instant): List<DataSubjectRequest> = databaseClient.sql(
        """
        SELECT id, request_type, status, requested_by, requested_by_email,
               workspace_id, notes, correction_data, result_ref, rejection_reason,
               created_at, updated_at, completed_at, expires_at
        FROM data_subject_requests
        WHERE expires_at < :before
        ORDER BY created_at DESC
        """.trimIndent(),
    )
        .bind("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC))
        .map { row, _ -> rowToRequest(row) }
        .all()
        .asFlow()
        .toList()

    private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
        name: String,
        value: T?,
        type: Class<T>,
    ): DatabaseClient.GenericExecuteSpec = if (value == null) {
        bindNull(name, type)
    } else {
        bind(name, value)
    }

    private fun rowToRequest(row: Readable): DataSubjectRequest = DataSubjectRequest(
        id = DataSubjectRequestId(requireNotNull(row.get("id", String::class.java))),
        requestType = RequestType.valueOf(requireNotNull(row.get("request_type", String::class.java))),
        status = DataSubjectRequestStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
        requestedBy = requireNotNull(row.get("requested_by", String::class.java)),
        requestedByEmail = requireNotNull(row.get("requested_by_email", String::class.java)),
        workspaceId = row.get("workspace_id", String::class.java),
        notes = row.get("notes", String::class.java),
        correctionData = row.get("correction_data", String::class.java),
        resultRef = row.get("result_ref", String::class.java),
        rejectionReason = row.get("rejection_reason", String::class.java),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
        completedAt = row.get("completed_at", OffsetDateTime::class.java)?.toInstant(),
        expiresAt = requireNotNull(row.get("expires_at", OffsetDateTime::class.java)).toInstant(),
    )
}
