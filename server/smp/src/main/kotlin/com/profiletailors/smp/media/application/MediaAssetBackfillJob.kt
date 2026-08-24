package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaStorageKeys
import kotlinx.coroutines.flow.collect
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

/**
 * Backfills CAS metadata for READY assets created before file_hash existed.
 *
 * For each READY asset with null file_hash:
 * 1) Download bytes from current storage_key and compute SHA-256 + size.
 * 2) Copy to canonical CAS key if needed.
 * 3) Upsert/mark blob READY.
 * 4) Update the asset with file_hash and canonical storage_key.
 */
class MediaAssetBackfillJob(
    private val mediaAssetRepository: MediaAssetRepository,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val storage: MediaStorage,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,
) {
    private val logger = LoggerFactory.getLogger(MediaAssetBackfillJob::class.java)

    suspend fun run(limit: Int = BATCH_SIZE): BackfillRunResult {
        val startedAt = Instant.now()
        var scanned = 0
        var backfilled = 0
        var failed = 0

        val candidates = mediaAssetRepository.findReadyAssetsWithoutHash(limit)

        for (asset in candidates) {
            scanned++
            when (processAsset(asset)) {
                AssetOutcome.BACKFILLED -> backfilled++
                AssetOutcome.FAILED -> failed++
            }
        }

        logger.info(
            "media.backfill.run scanned={} backfilled={} failed={}",
            scanned,
            backfilled,
            failed,
        )

        return BackfillRunResult(
            scanned = scanned,
            backfilled = backfilled,
            failed = failed,
            startedAt = startedAt,
            completedAt = Instant.now(),
        )
    }

    private suspend fun processAsset(asset: MediaAsset): AssetOutcome {
        return try {
            val currentStorageKey = asset.storageKey
            if (currentStorageKey.isNullOrBlank()) {
                logger.warn(
                    "media.backfill.skip.noStorageKey assetId={} workspaceId={}",
                    asset.assetId,
                    asset.workspaceId,
                )
                return AssetOutcome.FAILED
            }

            val detectedMediaType = asset.detectedMediaType ?: asset.mediaType
            val hashAndSize = computeHashAndSize(asset.workspaceId, currentStorageKey)
            val fileHash = hashAndSize.hash
            val fileSizeBytes = hashAndSize.sizeBytes
            val canonicalKey = MediaStorageKeys.canonicalKey(asset.workspaceId, fileHash, detectedMediaType)

            if (canonicalKey != currentStorageKey) {
                storage.copyObject(
                    bucket = uploadSettings.storageBucket,
                    sourceKey = currentStorageKey,
                    destKey = canonicalKey,
                )
            }

            transactionRunner.runAtomically<Unit> {
                workspaceFileBlobRepository.upsertBlob(asset.workspaceId, fileHash)
                workspaceFileBlobRepository.markBlobReady(
                    workspaceId = asset.workspaceId,
                    fileHash = fileHash,
                    storageKey = canonicalKey,
                    detectedMediaType = detectedMediaType,
                    fileSizeBytes = fileSizeBytes,
                )
                mediaAssetRepository.updateReadyAssetCasMetadata(
                    assetId = asset.assetId,
                    workspaceId = asset.workspaceId,
                    fileHash = fileHash,
                    storageKey = canonicalKey,
                    detectedMediaType = detectedMediaType,
                    fileSizeBytes = fileSizeBytes,
                )
            }

            AssetOutcome.BACKFILLED
        } catch (e: Exception) {
            logger.error(
                "media.backfill.failed assetId={} workspaceId={}",
                asset.assetId,
                asset.workspaceId,
                e,
            )
            AssetOutcome.FAILED
        }
    }

    private suspend fun computeHashAndSize(workspaceId: String, storageKey: String): HashAndSize {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L

        storage.download(
            bucket = uploadSettings.storageBucket,
            key = storageKey,
            downloaderId = "media-backfill:$workspaceId",
        ).collect { chunk ->
            size += chunk.size.toLong()
            digest.update(chunk)
        }

        return HashAndSize(hash = digest.digest().toHexString(), sizeBytes = size)
    }

    private enum class AssetOutcome { BACKFILLED, FAILED }

    private data class HashAndSize(val hash: String, val sizeBytes: Long)

    companion object {
        private const val BATCH_SIZE = 50
    }
}

data class BackfillRunResult(
    val scanned: Int,
    val backfilled: Int,
    val failed: Int,
    val startedAt: Instant,
    val completedAt: Instant,
)
