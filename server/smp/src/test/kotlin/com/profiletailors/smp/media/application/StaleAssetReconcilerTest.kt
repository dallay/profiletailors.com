package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StaleAssetReconcilerTest {

    // Fixed clock: 2026-06-19T12:00:00Z
    // Stale threshold: 2 hours  → assets created before 10:00:00Z are stale
    // Grace period: 30 minutes → assets with uploadStartedAt after 11:30:00Z get grace period
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC)

    private companion object {
        const val STALE_THRESHOLD_HOURS = 2L
        const val GRACE_PERIOD_MINUTES = 30L
    }

    // --- Threshold boundary tests ---

    @Test
    fun `reconciler transitions PROCESSING asset just under 2-hour threshold`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        // Created at 09:59:00Z — just under the 2-hour stale threshold (cutoff is 10:00:00Z)
        repository.createSync(
            MediaAsset(
                assetId = "asset-just-stale",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-just-stale",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:59:00Z"),
                uploadStartedAt = null,
            ),
        )

        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository())

        val result = reconciler.run()

        assertEquals(1, result.recordsTransitioned)
        assertEquals(1, result.recordsScanned)
        // Asset should be transitioned to FAILED
        val asset = repository.findByWorkspaceAndId("ws-1", "asset-just-stale")
        assertEquals(MediaAssetStatus.FAILED, asset?.status)
        assertNull(asset?.uploadStartedAt) // Reset to null for retryability
    }

    @Test
    fun `reconciler transitions PROCESSING asset just under 2-hour threshold (second)`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        // Created at 09:59:00Z — under the 2-hour threshold
        repository.createSync(
            MediaAsset(
                assetId = "asset-stale",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-stale",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:59:00Z"),
                uploadStartedAt = null,
            ),
        )

        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository())

        val result = reconciler.run()

        assertEquals(1, result.recordsTransitioned)
        assertEquals(1, result.recordsScanned)
        // Asset should be transitioned to FAILED
        val asset = repository.findByWorkspaceAndId("ws-1", "asset-stale")
        assertEquals(MediaAssetStatus.FAILED, asset?.status)
        assertNull(asset?.uploadStartedAt) // Reset to null for retryability
    }

    // --- Active-upload grace period tests ---

    @Test
    fun `reconciler does not transition PROCESSING asset with recent upload activity within grace period`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        // Created > 2 hours ago but uploadStartedAt is within the last 30 minutes (grace period)
        // Created at 09:00:00Z (3h ago), uploadStartedAt = 11:45:00Z (15m ago) → within grace period
        repository.createSync(
            MediaAsset(
                assetId = "asset-in-grace-period",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "video/mp4",
                storageKey = "assets/ws-1/asset-in-grace-period",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = Instant.parse("2026-06-19T11:45:00Z"),
            ),
        )

        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository())

        val result = reconciler.run()

        assertEquals(0, result.recordsTransitioned)
        val asset = repository.findByWorkspaceAndId("ws-1", "asset-in-grace-period")
        assertEquals(MediaAssetStatus.PROCESSING, asset?.status)
    }

    @Test
    fun `reconciler transitions PROCESSING asset with upload activity outside grace period`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        // Created > 2 hours ago, and uploadStartedAt was set > 30 minutes ago (outside grace period)
        // Created at 08:00:00Z (4h ago), uploadStartedAt = 11:20:00Z (40m ago) → outside grace
        repository.createSync(
            MediaAsset(
                assetId = "asset-outside-grace",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "video/mp4",
                storageKey = "assets/ws-1/asset-outside-grace",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T08:00:00Z"),
                uploadStartedAt = Instant.parse("2026-06-19T11:20:00Z"),
            ),
        )

        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository())

        val result = reconciler.run()

        assertEquals(1, result.recordsTransitioned)
        val asset = repository.findByWorkspaceAndId("ws-1", "asset-outside-grace")
        assertEquals(MediaAssetStatus.FAILED, asset?.status)
    }

    // --- Storage delete failure during cleanup ---

    @Test
    fun `reconciler transitions stale asset to FAILED even when storage delete fails`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-storage-fail",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-storage-fail",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val failingStorageBackend = InMemoryFakeStorage(failDeleteAll = true)
        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository(), testStorageApplicationService(failingStorageBackend))

        val result = reconciler.run()

        // Asset must still be transitioned to FAILED even though storage delete failed.
        // The reconciler also retries cleanup in its FAILED-assets phase, so the same failing
        // storage key contributes a second error in the same run.
        assertEquals(1, result.recordsTransitioned)
        assertEquals(2, result.errors)
        val asset = repository.findByWorkspaceAndId("ws-1", "asset-storage-fail")
        assertEquals(MediaAssetStatus.FAILED, asset?.status)
        assertNull(asset?.uploadStartedAt)
    }

    // --- Idempotency tests ---

    @Test
    fun `reconciler run twice does not double-transition assets`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-idempotent",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-idempotent",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository())

        // First run: transitions to FAILED
        val result1 = reconciler.run()
        assertEquals(1, result1.recordsTransitioned)
        assertEquals(MediaAssetStatus.FAILED, repository.findByWorkspaceAndId("ws-1", "asset-idempotent")?.status)

        // Second run: should not transition again (asset is now FAILED)
        val result2 = reconciler.run()
        assertEquals(0, result2.recordsTransitioned)
        assertEquals(MediaAssetStatus.FAILED, repository.findByWorkspaceAndId("ws-1", "asset-idempotent")?.status)
    }

    // --- Storage cleanup on stale transitions ---

    @Test
    fun `reconciler attempts storage delete for each transitioned stale asset`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-cleanup-1",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-cleanup-1",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = null,
            ),
        )
        repository.createSync(
            MediaAsset(
                assetId = "asset-cleanup-2",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-cleanup-2",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T08:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val trackingStorageBackend = InMemoryFakeStorage()
        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository(), testStorageApplicationService(trackingStorageBackend))

        reconciler.run()

        // Both stale assets should have had storage delete attempted (Phase 1: PROCESSING→FAILED).
        // Phase 2 also retries storage delete for FAILED assets (the newly transitioned ones), so we see 4 deletes total.
        assertEquals(4, trackingStorageBackend.deletedKeys.size)
        assertTrue(trackingStorageBackend.deletedKeys.contains("assets/ws-1/asset-cleanup-1"))
        assertTrue(trackingStorageBackend.deletedKeys.contains("assets/ws-1/asset-cleanup-2"))
    }

    @Test
    fun `reconciler continues processing other assets when storage delete fails for one`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-succeeds",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-succeeds",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = null,
            ),
        )
        repository.createSync(
            MediaAsset(
                assetId = "asset-fails",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-fails",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T08:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val selectiveStorageBackend = InMemoryFakeStorage(failDeleteKeys = setOf("assets/ws-1/asset-fails"))
        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository(), testStorageApplicationService(selectiveStorageBackend))

        val result = reconciler.run()

        // Both should be transitioned despite one storage delete failing.
        // The failing key is attempted again during the FAILED-assets retry phase, so errors = 2.
        assertEquals(2, result.recordsTransitioned)
        assertEquals(MediaAssetStatus.FAILED, repository.findByWorkspaceAndId("ws-1", "asset-succeeds")?.status)
        assertEquals(MediaAssetStatus.FAILED, repository.findByWorkspaceAndId("ws-1", "asset-fails")?.status)
        assertEquals(2, result.errors)
    }

    // --- FAILED asset orphaned storage cleanup retry ---

    @Test
    fun `reconciler retries storage deletion for FAILED assets with unresolved storage objects`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        // A FAILED asset created recently (within 7 days) with a non-null storageKey
        repository.createSync(
            MediaAsset(
                assetId = "failed-asset-retry",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/failed-asset-retry",
                status = MediaAssetStatus.FAILED,
                createdAt = Instant.parse("2026-06-19T08:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val trackingStorageBackend = InMemoryFakeStorage()
        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository(), testStorageApplicationService(trackingStorageBackend))

        reconciler.run()

        // The FAILED asset should have had a storage delete retry attempted
        assertTrue(trackingStorageBackend.deletedKeys.contains("assets/ws-1/failed-asset-retry"))
    }

    @Test
    fun `reconciler recordsScanned recordsTransitioned durationMs errors in result`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-scanned-1",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-scanned-1",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = null,
            ),
        )
        repository.createSync(
            MediaAsset(
                assetId = "asset-scanned-2",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-scanned-2",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T08:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val failingStorageBackend = InMemoryFakeStorage(failDeleteAll = true)
        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository(), testStorageApplicationService(failingStorageBackend))

        val result = reconciler.run()

        // Verify canonical field names from the design's observability contract.
        // Each stale PROCESSING asset fails cleanup once during transition and again during the
        // FAILED-assets retry phase, so errors = 4 for this fixture.
        assertEquals(2, result.recordsScanned)
        assertEquals(2, result.recordsTransitioned)
        assertTrue(result.durationMs >= 0)
        assertEquals(4, result.errors)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `reconciler emits alert after 3 consecutive error runs`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "always-fails",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/always-fails",
                status = MediaAssetStatus.PROCESSING,
                createdAt = Instant.parse("2026-06-19T09:00:00Z"),
                uploadStartedAt = null,
            ),
        )

        val failingStorageBackend = InMemoryFakeStorage(failDeleteAll = true)
        val reconciler = buildReconciler(repository, InMemoryMediaRateLimitRepository(), testStorageApplicationService(failingStorageBackend))

        assertFalse(reconciler.shouldAlert())

        reconciler.run()
        assertFalse(reconciler.shouldAlert())

        reconciler.run()
        assertFalse(reconciler.shouldAlert())

        reconciler.run()
        assertTrue(reconciler.shouldAlert())
    }

    // --- Helper factories ---

    private fun buildReconciler(
        repository: InMemoryMediaAssetRepository,
        rateLimitRepo: InMemoryMediaRateLimitRepository,
        storage: com.profiletailors.storage.application.StorageApplicationService = testStorageApplicationService(InMemoryFakeStorage()),
    ): StaleAssetReconciler = StaleAssetReconciler(
        mediaAssetRepository = repository,
        mediaRateLimitRepository = rateLimitRepo,
        storageApplicationService = storage,
        staleThresholdHours = STALE_THRESHOLD_HOURS,
        gracePeriodMinutes = GRACE_PERIOD_MINUTES,
    )

    // --- In-memory test doubles ---

    private class InMemoryMediaAssetRepository : MediaAssetRepository {
        private val items = mutableMapOf<String, MediaAsset>()

        fun createSync(asset: MediaAsset): MediaAsset {
            items[asset.assetId] = asset
            return asset
        }

        override suspend fun create(asset: MediaAsset): MediaAsset {
            items[asset.assetId] = asset
            return asset
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset? =
            items[assetId]?.takeIf { it.workspaceId == workspaceId }

        override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>): List<MediaAsset> =
            items.values.filter { it.workspaceId == workspaceId && it.assetId in assetIds }

        override suspend fun listByWorkspace(
            workspaceId: String,
            statuses: Set<MediaAssetStatus>,
            pageSize: Int,
            cursor: String?,
        ): PagedMediaAssets = PagedMediaAssets(emptyList(), null)

        override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean = true

        override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? {
            val asset = items[assetId] ?: return null
            val updated = asset.copy(status = MediaAssetStatus.READY, fileSizeBytes = fileSizeBytes)
            items[assetId] = updated
            return updated
        }

        override suspend fun markAsFailed(assetId: String, workspaceId: String): MediaAsset? {
            val asset = items[assetId] ?: return null
            val updated = asset.copy(status = MediaAssetStatus.FAILED, uploadStartedAt = null)
            items[assetId] = updated
            return updated
        }

        override suspend fun findStaleProcessingAssets(
            thresholdHours: Long,
            gracePeriodMinutes: Long,
        ): List<MediaAsset> {
            val referenceNow = Instant.parse("2026-06-19T12:00:00Z")
            val cutoff = referenceNow.minusSeconds(thresholdHours * 3600)
            val graceCutoff = referenceNow.minusSeconds(gracePeriodMinutes * 60)
            return items.values.filter { asset ->
                asset.status == MediaAssetStatus.PROCESSING &&
                    asset.createdAt.isBefore(cutoff) &&
                    (asset.uploadStartedAt == null || asset.uploadStartedAt.isBefore(graceCutoff))
            }
        }

        override suspend fun findRecentlyFailedAssets(): List<MediaAsset> {
            val referenceNow = Instant.parse("2026-06-19T12:00:00Z")
            val sevenDaysAgo = referenceNow.minusSeconds(7 * 24 * 3600)
            return items.values.filter { asset ->
                asset.status == MediaAssetStatus.FAILED &&
                    asset.storageKey.isNotBlank() &&
                    asset.createdAt.isAfter(sevenDaysAgo)
            }
        }
    }

    private class InMemoryMediaRateLimitRepository : MediaRateLimitRepository {
        override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean = true
        override suspend fun releaseConcurrentUploadSlot(workspaceId: String) = Unit
        override suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int): Boolean = true
    }

    // In-memory test doubles are provided by FakeStorageApplicationService.kt
}
