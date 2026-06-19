package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Stale asset reconciler that transitions abandoned PROCESSING assets to FAILED.
 *
 * An asset is considered stale when:
 * - status == PROCESSING
 * - created_at < (now - 2 hours)
 * - (uploadStartedAt IS NULL OR uploadStartedAt < (now - 30 minutes))
 *
 * This grace period allows large uploads that are actively streaming.
 *
 * The reconciler also retries storage deletion for FAILED assets with unresolved
 * cleanup failures (logged during upload failure paths).
 *
 * Metrics emitted per run:
 * - recordsScanned: total PROCESSING assets scanned
 * - recordsTransitioned: assets transitioned to FAILED
 * - durationMs: total run duration
 * - errors: count of errors during the run
 *
 * Alert: fire when errors > 0 on 3 consecutive runs.
 */
@Service
class StaleAssetReconciler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val storageApplicationService: StorageApplicationService,
    private val reconcilerSettings: MediaReconcilerSettings,
) {
    private val logger = LoggerFactory.getLogger(StaleAssetReconciler::class.java)

    companion object {
        private const val SCHEDULED_FIXED_RATE_MILLIS = 15 * 60 * 1000L
        private const val SCHEDULED_INITIAL_DELAY_MILLIS = 60 * 1000L
        private const val STORAGE_DELETE_TIMEOUT_MILLIS = 30_000L
        private const val ALERT_THRESHOLD_RUNS = 3
    }

    // Track consecutive error runs for alerting
    private var consecutiveErrorRuns = 0

    /**
     * Runs the stale asset reconciliation.
     * Scheduling is owned by infrastructure; application exposes only the use case.
     */

    /**
     * Run the reconciliation. Public for testing and manual trigger.
     * @return ReconcilerRunResult with metrics for the run.
     */
    suspend fun run(): ReconcilerRunResult {
        val startTime = System.currentTimeMillis()
        var recordsScanned = 0
        var recordsTransitioned = 0
        var errors = 0

        try {
            val staleAssets = mediaAssetRepository.findStaleProcessingAssets(
                thresholdHours = reconcilerSettings.staleThresholdHours,
                gracePeriodMinutes = reconcilerSettings.gracePeriodMinutes,
            )
            recordsScanned = staleAssets.size
            val staleResult = reconcileStaleProcessingAssets(staleAssets)
            recordsTransitioned += staleResult.transitions
            errors += staleResult.errors

            val recentlyFailedAssets = mediaAssetRepository.findRecentlyFailedAssets()
            errors += retryFailedAssetCleanup(recentlyFailedAssets)
        } catch (e: IllegalStateException) {
            errors++
            logger.error("Stale asset reconciler run failed", e)
        }

        return finalizeRun(startTime, recordsScanned, recordsTransitioned, errors)
    }

    private suspend fun reconcileStaleProcessingAssets(assets: List<MediaAsset>): ReconcileBatchResult {
        var transitions = 0
        var errors = 0

        for (asset in assets) {
            try {
                val storageDeleteSucceeded = attemptStorageDelete(asset)
                if (!storageDeleteSucceeded) {
                    errors++
                }
                mediaAssetRepository.markAsFailed(asset.assetId, asset.workspaceId)
                transitions++
                releaseUploadSlot(asset)
                logger.info(
                    "Stale PROCESSING asset transitioned to FAILED: assetId={} workspaceId={} " +
                        "storageKey={} storageDeleted={}",
                    asset.assetId,
                    asset.workspaceId,
                    asset.storageKey,
                    storageDeleteSucceeded,
                )
            } catch (e: IllegalStateException) {
                errors++
                logger.error(
                    "Failed to transition stale asset: assetId={} error={}",
                    asset.assetId,
                    e.message,
                    e,
                )
            }
        }

        return ReconcileBatchResult(transitions = transitions, errors = errors)
    }

    private suspend fun retryFailedAssetCleanup(assets: List<MediaAsset>): Int {
        var errors = 0

        for (asset in assets) {
            try {
                val storageDeleteSucceeded = attemptStorageDelete(asset)
                if (!storageDeleteSucceeded) {
                    errors++
                }
            } catch (e: IllegalStateException) {
                errors++
                logger.error(
                    "Failed to retry cleanup for FAILED asset: assetId={} error={}",
                    asset.assetId,
                    e.message,
                    e,
                )
            }
        }

        return errors
    }

    private suspend fun attemptStorageDelete(asset: MediaAsset): Boolean {
        return try {
            withTimeout(STORAGE_DELETE_TIMEOUT_MILLIS) {
                storageApplicationService.delete(
                    bucket = reconcilerSettings.storageBucket,
                    key = asset.storageKey,
                    deleterId = "stale-reconciler",
                )
            }
            logger.info(
                "media.asset.cleanup.attempted assetId={} storageKey={} success=true",
                asset.assetId,
                asset.storageKey,
            )
            true
        } catch (e: StorageException) {
            logStorageDeleteFailure(asset, e)
            false
        } catch (e: TimeoutCancellationException) {
            logStorageDeleteFailure(asset, e)
            false
        }
    }

    private suspend fun releaseUploadSlot(asset: MediaAsset) {
        try {
            mediaRateLimitRepository.releaseConcurrentUploadSlot(asset.workspaceId)
        } catch (e: IllegalStateException) {
            logger.debug(
                "No upload slot to release for stale asset: assetId={}",
                asset.assetId,
                e,
            )
        }
    }

    private fun logStorageDeleteFailure(asset: MediaAsset, error: Throwable) {
        logger.warn(
            "media.asset.cleanup.attempted assetId={} storageKey={} success=false error={}",
            asset.assetId,
            asset.storageKey,
            error.message,
            error,
        )
    }

    private fun finalizeRun(
        startTime: Long,
        recordsScanned: Int,
        recordsTransitioned: Int,
        errors: Int,
    ): ReconcilerRunResult {
        val durationMs = System.currentTimeMillis() - startTime
        consecutiveErrorRuns = if (errors > 0) {
            consecutiveErrorRuns + 1
        } else {
            0
        }

        logger.info(
            "media.reconciler.run recordsScanned={} recordsTransitioned={} durationMs={} errors={} " +
                "consecutiveErrorRuns={}",
            recordsScanned,
            recordsTransitioned,
            durationMs,
            errors,
            consecutiveErrorRuns,
        )

        if (consecutiveErrorRuns >= ALERT_THRESHOLD_RUNS) {
            logger.error(
                "ALERT: Stale reconciler has errors > 0 on 3 consecutive runs. consecutiveErrorRuns={}",
                consecutiveErrorRuns,
            )
        }

        return ReconcilerRunResult(
            recordsScanned = recordsScanned,
            recordsTransitioned = recordsTransitioned,
            durationMs = durationMs,
            errors = errors,
            consecutiveErrorRuns = consecutiveErrorRuns,
            timestamp = Instant.now(),
        )
    }

    /**
     * Check if the reconciler should fire an alert.
     * Called externally by monitoring/health systems.
     */
    fun shouldAlert(): Boolean = consecutiveErrorRuns >= ALERT_THRESHOLD_RUNS

    /**
     * Reset consecutive error counter. For testing.
     */
    fun resetAlertState() {
        consecutiveErrorRuns = 0
    }
}

/**
 * Result of a reconciler run.
 */
private data class ReconcileBatchResult(
    val transitions: Int,
    val errors: Int,
)

data class ReconcilerRunResult(
    val recordsScanned: Int,
    val recordsTransitioned: Int,
    val durationMs: Long,
    val errors: Int,
    val consecutiveErrorRuns: Int,
    val timestamp: Instant,
)
