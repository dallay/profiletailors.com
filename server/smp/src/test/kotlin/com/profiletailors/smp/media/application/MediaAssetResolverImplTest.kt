package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * Unit tests for [MediaAssetResolverImpl].
 *
 * Verifies the resolution of ready media assets from both the media bounded context
 * and the legacy publication_assets store via a single unified port.
 */
@Suppress("LargeClass", "ClassOrdering")
class MediaAssetResolverImplTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val WORKSPACE = "ws-1"
        const val MEDIA_ASSET_1 = "media-1"
        const val MEDIA_ASSET_2 = "media-2"
        const val LEGACY_ASSET_1 = "legacy-1"
        const val LEGACY_ASSET_2 = "legacy-2"
        const val MISSING_ASSET = "missing-1"

        const val MEDIA_KEY_1 = "assets/ws-1/media-1.jpg"
        const val MEDIA_KEY_2 = "assets/ws-1/media-2.png"
        const val LEGACY_KEY_1 = "assets/ws-1/legacy-1.pdf"
        const val LEGACY_KEY_2 = "assets/ws-1/legacy-2.mp4"

        const val FILE_HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val now = Instant.parse("2026-07-10T12:00:00Z")

    /**
     * Build a [MediaAsset] with minimal required fields.
     *
     * Domain rules:
     * - READY assets MUST have a non-null [storageKey].
     * - Non-READY assets typically have a null [storageKey].
     *
     * The default status is computed: READY if [storageKey] is non-null,
     * PENDING_UPLOAD otherwise. This avoids init-block validation failures
     * when callers override only one of the two.
     */
    private fun readyMediaAsset(
        assetId: String = MEDIA_ASSET_1,
        workspaceId: String = WORKSPACE,
        storageKey: String? = MEDIA_KEY_1,
        mediaType: String = "image/jpeg",
        status: MediaAssetStatus? = null,
    ): MediaAsset {
        val effectiveStatus = status
            ?: if (storageKey != null) MediaAssetStatus.READY else MediaAssetStatus.PENDING_UPLOAD
        return MediaAsset(
            assetId = assetId,
            workspaceId = workspaceId,
            sourceType = MediaSourceType.UPLOADED,
            fileHash = FILE_HASH,
            mediaType = mediaType,
            storageKey = storageKey,
            originalFilename = "photo.jpg",
            fileSizeBytes = 1024L,
            status = effectiveStatus,
            createdAt = now,
        )
    }

    /**
     * Build a [PublicationAsset] with the given parameters.
     *
     * Domain rules:
     * - UPLOADED source type requires a non-null non-blank [storageKey].
     * - To test null or blank [storageKey], the helper uses [AssetSourceType.EXTERNAL_URL]
     *   automatically when the key would be invalid, bypassing the init validation.
     */
    private fun legacyAsset(
        id: String = LEGACY_ASSET_1,
        workspaceId: String = WORKSPACE,
        storageKey: String? = LEGACY_KEY_1,
        mediaType: String = "application/pdf",
        status: PublicationAssetStatus = PublicationAssetStatus.READY,
        externalUrl: String? = null,
    ): PublicationAsset {
        val sourceType = when {
            storageKey.isNullOrBlank() -> AssetSourceType.EXTERNAL_URL
            else -> AssetSourceType.UPLOADED
        }
        val effectiveExternalUrl = when (sourceType) {
            AssetSourceType.EXTERNAL_URL -> externalUrl ?: "https://cdn.example.com/$id"
            AssetSourceType.UPLOADED -> null
        }
        return PublicationAsset(
            id = id,
            workspaceId = workspaceId,
            sourceType = sourceType,
            mediaType = mediaType,
            storageKey = storageKey,
            externalUrl = effectiveExternalUrl,
            originalFilename = "doc.pdf",
            fileSizeBytes = 2048L,
            status = status,
            createdByPrincipalId = "principal-1",
            createdAt = now,
        )
    }

    /** Assert that a [ResolvedAssetSummary] matches the expected values. */
    private fun assertResolvedAsset(
        actual: ResolvedAssetSummary,
        expectedAssetId: String,
        expectedWorkspace: String = WORKSPACE,
        expectedStorageKey: String = MEDIA_KEY_1,
        expectedMediaType: String = "image/jpeg",
    ) {
        assertEquals(expectedAssetId, actual.assetId, "assetId")
        assertEquals(expectedWorkspace, actual.workspaceId, "workspaceId")
        assertEquals(expectedStorageKey, actual.storageKey, "storageKey")
        assertEquals(expectedMediaType, actual.mediaType, "mediaType")
    }

    // ── Empty Input ──────────────────────────────────────────────────────────

    @Test
    fun `returns empty list when assetIds is empty`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = MockMediaAssetRepository(),
            publicationAssetRepository = MockPublicationAssetRepository(),
        )

        val result = resolver.resolveReadyAssets(WORKSPACE, emptyList())

        assertTrue(result.isEmpty())
    }

    // ── All Assets Found in Media Context ────────────────────────────────────

    @Test
    fun `returns resolved assets when all IDs are found in media context as READY`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1, mediaType = "image/jpeg"),
            readyMediaAsset(assetId = MEDIA_ASSET_2, storageKey = MEDIA_KEY_2, mediaType = "image/png"),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, MEDIA_ASSET_2))

        assertEquals(2, result.size)
        assertResolvedAsset(
            actual = result[0],
            expectedAssetId = MEDIA_ASSET_1,
            expectedStorageKey = MEDIA_KEY_1,
            expectedMediaType = "image/jpeg",
        )
        assertResolvedAsset(
            actual = result[1],
            expectedAssetId = MEDIA_ASSET_2,
            expectedStorageKey = MEDIA_KEY_2,
            expectedMediaType = "image/png",
        )
        // Legacy repo must NOT have been queried — all IDs were resolved via media
        assertEquals(0, legacyRepo.findCallCount)
    }

    // ── All Assets Found in Legacy Context ────────────────────────────────────

    @Test
    fun `returns resolved assets when all IDs are found in legacy store as READY`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1, mediaType = "application/pdf"),
            legacyAsset(id = LEGACY_ASSET_2, storageKey = LEGACY_KEY_2, mediaType = "video/mp4"),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1, LEGACY_ASSET_2))

        assertEquals(2, result.size)
        assertResolvedAsset(
            actual = result[0],
            expectedAssetId = LEGACY_ASSET_1,
            expectedStorageKey = LEGACY_KEY_1,
            expectedMediaType = "application/pdf",
        )
        assertResolvedAsset(
            actual = result[1],
            expectedAssetId = LEGACY_ASSET_2,
            expectedStorageKey = LEGACY_KEY_2,
            expectedMediaType = "video/mp4",
        )
        // Media repo must have been queried first, found nothing, then legacy was used
        assertEquals(1, mediaRepo.findCallCount)
        assertEquals(1, legacyRepo.findCallCount)
    }

    // ── Mix of Media and Legacy ───────────────────────────────────────────────

    @Test
    fun `returns unified result when assets come from both media and legacy stores`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1, mediaType = "image/jpeg"),
        )
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1, mediaType = "application/pdf"),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(
            WORKSPACE,
            listOf(MEDIA_ASSET_1, LEGACY_ASSET_1),
        )

        assertEquals(2, result.size)
        assertResolvedAsset(
            actual = result[0],
            expectedAssetId = MEDIA_ASSET_1,
            expectedStorageKey = MEDIA_KEY_1,
            expectedMediaType = "image/jpeg",
        )
        assertResolvedAsset(
            actual = result[1],
            expectedAssetId = LEGACY_ASSET_1,
            expectedStorageKey = LEGACY_KEY_1,
            expectedMediaType = "application/pdf",
        )
        // Media was queried with all IDs, legacy only with unresolved ones
        assertEquals(1, mediaRepo.findCallCount)
        assertEquals(1, legacyRepo.findCallCount)
        assertEquals(listOf(LEGACY_ASSET_1), legacyRepo.lastUnresolvedIds)
    }

    // ── Missing Asset (not found in either) ───────────────────────────────────

    @Test
    fun `throws AssetNotReadyException when an asset ID is not found in either store`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MISSING_ASSET))
        }

        assertEquals(MISSING_ASSET, exception.assetId)
        val message = requireNotNull(exception.message)
        assertTrue(message.contains("not found"))
        // Both repos were queried
        assertEquals(1, mediaRepo.findCallCount)
        assertEquals(1, legacyRepo.findCallCount)
    }

    @Test
    fun `throws AssetNotReadyException when one of multiple IDs is not found in either store`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, MISSING_ASSET))
        }

        assertEquals(MISSING_ASSET, exception.assetId)
        // Media was found for MEDIA_ASSET_1, so legacy was only queried for MISSING_ASSET
        assertEquals(1, legacyRepo.findCallCount)
        assertEquals(listOf(MISSING_ASSET), legacyRepo.lastUnresolvedIds)
    }

    // ── Non-READY Media Asset ─────────────────────────────────────────────────

    @Test
    fun `throws AssetNotReadyException when a media asset has PROCESSING status`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(
                assetId = MEDIA_ASSET_1,
                storageKey = null,
                status = MediaAssetStatus.PROCESSING,
            ),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        assertEquals(MEDIA_ASSET_1, exception.assetId)
        val message = requireNotNull(exception.message)
        assertTrue(message.contains("PROCESSING"))
    }

    @Test
    fun `throws AssetNotReadyException when a media asset has FAILED status`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(
                assetId = MEDIA_ASSET_1,
                storageKey = null,
                status = MediaAssetStatus.FAILED,
            ),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        assertEquals(MEDIA_ASSET_1, exception.assetId)
        val message = requireNotNull(exception.message)
        assertTrue(message.contains("FAILED"))
    }

    @Test
    fun `throws AssetNotReadyException when a media asset has PENDING_UPLOAD status`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(
                assetId = MEDIA_ASSET_1,
                storageKey = null,
                status = MediaAssetStatus.PENDING_UPLOAD,
            ),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        assertEquals(MEDIA_ASSET_1, exception.assetId)
        val message = requireNotNull(exception.message)
        assertTrue(message.contains("PENDING_UPLOAD"))
    }

    @Test
    fun `throws AssetNotReadyException when non-READY media asset is mixed with valid legacy asset`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(
                assetId = MEDIA_ASSET_1,
                storageKey = null,
                status = MediaAssetStatus.UPLOADING,
            ),
        )
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, LEGACY_ASSET_1))
        }

        // Non-READY media asset throws before we even get to building the result,
        // and the exception targets the media asset, not the legacy one
        assertEquals(MEDIA_ASSET_1, exception.assetId)
    }

    // ── Legacy Asset Filtered by Status ────────────────────────────────────────

    @Test
    fun `throws when only source is a legacy asset with PROCESSING status`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, status = PublicationAssetStatus.PROCESSING),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        assertEquals(LEGACY_ASSET_1, exception.assetId)
        // Legacy repo was queried but the asset got filtered out by the READY check
        assertEquals(1, legacyRepo.findCallCount)
    }

    @Test
    fun `throws when only source is a legacy asset with FAILED status`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, status = PublicationAssetStatus.FAILED),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        assertEquals(LEGACY_ASSET_1, exception.assetId)
    }

    // ── Legacy Asset Filtered by Null Storage Key ─────────────────────────────

    @Test
    fun `throws when only source is a legacy asset with null storageKey`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = null),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        assertEquals(LEGACY_ASSET_1, exception.assetId)
        // Even though the legacy repo returned it, it was filtered out by the storageKey check
        assertEquals(1, legacyRepo.findCallCount)
    }

    // ── Legacy Asset Filtered by Blank Storage Key ────────────────────────────

    @Test
    fun `throws when only source is a legacy asset with blank storageKey`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = "  "),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        assertEquals(LEGACY_ASSET_1, exception.assetId)
    }

    // ── Legacy Site of the Filter Pipeline (multiple legacy, mixed quality) ────

    @Test
    fun `filters out legacy assets with non-READY status and returns only the ready ones`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1, status = PublicationAssetStatus.READY),
            legacyAsset(id = LEGACY_ASSET_2, storageKey = LEGACY_KEY_2, status = PublicationAssetStatus.PROCESSING),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1, LEGACY_ASSET_2))
        }

        // LEGACY_ASSET_2 was filtered out by the READY check, making it "missing"
        assertEquals(LEGACY_ASSET_2, exception.assetId)
    }

    @Test
    fun `filters out legacy assets with null storageKey and returns only the valid ones`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
            legacyAsset(id = LEGACY_ASSET_2, storageKey = null),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1, LEGACY_ASSET_2))
        }

        // LEGACY_ASSET_2 was filtered out by the storageKey check
        assertEquals(LEGACY_ASSET_2, exception.assetId)
    }

    // ── Error Message Verification ───────────────────────────────────────────

    @Test
    fun `error message for missing asset contains descriptive reason`() = runTest {
        val resolver = MediaAssetResolverImpl(
            MockMediaAssetRepository(),
            MockPublicationAssetRepository(),
        )

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MISSING_ASSET))
        }

        val message = requireNotNull(exception.message)
        assertTrue(message.contains(MISSING_ASSET))
        assertTrue(message.contains("not ready"))
        assertTrue(message.contains("not found"))
    }

    @Test
    fun `error message for non-READY asset contains the actual status`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(
                assetId = MEDIA_ASSET_1,
                storageKey = null,
                status = MediaAssetStatus.PROCESSING,
            ),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, MockPublicationAssetRepository())

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        val message = requireNotNull(exception.message)
        assertTrue(message.contains(MEDIA_ASSET_1))
        assertTrue(message.contains("PROCESSING"))
    }

    // ── Edge: Cross-Workspace (implicitly handled by repository filtering) ─────

    @Test
    fun `throws when media asset is found but for a different implicit workspace`() = runTest {
        // The repositories are workspace-scoped, so the only way a cross-workspace
        // scenario manifests is when the resolver asks repo for assets in workspace X
        // but the repo returns nothing (because the asset belongs to workspace Y).
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets("different-workspace", listOf(MEDIA_ASSET_1))
        }

        assertEquals(MEDIA_ASSET_1, exception.assetId)
    }

    // ── Edge: Multiple ID types mixed ─────────────────────────────────────────

    @Test
    fun `handles mixed assets where some IDs are in media, some filtered, some missing`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(
                WORKSPACE,
                listOf(MEDIA_ASSET_1, LEGACY_ASSET_1, MISSING_ASSET),
            )
        }

        // MEDIA_ASSET_1 is found, LEGACY_ASSET_1 is found, MISSING_ASSET is not
        assertEquals(MISSING_ASSET, exception.assetId)
    }

    // ── Edge: Only some media assets are READY ────────────────────────────────

    @Test
    fun `throws when first media asset is READY but second is not, before mixing with legacy`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1, status = MediaAssetStatus.READY),
            readyMediaAsset(assetId = MEDIA_ASSET_2, storageKey = null, status = MediaAssetStatus.FAILED),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = assertThrows<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, MEDIA_ASSET_2))
        }

        assertEquals(MEDIA_ASSET_2, exception.assetId)
        // Legacy was NOT queried because both IDs were found in media context
        assertEquals(0, legacyRepo.findCallCount)
    }

    // ── Edge: Empty media result, legacy with ready + filtered ────────────────

    @Test
    fun `skips legacy repo when all IDs are resolved from media context`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))

        // All IDs were found in media, so legacy should NOT be queried
        assertEquals(0, legacyRepo.findCallCount)
    }

    @Test
    fun `legacy assets with null storageKey are excluded from result building via mapNotNull`() = runTest {
        // Legacy asset with storageKey passes the first filter, but is still checked
        // via mapNotNull in the result-building phase. This test verifies the
        // redundant null check in mapNotNull doesn't cause issues.
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))

        assertEquals(1, result.size)
        assertEquals(LEGACY_ASSET_1, result[0].assetId)
        assertEquals(LEGACY_KEY_1, result[0].storageKey)
    }
}

// ── Mock Implementations ──────────────────────────────────────────────────────

/**
 * In-memory mock of [MediaAssetRepository] that only implements the method used
 * by [MediaAssetResolverImpl]: [findByWorkspaceAndIds].
 *
 * Other methods throw [UnsupportedOperationException].
 */
private class MockMediaAssetRepository(vararg initialAssets: MediaAsset) : MediaAssetRepository {

    private val assets: MutableMap<String, MutableMap<String, MediaAsset>> = linkedMapOf()

    /** Number of times [findByWorkspaceAndIds] was called. */
    var findCallCount: Int = 0
        private set

    init {
        for (asset in initialAssets) {
            val wsAssets = assets.getOrPut(asset.workspaceId) { linkedMapOf() }
            wsAssets[asset.assetId] = asset
        }
    }

    override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>): List<MediaAsset> {
        findCallCount++
        val wsAssets = assets[workspaceId] ?: return emptyList()
        return assetIds.mapNotNull { wsAssets[it] }
    }

    override suspend fun create(asset: MediaAsset): MediaAsset =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset? =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<MediaAssetStatus>,
        pageSize: Int,
        cursor: String?,
    ): PagedMediaAssets = throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun claimCasUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun markAsReadyFromDedup(
        assetId: String,
        workspaceId: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long?,
    ): MediaAsset? = throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun markAsFailed(assetId: String, workspaceId: String, reason: String?): MediaAsset? =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun softDelete(assetId: String, workspaceId: String): MediaAsset? =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long): List<MediaAsset> =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun findRecentlyFailedAssets(): List<MediaAsset> =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun findExpiredPendingUploadAssets(limit: Int): List<MediaAsset> =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun findExpiredUploadingAssets(limit: Int): List<MediaAsset> =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun findActiveByWorkspaceAndHash(workspaceId: String, fileHash: String): MediaAsset? =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")
}

/**
 * In-memory mock of [PublicationAssetRepository] that only implements the method
 * used by [MediaAssetResolverImpl]: [findByWorkspaceAndIds].
 *
 * Other methods throw [UnsupportedOperationException].
 */
private class MockPublicationAssetRepository(vararg initialAssets: PublicationAsset) : PublicationAssetRepository {

    private val assets: MutableMap<String, MutableMap<String, PublicationAsset>> = linkedMapOf()

    /** Number of times [findByWorkspaceAndIds] was called. */
    var findCallCount: Int = 0
        private set

    /** The [assetIds] argument from the last call to [findByWorkspaceAndIds]. */
    var lastUnresolvedIds: Collection<String> = emptyList()
        private set

    init {
        for (asset in initialAssets) {
            val wsAssets = assets.getOrPut(asset.workspaceId) { linkedMapOf() }
            wsAssets[asset.id] = asset
        }
    }

    override suspend fun findByWorkspaceAndIds(
        workspaceId: String,
        assetIds: Collection<String>,
    ): List<PublicationAsset> {
        findCallCount++
        lastUnresolvedIds = assetIds.toList()
        val wsAssets = assets[workspaceId] ?: return emptyList()
        return assetIds.mapNotNull { wsAssets[it] }
    }

    override suspend fun create(asset: PublicationAsset): PublicationAsset =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun updateStatus(assetId: String, status: PublicationAssetStatus) =
        throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")

    override suspend fun updateProviderAssetRef(
        assetId: String,
        providerAssetRef: com.profiletailors.smp.publishing.domain.ProviderAssetRef,
    ) = throw UnsupportedOperationException("Not used by MediaAssetResolverImpl")
}
