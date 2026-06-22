package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaAssetResolverImplTest {

    @Test
    fun `returns empty list when no asset ids are requested`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(),
            publicationAssetRepository = InMemoryPublicationAssetRepository(),
        )

        val result = resolver.resolveReadyAssets("ws-1", emptyList())

        assertEquals(emptyList<ResolvedAssetSummary>(), result)
    }

    @Test
    fun `resolves ready assets from media repository first`() = runTest {
        val mediaRepository = InMemoryMediaAssetRepository(
            listOf(
                mediaAsset(assetId = "asset-1", workspaceId = "ws-1", status = MediaAssetStatus.READY),
            ),
        )
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = mediaRepository,
            publicationAssetRepository = InMemoryPublicationAssetRepository(),
        )

        val result = resolver.resolveReadyAssets("ws-1", listOf("asset-1"))

        assertEquals(
            listOf(
                ResolvedAssetSummary(
                    assetId = "asset-1",
                    workspaceId = "ws-1",
                    storageKey = "assets/ws-1/asset-1",
                    mediaType = "image/png",
                ),
            ),
            result,
        )
    }

    @Test
    fun `falls back to ready legacy publication assets when media asset is not found`() = runTest {
        val legacyRepository = InMemoryPublicationAssetRepository(
            listOf(
                publicationAsset(id = "legacy-1", workspaceId = "ws-1", status = PublicationAssetStatus.READY),
            ),
        )
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(),
            publicationAssetRepository = legacyRepository,
        )

        val result = resolver.resolveReadyAssets("ws-1", listOf("legacy-1"))

        assertEquals(
            listOf(
                ResolvedAssetSummary(
                    assetId = "legacy-1",
                    workspaceId = "ws-1",
                    storageKey = "legacy/ws-1/legacy-1",
                    mediaType = "image/jpeg",
                ),
            ),
            result,
        )
    }

    @Test
    fun `throws when requested asset is missing from both stores`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(),
            publicationAssetRepository = InMemoryPublicationAssetRepository(),
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolveReadyAssets("ws-1", listOf("missing"))
            }
        }

        assertEquals("missing", error.assetId)
    }

    @Test
    fun `throws when media asset exists but is not ready`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(
                listOf(mediaAsset(assetId = "asset-1", workspaceId = "ws-1", status = MediaAssetStatus.PROCESSING)),
            ),
            publicationAssetRepository = InMemoryPublicationAssetRepository(),
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolveReadyAssets("ws-1", listOf("asset-1"))
            }
        }

        assertEquals("asset-1", error.assetId)
        assertEquals("asset status is PROCESSING", error.reason)
    }

    @Test
    fun `ignores legacy publication assets that are not ready or missing storage key`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(),
            publicationAssetRepository = InMemoryPublicationAssetRepository(
                listOf(
                    publicationAsset(id = "legacy-processing", workspaceId = "ws-1", status = PublicationAssetStatus.PROCESSING),
                ),
            ),
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolveReadyAssets("ws-1", listOf("legacy-processing"))
            }
        }

        assertEquals("legacy-processing", error.assetId)
    }

    @Test
    fun `ignores legacy ready asset without storage key`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(),
            publicationAssetRepository = InMemoryPublicationAssetRepository(
                listOf(
                    publicationAsset(
                        id = "legacy-no-key",
                        workspaceId = "ws-1",
                        status = PublicationAssetStatus.READY,
                        sourceType = AssetSourceType.EXTERNAL_URL,
                        storageKey = null,
                        externalUrl = "https://example.com/image.jpg",
                    ),
                ),
            ),
        )

        val error = assertThrows(AssetNotReadyException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolveReadyAssets("ws-1", listOf("legacy-no-key"))
            }
        }

        assertEquals("legacy-no-key", error.assetId)
    }

    @Test
    fun `returns combined ready assets from media and legacy stores preserving source grouping`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = InMemoryMediaAssetRepository(
                listOf(mediaAsset(assetId = "media-1", workspaceId = "ws-1", status = MediaAssetStatus.READY)),
            ),
            publicationAssetRepository = InMemoryPublicationAssetRepository(
                listOf(publicationAsset(id = "legacy-1", workspaceId = "ws-1", status = PublicationAssetStatus.READY)),
            ),
        )

        val result = resolver.resolveReadyAssets("ws-1", listOf("media-1", "legacy-1"))

        assertEquals(listOf("media-1", "legacy-1"), result.map { it.assetId })
    }

    private fun mediaAsset(assetId: String, workspaceId: String, status: MediaAssetStatus): MediaAsset =
        MediaAsset(
            assetId = assetId,
            workspaceId = workspaceId,
            sourceType = MediaSourceType.UPLOADED,
            mediaType = "image/png",
            storageKey = "assets/$workspaceId/$assetId",
            fileSizeBytes = if (status == MediaAssetStatus.READY) 1234L else null,
            status = status,
            createdAt = Instant.parse("2026-06-20T08:00:00Z"),
        )

    private fun publicationAsset(
        id: String,
        workspaceId: String,
        status: PublicationAssetStatus,
        sourceType: AssetSourceType = AssetSourceType.UPLOADED,
        storageKey: String? = "legacy/$workspaceId/$id",
        externalUrl: String? = null,
    ): PublicationAsset =
        PublicationAsset(
            id = id,
            workspaceId = workspaceId,
            sourceType = sourceType,
            mediaType = "image/jpeg",
            storageKey = storageKey,
            externalUrl = externalUrl,
            status = status,
            createdByPrincipalId = "principal-1",
            createdAt = Instant.parse("2026-06-20T08:00:00Z"),
            providerAssetRef = ProviderAssetRef("provider-$id", "image/jpeg"),
        )

    private class InMemoryMediaAssetRepository(
        private val assets: List<MediaAsset> = emptyList(),
    ) : MediaAssetRepository {
        override suspend fun create(asset: MediaAsset): MediaAsset = asset

        override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset? =
            assets.firstOrNull { it.workspaceId == workspaceId && it.assetId == assetId }

        override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>): List<MediaAsset> =
            assets.filter { it.workspaceId == workspaceId && it.assetId in assetIds }

        override suspend fun listByWorkspace(
            workspaceId: String,
            statuses: Set<MediaAssetStatus>,
            pageSize: Int,
            cursor: String?,
        ): PagedMediaAssets = PagedMediaAssets(emptyList(), null)

        override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean = false

        override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? = null

        override suspend fun markAsFailed(assetId: String, workspaceId: String): MediaAsset? = null

        override suspend fun delete(assetId: String, workspaceId: String): MediaAsset? = null

        override suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long): List<MediaAsset> = emptyList()

        override suspend fun findRecentlyFailedAssets(): List<MediaAsset> = emptyList()
    }

    private class InMemoryPublicationAssetRepository(
        private val assets: List<PublicationAsset> = emptyList(),
    ) : PublicationAssetRepository {
        override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: Collection<String>): List<PublicationAsset> =
            assets.filter { it.workspaceId == workspaceId && it.id in assetIds }

        override suspend fun create(asset: PublicationAsset): PublicationAsset = asset

        override suspend fun updateStatus(assetId: String, status: PublicationAssetStatus) = Unit

        override suspend fun updateProviderAssetRef(assetId: String, providerAssetRef: ProviderAssetRef) = Unit
    }
}
