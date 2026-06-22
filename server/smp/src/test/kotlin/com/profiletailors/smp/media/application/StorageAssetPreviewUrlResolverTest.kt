package com.profiletailors.smp.media.application

import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.Storage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageAssetPreviewUrlResolverTest {

    private val signingSecret = "test-resolver-secret"
    private val tokenService = MediaPreviewTokenService(
        signingSecret = signingSecret,
        previewUrlExpirySeconds = 3_600,
    )
    private val storageBucket = "attachments"

    // --- resolvePreviewUrl ---

    @Test
    fun `resolvePreviewUrl returns null for non-image media type`() = runTest {
        val resolver = StorageAssetPreviewUrlResolver(
            bucketRegistry = fakeRegistry(NonPresignableStorage()),
            mediaPreviewTokenService = tokenService,
            storageBucket = storageBucket,
            previewUrlExpirySeconds = 3_600,
        )

        val result = resolver.resolvePreviewUrl(
            assetId = "asset-1",
            workspaceId = "ws-1",
            mediaType = "video/mp4",
            storageKey = "assets/ws-1/asset-1",
            externalUrl = null,
        )

        assertNull(result)
    }

    @Test
    fun `resolvePreviewUrl returns externalUrl when provided for image types`() = runTest {
        val externalUrl = "https://cdn.example.com/image.jpg"
        val resolver = StorageAssetPreviewUrlResolver(
            bucketRegistry = fakeRegistry(NonPresignableStorage()),
            mediaPreviewTokenService = tokenService,
            storageBucket = storageBucket,
            previewUrlExpirySeconds = 3_600,
        )

        val result = resolver.resolvePreviewUrl(
            assetId = "asset-1",
            workspaceId = "ws-1",
            mediaType = "image/jpeg",
            storageKey = null,
            externalUrl = externalUrl,
        )

        assertEquals(externalUrl, result)
    }

    @Test
    fun `resolvePreviewUrl prefers presigned URL when storage supports presigning`() = runTest {
        val expectedPresigned = "https://s3.example.com/attachments/assets/ws-1/asset-1?signature=abc"
        val resolver = StorageAssetPreviewUrlResolver(
            bucketRegistry = fakeRegistry(FakePresignableStorage(expectedPresigned)),
            mediaPreviewTokenService = tokenService,
            storageBucket = storageBucket,
            previewUrlExpirySeconds = 3_600,
        )

        val result = resolver.resolvePreviewUrl(
            assetId = "asset-1",
            workspaceId = "ws-1",
            mediaType = "image/png",
            storageKey = "assets/ws-1/asset-1",
            externalUrl = null,
        )

        assertEquals(expectedPresigned, result)
    }

    @Test
    fun `resolvePreviewUrl falls back to signed local path when presigning fails`() = runTest {
        val resolver = StorageAssetPreviewUrlResolver(
            bucketRegistry = fakeRegistry(FailingPresignableStorage()),
            mediaPreviewTokenService = tokenService,
            storageBucket = storageBucket,
            previewUrlExpirySeconds = 3_600,
        )

        val result = resolver.resolvePreviewUrl(
            assetId = "asset-1",
            workspaceId = "ws-1",
            mediaType = "image/jpeg",
            storageKey = "assets/ws-1/asset-1",
            externalUrl = null,
        )

        assertTrue(result!!.startsWith("/api/media/assets/asset-1/preview?workspaceId=ws-1&expiresAt="))
    }

    @Test
    fun `resolvePreviewUrl falls back to signed local path when storage is not presignable`() = runTest {
        val resolver = StorageAssetPreviewUrlResolver(
            bucketRegistry = fakeRegistry(NonPresignableStorage()),
            mediaPreviewTokenService = tokenService,
            storageBucket = storageBucket,
            previewUrlExpirySeconds = 3_600,
        )

        val result = resolver.resolvePreviewUrl(
            assetId = "asset-1",
            workspaceId = "ws-1",
            mediaType = "image/gif",
            storageKey = "assets/ws-1/asset-1",
            externalUrl = null,
        )

        assertTrue(result!!.startsWith("/api/media/assets/asset-1/preview?workspaceId=ws-1&expiresAt="))
    }

    @Test
    fun `resolvePreviewUrl returns null when storageKey is blank`() = runTest {
        val resolver = StorageAssetPreviewUrlResolver(
            bucketRegistry = fakeRegistry(NonPresignableStorage()),
            mediaPreviewTokenService = tokenService,
            storageBucket = storageBucket,
            previewUrlExpirySeconds = 3_600,
        )

        val result = resolver.resolvePreviewUrl(
            assetId = "asset-1",
            workspaceId = "ws-1",
            mediaType = "image/webp",
            storageKey = "  ",
            externalUrl = null,
        )

        assertNull(result)
    }

    // --- Helpers ---

    private fun fakeRegistry(storage: Storage): BucketRegistry =
        BucketRegistry { storage }

    private interface FakeStorage : Storage, PresignableStorage

    private class FakePresignableStorage(private val presignedUrl: String) : FakeStorage {
        override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String = presignedUrl
        override suspend fun upload(bucket: String, key: String, content: kotlinx.coroutines.flow.Flow<ByteArray>, metadata: Map<String, String>) {}
        override fun download(bucket: String, key: String): kotlinx.coroutines.flow.Flow<ByteArray> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun delete(bucket: String, key: String) {}
        override suspend fun list(bucket: String, prefix: String): List<String> = emptyList()
        override suspend fun exists(bucket: String, key: String): Boolean = false
    }

    private class FailingPresignableStorage : FakeStorage {
        override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String =
            throw IllegalStateException("Presigning failed")
        override suspend fun upload(bucket: String, key: String, content: kotlinx.coroutines.flow.Flow<ByteArray>, metadata: Map<String, String>) {}
        override fun download(bucket: String, key: String): kotlinx.coroutines.flow.Flow<ByteArray> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun delete(bucket: String, key: String) {}
        override suspend fun list(bucket: String, prefix: String): List<String> = emptyList()
        override suspend fun exists(bucket: String, key: String): Boolean = false
    }

    // NonPresignableStorage only implements Storage, not PresignableStorage
    private class NonPresignableStorage : Storage {
        override suspend fun upload(bucket: String, key: String, content: kotlinx.coroutines.flow.Flow<ByteArray>, metadata: Map<String, String>) {}
        override fun download(bucket: String, key: String): kotlinx.coroutines.flow.Flow<ByteArray> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun delete(bucket: String, key: String) {}
        override suspend fun list(bucket: String, prefix: String): List<String> = emptyList()
        override suspend fun exists(bucket: String, key: String): Boolean = false
    }
}
