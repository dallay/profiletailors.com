package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MediaHandlersTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC)
    private val uploadSettings = MediaUploadSettings(
        maxConcurrentUploads = 5,
        maxCreationsPerHour = 200,
        storageBucket = "attachments",
    )

    // --- CreateUploadedAssetHandler tests ---

    @Test
    fun `createUploadedAsset creates asset in PROCESSING state with valid media type`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val result = handler.handle(
            CreateUploadedAssetCommand(
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                originalFilename = null,
            ),
        )

        assertNotNull(result.assetId)
        assertEquals("ws-1", result.workspaceId)
        assertEquals(MediaSourceType.UPLOADED, result.sourceType)
        assertEquals("image/jpeg", result.mediaType)
        assertEquals("PROCESSING", result.status)
        assertNotNull(repository.lastCreated)
        assertEquals(MediaAssetStatus.PROCESSING, repository.lastCreated!!.status)
    }

    @Test
    fun `createUploadedAsset generates deterministic storage key`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val result = handler.handle(
            CreateUploadedAssetCommand(
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                originalFilename = null,
            ),
        )

        assertTrue(repository.lastCreated!!.storageKey.startsWith("assets/ws-1/"))
        assertTrue(repository.lastCreated!!.storageKey.endsWith(result.assetId))
    }

    @Test
    fun `createUploadedAsset rejects unsupported media type`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreateUploadedAssetCommand(
                        workspaceId = "ws-1",
                        sourceType = MediaSourceType.UPLOADED,
                        mediaType = "application/zip",
                        originalFilename = null,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("image/jpeg"))
        assertEquals("application/zip", error.declaredType)
    }

    @Test
    fun `createUploadedAsset requires originalFilename for OOXML media type`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreateUploadedAssetCommand(
                        workspaceId = "ws-1",
                        sourceType = MediaSourceType.UPLOADED,
                        mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        originalFilename = null,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("originalFilename"))
    }

    @Test
    fun `createUploadedAsset rejects invalid OOXML extension`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreateUploadedAssetCommand(
                        workspaceId = "ws-1",
                        sourceType = MediaSourceType.UPLOADED,
                        mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        originalFilename = "document.pdf",
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("Invalid file extension"))
    }

    @Test
    fun `createUploadedAsset accepts valid OOXML with correct extension`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val result = handler.handle(
            CreateUploadedAssetCommand(
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                originalFilename = "report.docx",
            ),
        )

        assertEquals("PROCESSING", result.status)
    }

    @Test
    fun `createUploadedAsset rejects non-UPLOADED source type`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreateUploadedAssetCommand(
                        workspaceId = "ws-1",
                        sourceType = MediaSourceType.EXTERNAL_URL,
                        mediaType = "image/jpeg",
                        originalFilename = null,
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("Only UPLOADED is supported"))
    }

    @Test
    fun `createUploadedAsset throws RateLimitExceededException when hourly creation limit reached`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository(maxCreationsPerHour = 0)
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings.copy(maxCreationsPerHour = 0),
        )

        val error = assertThrows(RateLimitExceededException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreateUploadedAssetCommand(
                        workspaceId = "ws-1",
                        sourceType = MediaSourceType.UPLOADED,
                        mediaType = "image/jpeg",
                        originalFilename = null,
                    ),
                )
            }
        }

        assertEquals("hourly_creations", error.limitType)
        assertEquals(0, error.currentValue)
    }

    // --- UploadAssetHandler tests ---

    @Test
    fun `uploadAsset marks asset READY on successful upload`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        val asset = repository.createSync(
            MediaAsset(
                assetId = "asset-1",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-1",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            storageApplicationService = storage,
            uploadSettings = uploadSettings,
        )

        // JPEG magic bytes (SOI + APP0 marker)
        val result = handler.handle(
            UploadAssetCommand(
                assetId = "asset-1",
                workspaceId = "ws-1",
                fileStream = kotlinx.coroutines.flow.flowOf(
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10),
                ),
                contentLength = 6L,
                maxFileSizeBytes = 500L * 1024 * 1024,
                contentType = "image/jpeg",
            ),
        )

        assertEquals("READY", result.status)
        assertEquals("asset-1", result.assetId)
        // Verify the storage upload was called with the correct key
        assertTrue(storageBackend.uploadedKeys.contains("assets/ws-1/asset-1"))
    }

    @Test
    fun `uploadAsset accepts a real PNG fixture from the repository`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-png",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-real-png",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            storageApplicationService = storage,
            uploadSettings = uploadSettings,
        )
        val pngBytes = readMediaFixtureBytes("sample.png")

        val result = handler.handle(
            UploadAssetCommand(
                assetId = "asset-real-png",
                workspaceId = "ws-1",
                fileStream = kotlinx.coroutines.flow.flowOf(pngBytes),
                contentLength = pngBytes.size.toLong(),
                maxFileSizeBytes = 500L * 1024 * 1024,
                contentType = "image/png",
            ),
        )

        assertEquals("READY", result.status)
        assertEquals(pngBytes.size.toLong(), result.fileSizeBytes)
        assertTrue(storageBackend.uploadedKeys.contains("assets/ws-1/asset-real-png"))
    }

    @Test
    fun `uploadAsset rejects a real PNG fixture when declared media type is jpeg`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-png-mismatch",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-real-png-mismatch",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            storageApplicationService = storage,
            uploadSettings = uploadSettings,
        )
        val pngBytes = readMediaFixtureBytes("sample.png")

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    UploadAssetCommand(
                        assetId = "asset-real-png-mismatch",
                        workspaceId = "ws-1",
                        fileStream = kotlinx.coroutines.flow.flowOf(pngBytes),
                        contentLength = pngBytes.size.toLong(),
                        maxFileSizeBytes = 500L * 1024 * 1024,
                        contentType = "image/png",
                    ),
                )
            }
        }

        assertEquals("image/jpeg", error.declaredType)
        assertEquals("image/png", error.detectedType)
    }

    @Test
    fun `uploadAsset accepts a real JPEG fixture from test resources`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-jpeg",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-real-jpeg",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(repository, rateLimitRepo, storage, uploadSettings)
        val jpegBytes = readMediaFixtureBytes("sample.jpeg")

        val result = handler.handle(
            UploadAssetCommand(
                assetId = "asset-real-jpeg",
                workspaceId = "ws-1",
                fileStream = kotlinx.coroutines.flow.flowOf(jpegBytes),
                contentLength = jpegBytes.size.toLong(),
                maxFileSizeBytes = 500L * 1024 * 1024,
                contentType = "image/jpeg",
            ),
        )

        assertEquals("READY", result.status)
        assertEquals(jpegBytes.size.toLong(), result.fileSizeBytes)
    }

    @Test
    fun `uploadAsset accepts a real GIF fixture from test resources`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-gif",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/gif",
                storageKey = "assets/ws-1/asset-real-gif",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(repository, rateLimitRepo, storage, uploadSettings)
        val gifBytes = readMediaFixtureBytes("sample.gif")

        val result = handler.handle(
            UploadAssetCommand(
                assetId = "asset-real-gif",
                workspaceId = "ws-1",
                fileStream = kotlinx.coroutines.flow.flowOf(gifBytes),
                contentLength = gifBytes.size.toLong(),
                maxFileSizeBytes = 500L * 1024 * 1024,
                contentType = "image/gif",
            ),
        )

        assertEquals("READY", result.status)
        assertEquals(gifBytes.size.toLong(), result.fileSizeBytes)
    }

    @Test
    fun `uploadAsset accepts a real WEBP fixture from test resources`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-webp",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/webp",
                storageKey = "assets/ws-1/asset-real-webp",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(repository, rateLimitRepo, storage, uploadSettings)
        val webpBytes = readMediaFixtureBytes("sample.webp")

        val result = handler.handle(
            UploadAssetCommand(
                assetId = "asset-real-webp",
                workspaceId = "ws-1",
                fileStream = kotlinx.coroutines.flow.flowOf(webpBytes),
                contentLength = webpBytes.size.toLong(),
                maxFileSizeBytes = 500L * 1024 * 1024,
                contentType = "image/webp",
            ),
        )

        assertEquals("READY", result.status)
        assertEquals(webpBytes.size.toLong(), result.fileSizeBytes)
    }

    @Test
    fun `uploadAsset accepts a real MP4 fixture from test resources`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-mp4",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "video/mp4",
                storageKey = "assets/ws-1/asset-real-mp4",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(repository, rateLimitRepo, storage, uploadSettings)
        val mp4Bytes = readMediaFixtureBytes("sample.mp4")

        val result = handler.handle(
            UploadAssetCommand(
                assetId = "asset-real-mp4",
                workspaceId = "ws-1",
                fileStream = kotlinx.coroutines.flow.flowOf(mp4Bytes),
                contentLength = mp4Bytes.size.toLong(),
                maxFileSizeBytes = 500L * 1024 * 1024,
                contentType = "video/mp4",
            ),
        )

        assertEquals("READY", result.status)
        assertEquals(mp4Bytes.size.toLong(), result.fileSizeBytes)
    }

    @Test
    fun `uploadAsset rejects a real AVIF fixture because format is unsupported`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-real-avif",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/avif",
                storageKey = "assets/ws-1/asset-real-avif",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.setUploadSlotClaimable("ws-1")

        val handler = UploadAssetHandler(repository, rateLimitRepo, storage, uploadSettings)
        val avifBytes = readMediaFixtureBytes("sample.avif")

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    UploadAssetCommand(
                        assetId = "asset-real-avif",
                        workspaceId = "ws-1",
                        fileStream = kotlinx.coroutines.flow.flowOf(avifBytes),
                        contentLength = avifBytes.size.toLong(),
                        maxFileSizeBytes = 500L * 1024 * 1024,
                        contentType = "image/avif",
                    ),
                )
            }
        }

        assertEquals("image/avif", error.declaredType)
    }

    @Test
    fun `createUploadedAsset rejects a real MP3 fixture metadata because format is unsupported`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val handler = CreateUploadedAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            uploadSettings = uploadSettings,
        )
        val mp3Bytes = readMediaFixtureBytes("sample.mp3")

        assertTrue(mp3Bytes.isNotEmpty())

        val error = assertThrows(UnsupportedMediaTypeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    CreateUploadedAssetCommand(
                        workspaceId = "ws-1",
                        sourceType = MediaSourceType.UPLOADED,
                        mediaType = "audio/mpeg",
                        originalFilename = "sample.mp3",
                    ),
                )
            }
        }

        assertEquals("audio/mpeg", error.declaredType)
    }

    @Test
    fun `uploadAsset throws UploadConflictException when asset is already READY`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)
        repository.createSync(
            MediaAsset(
                assetId = "asset-ready",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-ready",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 1024L,
                createdAt = fixedClock.instant(),
            ),
        )

        val handler = UploadAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            storageApplicationService = storage,
            uploadSettings = uploadSettings,
        )

        val error = assertThrows(UploadConflictException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    UploadAssetCommand(
                        assetId = "asset-ready",
                        workspaceId = "ws-1",
                        fileStream = kotlinx.coroutines.flow.flowOf("x".toByteArray()),
                        contentLength = 1L,
                        maxFileSizeBytes = 500L * 1024 * 1024,
                    ),
                )
            }
        }

        assertEquals("asset-ready", error.assetId)
        assertEquals("READY", error.currentStatus)
    }

    @Test
    fun `uploadAsset throws AssetNotFoundException for cross-workspace access`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        val rateLimitRepo = InMemoryMediaRateLimitRepository()
        val storageBackend = InMemoryFakeStorage()
        val storage = testStorageApplicationService(storageBackend)

        val handler = UploadAssetHandler(
            mediaAssetRepository = repository,
            mediaRateLimitRepository = rateLimitRepo,
            storageApplicationService = storage,
            uploadSettings = uploadSettings,
        )

        val error = assertThrows(AssetNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    UploadAssetCommand(
                        assetId = "non-existent",
                        workspaceId = "ws-other",
                        fileStream = kotlinx.coroutines.flow.flowOf("x".toByteArray()),
                        contentLength = 1L,
                        maxFileSizeBytes = 500L * 1024 * 1024,
                    ),
                )
            }
        }

        assertEquals("non-existent", error.assetId)
    }

    // --- ListWorkspaceAssetsHandler tests ---

    @Test
    fun `listWorkspaceAssets returns assets in newest-first order`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-older",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-older",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 100L,
                createdAt = Instant.parse("2026-06-18T12:00:00Z"),
            ),
        )
        repository.createSync(
            MediaAsset(
                assetId = "asset-newer",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-newer",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 200L,
                createdAt = Instant.parse("2026-06-19T12:00:00Z"),
            ),
        )

        val handler = ListWorkspaceAssetsHandler(mediaAssetRepository = repository)

        val result = handler.handle(
            ListWorkspaceAssetsQuery(
                workspaceId = "ws-1",
                statuses = setOf(MediaAssetStatus.READY),
                pageSize = 50,
            ),
        )

        assertEquals(2, result.assets.size)
        // Newest first
        assertEquals("asset-newer", result.assets[0].assetId)
        assertEquals("asset-older", result.assets[1].assetId)
    }

    @Test
    fun `listWorkspaceAssets filters by status`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-processing",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-processing",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.createSync(
            MediaAsset(
                assetId = "asset-ready",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-ready",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 200L,
                createdAt = fixedClock.instant(),
            ),
        )

        val handler = ListWorkspaceAssetsHandler(mediaAssetRepository = repository)

        val result = handler.handle(
            ListWorkspaceAssetsQuery(
                workspaceId = "ws-1",
                statuses = setOf(MediaAssetStatus.READY),
                pageSize = 50,
            ),
        )

        assertEquals(1, result.assets.size)
        assertEquals("asset-ready", result.assets[0].assetId)
    }

    @Test
    fun `listWorkspaceAssets returns empty for cross-workspace query`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-1",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-1",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 100L,
                createdAt = fixedClock.instant(),
            ),
        )

        val handler = ListWorkspaceAssetsHandler(mediaAssetRepository = repository)

        val result = handler.handle(
            ListWorkspaceAssetsQuery(
                workspaceId = "ws-other",
                statuses = setOf(MediaAssetStatus.READY),
                pageSize = 50,
            ),
        )

        assertEquals(0, result.assets.size)
    }

    @Test
    fun `listWorkspaceAssets returns both PROCESSING and READY when querying all statuses`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-processing",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-processing",
                status = MediaAssetStatus.PROCESSING,
                createdAt = fixedClock.instant(),
            ),
        )
        repository.createSync(
            MediaAsset(
                assetId = "asset-ready",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                storageKey = "assets/ws-1/asset-ready",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 200L,
                createdAt = fixedClock.instant(),
            ),
        )

        val handler = ListWorkspaceAssetsHandler(mediaAssetRepository = repository)

        val result = handler.handle(
            ListWorkspaceAssetsQuery(
                workspaceId = "ws-1",
                statuses = setOf(MediaAssetStatus.READY, MediaAssetStatus.PROCESSING),
                pageSize = 50,
            ),
        )

        assertEquals(2, result.assets.size)
    }

    // --- GetWorkspaceAssetHandler tests ---

    @Test
    fun `getWorkspaceAsset returns asset for correct workspace`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-1",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-1",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 1024L,
                createdAt = fixedClock.instant(),
            ),
        )

        val handler = GetWorkspaceAssetHandler(mediaAssetRepository = repository)

        val result = handler.handle(
            GetWorkspaceAssetQuery(
                assetId = "asset-1",
                workspaceId = "ws-1",
            ),
        )

        assertEquals("asset-1", result.assetId)
        assertEquals("image/jpeg", result.mediaType)
    }

    @Test
    fun `getWorkspaceAsset throws AssetNotFoundException for cross-workspace access`() = runTest {
        val repository = InMemoryMediaAssetRepository()
        repository.createSync(
            MediaAsset(
                assetId = "asset-1",
                workspaceId = "ws-1",
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/ws-1/asset-1",
                status = MediaAssetStatus.READY,
                fileSizeBytes = 1024L,
                createdAt = fixedClock.instant(),
            ),
        )

        val handler = GetWorkspaceAssetHandler(mediaAssetRepository = repository)

        val error = assertThrows(AssetNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(
                    GetWorkspaceAssetQuery(
                        assetId = "asset-1",
                        workspaceId = "ws-other",
                    ),
                )
            }
        }

        assertEquals("asset-1", error.assetId)
    }

    // --- In-memory repositories ---

    private fun readMediaFixtureBytes(fileName: String): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream("media-fixtures/$fileName")
            ?: error("Missing test media fixture: $fileName")
        return stream.use { it.readAllBytes() }
    }

    private class InMemoryMediaAssetRepository : MediaAssetRepository {
        private val items = mutableMapOf<String, MediaAsset>()
        private val uploadSlotClaimable = mutableMapOf<String, Boolean>()

        var lastCreated: MediaAsset? = null
            private set

        fun createSync(asset: MediaAsset): MediaAsset {
            items[asset.assetId] = asset
            lastCreated = asset
            return asset
        }

        fun setUploadSlotClaimable(workspaceId: String) {
            uploadSlotClaimable[workspaceId] = true
        }

        override suspend fun create(asset: MediaAsset): MediaAsset {
            items[asset.assetId] = asset
            lastCreated = asset
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
        ): PagedMediaAssets {
            val filtered = items.values
                .filter { it.workspaceId == workspaceId && it.status in statuses }
                .sortedWith(compareByDescending<MediaAsset> { it.createdAt }.thenByDescending { it.assetId })
                .take(pageSize)
            return PagedMediaAssets(assets = filtered, nextCursor = null)
        }

        override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean {
            return if (uploadSlotClaimable[workspaceId] == true) {
                val asset = items[assetId]
                if (asset != null && asset.uploadStartedAt == null) {
                    items[assetId] = asset.copy(uploadStartedAt = now)
                    true
                } else false
            } else {
                // Fallback: always succeed if uploadStartedAt is null
                val asset = items[assetId]
                if (asset != null && asset.uploadStartedAt == null) {
                    items[assetId] = asset.copy(uploadStartedAt = now)
                    true
                } else false
            }
        }

        override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? {
            val asset = items[assetId] ?: return null
            val updated = asset.copy(status = MediaAssetStatus.READY, fileSizeBytes = fileSizeBytes, uploadStartedAt = null)
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

    private class InMemoryMediaRateLimitRepository(
        private val maxCreationsPerHour: Int = 200,
        private val maxConcurrentUploads: Int = 5,
    ) : MediaRateLimitRepository {
        private val creationCounts = mutableMapOf<String, Int>()
        private val uploadSlots = mutableMapOf<String, Int>()

        override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean {
            val current = uploadSlots.getOrDefault(workspaceId, 0)
            if (current < maxConcurrent) {
                uploadSlots[workspaceId] = current + 1
                return true
            }
            return false
        }

        override suspend fun releaseConcurrentUploadSlot(workspaceId: String) {
            val current = uploadSlots.getOrDefault(workspaceId, 0)
            if (current > 0) {
                uploadSlots[workspaceId] = current - 1
            }
        }

        override suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int): Boolean {
            // Enforce the repository's own maxCreationsPerHour limit (not the caller's maxPerHour).
            // The caller's maxPerHour comes from handler config; this repo's maxCreationsPerHour
            // is used in tests to simulate hitting the limit.
            val effectiveMax = maxCreationsPerHour
            val current = creationCounts.getOrDefault(workspaceId, 0)
            if (current < effectiveMax) {
                creationCounts[workspaceId] = current + 1
                return true
            }
            return false
        }
    }
}
