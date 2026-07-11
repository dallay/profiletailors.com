package com.profiletailors.smp.publishing.infrastructure.media

import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.application.PagedMediaAssets
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaAssetResolverImplTest {

    private val now = Instant.parse("2026-07-10T12:00:00Z")

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

    private fun assertResolvedAsset(
        actual: ResolvedAssetSummary,
        expectedAssetId: String,
        expectedWorkspace: String = WORKSPACE,
        expectedStorageKey: String = MEDIA_KEY_1,
        expectedMediaType: String = "image/jpeg",
    ) {
        actual.assetId shouldBe expectedAssetId
        actual.workspaceId shouldBe expectedWorkspace
        actual.storageKey shouldBe expectedStorageKey
        actual.mediaType shouldBe expectedMediaType
    }

    @Test
    fun `returns empty list when assetIds is empty`() = runTest {
        val resolver = MediaAssetResolverImpl(
            mediaAssetRepository = MockMediaAssetRepository(),
            publicationAssetRepository = MockPublicationAssetRepository(),
        )

        val result = resolver.resolveReadyAssets(WORKSPACE, emptyList())

        result.shouldBeEmpty()
    }

    @Test
    fun `returns resolved assets when all IDs are found in media context as READY`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1, mediaType = "image/jpeg"),
            readyMediaAsset(assetId = MEDIA_ASSET_2, storageKey = MEDIA_KEY_2, mediaType = "image/png"),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, MEDIA_ASSET_2))

        result.size shouldBe 2
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
        legacyRepo.findCallCount shouldBe 0
    }

    @Test
    fun `returns resolved assets when all IDs are found in legacy store as READY`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1, mediaType = "application/pdf"),
            legacyAsset(id = LEGACY_ASSET_2, storageKey = LEGACY_KEY_2, mediaType = "video/mp4"),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1, LEGACY_ASSET_2))

        result.size shouldBe 2
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
        mediaRepo.findCallCount shouldBe 1
        legacyRepo.findCallCount shouldBe 1
    }

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

        result.size shouldBe 2
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
        mediaRepo.findCallCount shouldBe 1
        legacyRepo.findCallCount shouldBe 1
        legacyRepo.lastUnresolvedIds shouldBe listOf(LEGACY_ASSET_1)
    }

    @Test
    fun `throws AssetNotReadyException when an asset ID is not found in either store`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MISSING_ASSET))
        }

        exception.assetId shouldBe MISSING_ASSET
        exception.message.shouldContain("not found")
        mediaRepo.findCallCount shouldBe 1
        legacyRepo.findCallCount shouldBe 1
    }

    @Test
    fun `throws AssetNotReadyException when one of multiple IDs is not found in either store`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, MISSING_ASSET))
        }

        exception.assetId shouldBe MISSING_ASSET
        legacyRepo.findCallCount shouldBe 1
        legacyRepo.lastUnresolvedIds shouldBe listOf(MISSING_ASSET)
    }

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

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        exception.assetId shouldBe MEDIA_ASSET_1
        exception.message.shouldContain("PROCESSING")
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

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        exception.assetId shouldBe MEDIA_ASSET_1
        exception.message.shouldContain("FAILED")
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

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        exception.assetId shouldBe MEDIA_ASSET_1
        exception.message.shouldContain("PENDING_UPLOAD")
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

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, LEGACY_ASSET_1))
        }

        exception.assetId shouldBe MEDIA_ASSET_1
    }

    @Test
    fun `throws when only source is a legacy asset with PROCESSING status`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, status = PublicationAssetStatus.PROCESSING),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        exception.assetId shouldBe LEGACY_ASSET_1
        legacyRepo.findCallCount shouldBe 1
    }

    @Test
    fun `throws when only source is a legacy asset with FAILED status`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, status = PublicationAssetStatus.FAILED),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        exception.assetId shouldBe LEGACY_ASSET_1
    }

    @Test
    fun `throws when only source is a legacy asset with null storageKey`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = null),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        exception.assetId shouldBe LEGACY_ASSET_1
        legacyRepo.findCallCount shouldBe 1
    }

    @Test
    fun `throws when only source is a legacy asset with blank storageKey`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = "  "),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))
        }

        exception.assetId shouldBe LEGACY_ASSET_1
    }

    @Test
    fun `throws when a requested legacy asset is not READY`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1, status = PublicationAssetStatus.READY),
            legacyAsset(id = LEGACY_ASSET_2, storageKey = LEGACY_KEY_2, status = PublicationAssetStatus.PROCESSING),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1, LEGACY_ASSET_2))
        }

        exception.assetId shouldBe LEGACY_ASSET_2
    }

    @Test
    fun `throws when a requested legacy asset lacks a storage key`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
            legacyAsset(id = LEGACY_ASSET_2, storageKey = null),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1, LEGACY_ASSET_2))
        }

        exception.assetId shouldBe LEGACY_ASSET_2
    }

    @Test
    fun `error message for missing asset contains descriptive reason`() = runTest {
        val resolver = MediaAssetResolverImpl(
            MockMediaAssetRepository(),
            MockPublicationAssetRepository(),
        )

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MISSING_ASSET))
        }

        exception.message.shouldContain(MISSING_ASSET)
        exception.message.shouldContain("not ready")
        exception.message.shouldContain("not found")
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

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))
        }

        exception.message.shouldContain(MEDIA_ASSET_1)
        exception.message.shouldContain("PROCESSING")
    }

    @Test
    fun `throws when media asset is found but for a different implicit workspace`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, workspaceId = WORKSPACE, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets("different-workspace", listOf(MEDIA_ASSET_1))
        }

        exception.assetId shouldBe MEDIA_ASSET_1
    }

    @Test
    fun `handles mixed assets where some IDs are in media, some filtered, some missing`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(
                WORKSPACE,
                listOf(MEDIA_ASSET_1, LEGACY_ASSET_1, MISSING_ASSET),
            )
        }

        exception.assetId shouldBe MISSING_ASSET
    }

    @Test
    fun `throws when first media asset is READY but second is not, before mixing with legacy`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1, status = MediaAssetStatus.READY),
            readyMediaAsset(assetId = MEDIA_ASSET_2, storageKey = null, status = MediaAssetStatus.FAILED),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val exception = shouldThrow<AssetNotReadyException> {
            resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1, MEDIA_ASSET_2))
        }

        exception.assetId shouldBe MEDIA_ASSET_2
        legacyRepo.findCallCount shouldBe 0
    }

    @Test
    fun `skips legacy repo when all IDs are resolved from media context`() = runTest {
        val mediaRepo = MockMediaAssetRepository(
            readyMediaAsset(assetId = MEDIA_ASSET_1, storageKey = MEDIA_KEY_1),
        )
        val legacyRepo = MockPublicationAssetRepository()
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        resolver.resolveReadyAssets(WORKSPACE, listOf(MEDIA_ASSET_1))

        legacyRepo.findCallCount shouldBe 0
    }

    @Test
    fun `resolves a ready legacy asset with a valid storage key`() = runTest {
        val mediaRepo = MockMediaAssetRepository()
        val legacyRepo = MockPublicationAssetRepository(
            legacyAsset(id = LEGACY_ASSET_1, storageKey = LEGACY_KEY_1),
        )
        val resolver = MediaAssetResolverImpl(mediaRepo, legacyRepo)

        val result = resolver.resolveReadyAssets(WORKSPACE, listOf(LEGACY_ASSET_1))

        result.size shouldBe 1
        result[0].assetId shouldBe LEGACY_ASSET_1
        result[0].storageKey shouldBe LEGACY_KEY_1
    }

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
}

private class MockMediaAssetRepository(vararg initialAssets: MediaAsset) : MediaAssetRepository {

    private val assets: MutableMap<String, MutableMap<String, MediaAsset>> = linkedMapOf()

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

private class MockPublicationAssetRepository(vararg initialAssets: PublicationAsset) : PublicationAssetRepository {

    private val assets: MutableMap<String, MutableMap<String, PublicationAsset>> = linkedMapOf()

    var findCallCount: Int = 0
        private set

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
