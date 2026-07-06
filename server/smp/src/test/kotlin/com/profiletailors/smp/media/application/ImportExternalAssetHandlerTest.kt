package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.emailVerificationPolicyOf
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.media.application.port.ProviderExternalAsset
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.domain.BlobStatus
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest

class ImportExternalAssetHandlerTest {

    private val workspaceId = "ws-1"
    private val photoId = "photo-42"
    private val externalId = ProviderExternalId("unsplash:$photoId")
    private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    private val moreBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    private val sampleBytes = jpegMagic + moreBytes
    private val expectedHash = run {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(sampleBytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `creates a new EXTERNAL asset with attribution and returns deduped false`() = runTest {
        val assetRepo = ImportTestMediaAssetRepository()
        val blobRepo = ImportTestBlobRepository()
        val storage = ImportRecordingStorage().apply { preloadObject("bucket", "temp-key", sampleBytes) }
        val handler = handler(assetRepo, blobRepo, storage)

        val result = handler.handle(
            ImportExternalAssetCommand(
                workspaceId = workspaceId,
                externalAsset = sampleExternalAsset(),
            ),
        )

        assertFalse(result.deduped)
        val stored = assetRepo.asset(workspaceId, result.assetId)!!
        assertEquals(MediaSourceType.EXTERNAL, stored.sourceType)
        assertEquals("unsplash", stored.sourceProvider)
        assertEquals("unsplash:$photoId", stored.externalId)
        assertEquals("https://unsplash.com/photos/$photoId", stored.sourceUrl)
        assertEquals("Jane Creator", stored.authorName)
        assertEquals("https://unsplash.com/@jane", stored.authorUrl)
        assertEquals(expectedHash, stored.fileHash)
        assertEquals(MediaAssetStatus.READY, stored.status)
        assertNotNull(stored.storageKey)
        assertEquals("image/jpeg", stored.detectedMediaType)
        assertEquals(1, blobRepo.blobs.size)
        assertTrue(blobRepo.blob(workspaceId, expectedHash)?.status == BlobStatus.READY)
    }

    @Test
    fun `returns deduped true and canonical asset id when bytes already exist for workspace`() = runTest {
        val assetRepo = ImportTestMediaAssetRepository()
        val blobRepo = ImportTestBlobRepository()

        val existingAssetId = "asset-existing"
        assetRepo.assets[workspaceId to existingAssetId] = MediaAsset(
            assetId = existingAssetId,
            workspaceId = workspaceId,
            sourceType = MediaSourceType.EXTERNAL,
            fileHash = expectedHash,
            mediaType = "image/jpeg",
            storageKey = "assets/$workspaceId/blobs/$expectedHash.jpg",
            detectedMediaType = "image/jpeg",
            originalFilename = "previous-import.jpg",
            fileSizeBytes = sampleBytes.size.toLong(),
            status = MediaAssetStatus.READY,
            sourceProvider = "unsplash",
            externalId = "unsplash:previous",
            sourceUrl = "https://unsplash.com/photos/previous",
            authorName = "Previous",
            authorUrl = "https://unsplash.com/@previous",
            createdAt = java.time.Instant.now(),
        )
        blobRepo.put(
            WorkspaceFileBlob(
                workspaceId = workspaceId,
                fileHash = expectedHash,
                storageKey = "assets/$workspaceId/blobs/$expectedHash.jpg",
                detectedMediaType = "image/jpeg",
                fileSizeBytes = sampleBytes.size.toLong(),
                status = BlobStatus.READY,
                createdAt = java.time.Instant.now(),
            ),
        )

        val storage = ImportRecordingStorage()
        val handler = handler(assetRepo, blobRepo, storage)

        val result = handler.handle(
            ImportExternalAssetCommand(
                workspaceId = workspaceId,
                externalAsset = sampleExternalAsset(),
            ),
        )

        assertTrue(result.deduped)
        assertEquals(existingAssetId, result.assetId)
        assertTrue(storage.copies.isEmpty(), "dedup must NOT touch storage or copy temp bytes")
    }

    @Test
    fun `unverified email rejects import without touching the database`() = runTest {
        val assetRepo = ImportTestMediaAssetRepository()
        val handler = ImportExternalAssetHandler(
            mediaAssetRepository = assetRepo,
            workspaceFileBlobRepository = ImportTestBlobRepository(),
            storageApplicationService = ImportRecordingStorage().service(),
            mediaProvider = NoopMediaProvider(),
            uploadSettings = MediaUploadSettings(5, 200, "bucket"),
            transactionRunner = ImportNoopAtomicTransactionRunner,
            mediaRateLimitRepository = ImportTestRateLimitRepository(),
            principalContextProvider = ImportPrincipalContextProvider,
            principalIdentityLookup = ImportPrincipalIdentityLookup(EmailStatus.PENDING),
            emailVerificationPolicy = emailVerificationPolicyOf(),
        )

        assertThrows<FeatureEmailVerificationRequired> {
            handler.handle(
                ImportExternalAssetCommand(
                    workspaceId = workspaceId,
                    externalAsset = sampleExternalAsset(),
                ),
            )
        }
        assertTrue(assetRepo.assets.isEmpty())
    }

    @Test
    fun `rejects import when concurrent slot is full`() = runTest {
        val assetRepo = ImportTestMediaAssetRepository()
        val storage = ImportRecordingStorage()
        val handler = ImportExternalAssetHandler(
            mediaAssetRepository = assetRepo,
            workspaceFileBlobRepository = ImportTestBlobRepository(),
            storageApplicationService = storage.service(),
            mediaProvider = NoopMediaProvider(),
            uploadSettings = MediaUploadSettings(5, 200, "bucket"),
            transactionRunner = ImportNoopAtomicTransactionRunner,
            mediaRateLimitRepository = ImportTestRateLimitRepository(allowConcurrent = false),
            principalContextProvider = ImportPrincipalContextProvider,
            principalIdentityLookup = ImportPrincipalIdentityLookup(EmailStatus.VERIFIED),
            emailVerificationPolicy = emailVerificationPolicyOf(),
        )

        assertThrows<RateLimitExceededException> {
            handler.handle(
                ImportExternalAssetCommand(
                    workspaceId = workspaceId,
                    externalAsset = sampleExternalAsset(),
                ),
            )
        }
    }

    @Test
    fun `external asset must declare supported MIME or import is rejected`() = runTest {
        val assetRepo = ImportTestMediaAssetRepository()
        val handler = handler(assetRepo, ImportTestBlobRepository(), ImportRecordingStorage())

        val wrongType = sampleExternalAsset().copy(mediaType = "image/svg+xml")

        assertThrows<UnsupportedMediaTypeException> {
            handler.handle(ImportExternalAssetCommand(workspaceId, wrongType))
        }
    }

    private fun sampleExternalAsset(): ProviderExternalAsset = ProviderExternalAsset(
        externalId = externalId,
        mediaType = "image/jpeg",
        contentLength = sampleBytes.size.toLong(),
        bytes = flowOf(sampleBytes),
        sourceProvider = "unsplash",
        sourceUrl = "https://unsplash.com/photos/$photoId",
        authorName = "Jane Creator",
        authorUrl = "https://unsplash.com/@jane",
        metadata = mapOf(
            "color" to "#112233",
            "width" to 800,
            "height" to 600,
        ),
    )

    private fun handler(
        assetRepo: ImportTestMediaAssetRepository,
        blobRepo: ImportTestBlobRepository,
        storage: ImportRecordingStorage,
    ): ImportExternalAssetHandler = ImportExternalAssetHandler(
        mediaAssetRepository = assetRepo,
        workspaceFileBlobRepository = blobRepo,
        storageApplicationService = storage.service(),
        mediaProvider = NoopMediaProvider(),
        uploadSettings = MediaUploadSettings(5, 200, "bucket"),
        transactionRunner = ImportNoopAtomicTransactionRunner,
        mediaRateLimitRepository = ImportTestRateLimitRepository(),
        principalContextProvider = ImportPrincipalContextProvider,
        principalIdentityLookup = ImportPrincipalIdentityLookup(EmailStatus.VERIFIED),
        emailVerificationPolicy = emailVerificationPolicyOf(),
    )
}

// Local helpers shared with this test file only:
//
// These define the test doubles used only by ImportExternalAssetHandlerTest and its
// companions. We define them here as `private` top-level (rather than reusing the
// identically-named helpers in MediaCasHandlersTest.kt or InMemoryTestHelpers.kt)
// to keep the surface minimal and let the compiler catch accidental cross-test
// dependency.

private object ImportPrincipalContextProvider : PrincipalContextProvider {
    override suspend fun current(): PrincipalContext = PrincipalContext(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "local:owner@example.com",
        displayIdentity = "Owner",
        authenticationMethod = "TEST",
    )
}

private class ImportPrincipalIdentityLookup(private val emailStatus: EmailStatus) : PrincipalIdentityLookup {
    override suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts? = facts("principal-1")

    override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = facts("principal-1")

    override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = facts(principalId)

    private fun facts(principalId: String): PrincipalIdentityFacts = PrincipalIdentityFacts(
        principalId = principalId,
        principalType = PrincipalType.USER,
        subject = "local:owner@example.com",
        provider = null,
        displayIdentity = "Owner",
        email = "owner@example.com",
        username = "owner",
        emailStatus = emailStatus,
    )
}

private object ImportNoopAtomicTransactionRunner : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
}

private class ImportRecordingStorage : com.profiletailors.storage.domain.Storage {
    val uploaded = linkedMapOf<String, List<ByteArray>>()
    val deletedKeys = mutableListOf<String>()
    val copies = mutableListOf<Pair<String, String>>()

    fun service() = com.profiletailors.storage.application.StorageApplicationService(
        this,
        ImportNoopEventPublisher(),
        ImportNoopStorageObservation(),
    )

    fun preloadObject(@Suppress("UNUSED_PARAMETER") bucket: String, key: String, bytes: ByteArray) {
        uploaded[key] = listOf(bytes)
    }

    override suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String>) {
        uploaded[key] = content.toList()
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> = flowOf()

    override suspend fun delete(bucket: String, key: String) {
        deletedKeys += key
        uploaded.remove(key)
    }

    override suspend fun list(bucket: String, prefix: String) = uploaded.keys.filter { it.startsWith(prefix) }
    override suspend fun exists(bucket: String, key: String): Boolean = uploaded.containsKey(key)

    override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
        val sourceData = uploaded[sourceKey]
            ?: throw IllegalStateException("copyObject: source not found: $sourceKey")
        copies += sourceKey to destKey
        uploaded[destKey] = sourceData
    }
}

private class ImportNoopStorageObservation : com.profiletailors.storage.domain.StorageObservation {
    override fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean) = Unit
    override fun recordBytesUploaded(bytes: Long, provider: String, bucket: String) = Unit
    override fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String) = Unit
    override fun recordOperationLatency(operation: String, provider: String, durationNanos: Long) = Unit
    override fun recordError(operation: String, provider: String, bucket: String, errorType: String) = Unit
    override fun recordPresignedUrlGenerated(provider: String, success: Boolean) = Unit
    override suspend fun <T : Any> recordOperationTime(
        operation: String,
        provider: String,
        action: suspend () -> T,
    ): T = action()
}

private class ImportNoopEventPublisher :
    com.profiletailors.common.domain.bus.event.EventPublisher<BaseDomainEvent> {
    override suspend fun publish(event: BaseDomainEvent) = Unit
    override suspend fun publish(events: List<BaseDomainEvent>) = Unit
}
