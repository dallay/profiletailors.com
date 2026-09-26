package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.storage.domain.AttachmentsStorageBinding
import com.profiletailors.storage.domain.PresignableStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ThreadsMediaUrlResolverTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val properties = ThreadsPublishingProperties(
        enabled = true,
        clientId = "client",
        clientSecret = "secret",
        redirectUri = "https://app.example.com/callback",
        containerPollInterval = Duration.ofSeconds(2),
        containerPollTimeout = Duration.ofSeconds(10),
        mediaUrlTtl = Duration.ofSeconds(30),
    )

    @Test
    fun `resolver returns HTTPS URLs that outlive workflow deadline and safety margin`() = runTest {
        val resolver = ThreadsProviderMediaUrlResolver(
            properties = properties,
            storage = AttachmentsStorageBinding("r2", "attachments", PresigningStorage("https://cdn.example/media")),
            clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
            safetyMargin = Duration.ofSeconds(5),
        )

        val resolved = resolver.resolve("workspace-1", listOf(asset()), now.plusSeconds(10))

        assertEquals("https://cdn.example/media", resolved.single().url)
        assertEquals(now.plusSeconds(10 + 5), resolved.single().expiresAt)
    }

    @Test
    fun `resolver rejects non HTTPS URLs`() = runTest {
        val resolver = ThreadsProviderMediaUrlResolver(
            properties = properties,
            storage = AttachmentsStorageBinding("r2", "attachments", PresigningStorage("http://cdn.example/media")),
            clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
            safetyMargin = Duration.ofSeconds(5),
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolve("workspace-1", listOf(asset()), now.plusSeconds(10))
            }
        }
    }

    @Test
    fun `resolver rejects a URL TTL shorter than workflow margin`() = runTest {
        val resolver = ThreadsProviderMediaUrlResolver(
            properties = properties.copy(mediaUrlTtl = Duration.ofSeconds(10)),
            storage = AttachmentsStorageBinding("r2", "attachments", PresigningStorage("https://cdn.example/media")),
            clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
            safetyMargin = Duration.ofSeconds(5),
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolve("workspace-1", listOf(asset()), now.plusSeconds(10))
            }
        }
    }

    private fun asset() = PublicationAsset(
        id = "asset-1",
        workspaceId = "workspace-1",
        sourceType = AssetSourceType.UPLOADED,
        mediaType = "image/jpeg",
        storageKey = "assets/workspace-1/asset-1",
        status = PublicationAssetStatus.READY,
        createdByPrincipalId = "principal-1",
    )

    private class PresigningStorage(private val url: String) : PresignableStorage {
        override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String = url
        override suspend fun upload(
            bucket: String,
            key: String,
            content: Flow<ByteArray>,
            metadata: Map<String, String>,
        ) = Unit
        override fun download(bucket: String, key: String): Flow<ByteArray> = emptyFlow()
        override suspend fun delete(bucket: String, key: String) = Unit
        override suspend fun list(bucket: String, prefix: String): List<String> = emptyList()
        override suspend fun exists(bucket: String, key: String): Boolean = true
        override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) = Unit
    }
}
