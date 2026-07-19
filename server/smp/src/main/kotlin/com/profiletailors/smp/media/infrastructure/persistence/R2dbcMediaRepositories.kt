package com.profiletailors.smp.media.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.application.MediaRateLimitRepository
import com.profiletailors.smp.media.application.PagedMediaAssets
import com.profiletailors.smp.media.application.WorkspaceFileBlobRepository
import com.profiletailors.smp.media.domain.BlobStatus
import com.profiletailors.smp.media.domain.BlobUpsertResult
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import io.r2dbc.spi.Readable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64

@Repository
class R2dbcMediaAssetRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : MediaAssetRepository {

    override suspend fun create(asset: MediaAsset): MediaAsset {
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                detected_media_type, original_filename, file_size_bytes, status,
                failure_reason, upload_started_at, created_at, updated_at,
                source_provider, external_id, source_url, author_name, author_url, metadata
            ) VALUES (
                :assetId, :workspaceId, :sourceType, :fileHash, :mediaType, :storageKey,
                :detectedMediaType, :originalFilename, :fileSizeBytes, :status,
                :failureReason, :uploadStartedAt, :createdAt, :updatedAt,
                :sourceProvider, :externalId, :sourceUrl, :authorName, :authorUrl, CAST(:metadata AS JSONB)
            )
            """.trimIndent(),
        )
            .bind("assetId", asset.assetId)
            .bind("workspaceId", asset.workspaceId)
            .bind("sourceType", asset.sourceType.name)
            .bind("fileHash", asset.fileHash)
            .bind("mediaType", asset.mediaType)
            .bind("storageKey", asset.storageKey)
            .bind("detectedMediaType", asset.detectedMediaType)
            .bind("originalFilename", asset.originalFilename)
            .bind("fileSizeBytes", asset.fileSizeBytes)
            .bind("status", asset.status.name)
            .bind("failureReason", asset.failureReason)
            .bind("uploadStartedAt", asset.uploadStartedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) })
            .bind("createdAt", OffsetDateTime.ofInstant(asset.createdAt, ZoneOffset.UTC))
            .bind(
                "updatedAt",
                asset.updatedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
                    ?: OffsetDateTime.ofInstant(asset.createdAt, ZoneOffset.UTC),
            )
            .bindNullable("sourceProvider", asset.sourceProvider, String::class.java)
            .bindNullable("externalId", asset.externalId, String::class.java)
            .bindNullable("sourceUrl", asset.sourceUrl, String::class.java)
            .bindNullable("authorName", asset.authorName, String::class.java)
            .bindNullable("authorUrl", asset.authorUrl, String::class.java)
            .bindNullable("metadata", asset.metadata?.let { objectMapper.writeValueAsString(it) }, String::class.java)
            .then()
            .awaitSingleOrNull()

        return asset
    }

    override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset? = databaseClient.sql(
        """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE workspace_id = :workspaceId AND asset_id = :assetId
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("assetId", assetId)
        .map { row, _ -> rowToMediaAsset(row) }
        .one()
        .awaitSingleOrNull()

    override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>): List<MediaAsset> {
        if (assetIds.isEmpty()) return emptyList()

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE workspace_id = :workspaceId AND asset_id IN (:assetIds)
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("assetIds", assetIds)
            .map { row, _ -> rowToMediaAsset(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<MediaAssetStatus>,
        pageSize: Int,
        cursor: String?,
    ): PagedMediaAssets {
        val effectivePageSize = pageSize.coerceIn(1, 100)
        val (cursorCreatedAt, cursorAssetId) = parseCursor(cursor)

        val conditions = mutableListOf("workspace_id = :workspaceId")

        if (statuses.isNotEmpty()) {
            val statusPlaceholders = statuses.mapIndexed { index, _ -> ":status$index" }
            conditions.add("status IN (${statusPlaceholders.joinToString(", ")})")
        }

        if (cursorCreatedAt != null && cursorAssetId != null) {
            conditions.add("(created_at, asset_id) < (:cursorCreatedAt, :cursorAssetId)")
        }

        val sql = """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY created_at DESC, asset_id DESC
            LIMIT :pageSize
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("workspaceId", workspaceId)
            .bind("pageSize", effectivePageSize + 1)

        statuses.forEachIndexed { index, status ->
            spec = spec.bind("status$index", status.name)
        }

        if (cursorCreatedAt != null && cursorAssetId != null) {
            spec = spec.bind("cursorCreatedAt", OffsetDateTime.ofInstant(cursorCreatedAt, ZoneOffset.UTC))
            spec = spec.bind("cursorAssetId", cursorAssetId)
        }

        val assets = spec
            .map { row, _ -> rowToMediaAsset(row) }
            .all()
            .collectList()
            .awaitSingle()

        val hasNext = assets.size > effectivePageSize
        val resultAssets = if (hasNext) assets.dropLast(1) else assets

        val nextCursor = if (hasNext && resultAssets.isNotEmpty()) {
            val lastAsset = resultAssets.last()
            val cursorValue = "${lastAsset.createdAt}:${lastAsset.assetId}"
            Base64.getUrlEncoder().encodeToString(cursorValue.toByteArray())
        } else {
            null
        }

        return PagedMediaAssets(assets = resultAssets, nextCursor = nextCursor)
    }

    override suspend fun claimCasUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean {
        val result = databaseClient.sql(
            """
            UPDATE media_assets
            SET status = 'UPLOADING',
                upload_started_at = :now,
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = :assetId
              AND workspace_id = :workspaceId
              AND status IN ('PENDING_UPLOAD', 'FAILED')
            """.trimIndent(),
        )
            .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return result > 0
    }

    override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean {
        val threshold = now.minusSeconds(30 * 60) // 30 minutes ago (legacy grace period)

        val result = databaseClient.sql(
            """
            UPDATE media_assets
            SET upload_started_at = :now
            WHERE asset_id = :assetId
              AND workspace_id = :workspaceId
              AND status IN ('PROCESSING', 'FAILED')
              AND (upload_started_at IS NULL OR upload_started_at < :threshold)
            """.trimIndent(),
        )
            .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .bind("threshold", OffsetDateTime.ofInstant(threshold, ZoneOffset.UTC))
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return result > 0
    }

    override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? {
        databaseClient.sql(
            """
            UPDATE media_assets
            SET file_size_bytes = :fileSizeBytes,
                status = 'READY',
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("fileSizeBytes", fileSizeBytes)
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()

        return findByWorkspaceAndId(workspaceId, assetId)
    }

    override suspend fun markAsReadyFromDedup(
        assetId: String,
        workspaceId: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long?,
    ): MediaAsset? {
        var spec = databaseClient.sql(
            """
            UPDATE media_assets
            SET status = 'READY',
                storage_key = :storageKey,
                detected_media_type = :detectedMediaType,
                file_size_bytes = COALESCE(:fileSizeBytes, file_size_bytes),
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("storageKey", storageKey)
            .bind("detectedMediaType", detectedMediaType)
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)

        spec = if (fileSizeBytes == null) {
            spec.bindNull("fileSizeBytes", Long::class.java)
        } else {
            spec.bind("fileSizeBytes", fileSizeBytes)
        }

        spec.then().awaitSingleOrNull()

        return findByWorkspaceAndId(workspaceId, assetId)
    }

    override suspend fun markAsFailed(assetId: String, workspaceId: String, reason: String?): MediaAsset? {
        databaseClient.sql(
            """
            UPDATE media_assets
            SET status = 'FAILED',
                failure_reason = :reason,
                upload_started_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("reason", reason)
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()

        return findByWorkspaceAndId(workspaceId, assetId)
    }

    override suspend fun softDelete(assetId: String, workspaceId: String): MediaAsset? {
        databaseClient.sql(
            """
            UPDATE media_assets
            SET status = 'DELETED',
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()

        return findByWorkspaceAndId(workspaceId, assetId)
    }

    override suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long): List<MediaAsset> {
        val threshold = OffsetDateTime.now(ZoneOffset.UTC).minusHours(thresholdHours)
        val graceThreshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(gracePeriodMinutes)

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE status = 'PROCESSING'
              AND created_at < :threshold
              AND (upload_started_at IS NULL OR upload_started_at < :graceThreshold)
            ORDER BY created_at ASC
            """.trimIndent(),
        )
            .bind("threshold", threshold)
            .bind("graceThreshold", graceThreshold)
            .map { row, _ -> rowToMediaAsset(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun findRecentlyFailedAssets(): List<MediaAsset> {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7)

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE status = 'FAILED'
              AND created_at > :cutoff
              AND storage_key IS NOT NULL
            ORDER BY created_at DESC
            LIMIT 1000
            """.trimIndent(),
        )
            .bind("cutoff", cutoff)
            .map { row, _ -> rowToMediaAsset(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun findExpiredPendingUploadAssets(limit: Int): List<MediaAsset> {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusHours(24)

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE status = 'PENDING_UPLOAD'
              AND created_at < :cutoff
            ORDER BY created_at ASC
            LIMIT :limit
            """.trimIndent(),
        )
            .bind("cutoff", cutoff)
            .bind("limit", limit)
            .map { row, _ -> rowToMediaAsset(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun findExpiredUploadingAssets(limit: Int): List<MediaAsset> {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusHours(24)

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                   detected_media_type, original_filename, file_size_bytes, status,
                   failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
            FROM media_assets
            WHERE status = 'UPLOADING'
              AND upload_started_at < :cutoff
            ORDER BY upload_started_at ASC
            LIMIT :limit
            """.trimIndent(),
        )
            .bind("cutoff", cutoff)
            .bind("limit", limit)
            .map { row, _ -> rowToMediaAsset(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int = databaseClient.sql(
        """
            SELECT COUNT(*) AS cnt
            FROM media_assets
            WHERE workspace_id = :workspaceId
              AND file_hash = :fileHash
              AND status NOT IN ('DELETED', 'FAILED')
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("fileHash", fileHash)
        .map { row, _ -> row.get("cnt", Long::class.java)?.toInt() ?: 0 }
        .one()
        .awaitSingle()

    override suspend fun findActiveByWorkspaceAndHash(workspaceId: String, fileHash: String): MediaAsset? =
        databaseClient.sql(
            """
                SELECT asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                       detected_media_type, original_filename, file_size_bytes, status,
                       failure_reason, upload_started_at, created_at, updated_at,
                   source_provider, external_id, source_url, author_name, author_url, metadata
                FROM media_assets
                WHERE workspace_id = :workspaceId
                  AND file_hash = :fileHash
                  AND status NOT IN ('DELETED', 'FAILED')
                ORDER BY created_at ASC, asset_id ASC
                LIMIT 1
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .map { row, _ -> rowToMediaAsset(row) }
            .one()
            .awaitSingleOrNull()

    private fun rowToMediaAsset(row: Readable): MediaAsset = MediaAsset(
        assetId = requireNotNull(row.get("asset_id", String::class.java)),
        workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
        sourceType = MediaSourceType.valueOf(requireNotNull(row.get("source_type", String::class.java))),
        fileHash = row.get("file_hash", String::class.java),
        mediaType = requireNotNull(row.get("media_type", String::class.java)),
        storageKey = row.get("storage_key", String::class.java),
        detectedMediaType = row.get("detected_media_type", String::class.java),
        originalFilename = row.get("original_filename", String::class.java),
        fileSizeBytes = row.get("file_size_bytes", Long::class.javaObjectType),
        status = MediaAssetStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
        failureReason = row.get("failure_reason", String::class.java),
        uploadStartedAt = row.get("upload_started_at", OffsetDateTime::class.java)?.toInstant(),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = row.get("updated_at", OffsetDateTime::class.java)?.toInstant(),
        sourceProvider = row.get("source_provider", String::class.java),
        externalId = row.get("external_id", String::class.java),
        sourceUrl = row.get("source_url", String::class.java),
        authorName = row.get("author_name", String::class.java),
        authorUrl = row.get("author_url", String::class.java),
        metadata = readMetadata(row.get("metadata", String::class.java)),
    )

    private fun readMetadata(json: String?): Map<String, Any>? = json?.let {
        objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
    }
}

private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value == null) {
    bindNull(name, type)
} else {
    bind(name, value)
}

@Repository
class R2dbcWorkspaceFileBlobRepository(private val databaseClient: DatabaseClient) : WorkspaceFileBlobRepository {

    override suspend fun upsertBlob(workspaceId: String, fileHash: String): BlobUpsertResult {
        val inserted = databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (
                workspace_id, file_hash, status, created_at, updated_at
            ) VALUES (
                :workspaceId, :fileHash, 'UPLOADING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (workspace_id, file_hash) DO NOTHING
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .fetch()
            .rowsUpdated()
            .awaitSingle() > 0

        val blob = findByWorkspaceAndHash(workspaceId, fileHash)
            ?: throw IllegalStateException("Blob row missing after upsert for ($workspaceId, $fileHash)")

        return if (inserted) {
            BlobUpsertResult.Created(blob)
        } else {
            BlobUpsertResult.Existed(blob)
        }
    }

    override suspend fun findByWorkspaceAndHash(workspaceId: String, fileHash: String): WorkspaceFileBlob? =
        databaseClient.sql(
            """
            SELECT workspace_id, file_hash, storage_key, file_size_bytes, detected_media_type,
                   status, failure_reason, orphaned_at, gc_failure_count, last_gc_attempt_at,
                   created_at, updated_at
            FROM workspace_file_blobs
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .map { row, _ -> rowToBlob(row) }
            .one()
            .awaitSingleOrNull()

    override suspend fun findBlobForUpdate(workspaceId: String, fileHash: String): WorkspaceFileBlob? =
        databaseClient.sql(
            """
            SELECT workspace_id, file_hash, storage_key, file_size_bytes, detected_media_type,
                   status, failure_reason, orphaned_at, gc_failure_count, last_gc_attempt_at,
                   created_at, updated_at
            FROM workspace_file_blobs
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            FOR UPDATE
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .map { row, _ -> rowToBlob(row) }
            .one()
            .awaitSingleOrNull()

    override suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int = databaseClient.sql(
        """
            SELECT COUNT(*) AS cnt
            FROM media_assets
            WHERE workspace_id = :workspaceId
              AND file_hash = :fileHash
              AND status NOT IN ('DELETED', 'FAILED')
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("fileHash", fileHash)
        .map { row, _ -> row.get("cnt", Long::class.java)?.toInt() ?: 0 }
        .one()
        .awaitSingle()

    override suspend fun markReadyForGC(workspaceId: String, fileHash: String, orphanedAt: Instant) {
        databaseClient.sql(
            """
            UPDATE workspace_file_blobs
            SET status = 'READY_FOR_GC',
                orphaned_at = :orphanedAt,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("orphanedAt", OffsetDateTime.ofInstant(orphanedAt, ZoneOffset.UTC))
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun markAsGarbageCollected(workspaceId: String, fileHash: String) {
        databaseClient.sql(
            """
            UPDATE workspace_file_blobs
            SET status = 'GARBAGE_COLLECTED',
                failure_reason = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun findReadyForGC(threshold: Instant, batchSize: Int): Flow<WorkspaceFileBlob> =
        databaseClient.sql(
            """
            SELECT workspace_id, file_hash, storage_key, file_size_bytes, detected_media_type,
                   status, failure_reason, orphaned_at, gc_failure_count, last_gc_attempt_at,
                   created_at, updated_at
            FROM workspace_file_blobs
            WHERE status = 'READY_FOR_GC'
              AND orphaned_at < :threshold
              AND gc_failure_count < :maxFailures
            ORDER BY orphaned_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
        )
            .bind("threshold", OffsetDateTime.ofInstant(threshold, ZoneOffset.UTC))
            .bind("maxFailures", com.profiletailors.smp.media.domain.MediaAsset.GC_MAX_FAILURE_COUNT)
            .bind("batchSize", batchSize)
            .map { row, _ -> rowToBlob(row) }
            .all()
            .asFlow()

    override suspend fun recordGCFailure(workspaceId: String, fileHash: String, failureReason: String) {
        databaseClient.sql(
            """
            UPDATE workspace_file_blobs
            SET gc_failure_count = gc_failure_count + 1,
                last_gc_attempt_at = CURRENT_TIMESTAMP,
                failure_reason = :failureReason,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("failureReason", failureReason)
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun markBlobReady(
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    ) {
        databaseClient.sql(
            """
            UPDATE workspace_file_blobs
            SET status = 'READY',
                storage_key = :storageKey,
                detected_media_type = :detectedMediaType,
                file_size_bytes = :fileSizeBytes,
                failure_reason = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("storageKey", storageKey)
            .bind("detectedMediaType", detectedMediaType)
            .bind("fileSizeBytes", fileSizeBytes)
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun resetBlobToUploading(workspaceId: String, fileHash: String) {
        databaseClient.sql(
            """
            UPDATE workspace_file_blobs
            SET status = 'UPLOADING',
                storage_key = NULL,
                detected_media_type = NULL,
                file_size_bytes = NULL,
                orphaned_at = NULL,
                gc_failure_count = 0,
                failure_reason = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun markBlobFailed(workspaceId: String, fileHash: String, failureReason: String) {
        databaseClient.sql(
            """
            UPDATE workspace_file_blobs
            SET status = 'FAILED',
                failure_reason = :failureReason,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId AND file_hash = :fileHash
            """.trimIndent(),
        )
            .bind("failureReason", failureReason)
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .then()
            .awaitSingleOrNull()
    }

    private fun rowToBlob(row: Readable): WorkspaceFileBlob = WorkspaceFileBlob(
        workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
        fileHash = requireNotNull(row.get("file_hash", String::class.java)),
        storageKey = row.get("storage_key", String::class.java),
        fileSizeBytes = row.get("file_size_bytes", Long::class.javaObjectType),
        detectedMediaType = row.get("detected_media_type", String::class.java),
        status = BlobStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
        failureReason = row.get("failure_reason", String::class.java),
        orphanedAt = row.get("orphaned_at", OffsetDateTime::class.java)?.toInstant(),
        gcFailureCount = row.get("gc_failure_count", Int::class.javaObjectType) ?: 0,
        lastGcAttemptAt = row.get("last_gc_attempt_at", OffsetDateTime::class.java)?.toInstant(),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = row.get("updated_at", OffsetDateTime::class.java)?.toInstant(),
    )
}

@Repository
class R2dbcMediaRateLimitRepository(private val databaseClient: DatabaseClient) : MediaRateLimitRepository {

    override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean {
        databaseClient.sql(
            """
            INSERT INTO workspace_upload_slots (workspace_id, active_uploads, created_at, updated_at)
            VALUES (:workspaceId, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (workspace_id) DO NOTHING
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()

        val result = databaseClient.sql(
            """
            UPDATE workspace_upload_slots
            SET active_uploads = active_uploads + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId
              AND active_uploads < :maxConcurrent
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("maxConcurrent", maxConcurrent)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return result > 0
    }

    override suspend fun releaseConcurrentUploadSlot(workspaceId: String) {
        databaseClient.sql(
            """
            UPDATE workspace_upload_slots
            SET active_uploads = GREATEST(active_uploads - 1, 0),
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun tryIncrementHourlyCreationCount(
        workspaceId: String,
        maxPerHour: Int,
    ): MediaRateLimitRepository.RateLimitIncrementResult {
        val currentHour = OffsetDateTime.now(ZoneOffset.UTC)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        databaseClient.sql(
            """
            INSERT INTO media_rate_limits (workspace_id, hourly_creation_count, hour_bucket, created_at, updated_at)
            VALUES (:workspaceId, 0, :currentHour, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (workspace_id) DO UPDATE SET
                hour_bucket = :currentHour,
                hourly_creation_count = CASE
                    WHEN media_rate_limits.hour_bucket <> :currentHour THEN 0
                    ELSE media_rate_limits.hourly_creation_count
                END,
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("currentHour", currentHour)
            .then()
            .awaitSingleOrNull()

        val row = databaseClient.sql(
            """
            UPDATE media_rate_limits
            SET hourly_creation_count = hourly_creation_count + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId
              AND hour_bucket = :currentHour
              AND hourly_creation_count < :maxPerHour
            RETURNING hourly_creation_count
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("currentHour", currentHour)
            .bind("maxPerHour", maxPerHour)
            .fetch()
            .first()
            .awaitSingleOrNull()

        return if (row != null) {
            // Increment succeeded; RETURNING yielded the new count atomically.
            val count = (row["hourly_creation_count"] as? Number)?.toInt() ?: 1
            MediaRateLimitRepository.RateLimitIncrementResult(count, true)
        } else {
            MediaRateLimitRepository.RateLimitIncrementResult(maxPerHour, false)
        }
    }
}

private fun parseCursor(cursor: String?): Pair<Instant?, String?> {
    if (cursor == null) return Pair(null, null)

    return try {
        val decoded = String(Base64.getUrlDecoder().decode(cursor))
        val separatorIndex = decoded.lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex == decoded.lastIndex) {
            Pair(null, null)
        } else {
            val createdAt = decoded.substring(0, separatorIndex)
            val assetId = decoded.substring(separatorIndex + 1)
            Pair(Instant.parse(createdAt), assetId)
        }
    } catch (_: Exception) {
        Pair(null, null)
    }
}
