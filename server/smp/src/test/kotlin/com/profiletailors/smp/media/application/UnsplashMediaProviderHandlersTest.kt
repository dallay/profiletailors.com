package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.MediaAsset
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UnsplashMediaProviderHandlersTest {
    private val photo = UnsplashPhoto(
        externalId = "photo-1",
        name = "Remote team",
        previewUrl = "https://images.unsplash.com/photo-1-small",
        importUrl = "https://images.unsplash.com/photo-1-regular",
        sourceUrl = "https://unsplash.com/photos/photo-1",
        authorName = "Test Author",
        authorUrl = "https://unsplash.com/@test-author",
        downloadLocation = "https://api.unsplash.com/photos/photo-1/download",
    )

    @Test
    fun `should normalize blank query to null when handler receives whitespace`() = runTest {
        val provider = mockk<UnsplashPhotoProvider>()
        coEvery { provider.search(null) } returns listOf(photo)
        val handler = SearchUnsplashPhotosHandler(provider)

        val result = handler.handle(SearchUnsplashPhotosQuery("   "))

        result shouldBe listOf(photo)
        coVerify { provider.search(null) }
    }

    @Test
    fun `search normalizes whitespace-only query to null`() = runTest {
        val provider = mockk<UnsplashPhotoProvider>()
        coEvery { provider.search(null) } returns listOf(photo)
        val handler = SearchUnsplashPhotosHandler(provider)

        val result = handler.handle(SearchUnsplashPhotosQuery("  \t  "))

        coVerify { provider.search(null) }
        result shouldBe listOf(photo)
    }

    @Test
    fun `search preserves non-empty query`() = runTest {
        val provider = mockk<UnsplashPhotoProvider>()
        coEvery { provider.search("remote work") } returns listOf(photo)
        val handler = SearchUnsplashPhotosHandler(provider)

        val result = handler.handle(SearchUnsplashPhotosQuery("remote work"))

        coVerify { provider.search("remote work") }
        result shouldBe listOf(photo)
    }

    @Test
    fun `import persists a ready attributed asset after storage and download tracking`() = runTest {
        val fixture = fixture(flowOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))

        val result = fixture.handler.handle(ImportUnsplashPhotoCommand("workspace-1", "photo-1"))

        result.status shouldBe "READY"
        result.sourceType shouldBe "EXTERNAL"
        result.sourceProvider shouldBe "unsplash"
        result.externalId shouldBe "photo-1"
        result.fileSizeBytes shouldBe 4L
        result.authorUrl shouldBe "https://unsplash.com/@test-author"
        result.previewUrl.shouldNotBeNull()
        result.previewUrl shouldStartWith "/preview/"
        coVerifyOrder {
            fixture.storage.upload(any(), any(), any(), any(), any())
            fixture.provider.trackDownload(photo)
            fixture.repository.create(any())
        }
    }

    @Test
    fun `oversized provider image is rejected and partial storage is cleaned`() = runTest {
        val fixture = fixture(flowOf(byteArrayOf(1, 2, 3)), maxFileSizeBytes = 2)

        shouldThrow<UnsplashPhotoTooLargeException> {
            fixture.handler.handle(ImportUnsplashPhotoCommand("workspace-1", "photo-1"))
        }.message.shouldContain("exceeds the import size limit")

        coVerify(exactly = 1) { fixture.storage.delete(any(), any(), "unsplash-import") }
        coVerify(exactly = 0) { fixture.provider.trackDownload(any()) }
        coVerify(exactly = 0) { fixture.repository.create(any()) }
    }

    @Test
    fun `tracking failure cleans storage and does not persist an asset`() = runTest {
        val fixture = fixture(flowOf(byteArrayOf(1, 2, 3)))
        coEvery { fixture.provider.trackDownload(photo) } throws UnsplashProviderException("tracking failed")

        shouldThrow<UnsplashProviderException> {
            fixture.handler.handle(ImportUnsplashPhotoCommand("workspace-1", "photo-1"))
        }

        coVerify(exactly = 1) { fixture.storage.delete(any(), any(), "unsplash-import") }
        coVerify(exactly = 0) { fixture.repository.create(any()) }
    }

    @Test
    fun `summary failure preserves persisted asset and storage object`() = runTest {
        val fixture = fixture(
            content = flowOf(byteArrayOf(1, 2, 3)),
            assetPreviewUrlResolver = AssetPreviewUrlResolver { _, _, _, _, _ -> error("preview failed") },
        )

        shouldThrow<IllegalStateException> {
            fixture.handler.handle(ImportUnsplashPhotoCommand("workspace-1", "photo-1"))
        }

        coVerify(exactly = 1) { fixture.repository.create(any()) }
        coVerify(exactly = 0) { fixture.storage.delete(any(), any(), any()) }
    }

    @Test
    fun `workspace creation rate limit rejects import before calling Unsplash`() = runTest {
        val fixture = fixture(flowOf(byteArrayOf(1, 2, 3)), rateLimitAllowed = false)

        shouldThrow<RateLimitExceededException> {
            fixture.handler.handle(ImportUnsplashPhotoCommand("workspace-1", "photo-1"))
        }

        coVerify(exactly = 0) { fixture.provider.get(any()) }
        coVerify(exactly = 0) { fixture.storage.upload(any(), any(), any(), any(), any()) }
    }

    private fun fixture(
        content: Flow<ByteArray>,
        maxFileSizeBytes: Long = 1024,
        rateLimitAllowed: Boolean = true,
        assetPreviewUrlResolver: AssetPreviewUrlResolver =
            AssetPreviewUrlResolver { assetId, _, _, _, _ -> "/preview/$assetId" },
    ): Fixture {
        val provider = mockk<UnsplashPhotoProvider>()
        val repository = mockk<MediaAssetRepository>()
        val rateLimitRepository = mockk<MediaRateLimitRepository>()
        val storage = mockk<MediaStoragePort>()
        coEvery { provider.get("photo-1") } returns photo
        every { provider.download(photo) } returns content
        coEvery { provider.trackDownload(photo) } returns Unit
        coEvery { repository.create(any()) } answers { firstArg<MediaAsset>() }
        coEvery { storage.upload(any(), any(), any(), any(), any()) } coAnswers {
            thirdArg<Flow<ByteArray>>().toList()
            Unit
        }
        coEvery { storage.delete(any(), any(), any()) } returns Unit
        coEvery { rateLimitRepository.tryIncrementHourlyCreationCount("workspace-1", 200) } returns
            if (rateLimitAllowed) {
                MediaRateLimitRepository.RateLimitIncrementResult(1, true)
            } else {
                MediaRateLimitRepository.RateLimitIncrementResult(200, false)
            }

        val settings = UnsplashImportSettings("attachments", maxFileSizeBytes, 200)
        val mediaImportService = MediaImportService(
            provider = provider,
            mediaAssetRepository = repository,
            storagePort = storage,
            settings = settings,
            assetPreviewUrlResolver = assetPreviewUrlResolver,
            mediaPreviewTokenService = MediaPreviewTokenService("test-signing-secret", 3600),
        )
        val handler = ImportUnsplashPhotoHandler(
            mediaRateLimitRepository = rateLimitRepository,
            mediaImportService = mediaImportService,
            settings = settings,
        )
        return Fixture(handler, provider, repository, storage)
    }

    private data class Fixture(
        val handler: ImportUnsplashPhotoHandler,
        val provider: UnsplashPhotoProvider,
        val repository: MediaAssetRepository,
        val storage: MediaStoragePort,
    )
}
