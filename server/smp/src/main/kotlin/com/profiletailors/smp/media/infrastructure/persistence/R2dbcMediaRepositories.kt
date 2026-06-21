package com.profiletailors.smp.media.infrastructure.persistence

import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.application.MediaRateLimitRepository
import com.profiletailors.smp.media.application.PagedMediaAssets
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import io.r2dbc.spi.Readable
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
) : MediaAssetRepository {

    override suspend fun create(asset: MediaAsset): MediaAsset {
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, media_type, storage_key,
                original_filename, file_size_bytes, status, upload_started_at, created_at
            ) VALUES (
                :assetId, :workspaceId, :sourceType, :mediaType, :storageKey,
                :originalFilename, :fileSizeBytes, :status, :uploadStartedAt, :createdAt
            )
            """.trimIndent(),
        )
            .bind("assetId", asset.assetId)
            .bind("workspaceId", asset.workspaceId)
            .bind("sourceType", asset.sourceType.name)
            .bind("mediaType", asset.mediaType)
            .bind("storageKey", asset.storageKey)
            .bind("originalFilename", asset.originalFilename)
            .bind("fileSizeBytes", asset.fileSizeBytes)
            .bind("status", asset.status.name)
            .bind("uploadStartedAt", asset.uploadStartedAt)
            .bind("createdAt", OffsetDateTime.ofInstant(asset.createdAt, ZoneOffset.UTC))
            .then()
            .awaitSingleOrNull()

        return asset
    }

    override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset? {
        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, media_type, storage_key,
                   original_filename, file_size_bytes, status, upload_started_at, created_at
            FROM media_assets
            WHERE workspace_id = :workspaceId AND asset_id = :assetId
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("assetId", assetId)
            .map { row, _ -> rowToMediaAsset(row) }
            .one()
            .awaitSingleOrNull()
    }

    override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>): List<MediaAsset> {
        if (assetIds.isEmpty()) return emptyList()

        val idBindings = assetIds.mapIndexed { index, _ -> ":id$index" }
        val idParams = assetIds.mapIndexed { index, id -> "id$index" to id }.toMap()

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, media_type, storage_key,
                   original_filename, file_size_bytes, status, upload_started_at, created_at
            FROM media_assets
            WHERE workspace_id = :workspaceId AND asset_id IN (${idBindings.joinToString(", ")})
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .apply {
                idParams.forEach { (key, value) -> bind(key, value) }
            }
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

        // Build conditions
        val conditions = mutableListOf("workspace_id = :workspaceId")

        if (statuses.isNotEmpty()) {
            val statusPlaceholders = statuses.mapIndexed { index, _ -> ":status$index" }
            conditions.add("status IN (${statusPlaceholders.joinToString(", ")})")
        }

        if (cursorCreatedAt != null && cursorAssetId != null) {
            conditions.add("(created_at, asset_id) < (:cursorCreatedAt, :cursorAssetId)")
        }

        val sql = """
            SELECT asset_id, workspace_id, source_type, media_type, storage_key,
                   original_filename, file_size_bytes, status, upload_started_at, created_at
            FROM media_assets
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY created_at DESC, asset_id DESC
            LIMIT :pageSize
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("workspaceId", workspaceId)
            .bind("pageSize", effectivePageSize + 1) // Fetch one extra to check if there's a next page

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
            java.util.Base64.getUrlEncoder().encodeToString(cursorValue.toByteArray())
        } else {
            null
        }

        return PagedMediaAssets(assets = resultAssets, nextCursor = nextCursor)
    }

    override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean {
        val threshold = now.minusSeconds(30 * 60) // 30 minutes ago

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
                status = 'READY'
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

    override suspend fun markAsFailed(assetId: String, workspaceId: String): MediaAsset? {
        databaseClient.sql(
            """
            UPDATE media_assets
            SET status = 'FAILED',
                upload_started_at = NULL
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()

        return findByWorkspaceAndId(workspaceId, assetId)
    }

    override suspend fun delete(assetId: String, workspaceId: String): MediaAsset? {
        val existing = findByWorkspaceAndId(workspaceId, assetId) ?: return null

        databaseClient.sql(
            """
            DELETE FROM media_assets
            WHERE asset_id = :assetId AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .then()
            .awaitSingleOrNull()

        return existing
    }

    override suspend fun findStaleProcessingAssets(
        thresholdHours: Long,
        gracePeriodMinutes: Long,
    ): List<MediaAsset> {
        val threshold = OffsetDateTime.now(ZoneOffset.UTC).minusHours(thresholdHours)
        val graceThreshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(gracePeriodMinutes)

        return databaseClient.sql(
            """
            SELECT asset_id, workspace_id, source_type, media_type, storage_key,
                   original_filename, file_size_bytes, status, upload_started_at, created_at
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
            SELECT asset_id, workspace_id, source_type, media_type, storage_key,
                   original_filename, file_size_bytes, status, upload_started_at, created_at
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

    private fun rowToMediaAsset(row: Readable): MediaAsset {
        return MediaAsset(
            assetId = requireNotNull(row.get("asset_id", String::class.java)),
            workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
            sourceType = MediaSourceType.valueOf(requireNotNull(row.get("source_type", String::class.java))),
            mediaType = requireNotNull(row.get("media_type", String::class.java)),
            storageKey = requireNotNull(row.get("storage_key", String::class.java)),
            originalFilename = row.get("original_filename", String::class.java),
            fileSizeBytes = row.get("file_size_bytes", Long::class.javaObjectType),
            status = MediaAssetStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
            uploadStartedAt = row.get("upload_started_at", OffsetDateTime::class.java)?.toInstant(),
            createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        )
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

@Repository
class R2dbcMediaRateLimitRepository(
    private val databaseClient: DatabaseClient,
) : MediaRateLimitRepository {

    override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean {
        // First, try to insert the row if it doesn't exist
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

        // Then try to increment atomically
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

    override suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int): Boolean {
        val currentHour = OffsetDateTime.now(ZoneOffset.UTC)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // First, try to insert the row if it doesn't exist
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

        // Then try to increment atomically
        val result = databaseClient.sql(
            """
            UPDATE media_rate_limits
            SET hourly_creation_count = hourly_creation_count + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId
              AND hour_bucket = :currentHour
              AND hourly_creation_count < :maxPerHour
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("currentHour", currentHour)
            .bind("maxPerHour", maxPerHour)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return result > 0
    }

}
